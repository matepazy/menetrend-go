package hu.matepazy.menetrendgo

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/** A vehicle trip can serve several alighting stops; the alert identity must include its target. */
internal val Journey.trackingKey get() = legs.joinToString("|") {
    "${it.mode}:${it.tripId}:${it.start}:${it.from.name}:${it.to.name}:${it.to.lat}:${it.to.lon}"
}

/** Only route/session state is persisted; location fixes never leave memory. */
internal data class TrackingSession(
    val journey: Journey,
    val legIndex: Int,
    val warningMinutes: Int = 2,
    val alerted: Boolean = false,
    val awaitingBoarding: Boolean = false,
    val progress: Double? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val progressAt: Long = startedAt,
) {
    val leg get() = journey.legs[legIndex]
    val controlToken get() = "$startedAt|${journey.trackingKey}|$legIndex"
    val nextLegIndex get() = journey.legs.indices.firstOrNull { it > legIndex && !journey.legs[it].walking }
    fun json() = JSONObject().put("legs", JSONArray(journey.legs.map { leg ->
        JSONObject().put("mode", leg.mode).put("startTime", leg.start).put("endTime", leg.end)
            .put("from", leg.from.json()).put("to", leg.to.json())
            .put("route", JSONObject().put("shortName", leg.route).put("color", leg.routeColor))
            .put("trip", JSONObject().put("gtfsId", leg.tripId).put("tripHeadsign", leg.headsign))
            .put("intermediateStops", JSONArray(leg.intermediate.map { it.json() }))
            .put("legGeometry", JSONObject().put("points", leg.geometry))
            .put("realTime", leg.realTime).put("arrivalDelay", leg.arrivalDelay).put("departureDelay", leg.departureDelay)
    })).put("duration", journey.duration).put("legIndex", legIndex).put("warningMinutes", warningMinutes)
        .put("alerted", alerted).put("awaitingBoarding", awaitingBoarding).put("progress", progress)
        .put("startedAt", startedAt).put("progressAt", progressAt)

    companion object {
        fun parse(json: JSONObject): TrackingSession {
            val journey = Journey.parse(json)
            val index = json.getInt("legIndex")
            require(index in journey.legs.indices && !journey.legs[index].walking)
            return TrackingSession(journey, index, json.getInt("warningMinutes").also { require(it in 1..3) },
                json.optBoolean("alerted"), json.optBoolean("awaitingBoarding"),
                json.optDouble("progress").takeIf { it.isFinite() && it >= 0 }, json.getLong("startedAt"),
                json.optLong("progressAt", json.getLong("startedAt")))
        }
    }
}

