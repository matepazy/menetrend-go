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
    val updates = UpdateController(app, viewModelScope)
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
    var passReminders by mutableStateOf(PassNotifications.enabledReminders(app)); private set
    fun choosePassReminder(reminder: PassReminder, enabled: Boolean) {
        PassNotifications.setEnabled(getApplication(), reminder, enabled)
        passReminders = PassNotifications.enabledReminders(getApplication())
    }
    var developerSettingsUnlocked by mutableStateOf(prefs.getBoolean("developer_settings_unlocked", false)); private set
    var showRawGraphQl by mutableStateOf(prefs.getBoolean("show_raw_graphql", false)); private set
    var mapRefreshSeconds by mutableIntStateOf(prefs.getInt("map_refresh_seconds", 75).coerceIn(15, 90)); private set
    var customMapEnabled by mutableStateOf(prefs.getBoolean("custom_map_enabled", false)); private set
    var unofficialGoogleMapEnabled by mutableStateOf(prefs.getBoolean("unofficial_google_map_enabled", false)); private set
    var mapType by mutableStateOf(MapType.fromId(prefs.getString("map_type", "standard"))); private set
    var vehicleFilters by mutableStateOf(MapVehicleFilter.entries.filter { prefs.getBoolean(it.prefKey, it.defaultEnabled) }.toSet()); private set
    val showLocalBuses get() = MapVehicleFilter.LocalBus in vehicleFilters
    var customMapUrl by mutableStateOf(prefs.getString("custom_map_url", "").orEmpty()); private set
    val effectiveMapType get() = if (mapType == MapType.Custom && (!customMapEnabled || !validMapTileUrl(customMapUrl))) MapType.Standard else mapType
    var recents by mutableStateOf(readArray("recents").mapNotNull { runCatching { Recent(Station.parse(it.getJSONObject("from")), Station.parse(it.getJSONObject("to"))) }.getOrNull() }); private set
    var passes by mutableStateOf(readArray("passes").mapNotNull { runCatching { Pass.parse(it) }.getOrNull() }); private set
    var from by mutableStateOf<Station?>(null)
    var to by mutableStateOf<Station?>(null)
    var dateTime by mutableStateOf(LocalDateTime.now(Budapest).withSecond(0).withNano(0))
    var useNow by mutableStateOf(true)
    var arriveBy by mutableStateOf(false)
    var searchFilters by mutableStateOf(SearchFilter.entries.filter { prefs.getBoolean("search_filter_${it.name}", false) }.toSet()); private set
    var activeSearchFilters by mutableStateOf<Set<SearchFilter>>(emptySet()); private set
    val filteredJourneys get() = filterJourneys(journeys, activeSearchFilters)
    fun chooseSearchFilter(filter: SearchFilter, enabled: Boolean) {
        searchFilters = if (enabled) searchFilters + filter else searchFilters - filter
        prefs.edit().putBoolean("search_filter_${filter.name}", enabled).apply()
    }
    fun clearSearchFilters() {
        searchFilters = emptySet()
        prefs.edit().also { editor -> SearchFilter.entries.forEach { editor.remove("search_filter_${it.name}") } }.apply()
    }
    var journeys by mutableStateOf<List<Journey>>(emptyList()); private set
    var searching by mutableStateOf(false); private set
    var searchError by mutableStateOf<String?>(null); private set
    var canLoadMore by mutableStateOf(false); private set
    var canLoadEarlier by mutableStateOf(false); private set
    var loadingEarlier by mutableStateOf(false); private set
    var earlierRequested by mutableStateOf(false); private set
    private var failedSearchMore = false
    private var failedSearchEarlier = false
    var vehicles by mutableStateOf<List<Vehicle>>(emptyList()); private set
    var vehicleUpdated by mutableLongStateOf(0L); private set
    var vehicleError by mutableStateOf<String?>(null); private set
    var vehiclesLoading by mutableStateOf(false); private set
    var detail by mutableStateOf<JSONObject?>(null); private set
    var detailError by mutableStateOf<String?>(null); private set
    var detailLoading by mutableStateOf(false); private set
    var selectedJourney by mutableStateOf<Journey?>(null)
    var searchGeneration by mutableIntStateOf(0); private set
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
    fun unlockDeveloperSettings() {
        developerSettingsUnlocked = true
        prefs.edit().putBoolean("developer_settings_unlocked", true).apply()
    }
    fun chooseRawGraphQl(enabled: Boolean) { showRawGraphQl=enabled; prefs.edit().putBoolean("show_raw_graphql",enabled).apply() }
    fun chooseMapRefreshSeconds(seconds: Int) {
        mapRefreshSeconds = seconds.coerceIn(15, 90)
        prefs.edit().putInt("map_refresh_seconds", mapRefreshSeconds).apply()
    }
    fun chooseVehicleFilter(filter: MapVehicleFilter, enabled: Boolean) { vehicleFilters = if (enabled) vehicleFilters + filter else vehicleFilters - filter; prefs.edit().putBoolean(filter.prefKey, enabled).apply() }
    fun chooseLocalBuses(enabled: Boolean) = chooseVehicleFilter(MapVehicleFilter.LocalBus, enabled)
    fun chooseMapType(value: MapType) {
        if (value == MapType.Custom && (!customMapEnabled || !validMapTileUrl(customMapUrl))) return
        mapType=value; prefs.edit().putString("map_type",value.id).apply()
    }
    fun chooseCustomMapEnabled(enabled: Boolean) {
        customMapEnabled=enabled
        if (!enabled && mapType == MapType.Custom) mapType=MapType.Standard
        prefs.edit().putBoolean("custom_map_enabled",enabled).putString("map_type",mapType.id).apply()
    }
    fun chooseUnofficialGoogleMapEnabled(enabled: Boolean) {
        unofficialGoogleMapEnabled = enabled
        prefs.edit().putBoolean("unofficial_google_map_enabled", enabled).apply()
    }
    fun applyCustomMapUrl(value: String) {
        val url=value.trim()
        if (!customMapEnabled || !validMapTileUrl(url)) return
        customMapUrl=url; mapType=MapType.Custom
        prefs.edit().putString("custom_map_url",url).putString("map_type",mapType.id).apply()
    }
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
    fun retrySearch() = search(failedSearchMore, failedSearchEarlier)
    fun loadEarlier() {
        if (!searching && canLoadEarlier) {
            earlierRequested = true
            search(more = true, earlier = true)
        }
    }
    fun search(more: Boolean = false, earlier: Boolean = false) {
        if (more && searching) return
        val criteria = if (more) activeSearch else {
            val f = from ?: return; val t = to ?: return
            Search(f, t, if (useNow) LocalDateTime.now(Budapest) else dateTime, arriveBy).also {
                activeSearch = it
                recents = (listOf(Recent(f, t)) + recents.filterNot { r -> r.from.id == f.id && r.to.id == t.id }).take(8)
                prefs.edit().putString("recents", JSONArray(recents.map { r -> r.json() }).toString()).apply()
            }
        } ?: return
        if (!more) { searchGeneration++; activeSearchFilters = searchFilters.toSet() }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searching = true; loadingEarlier = earlier; searchError = null
            failedSearchMore = more; failedSearchEarlier = earlier
            if (!more) { journeys = emptyList(); canLoadMore = false; canLoadEarlier = false; earlierRequested = false }
            try {
                val edge = if (more && journeys.isNotEmpty()) {
                    val epoch = if (earlier) journeys.minOf { it.end } - 60_000 else journeys.maxOf { it.start } + 60_000
                    Instant.ofEpochMilli(epoch).atZone(Budapest).toLocalDateTime()
                } else criteria.time
                val next = mav.plan(criteria.from,criteria.to,edge,if(more) earlier else criteria.arrive,activeSearchFilters)
                val fresh = next.filterNot { n -> journeys.any { it.key == n.key } }
                journeys = (journeys + fresh).distinctBy { it.key }.sortedBy { it.start }
                if (!more) {
                    canLoadMore = fresh.isNotEmpty()
                    canLoadEarlier = fresh.isNotEmpty()
                } else if (earlier) canLoadEarlier = fresh.isNotEmpty()
                else canLoadMore = fresh.isNotEmpty()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { searchError = e.message ?: "Nem sikerült betölteni a járatokat." }
            finally { searching = false; loadingEarlier = false }
        }
    }
    suspend fun refreshVehicles(forceRefresh: Boolean = false) {
        if (vehiclesLoading) return
        vehiclesLoading = true
        try {
            val snapshot=mav.vehicles(forceRefresh, mapRefreshSeconds)
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
