package hu.matepazy.menetrendgo

/** Live time determines whether boarding is still possible. Keep loaded rows in either case. */
internal enum class DeparturePresentation { Upcoming, DepartingNow, Past, LeftEarly }

internal fun departurePresentation(time: ServiceTime, now: Long): DeparturePresentation {
    val effective = if (time.realtime) time.estimate else time.scheduled
    return when {
        time.realtime && effective < now && time.scheduled >= now -> DeparturePresentation.LeftEarly
        time.realtime && effective < now -> DeparturePresentation.Past
        effective / 60_000L < now / 60_000L -> DeparturePresentation.Past
        effective / 60_000L == now / 60_000L -> DeparturePresentation.DepartingNow
        else -> DeparturePresentation.Upcoming
    }
}

internal const val LeftEarlyDescription = "Az élő adat szerint a járat már elindult a menetrendi idő előtt"
