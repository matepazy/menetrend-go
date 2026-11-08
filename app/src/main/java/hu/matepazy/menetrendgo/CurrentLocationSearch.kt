package hu.matepazy.menetrendgo

/** Resolve stale current-location endpoints once, before starting a new route search. */
internal suspend fun refreshSearchLocations(
    from: Station,
    to: Station,
    now: Long,
    locate: suspend () -> Station
): Pair<Station, Station> {
    fun Station.isStale(): Boolean = currentLocationCalculatedAt?.let {
        now - it > 60_000L || now < it
    } ?: false
    val refreshFrom = from.isStale()
    val refreshTo = to.isStale()
    if (!refreshFrom && !refreshTo) return from to to
    val location = locate()
    return (if (refreshFrom) location else from) to (if (refreshTo) location else to)
}
