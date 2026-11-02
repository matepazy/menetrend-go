package hu.matepazy.menetrendgo

/** Fixed geographic warning areas. No route match or arrival prediction is required. */
internal fun destinationAlertRadius(mode: String): Double = when (mode) {
    "RAIL", "SUBURBAN_RAILWAY" -> 250.0
    "COACH", "RAIL_REPLACEMENT_BUS" -> 150.0
    else -> 100.0
}

/** Use distance to the circle's edge and a conservative vehicle speed, including on detours. */
internal fun destinationLocationInterval(mode: String, distance: Double?): Long {
    if (distance == null || !distance.isFinite()) return 5_000L
    val edge = distance - destinationAlertRadius(mode)
    val speed = trackingMaxSpeed(mode)
    return when {
        edge <= maxOf(500.0, speed * 15) -> 2_000L
        edge <= speed * 60 -> 5_000L
        edge <= speed * 180 -> 10_000L
        else -> 20_000L
    }
}

internal data class DestinationFix(val distance: Double, val shouldAlert: Boolean, val arrived: Boolean)

internal class DestinationAlert(private val leg: Leg) {
    private data class Fix(val point: TrackPoint, val time: Long, val accuracy: Float)
    private var previous: Fix? = null
    private var arrivalSince: Long? = null

    fun update(point: TrackPoint, accuracy: Float, time: Long, speed: Float?, alerted: Boolean): DestinationFix? {
        if (!point.lat.isFinite() || !point.lon.isFinite() || point.lat !in -90.0..90.0 ||
            point.lon !in -180.0..180.0 || !accuracy.isFinite() || accuracy !in 0f..100f) return null
        val old = previous
        val elapsed = old?.let { (time - it.time) / 1000.0 }
        if (elapsed != null && elapsed <= 0) return null
        val movement = old?.let { trackDistance(it.point, point) }
        if (elapsed != null && elapsed <= 30 && movement!! >
            trackingMaxSpeed(leg.mode) * elapsed + accuracy + old.accuracy + 50) return null
        val distance = trackDistance(point, leg.to.trackPoint())
        if (!distance.isFinite()) return null
        previous = Fix(point, time, accuracy)
        val observedSpeed = if (elapsed != null && elapsed in 1.0..30.0) movement!! / elapsed else null
        val reportedSpeed = speed?.takeIf { it.isFinite() && it >= 0 }?.toDouble()
        // Arrival is separate from the warning area. Confirm a slow stop with fresh fixes.
        val atStop = distance + accuracy <= 100 && accuracy <= 60 &&
            maxOf(observedSpeed ?: 0.0, reportedSpeed ?: 0.0) <= 8
        if (!atStop || elapsed == null || elapsed > 30) arrivalSince = null
        if (atStop && arrivalSince == null) arrivalSince = time
        val arrived = arrivalSince?.let { time - it >= 3_000 } == true
        // The accuracy disk must fit within the circle to avoid a noisy boundary alert.
        return DestinationFix(distance, !alerted && distance + accuracy <= destinationAlertRadius(leg.mode), arrived)
    }
}