internal data class TrackPoint(val lat: Double, val lon: Double)
internal fun Station.trackPoint() = TrackPoint(lat, lon)
internal fun trackDistance(a: TrackPoint, b: TrackPoint): Double {
    val lat = Math.toRadians(b.lat - a.lat)
    val lon = Math.toRadians(b.lon - a.lon)
    val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(lon / 2).pow(2)
    return 6_371_000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

internal fun decodeTrackingGeometry(encoded: String): List<TrackPoint> = runCatching {
    var cursor = 0
    var lat = 0
    var lon = 0
    fun number(): Int {
        var value = 0
        var shift = 0
        var byte: Int
        do {
            require(cursor < encoded.length && shift <= 30)
            byte = encoded[cursor++].code - 63
            require(byte in 0..63)
            value = value or ((byte and 31) shl shift)
            shift += 5
        } while (byte >= 32)
        return if (value and 1 != 0) (value shr 1).inv() else value shr 1
    }
    buildList {
        while (cursor < encoded.length) {
            lat += number(); lon += number()
            val point = TrackPoint(lat / 100_000.0, lon / 100_000.0)
            require(point.lat in -90.0..90.0 && point.lon in -180.0..180.0)
            add(point)
        }
    }
}.getOrDefault(emptyList())

internal data class TrackingEstimate(
    val progress: Double,
    val remainingMeters: Double,
    val stopsRemaining: Int,
    val secondsRemaining: Long?,
    val shouldAlert: Boolean,
    val arrived: Boolean,
    val passedDestination: Boolean = false,
    val matchedProgress: Double = progress,
)

internal fun trackingMaxSpeed(mode: String): Double = when (mode) {
    "RAIL", "SUBURBAN_RAILWAY" -> 110.0
    "COACH", "RAIL_REPLACEMENT_BUS" -> 50.0
    else -> 35.0
}

/** Ordered route projection avoids a destination geofence firing on an earlier pass nearby. */
internal class JourneyProgress(private val leg: Leg) {
    private val points = decodeTrackingGeometry(leg.geometry).takeIf { it.size >= 2 &&
        trackDistance(it.first(), leg.from.trackPoint()) <= 300 && trackDistance(it.last(), leg.to.trackPoint()) <= 300 }
        ?: (listOf(leg.from) + leg.intermediate + leg.to).map { it.trackPoint() }
    private val distances = DoubleArray(points.size).apply {
        for (i in 1 until size) this[i] = this[i - 1] + trackDistance(points[i - 1], points[i])
    }
    val length get() = distances.last()
    private data class Projection(val along: Double, val away: Double)

    private fun projections(point: TrackPoint): List<Projection> = (1 until points.size).map { i ->
        val a = points[i - 1]; val b = points[i]
        val cosLat = cos(Math.toRadians(point.lat))
        val dx = (b.lon - a.lon) * cosLat; val dy = b.lat - a.lat
        val px = (point.lon - a.lon) * cosLat; val py = point.lat - a.lat
        val t = if (dx * dx + dy * dy == 0.0) 0.0 else ((px * dx + py * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        Projection(distances[i - 1] + t * (distances[i] - distances[i - 1]),
            trackDistance(point, TrackPoint(a.lat + t * dy, a.lon + t * (b.lon - a.lon))))
    }

    private val stopDistances = buildList {
        var previous = 0.0
        leg.intermediate.forEach { stop ->
            val projection = projections(stop.trackPoint()).filter { it.along >= previous }
                .minByOrNull { it.away }
            if (projection != null) { add(projection.along); previous = projection.along }
        }
        add(length)
    }

    fun estimate(
        point: TrackPoint, accuracy: Float, speed: Float?, previous: Double?, elapsedSeconds: Double,
        warningMinutes: Int, alreadyAlerted: Boolean,
    ): TrackingEstimate? {
        if (!accuracy.isFinite() || accuracy !in 0f..100f || length < 1 ||
            !point.lat.isFinite() || !point.lon.isFinite() || point.lat !in -90.0..90.0 || point.lon !in -180.0..180.0) return null
        // Plausibility bound accommodates a fast train, but rejects jumps to a later loop.
        val candidates = projections(point).filter {
            previous == null || it.along in (previous - 150)..
                (previous + max(100.0, elapsedSeconds.coerceAtLeast(0.0) * trackingMaxSpeed(leg.mode) + accuracy * 2))
        }
        val closest = candidates.minOfOrNull { it.away } ?: return null
        // A fast vehicle can cross the final stop between fixes. Require a recent approach
        // and a narrow forward continuation of the final route segment, not mere proximity.
        val lastSegment = (points.lastIndex downTo 1).firstOrNull { distances[it] - distances[it - 1] > 1 }
        val possiblePassed = if (lastSegment != null && previous != null && length - previous in 0.0..700.0 &&
            elapsedSeconds in 0.0..30.0 && accuracy <= 60) {
            val a = points[lastSegment - 1]; val b = points[lastSegment]
            val scale = cos(Math.toRadians(b.lat))
            val dx = (b.lon - a.lon) * scale; val dy = b.lat - a.lat
            val px = (point.lon - b.lon) * scale; val py = point.lat - b.lat
            val forward = (px * dx + py * dy) / (dx * dx + dy * dy)
            val sideways = abs(px * dy - py * dx) / sqrt(dx * dx + dy * dy) * 111_195
            forward > 0 && sideways <= max(50.0, accuracy.toDouble()) && trackDistance(point, b) <= 350
        } else false
        // At crossings, choose the earlier equally plausible segment.
        val projection = candidates.filter { it.away <= closest + 25 }.minBy { it.along }
        val passed = possiblePassed && length - projection.along <= 40
        if (closest > max(150.0, accuracy * 2.0) && !passed) return null
        val progress = max(previous ?: 0.0, projection.along).coerceAtMost(length)
        val remaining = length - progress
        val seconds = speed?.takeIf { it.isFinite() && it >= 1.5f && it <= trackingMaxSpeed(leg.mode) }?.let { (remaining / it).toLong() }
        val arrived = passed || (remaining <= 120 && trackDistance(point, leg.to.trackPoint()) + accuracy <= 100 && accuracy <= 60)
        val alert = !alreadyAlerted && (arrived || remaining <= 300 || (seconds != null && seconds <= warningMinutes * 60))
        return TrackingEstimate(progress, remaining, stopDistances.count { it > progress + 40 }, seconds, alert, arrived, passed, projection.along)
    }
}

/** In-memory evidence filter: a single speed spike or proximity fix cannot finish a leg. */
internal class TrackingMotion(private val mode: String) {
    private data class Sample(val time: Long, val speed: Double)
    private val samples = ArrayDeque<Sample>()
    private var previous: Pair<Long, Double>? = null
    private var arrivalSince: Long? = null
    fun reset() { samples.clear(); previous = null; arrivalSince = null }

    fun update(result: TrackingEstimate, time: Long, reportedSpeed: Float?, accuracy: Float,
        warningMinutes: Int, alreadyAlerted: Boolean): TrackingEstimate {
        val old = previous
        val elapsed = old?.let { (time - it.first) / 1000.0 }
        if (elapsed != null && elapsed > 30) { samples.clear(); arrivalSince = null }
        val movement = if (old != null && elapsed != null && elapsed in 2.0..30.0)
            ((result.matchedProgress - old.second) / elapsed).takeIf { abs(it) <= trackingMaxSpeed(mode) } else null
        val observed = movement?.coerceAtLeast(0.0)
        val reported = reportedSpeed?.toDouble()?.takeIf { it.isFinite() && it in 0.0..trackingMaxSpeed(mode) }
        val speed = when {
            observed != null && reported != null && abs(reported - observed) <= max(5.0, observed * .5) -> reported
            observed != null -> observed
            else -> reported
        }
        previous = time to result.matchedProgress
        while (samples.isNotEmpty() && time - samples.first().time > 30_000) samples.removeFirst()
        if (speed != null) samples.addLast(Sample(time, speed))
        while (samples.size > 5) samples.removeFirst()
        val stableSpeed = if (samples.size >= 3 && time - samples.first().time >= 6_000)
            samples.map { it.speed }.sorted()[samples.size / 2].takeIf { it >= 1.5 } else null
        val seconds = stableSpeed?.let { (result.remainingMeters / it).toLong() }
        val slowAtStop = max(movement?.let(::abs) ?: 0.0, reported ?: 0.0) <= 8
        if (!result.arrived || !slowAtStop || accuracy > 60) arrivalSince = null
        else if (arrivalSince == null) arrivalSince = time
        val confirmedArrival = result.passedDestination || arrivalSince?.let { time - it >= 3_000 } == true
        return result.copy(secondsRemaining = seconds, arrived = confirmedArrival,
            shouldAlert = !alreadyAlerted && (confirmedArrival || result.remainingMeters <= 300 ||
                (seconds != null && seconds <= warningMinutes * 60)))
    }
}

/** Live data is a warning fallback only, never proof of arrival or a timetable-only alarm. */
internal fun liveArrivalWarning(session: TrackingSession, arrival: Long?, fetchedAgeMs: Long,
    locationAgeMs: Long, remainingMeters: Double?, now: Long): Boolean =
    !session.alerted && !session.awaitingBoarding && arrival != null &&
        fetchedAgeMs in 0..90_000 && locationAgeMs in 0..120_000 &&
        remainingMeters != null && remainingMeters <= min(5_000.0, trackingMaxSpeed(session.leg.mode) * 120) &&
        arrival - now in -30_000..session.warningMinutes * 60_000L

/** Match the exact trip's stop occurrence by coordinates and scheduled time, including loops. */
internal fun trackingArrival(trip: JSONObject, leg: Leg): Long? {
    if (trip.optString("gtfsId") != leg.tripId) return null
    return trip.array("stoptimes").filter { entry ->
        val stop = entry.optJSONObject("stop") ?: return@filter false
        stop.optString("name") == leg.to.name &&
            trackDistance(leg.to.trackPoint(), TrackPoint(stop.optDouble("lat"), stop.optDouble("lon"))) < 150 &&
            abs((entry.optLong("serviceDay") + entry.optLong("scheduledArrival")) * 1000 - leg.arrivalTime.scheduled) < 120_000
    }.singleOrNull()?.takeIf { it.optBoolean("realtime") }?.let {
        (it.getLong("serviceDay") + it.getLong("realtimeArrival")) * 1000
    }
}
