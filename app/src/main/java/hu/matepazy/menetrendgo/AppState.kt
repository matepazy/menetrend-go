package hu.matepazy.menetrendgo

import android.app.Application
import android.os.SystemClock
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
    var passesEnabled by mutableStateOf(PassNotifications.featureEnabled(app)); private set
    // Decide eligibility once, before this installation can add any new passes.
    // Persist a pending notice so recreation cannot consume it before a user responds.
    var showPassesFeatureIntro by mutableStateOf(
        prefs.getBoolean("passes_feature_intro_pending", !passesEnabled && PassNotifications.saved(app).isNotEmpty())
            .also { pending ->
                if (!prefs.contains("passes_feature_intro_pending")) {
                    prefs.edit().putBoolean("passes_feature_intro_pending", pending).apply()
                }
            }
    ); private set
    fun dismissPassesFeatureIntro() {
        showPassesFeatureIntro = false
        prefs.edit().putBoolean("passes_feature_intro_pending", false).apply()
    }
    fun choosePassesEnabled(enabled: Boolean) {
        passesEnabled = enabled
        if (enabled) dismissPassesFeatureIntro()
        prefs.edit().putBoolean("passes_enabled", enabled).apply()
        PassNotifications.saved(getApplication()).forEach { pass ->
            if (enabled) PassNotifications.schedule(getApplication(), pass)
            else PassNotifications.cancel(getApplication(), pass)
        }
    }
    val activeExtraFeatureCount get() = listOf(journeyTrackingBetaEnabled, passesEnabled).count { it }
    val extraFeaturesSummary get() = if (activeExtraFeatureCount == 0) "Nincs aktív kiegészítő funkció"
        else "$activeExtraFeatureCount funkció bekapcsolva"
    var developerSettingsUnlocked by mutableStateOf(prefs.getBoolean("developer_settings_unlocked", false)); private set
    var journeyTrackingBetaEnabled by mutableStateOf(JourneyTracking.enabled(app)); private set
    init { if (!journeyTrackingBetaEnabled) JourneyTracking.stop(app) }
    fun chooseJourneyTrackingBeta(enabled: Boolean) {
        journeyTrackingBetaEnabled = enabled
        prefs.edit().putBoolean("journey_tracking_beta_enabled", enabled).apply()
        if (!enabled) JourneyTracking.stop(getApplication())
    }
    var showRawGraphQl by mutableStateOf(prefs.getBoolean("show_raw_graphql", false)); private set
    var rapidRefresh by mutableStateOf(
        prefs.getBoolean("rapid_refresh", prefs.getInt("map_refresh_seconds", 75) < 45).also { enabled ->
            // Migrate once, so later explicit choices always take precedence over the old slider.
            if (!prefs.contains("rapid_refresh")) prefs.edit().putBoolean("rapid_refresh", enabled).apply()
        }
    ); private set
    val mapRefreshSeconds get() = if (rapidRefresh) 15 else 75
    val journeyRefreshSeconds get() = if (rapidRefresh) 30 else 75
    var customMapEnabled by mutableStateOf(prefs.getBoolean("custom_map_enabled", false)); private set
    var unofficialGoogleMapEnabled by mutableStateOf(prefs.getBoolean("unofficial_google_map_enabled", false)); private set
    var mapType by mutableStateOf(MapType.fromId(prefs.getString("map_type", "standard"))); private set
    var vehicleFilters by mutableStateOf(MapVehicleFilter.entries.filter { prefs.getBoolean(it.prefKey, it.defaultEnabled) }.toSet()); private set
    val showLocalBuses get() = MapVehicleFilter.LocalBus in vehicleFilters
    var showMapStops by mutableStateOf(prefs.getBoolean("show_map_stops", true)); private set
    fun chooseMapStops(enabled: Boolean) { showMapStops = enabled; prefs.edit().putBoolean("show_map_stops", enabled).apply() }
    var customMapUrl by mutableStateOf(prefs.getString("custom_map_url", "").orEmpty()); private set
    val effectiveMapType get() = if (mapType == MapType.Custom && (!customMapEnabled || !validMapTileUrl(customMapUrl))) MapType.Standard else mapType
    var recents by mutableStateOf(readArray("recents").mapNotNull { runCatching { Recent(Station.parse(it.getJSONObject("from")), Station.parse(it.getJSONObject("to"))) }.getOrNull() }); private set
    var passes by mutableStateOf(readArray("passes").mapNotNull { runCatching { Pass.parse(it) }.getOrNull() }); private set
    var savedSearches by mutableStateOf(readArray("saved_searches").mapNotNull {
        runCatching { SavedSearch.parse(it) }.getOrNull()
    }.distinctBy { it.key }); private set
    var showSavedSearches by mutableStateOf(prefs.getBoolean("show_saved_searches", false)); private set
    var from by mutableStateOf<Station?>(null)
    var to by mutableStateOf<Station?>(null)
    var dateTime by mutableStateOf(LocalDateTime.now(Budapest).withSecond(0).withNano(0))
    var useNow by mutableStateOf(true)
    var arriveBy by mutableStateOf(false)
    var searchFilters by mutableStateOf(SearchFilter.entries.filter { prefs.getBoolean("search_filter_${it.name}", false) }.toSet()); private set
    var activeSearchFilters by mutableStateOf<Set<SearchFilter>>(emptySet()); private set
    var searchVehicles by mutableStateOf(SearchVehicle.entries.filter { it.name in prefs.getStringSet("search_vehicles", emptySet()).orEmpty() }.toSet()); private set
    var activeSearchVehicles by mutableStateOf<Set<SearchVehicle>>(emptySet()); private set
    val searchConditionCount get() = searchFilters.size + if (searchVehicles.isEmpty()) 0 else 1
    val searchConditionSummary get() = listOf(searchVehicleSummary(searchVehicles), searchFilters.sortedBy { it.ordinal }.joinToString(" · ") { it.title }).filter { it.isNotBlank() }.joinToString(" · ")
    val filteredJourneys get() = filterJourneyVehicles(filterJourneys(journeys, activeSearchFilters), activeSearchVehicles)
    fun chooseSearchVehicles(vehicles: Set<SearchVehicle>) {
        searchVehicles = if (vehicles.size == SearchVehicle.entries.size) emptySet() else vehicles.toSet()
        prefs.edit().putStringSet("search_vehicles", searchVehicles.map { it.name }.toSet()).apply()
    }
    fun chooseSearchVehicle(vehicle: SearchVehicle) {
        chooseSearchVehicles(toggleSearchVehicle(searchVehicles, vehicle))
    }
    fun chooseSearchFilter(filter: SearchFilter, enabled: Boolean) {
        searchFilters = if (enabled) searchFilters + filter else searchFilters - filter
        prefs.edit().putBoolean("search_filter_${filter.name}", enabled).apply()
    }
    fun clearSearchFilters() {
        searchFilters = emptySet()
        chooseSearchVehicles(emptySet())
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
    var networkAlerts by mutableStateOf<List<NetworkAlert>?>(null); private set
    var networkAlertsUpdated by mutableLongStateOf(0L); private set
    var networkAlertsLoading by mutableStateOf(false); private set
    var networkAlertsError by mutableStateOf<String?>(null); private set
    private var networkAlertsJob: Job?=null
    fun loadNetworkAlerts(force: Boolean=false) {
        if(networkAlertsJob?.isActive==true) return
        networkAlertsJob=viewModelScope.launch {
            networkAlertsLoading=true
            try {
                val (items,updated)=mav.networkAlerts(force)
                networkAlerts=items;networkAlertsUpdated=updated;networkAlertsError=null
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {networkAlertsError=e.message ?: "A közlemények nem tölthetők be."}
            finally {networkAlertsLoading=false}
        }
    }
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
    val resultFrom get() = activeSearch?.from ?: from
    val resultTo get() = activeSearch?.to ?: to
    private data class Search(val from: Station, val to: Station, val time: LocalDateTime, val arrive: Boolean)
    private fun readArray(key: String) = runCatching { JSONArray(prefs.getString(key, "[]")).objects() }.getOrDefault(emptyList())

    suspend fun cachedStations(query: String) = stationSearch.cached(query)
    suspend fun stations(query: String) = stationSearch.search(query)
    fun selectStation(station: Station) { if(station.id.isNotBlank()) viewModelScope.launch { stationSearch.selected(station) } }
    fun chooseTheme(value: String) {
        theme = value
        prefs.edit().putString("theme", value).apply()
        val context = getApplication<Application>()
        StopBoardWidget.widgetIds(context).forEach { StopBoardWidget.render(context, it) }
    }
    fun unlockDeveloperSettings() {
        developerSettingsUnlocked = true
        prefs.edit().putBoolean("developer_settings_unlocked", true).apply()
    }
    fun chooseRawGraphQl(enabled: Boolean) { showRawGraphQl=enabled; prefs.edit().putBoolean("show_raw_graphql",enabled).apply() }
    fun chooseRapidRefresh(enabled: Boolean) {
        rapidRefresh = enabled
        prefs.edit().putBoolean("rapid_refresh", enabled).apply()
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
    fun removeRecent(recent: Recent) {
        recents = recents.filterNot { it.from.id == recent.from.id && it.to.id == recent.to.id }
        prefs.edit().putString("recents", JSONArray(recents.map { it.json() }).toString()).apply()
    }
    fun clearRecents() { recents = emptyList(); prefs.edit().remove("recents").apply() }
    fun chooseSearchCollection(saved: Boolean) {
        showSavedSearches = saved
        prefs.edit().putBoolean("show_saved_searches", saved).apply()
    }
    fun saveSearch(from: Station, to: Station, name: String, icon: SavedSearchIcon) {
        val label = name.trim().take(SavedSearch.MAX_NAME_LENGTH)
        if (!canSaveRoute(from, to) || label.isEmpty()) return
        val saved = SavedSearch(from, to, label, icon)
        val index = savedSearches.indexOfFirst { it.key == saved.key }
        savedSearches = if (index < 0) listOf(saved) + savedSearches else
            savedSearches.toMutableList().apply { this[index] = saved }
        persistSavedSearches()
    }
    fun removeSavedSearch(saved: SavedSearch) {
        savedSearches = savedSearches.filterNot { it.key == saved.key }
        persistSavedSearches()
    }
    private fun persistSavedSearches() {
        prefs.edit().putString("saved_searches", JSONArray(savedSearches.map { it.json() }).toString()).apply()
    }
    fun useSavedSearch(saved: SavedSearch) {
        from = saved.from; to = saved.to; useNow = true; arriveBy = false
        dateTime = LocalDateTime.now(Budapest).withSecond(0).withNano(0)
    }
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
        val requestedCriteria = if (more) activeSearch else {
            val f = from ?: return; val t = to ?: return
            Search(f, t, if (useNow) LocalDateTime.now(Budapest) else dateTime, arriveBy).also {
                activeSearch = it
                // Current-location searches do not belong in reusable stop history.
                if(f.id.isNotBlank() && t.id.isNotBlank()) {
                    recents = (listOf(Recent(f, t)) + recents.filterNot { r -> r.from.id == f.id && r.to.id == t.id }).take(8)
                    prefs.edit().putString("recents", JSONArray(recents.map { r -> r.json() }).toString()).apply()
                }
            }
        } ?: return
        if (!more) { searchGeneration++; activeSearchFilters = searchFilters.toSet(); activeSearchVehicles = searchVehicles.toSet() }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searching = true; loadingEarlier = earlier; searchError = null
            failedSearchMore = more; failedSearchEarlier = earlier
            if (!more) { journeys = emptyList(); canLoadMore = false; canLoadEarlier = false; earlierRequested = false }
            try {
                val criteria = if (more) requestedCriteria else {
                    val (freshFrom, freshTo) = refreshSearchLocations(requestedCriteria.from, requestedCriteria.to, SystemClock.elapsedRealtime()) {
                        currentDepartureLocation(getApplication())?.asCurrentLocationStation()
                            ?: error("Nem sikerült meghatározni a helyzeted. Ellenőrizd a helyhozzáférést és a helymeghatározást, majd próbáld újra.")
                    }
                    if (from == requestedCriteria.from) from = freshFrom
                    if (to == requestedCriteria.to) to = freshTo
                    requestedCriteria.copy(from = freshFrom, to = freshTo).also { activeSearch = it }
                }
                val edge = if (more && journeys.isNotEmpty()) {
                    val epoch = if (earlier) journeys.minOf { it.end } - 60_000 else journeys.maxOf { it.start } + 60_000
                    Instant.ofEpochMilli(epoch).atZone(Budapest).toLocalDateTime()
                } else criteria.time
                val next = mav.plan(criteria.from,criteria.to,edge,if(more) earlier else criteria.arrive,activeSearchFilters,activeSearchVehicles)
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
    internal suspend fun refreshDepartures() {
        if (searching || journeys.isEmpty()) return
        val generation = searchGeneration
        val snapshot = journeys
        val refreshed = refreshDepartureJourneys(snapshot) { id, day -> mav.trip(id, day, forceRefresh = true) }
        // Pagination can finish during the refresh. Keep its additions, and ignore a replaced search.
        if (generation != searchGeneration) return
        val replacements = snapshot.zip(refreshed).associate { (old, fresh) -> old.key to fresh }
        journeys = journeys.map { replacements[it.key] ?: it }
    }

    internal suspend fun refreshJourneyTimes(journey: Journey, previous: Map<Int, TransferTimes>) =
        refreshJourneyLegTimes(journey, previous) { id, day -> mav.trip(id, day, forceRefresh = true) }

    /** Optional one-line fallback. Failures and missing matches stay silent. */
    internal suspend fun refreshTransferAlternatives(journey: Journey, times: Map<Int, TransferTimes>): Map<Int, TransferTimes> {
        val result = times.toMutableMap()
        for (connection in journeyTransfers(journey)) {
            val current = times.getValue(connection.incomingIndex)
            val now = System.currentTimeMillis()
            if (transferWarning(connection, current, now) == null || current.arrival.cancelled) continue
            val incoming = journey.legs[connection.incomingIndex]
            val outgoing = journey.legs[connection.outgoingIndex]
            try {
                val modes = SearchVehicle.entries.filter { it.mode == outgoing.mode }.toSet()
                val plans = mav.plan(incoming.to, outgoing.to,
                    onwardSearchTime(current.arrival.time.estimate + 120_000, now), false,
                    journey.providerFilters, modes)
                val candidates = reachableTransferCandidates(plans, outgoing, current.arrival.time.estimate, now)
                val verified = mutableListOf<TransferAlternative>()
                for (candidate in candidates.take(3)) {
                    val leg = candidate.leg
                    val day = Instant.ofEpochMilli(leg.departureTime.scheduled).atZone(Budapest).toLocalDate()
                    for (serviceDay in listOf(day, day.minusDays(1))) {
                        val trip = try { mav.trip(leg.tripId, serviceDay.toString(), forceRefresh = true) }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { continue }
                        val fetchedAt = System.currentTimeMillis()
                        val departure = transferStopEstimate(trip, leg, false, fetchedAt) ?: continue
                        val arrival = transferStopEstimate(trip, leg, true, fetchedAt) ?: continue
                        if (!departure.cancelled && !arrival.cancelled &&
                            departure.time.estimate >= maxOf(candidate.readyAt, fetchedAt + 120_000)) {
                            verified += TransferAlternative(departure.time, leg.route, fetchedAt)
                        }
                        break
                    }
                }
                result[connection.incomingIndex] = current.copy(nextDeparture = verified.minByOrNull { it.time.estimate })
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* The warning alone is the complete fallback UI. */ }
        }
        return result
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
            try { detail = mav.trip(id,day,refreshSeconds=mapRefreshSeconds) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { detailError = e.message }
            finally { detailLoading = false }
        }
    }
}
