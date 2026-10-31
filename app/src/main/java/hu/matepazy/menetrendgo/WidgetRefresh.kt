package hu.matepazy.menetrendgo

internal const val WidgetRefreshInterval = 5 * 60_000L
internal const val WidgetRefreshRetry = 60_000L
internal const val WidgetDeparturePoolSize = 10

internal fun upcomingWidgetDepartures(departures: List<StopDeparture>, now: Long): List<StopDeparture> =
    departures.filter { it.time.estimate >= now }.sortedBy { it.time.estimate }

/** Roll over cached services locally; fetch after five minutes or when the reserve is exhausted. */
internal fun widgetRefreshAt(updated: Long, lastAttempt: Long, failed: Boolean,
    departures: List<StopDeparture>, now: Long): Long {
    if (updated <= 0 || updated > now) return if (failed) maxOf(now, minOf(lastAttempt, now) + WidgetRefreshRetry) else now
    val services = departures.filterNot { it.cancelled }
    val exhausted = services.isNotEmpty() && services.none { it.time.estimate >= now }
    val due = if (exhausted) now else updated + WidgetRefreshInterval
    return maxOf(now, due, if (failed) lastAttempt + WidgetRefreshRetry else 0)
}
