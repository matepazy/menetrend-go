package hu.matepazy.menetrendgo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/** Live vehicles for the map: the list, the oldest contributing fetch time (epoch seconds), and a notice
 * when one source failed but another answered. */
data class VehicleSnapshot(val vehicles: List<Vehicle>, val updatedSeconds: Long, val notice: String? = null)

/**
 * The app's single way to transit data (docs/ADATRETEG.md, 3–4. fejezet). It decides which source answers:
 * MÁVPlusz is primary; utas.hu only augments live vehicles and its own trips; the downloaded offline
 * timetables answer when MÁVPlusz is unavailable (or Offline mód is on) and they cover the request, and beside a
 * working MÁVPlusz answer they add the stops, departures and journeys it does not know ([OfflineSupplement]).
 * One shared [TransitCache] serves every screen, the widget and the tracking service.
 *
 * For now it speaks the types the screens use; the canonical providers ([MavProvider]) sit beside it.
 */
class TransitRepository(
    private val mav: MavClient = MavClient(),
    private val utas: UtasClient = UtasClient(),
    private val offline: suspend () -> OfflineNetwork? = { OfflineSchedules.network() },
    private val cache: TransitCache = TransitCache(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        val shared: TransitRepository by lazy { TransitRepository() }
        /** The user's map refresh setting, bounded like the provider polling it drives. */
        internal fun freshnessMillis(refreshSeconds: Int) = refreshSeconds.coerceIn(15, 90) * 1000L
        private const val ALERTS_MAX_AGE = 120_000L
        /** A failed utas.hu poll keeps the last good list this long, so buses don't blink off the map. */
        private const val UTAS_STALE = 90_000L
    }

    /** The fallback role: answer from the offline network only when MÁVPlusz is unavailable and the
     * timetables cover the request; otherwise the original failure stands. */
    private suspend fun <T> primaryOrOffline(fallback: (OfflineNetwork) -> T, primary: suspend () -> T): T = try {
        primary()
    } catch (e: ProviderUnavailable) {
        val network = offline() ?: throw e
        try { withContext(Dispatchers.Default) { fallback(network) } } catch (_: OfflineNotCovered) { throw e }
    }

    /** The supplement role: the offline answer beside a live request. It never fails or delays that request's error. */
    private suspend fun <T> supplement(block: (OfflineNetwork) -> T): T? = try {
        offline()?.let { net -> withContext(Dispatchers.Default) { block(net) } }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    // ---- Stops ----
    suspend fun stations(query: String): List<Station> = coroutineScope {
        val local = async { supplement { it.searchStops(query) }.orEmpty() }
        OfflineSupplement.stations(mav.stations(query), local.await())
    }
    /** Stops by name from the downloaded timetables, for when [stations] cannot reach the provider. */
    suspend fun offlineStations(query: String): List<Station> =
        offline()?.let { net -> withContext(Dispatchers.Default) { net.searchStops(query) } }.orEmpty()
    suspend fun nearbyStops(lat: Double, lon: Double, radius: Int): List<Station> = coroutineScope {
        val local = async { supplement { it.stationsNear(lat, lon, radius.toDouble()) }.orEmpty() }
        OfflineSupplement.stations(mav.nearbyStops(lat, lon, radius), local.await())
    }

    // ---- Departures, journeys, trips ----
    /** The id's namespace picks the source: a MÁVPlusz stop asks MÁVPlusz (offline as fallback), an offline-only
     * stop goes straight to the timetables. [station] lets the offline lookup match by name when the id is unknown there. */
    suspend fun stopDepartures(id: String, now: Long = clock(), station: Station? = null): List<StopDeparture> {
        val offlineDepartures = { net: OfflineNetwork -> net.departures(station?.takeIf { it.id == id } ?: Station(id, "", 0.0, 0.0), now) }
        if (StationIds.source(id) == "gtfs") {
            val network = offline() ?: throw ProviderUnavailable("Ehhez a megállóhoz csak letöltött menetrend tartozik, de most nem érhető el.")
            return try { withContext(Dispatchers.Default) { offlineDepartures(network) } }
            catch (e: OfflineNotCovered) { throw ProviderUnavailable("Ehhez a megállóhoz nincs letöltött menetrend.", e) }
        }
        return primaryOrOffline(offlineDepartures) { coroutineScope {
            val local = async { supplement(offlineDepartures).orEmpty() }
            OfflineSupplement.departures(mav.stopDepartures(StationIds.local(id), now), local.await())
        } }
    }

    suspend fun plan(from: Station, to: Station, time: LocalDateTime, arrive: Boolean,
        filters: Set<SearchFilter> = emptySet(), vehicles: Set<SearchVehicle> = emptySet()): List<Journey> =
        primaryOrOffline({ net -> net.plan(from, to, time, arrive) }) { coroutineScope {
            val local = async { supplement { it.plan(from, to, time, arrive) }.orEmpty() }
            OfflineSupplement.journeys(mav.plan(from, to, time, arrive, filters, vehicles), local.await(), vehicles)
        } }

    /** Only MÁVPlusz answers are cached; an offline answer is computed fresh, as it costs no request.
     * A trip MÁVPlusz does not know (one from an offline-only network) comes from the timetables too. */
    suspend fun trip(id: String, day: String, forceRefresh: Boolean = false, refreshSeconds: Int = 75): Trip =
        primaryOrOffline({ net -> net.trip(id, day) ?: throw OfflineNotCovered() }) {
            try { cache.get("mav:trip:$id|$day", freshnessMillis(refreshSeconds), forceRefresh) { mav.trip(id, day) } }
            catch (e: CancellationException) { throw e } catch (e: ProviderUnavailable) { throw e }
            catch (e: IllegalStateException) { supplement { it.trip(id, day) } ?: throw e }
        }

    suspend fun trackingTrip(id: String, day: String): Trip = mav.trackingTrip(id, day)

    suspend fun utasTrip(id: String, date: LocalDate, refreshSeconds: Int): Trip =
        cache.get("utas:trip:$id|$date", freshnessMillis(refreshSeconds)) { utas.trip(id, date) }

    // ---- Live vehicles ----
    suspend fun vehiclePaths(ids: List<String>): JSONObject = mav.vehiclePaths(ids)

    private suspend fun mavVehicles(force: Boolean, refreshSeconds: Int) =
        cache.get("mav:vehicles", freshnessMillis(refreshSeconds), force) { mav.vehicles() }
    private suspend fun utasVehicles(force: Boolean, refreshSeconds: Int) =
        cache.get("utas:vehicles", freshnessMillis(refreshSeconds), force, staleMillis = UTAS_STALE) { utas.vehicles() }

    /** [DataSourceMode.Single] is MÁVPlusz alone. [DataSourceMode.Aggregated] asks both in parallel and merges
     * utas.hu into MÁVPlusz ([mergeVehicleSources]); one failing source degrades to the other with a notice. */
    suspend fun vehicles(mode: DataSourceMode, force: Boolean = false, refreshSeconds: Int = 75): VehicleSnapshot = when (mode) {
        DataSourceMode.Single -> mavVehicles(force, refreshSeconds).let { (list, at) -> VehicleSnapshot(list, at) }
        DataSourceMode.Aggregated -> coroutineScope {
            suspend fun <T> attempt(block: suspend () -> T): Result<T> =
                try { Result.success(block()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
            val mavResult = async { attempt { mavVehicles(force, refreshSeconds) } }
            val utasResult = async { attempt { utasVehicles(force, refreshSeconds) } }
            val m = mavResult.await(); val u = utasResult.await()
            val mavSnapshot = m.getOrNull(); val utasSnapshot = u.getOrNull()
            if (mavSnapshot == null && utasSnapshot == null) throw m.exceptionOrNull()!!
            val merged = withContext(Dispatchers.Default) {
                mergeVehicleSources(mavSnapshot?.first.orEmpty(), utasSnapshot?.first.orEmpty(), clock() / 1000)
            }
            VehicleSnapshot(merged, listOfNotNull(mavSnapshot?.second, utasSnapshot?.second).min(), when {
                mavSnapshot == null -> "A MÁVPlusz járműadatai most nem érhetők el, csak az utas.hu járművei láthatók."
                utasSnapshot == null -> "Az utas.hu most nem érhető el, csak a MÁVPlusz járművei láthatók."
                else -> null
            })
        }
    }

    // ---- Notices ----
    /** Line and station notices across every feed; cached briefly because the response is large. */
    suspend fun networkAlerts(force: Boolean = false): Pair<List<NetworkAlert>, Long> =
        cache.get("mav:alerts", ALERTS_MAX_AGE, force) { mav.networkAlerts() }
}
