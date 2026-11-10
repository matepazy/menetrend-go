package hu.matepazy.menetrendgo

import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.time.Instant
import kotlin.math.abs

internal const val TransferFreshnessMs = 150_000L

internal data class TransferConnection(val incomingIndex: Int, val outgoingIndex: Int, val walkMillis: Long)
internal data class TransferEstimate(val time: ServiceTime, val fetchedAt: Long, val failed: Boolean = false,
    val cancelled: Boolean = false)
internal data class TransferAlternative(val time: ServiceTime, val route: String, val fetchedAt: Long)
internal data class TransferTimes(val arrival: TransferEstimate, val departure: TransferEstimate,
    val nextDeparture: TransferAlternative? = null, val vehicle: JourneyVehicleDetails? = null)

internal data class JourneyVehicleDetails(val model: String, val registration: String)

/** Trip metadata can report a model even when no usable map coordinates are available. */
internal fun journeyVehicleDetails(trip: JSONObject, leg: Leg): JourneyVehicleDetails? {
    if(transferStopEstimate(trip,leg,false,0)==null) return null
    fun JSONObject.text(key: String) = optString(key).trim().takeUnless {isNull(key) || it.equals("null",true)}.orEmpty()
    val vehicles=trip.array("vehiclePositions").map { JourneyVehicleDetails(it.text("vehicleModel"),it.text("licensePlate")) }
    return vehicles.firstOrNull {it.model.isNotBlank()} ?: vehicles.firstOrNull {it.registration.isNotBlank()}
}
internal enum class TransferRisk { Scheduled, Unknown, Tight, Missed, Available, Cancelled, Past }
internal data class TransferAssessment(val risk: TransferRisk, val remainingMillis: Long)

/** Access/egress walks are excluded. Sum every transfer walk, without double-counting waiting. */
internal fun journeyTransfers(journey: Journey): List<TransferConnection> = journey.legs.indices
    .filter { !journey.legs[it].walking }.zipWithNext { incoming, outgoing ->
        TransferConnection(incoming, outgoing, journey.legs.subList(incoming + 1, outgoing)
            .sumOf { (it.end - it.start).coerceAtLeast(0L) })
    }

internal fun initialTransferTimes(journey: Journey, connection: TransferConnection) = TransferTimes(
    TransferEstimate(journey.legs[connection.incomingIndex].arrivalTime, journey.fetchedAt),
    TransferEstimate(journey.legs[connection.outgoingIndex].departureTime, journey.fetchedAt))

internal fun assessTransfer(connection: TransferConnection, times: TransferTimes, now: Long): TransferAssessment {
    val remaining = times.departure.time.estimate - times.arrival.time.estimate - connection.walkMillis
    val endpoints = listOf(times.arrival, times.departure)
    val fresh = endpoints.all { !it.failed && it.fetchedAt > 0 && now - it.fetchedAt in 0..TransferFreshnessMs }
    val risk = when {
        !fresh -> TransferRisk.Unknown
        endpoints.any { it.cancelled } -> TransferRisk.Cancelled
        times.departure.time.estimate < now -> TransferRisk.Past
        endpoints.any { !it.time.realtime } -> TransferRisk.Scheduled
        remaining < 0 -> TransferRisk.Missed
        remaining < 3 * 60_000 -> TransferRisk.Tight
        else -> TransferRisk.Available
    }
    return TransferAssessment(risk, remaining)
}

internal fun transferWarning(connection: TransferConnection, times: TransferTimes, now: Long): String? {
    val assessment = assessTransfer(connection, times, now)
    return when (assessment.risk) {
        TransferRisk.Tight -> "Szoros átszállás"
        TransferRisk.Missed -> "Nem elérhető csatlakozás"
        TransferRisk.Cancelled -> "Kimaradó csatlakozás"
        TransferRisk.Scheduled -> if (assessment.remainingMillis < 180_000) "Szoros átszállás" else null
        else -> null
    }
}

/** The planner must reach the original next stop with one vehicle, including platform walks.
 * Keep two minutes after access walking, exclude the original run and prefer the first departure.
 */
