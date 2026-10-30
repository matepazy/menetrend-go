package hu.matepazy.menetrendgo

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

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
)

/** Ordered route projection avoids a destination geofence firing on an earlier pass nearby. */
internal class JourneyProgress(private val leg: Leg) {
    private val points = decodeTrackingGeometry(leg.geometry).takeIf { it.size >= 2 }
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
            !point.lat.isFinite() || !point.lon.isFinite()) return null
        // Plausibility bound accommodates a fast train, but rejects jumps to a later loop.
        val candidates = projections(point).filter {
            previous == null || it.along in (previous - 150)..(previous + max(300.0, elapsedSeconds.coerceAtLeast(0.0) * 100))
        }
        val closest = candidates.minOfOrNull { it.away } ?: return null
        if (closest > max(150.0, accuracy * 2.0)) return null
        // At crossings, choose the earlier equally plausible segment.
        val projection = candidates.filter { it.away <= closest + 25 }.minBy { it.along }
        val progress = max(previous ?: 0.0, projection.along).coerceAtMost(length)
        val remaining = length - progress
        val seconds = speed?.takeIf { it.isFinite() && it >= 1.5f }?.let { (remaining / it).toLong() }
        val arrived = remaining <= 120 && trackDistance(point, leg.to.trackPoint()) <= 100 && accuracy <= 60
        val alert = !alreadyAlerted && (arrived || remaining <= 300 || (seconds != null && seconds <= warningMinutes * 60))
        return TrackingEstimate(progress, remaining, stopDistances.count { it > progress + 40 }, seconds, alert, arrived)
    }
}

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
