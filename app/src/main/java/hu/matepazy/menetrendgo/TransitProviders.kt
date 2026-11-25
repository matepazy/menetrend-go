package hu.matepazy.menetrendgo

import kotlinx.coroutines.CancellationException
import java.time.LocalDateTime

/* What a data provider can be asked (docs/ADATRETEG.md, 3. fejezet). A provider implements only the
 * capabilities it has; every answer is a [ProviderResult] in the canonical model, never provider JSON. */

/** Primary answers what it covers; Augment only adds to a primary answer; Fallback answers only when every
 * primary covering the request is unavailable, or while Offline mód is on. */
enum class ProviderRole { Primary, Augment, Fallback }

/** A lat/lon box, inclusive. */
data class Area(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
    init { require(minLat <= maxLat && minLon <= maxLon) }
    operator fun contains(point: Pair<Double, Double>) = point.first in minLat..maxLat && point.second in minLon..maxLon
    fun intersects(other: Area) = minLat <= other.maxLat && other.minLat <= maxLat && minLon <= other.maxLon && other.minLon <= maxLon
    companion object {
        /** The box the MÁVPlusz live vehicle query uses. */
        val Hungary = Area(45.5, 16.1, 48.7, 22.8)
    }
}

/** [attribution] is shown wherever the provider's data appears, when its licence asks for it. */
data class ProviderInfo(val id: ProviderId, val name: String, val role: ProviderRole, val priority: Int,
    val coverage: Area? = null, val attribution: String = "") {
    fun covers(lat: Double, lon: Double) = coverage == null || (lat to lon) in coverage
}

sealed interface ProviderResult<out T> {
    data class Ok<T>(val value: T, val provenance: Provenance) : ProviderResult<T>
    /** The provider could not answer now (network, server error, Offline mód); another provider may. */
    data class Unavailable(val reason: String, val cause: Throwable? = null) : ProviderResult<Nothing>
    /** The request lies outside what this provider knows. */
    data object NotCovered : ProviderResult<Nothing>

    fun valueOrNull(): T? = (this as? Ok<T>)?.value
}

interface Provider { val info: ProviderInfo }

data class StopQuery(val text: String, val limit: Int = 10)
data class DepartureQuery(val stop: Gid, val from: Long, val limit: Int = 30)
data class PlanQuery(val from: Place, val to: Place, val time: LocalDateTime, val arriveBy: Boolean,
    val filters: Set<SearchFilter> = emptySet(), val vehicles: Set<SearchVehicle> = emptySet())

interface StopSearchProvider : Provider { suspend fun searchStops(query: StopQuery): ProviderResult<List<Stop>> }
interface NearbyStopsProvider : Provider { suspend fun stopsNear(lat: Double, lon: Double, radiusMeters: Int): ProviderResult<List<Stop>> }
interface DepartureBoardProvider : Provider { suspend fun departures(query: DepartureQuery): ProviderResult<List<Departure>> }
interface TripProvider : Provider { suspend fun trip(id: Gid, day: java.time.LocalDate, fresh: Boolean = false): ProviderResult<Trip> }
interface JourneyPlannerProvider : Provider { suspend fun plan(query: PlanQuery): ProviderResult<List<Itinerary>> }
interface LivePositionsProvider : Provider { suspend fun vehicles(area: Area, fresh: Boolean = false): ProviderResult<List<VehiclePosition>> }

/** Runs one provider request and maps the app's failure types onto [ProviderResult]. Only unexpected
 * programming errors propagate; cancellation always does. */
internal suspend fun <T> providerCall(block: suspend () -> ProviderResult<T>): ProviderResult<T> = try {
    block()
} catch (e: CancellationException) { throw e
} catch (e: OfflineNotCovered) { ProviderResult.NotCovered
} catch (e: ProviderUnavailable) { ProviderResult.Unavailable(e.message.orEmpty(), e)
} catch (e: java.io.IOException) { ProviderResult.Unavailable(e.message.orEmpty(), e)
} catch (e: IllegalStateException) { ProviderResult.Unavailable(e.message.orEmpty(), e) }