internal data class TransferCandidate(val leg: Leg, val readyAt: Long)
internal fun reachableTransferCandidates(journeys: List<Journey>, original: Leg, arrival: Long, now: Long): List<TransferCandidate> =
    journeys.mapNotNull { journey ->
        val candidate = journey.legs.singleOrNull { !it.walking } ?: return@mapNotNull null
        if (candidate.tripId.isBlank() || candidate.mode != original.mode) return@mapNotNull null
        if (candidate.tripId == original.tripId && candidate.departureTime.scheduled == original.departureTime.scheduled)
            return@mapNotNull null
        val accessWalk = journey.legs.takeWhile { it.walking }.sumOf { (it.end - it.start).coerceAtLeast(0) }
        val readyAt = maxOf(arrival, now) + accessWalk + 120_000
        if (candidate.start < readyAt) return@mapNotNull null
        TransferCandidate(candidate, readyAt)
    }.distinctBy { it.leg.tripId to it.leg.departureTime.scheduled }.sortedBy { it.leg.start }

/** Identify an exact stop occurrence by provider ID and scheduled absolute time, including loops.
 * Older saved journeys have no stop IDs, so require both name and nearby coordinates in that case.
 */
internal fun transferStopEstimate(trip: JSONObject, leg: Leg, arrival: Boolean, fetchedAt: Long): TransferEstimate? {
    if (trip.optString("gtfsId") != leg.tripId) return null
    val station = if (arrival) leg.to else leg.from
    val original = if (arrival) leg.arrivalTime else leg.departureTime
    val suffix = if (arrival) "Arrival" else "Departure"
    val entry = trip.array("stoptimes").filter { entry ->
        val stop = entry.optJSONObject("stop") ?: return@filter false
        val sameStop = if (station.id.isNotBlank()) stop.optString("gtfsId") == station.id else
            stop.optString("name") == station.name &&
                abs(stop.optDouble("lat") - station.lat) < .001 && abs(stop.optDouble("lon") - station.lon) < .001
        sameStop && !entry.isNull("serviceDay") && !entry.isNull("scheduled$suffix") &&
            (entry.optLong("serviceDay") + entry.optLong("scheduled$suffix")) * 1000 == original.scheduled
    }.singleOrNull() ?: return null
    val realtime = entry.optBoolean("realtime") && !entry.isNull("realtime$suffix") && entry.optLong("realtime$suffix") >= 0
    val estimate = if (realtime) (entry.getLong("serviceDay") + entry.getLong("realtime$suffix")) * 1000 else original.scheduled
    return TransferEstimate(ServiceTime(original.scheduled, estimate, realtime), fetchedAt,
        cancelled = entry.optString("realtimeState") in setOf("CANCELED", "CANCELLED"))
}

/** Search from the arrival stop, letting the planner include the remaining interchange walk. */
internal fun onwardSearchTime(arrival: Long, now: Long): java.time.LocalDateTime =
    Instant.ofEpochMilli(((maxOf(arrival, now) + 59_999) / 60_000) * 60_000).atZone(Budapest).toLocalDateTime()

/** Refresh exact stop occurrences for every ridden leg, retaining estimates when requests fail. */
internal suspend fun refreshJourneyLegTimes(journey: Journey, previous: Map<Int, TransferTimes>,
    fetchTrip: suspend (String, String) -> JSONObject): Map<Int, TransferTimes> {
    val trips = mutableMapOf<Pair<String, String>, JSONObject?>()
    suspend fun endpoint(index: Int, arrival: Boolean, old: TransferEstimate): TransferEstimate {
        val leg = journey.legs[index]
        if (leg.tripId.isBlank()) return old.copy(failed = true)
        val day = Instant.ofEpochMilli(leg.departureTime.scheduled).atZone(Budapest).toLocalDate()
        // A service after midnight can belong to the preceding service day (24:xx stop times).
        for (candidate in listOf(day, day.minusDays(1))) {
            val key = leg.tripId to candidate.toString()
            if (!trips.containsKey(key)) trips[key] = try { fetchTrip(key.first, key.second) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            val trip = trips[key] ?: continue
            transferStopEstimate(trip, leg, arrival, System.currentTimeMillis())?.let { return it }
        }
        return old.copy(failed = true)
    }
    return journey.legs.indices.filter { !journey.legs[it].walking }.associateWith { index ->
        val leg = journey.legs[index]
        val old = previous[index] ?: TransferTimes(TransferEstimate(leg.arrivalTime, journey.fetchedAt),
            TransferEstimate(leg.departureTime, journey.fetchedAt))
        val arrival=endpoint(index, true, old.arrival)
        val departure=endpoint(index, false, old.departure)
        val vehicle=trips.values.filterNotNull().firstNotNullOfOrNull {journeyVehicleDetails(it,leg)}
        TransferTimes(arrival,departure,vehicle=vehicle)
    }
}
