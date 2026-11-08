package hu.matepazy.menetrendgo

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/** Prefer departure times, falling back to the matching arrival pair at the terminus. */
internal fun tripStopTime(stop: JSONObject, day: String): ServiceTime? {
    fun seconds(key: String): Long? =
        if (stop.isNull(key)) null else stop.optLong(key, -1).takeIf { it >= 0 }
    val departure = seconds("scheduledDeparture")
    val scheduled = departure ?: seconds("scheduledArrival") ?: return null
    val estimate = seconds(if (departure != null) "realtimeDeparture" else "realtimeArrival")
    val realtime = stop.optBoolean("realtime") && estimate != null
    val date = seconds("serviceDay")?.let { Instant.ofEpochSecond(it).atZone(Budapest).toLocalDate() }
        ?: LocalDate.parse(day)
    return ServiceTime(vehicleStopTime(date, scheduled),
        vehicleStopTime(date, if (realtime) estimate!! else scheduled), realtime)
}
