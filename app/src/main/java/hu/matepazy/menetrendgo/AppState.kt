package hu.matepazy.menetrendgo

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.*
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.Instant
import java.time.format.DateTimeFormatter

class AppState(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("menetrendgo", 0)
    var backend by mutableStateOf(prefs.getString("backend", BuildConfig.API_URL).orEmpty()); private set
    var theme by mutableStateOf(prefs.getString("theme", "system").orEmpty()); private set
    var recents by mutableStateOf(readArray("recents").mapNotNull { runCatching { Recent(Station.parse(it.getJSONObject("from")), Station.parse(it.getJSONObject("to"))) }.getOrNull() }); private set
    var passes by mutableStateOf(readArray("passes").mapNotNull { runCatching { Pass.parse(it) }.getOrNull() }); private set
    var from by mutableStateOf<Station?>(null)
    var to by mutableStateOf<Station?>(null)
    var dateTime by mutableStateOf(LocalDateTime.now(Budapest).withSecond(0).withNano(0))
    var useNow by mutableStateOf(true)
    var arriveBy by mutableStateOf(false)
    var journeys by mutableStateOf<List<Journey>>(emptyList()); private set
    var searching by mutableStateOf(false); private set
    var searchError by mutableStateOf<String?>(null); private set
    var canLoadMore by mutableStateOf(false); private set
    var vehicles by mutableStateOf<List<Vehicle>>(emptyList()); private set
    var vehicleUpdated by mutableLongStateOf(0L); private set
    var vehicleError by mutableStateOf<String?>(null); private set
    var vehiclesLoading by mutableStateOf(false); private set
    var detail by mutableStateOf<JSONObject?>(null); private set
    var detailError by mutableStateOf<String?>(null); private set
    var detailLoading by mutableStateOf(false); private set
    var selectedJourney by mutableStateOf<Journey?>(null)
    var trackedJourney by mutableStateOf<Journey?>(null)
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var detailKey = ""
    private var activeSearch: Search? = null
    private data class Search(val from: Station, val to: Station, val time: LocalDateTime, val arrive: Boolean)
    private fun readArray(key: String) = runCatching { JSONArray(prefs.getString(key, "[]")).objects() }.getOrDefault(emptyList())

    suspend fun api(path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        check(backend.isNotBlank()) { "Előbb add meg a kiszolgáló címét a Beállításokban." }
        val connection = URI(backend.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 40_000
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result = runCatching { JSONObject(raw) }.getOrElse { throw IllegalStateException("A kiszolgáló nem megfelelő választ adott ($code).") }
            check(code in 200..299 && !result.has("error")) { result.optString("error", "Kapcsolódási hiba ($code).") }
            result
        } finally { connection.disconnect() }
    }
    suspend fun stations(query: String) = api("/api/stations?q=" + URLEncoder.encode(query, "UTF-8")).array("stations").map(Station::parse)
    fun configure(url: String, selectedTheme: String): String? {
        val cleaned = url.trim().trimEnd('/')
        val valid = runCatching { val u = URI(cleaned); u.host != null && u.userInfo == null && u.query == null && u.fragment == null &&
            (u.scheme == "https" || (BuildConfig.DEBUG && u.scheme == "http" && u.host in listOf("10.0.2.2", "localhost", "127.0.0.1"))) }.getOrDefault(false)
        if (!valid) return "Adj meg egy HTTPS kiszolgálócímet."
        backend = cleaned; theme = selectedTheme
        prefs.edit().putString("backend", cleaned).putString("theme", selectedTheme).apply()
        searchJob?.cancel(); detailJob?.cancel(); journeys = emptyList(); vehicles = emptyList(); vehicleUpdated = 0; detail = null
        return null
    }
    fun chooseTheme(value: String) { theme = value; prefs.edit().putString("theme", value).apply() }
    fun clearRecents() { recents = emptyList(); prefs.edit().remove("recents").apply() }
    fun addPass(pass: Pass) {
        passes = passes.filterNot { it.id == pass.id } + pass
        savePasses(); PassNotifications.schedule(getApplication(), pass)
    }
    fun removePass(pass: Pass) {
        PassNotifications.cancel(getApplication(), pass)
        passes = passes.filterNot { it.id == pass.id }; savePasses()
    }
    private fun savePasses() { prefs.edit().putString("passes", JSONArray(passes.map { it.json() }).toString()).apply() }
    fun search(more: Boolean = false) {
        val criteria = if (more) activeSearch else {
            val f = from ?: return; val t = to ?: return
            Search(f, t, if (useNow) LocalDateTime.now(Budapest) else dateTime, arriveBy).also {
                activeSearch = it
                recents = (listOf(Recent(f, t)) + recents.filterNot { r -> r.from.id == f.id && r.to.id == t.id }).take(8)
                prefs.edit().putString("recents", JSONArray(recents.map { r -> r.json() }).toString()).apply()
            }
        } ?: return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searching = true; searchError = null
            if (!more) { journeys = emptyList(); canLoadMore = false }
            try {
                val edge = if (more && journeys.isNotEmpty()) {
                    val epoch = if (criteria.arrive) journeys.minOf { it.end } - 60_000 else journeys.maxOf { it.start } + 60_000
                    Instant.ofEpochMilli(epoch).atZone(Budapest).toLocalDateTime()
                } else criteria.time
                val json = api("/api/plan", JSONObject().put("from", criteria.from.json()).put("to", criteria.to.json())
                    .put("dateTime", edge.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).put("arriveBy", criteria.arrive))
                val next = json.getJSONObject("plan").array("itineraries").map(Journey::parse).filter { it.legs.isNotEmpty() }
                val fresh = next.filterNot { n -> journeys.any { it.key == n.key } }
                journeys = (journeys + fresh).distinctBy { it.key }.sortedBy { it.start }
                canLoadMore = fresh.isNotEmpty()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { searchError = e.message ?: "Nem sikerült betölteni a járatokat." }
            finally { searching = false }
        }
    }
    suspend fun refreshVehicles() {
        if (vehiclesLoading) return
        vehiclesLoading = true
        try {
            val json = api("/live_data.json")
            vehicles = json.array("vehicles").mapNotNull { v -> runCatching { Vehicle(v.getString("id"), v.optString("name"), v.optString("headsign"), v.optString("mode"), v.getDouble("lat"), v.getDouble("lon")) }.getOrNull() }
            vehicleUpdated = json.optLong("lastUpdated")
            vehicleError = null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { vehicleError = e.message ?: "Nem sikerült frissíteni a járműveket." }
        finally { vehiclesLoading = false }
    }
    fun loadTrip(id: String, day: String) {
        detailJob?.cancel()
        val key="$id|$day"
        if(detailKey!=key) detail=null
        detailKey=key
        detailError = null
        if (id.isBlank()) { detailError = "Ehhez a szakaszhoz nincs járatazonosító."; return }
        detailJob = viewModelScope.launch {
            detailLoading = true
            try { detail = api("/api/trip?id=" + URLEncoder.encode(id, "UTF-8") + "&date=" + day).optJSONObject("trip") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { detailError = e.message }
            finally { detailLoading = false }
        }
    }
}
