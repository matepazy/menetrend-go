package hu.matepazy.menetrendgo

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import org.json.*
import java.time.LocalDateTime
import java.time.Instant

class AppState(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("menetrendgo", 0)
    private val mav=MavClient()
    var passGeography by mutableStateOf<PassGeography?>(null); private set
    init {
        viewModelScope.launch {
            passGeography=withContext(Dispatchers.IO) {
                runCatching {app.assets.open("hungary-counties.txt").reader(Charsets.UTF_8).use {PassGeography(it)}}.getOrNull()
            }
        }
    }
    private val stationSearch=StationSearch(app, mav::stations)
    var theme by mutableStateOf(prefs.getString("theme", "system").orEmpty()); private set
    var showRawGraphQl by mutableStateOf(prefs.getBoolean("show_raw_graphql", false)); private set
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
    var mapVehicle by mutableStateOf<Vehicle?>(null)
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var detailKey = ""
    private var activeSearch: Search? = null
    private data class Search(val from: Station, val to: Station, val time: LocalDateTime, val arrive: Boolean)
    private fun readArray(key: String) = runCatching { JSONArray(prefs.getString(key, "[]")).objects() }.getOrDefault(emptyList())

    suspend fun cachedStations(query: String) = stationSearch.cached(query)
    suspend fun stations(query: String) = stationSearch.search(query)
    fun selectStation(station: Station) { viewModelScope.launch { stationSearch.selected(station) } }
    fun chooseTheme(value: String) { theme = value; prefs.edit().putString("theme", value).apply() }
    fun chooseRawGraphQl(enabled: Boolean) { showRawGraphQl=enabled; prefs.edit().putBoolean("show_raw_graphql",enabled).apply() }
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
                val next = mav.plan(criteria.from,criteria.to,edge,criteria.arrive)
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
            val snapshot=mav.vehicles()
            vehicles=snapshot.first
            vehicleUpdated=snapshot.second
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
            try { detail = mav.trip(id,day) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { detailError = e.message }
            finally { detailLoading = false }
        }
    }
}
