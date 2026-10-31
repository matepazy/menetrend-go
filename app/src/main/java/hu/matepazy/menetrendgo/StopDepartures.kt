package hu.matepazy.menetrendgo

import org.json.JSONObject

data class StopDeparture(val tripId: String, val headsign: String, val time: ServiceTime,
    val platform: String, val cancelled: Boolean, val leg: Leg) {
    val key get() = "$tripId:${time.scheduled}"
}

internal fun departureCountdown(estimate: Long, now: Long): String {
    val remaining = estimate - now
    return if (remaining < 60_000) "Most" else "${(remaining + 59_999) / 60_000} perc"
}

internal fun departureClockTime(estimate: Long, now: Long, cancelled: Boolean = false): String? =
    if (!cancelled && estimate - now > 15 * 60_000L) timeLabel(estimate) else null

internal enum class DepartureStatus { Scheduled, Live, MinorDelay, MajorDelay }

internal fun departureStatus(time: ServiceTime): DepartureStatus = when {
    !time.realtime -> DepartureStatus.Scheduled
    time.estimate - time.scheduled >= 600_000 -> DepartureStatus.MajorDelay
    time.estimate > time.scheduled -> DepartureStatus.MinorDelay
    else -> DepartureStatus.Live
}

internal fun departureColorResource(time: ServiceTime, cancelled: Boolean = false): Int =
    if (cancelled) R.color.departure_red else when (departureStatus(time)) {
        DepartureStatus.Scheduled -> R.color.departure_scheduled
        DepartureStatus.Live -> R.color.departure_green
        DepartureStatus.MinorDelay -> R.color.departure_yellow
        DepartureStatus.MajorDelay -> R.color.departure_red
    }

internal fun departureStateDescription(departure: StopDeparture): String = when {
    departure.cancelled -> "Kimarad"
    !departure.time.realtime -> "Menetrend szerinti adat"
    departure.time.estimate > departure.time.scheduled -> "Élő adat, ${(departure.time.estimate - departure.time.scheduled + 59_999) / 60_000} perc késés"
    else -> "Élő adat"
}

internal fun updateAge(updated: Long, now: Long): String {
    val minutes = ((now - updated).coerceAtLeast(0) / 60_000)
    return when {
        minutes == 0L -> "most"
        minutes < 60 -> "$minutes perce"
        minutes < 1440 -> "${minutes / 60} órája"
        else -> "${minutes / 1440} napja"
    }
}

/** Store absolute times and original logo metadata so a widget can redraw its countdown offline. */
internal fun StopDeparture.cacheJson(): JSONObject = JSONObject().put("trip", tripId).put("headsign", headsign)
    .put("scheduled", time.scheduled).put("estimate", time.estimate).put("live", time.realtime)
    .put("platform", platform).put("cancelled", cancelled).put("route", leg.route).put("mode", leg.mode)
    .put("logo", leg.routeLogo?.let { JSONObject().put("code", it.code).put("foreground", it.foreground).put("background", it.background) })

internal fun cachedDeparture(json: JSONObject): StopDeparture {
    val time = ServiceTime(json.getLong("scheduled"), json.getLong("estimate"), json.getBoolean("live"))
    val station = Station("", "", 0.0, 0.0)
    val logo = json.optJSONObject("logo")?.let { RouteLogo(it.getInt("code"), it.getString("foreground"), it.getString("background")) }
    val leg = Leg(json.getString("mode"), time.estimate, time.estimate, station, station, json.getString("route"),
        json.getString("trip"), time.realtime, 0, emptyList(), "", routeLogo = logo)
    return StopDeparture(json.getString("trip"), json.getString("headsign"), time, json.getString("platform"), json.getBoolean("cancelled"), leg)
}

internal fun parseMapStops(data: JSONObject): List<Station> =
    data.optJSONObject("stopsByRadius")?.array("edges").orEmpty().mapNotNull { edge ->
        val stop = edge.optJSONObject("node")?.optJSONObject("stop") ?: return@mapNotNull null
        // A station owns its platforms. Street stops retain their directional identity.
        val place = stop.optJSONObject("parentStation") ?: stop
        Station(place.getString("gtfsId"), place.getString("name"), place.getDouble("lat"), place.getDouble("lon"),
            modes = listOfNotNull(stop.optString("vehicleMode").takeUnless { it.isBlank() || it == "null" }),
            platform = if (place === stop) stop.optString("platformCode").takeUnless { it == "null" }.orEmpty() else "")
    }.distinctBy { it.id }

internal fun parseStopDepartures(data: JSONObject, now: Long): List<StopDeparture> {
    fun JSONObject.text(key: String) = optString(key).takeUnless { it == "null" }.orEmpty()
    val stop = data.optJSONObject("stop") ?: error("Ez a megálló már nem érhető el a MÁV-nál.")
    return stop.array("stoptimesWithoutPatterns").mapNotNull { row ->
        if (row.optString("pickupType") == "NONE") return@mapNotNull null
        val trip = row.optJSONObject("trip") ?: return@mapNotNull null
        val route = trip.optJSONObject("route") ?: JSONObject()
        val live = row.optBoolean("realtime")
        val day = row.getLong("serviceDay")
        val scheduled = (day + row.getLong("scheduledDeparture")) * 1000
        val estimate = if (live) (day + row.getLong("realtimeDeparture")) * 1000 else scheduled
        if (estimate < now) return@mapNotNull null
        val point = row.optJSONObject("stop") ?: stop
        val station = Station(point.optString("gtfsId"), point.optString("name"), point.optDouble("lat", 0.0), point.optDouble("lon", 0.0))
        val label = trip.text("tripShortName").ifBlank { route.text("shortName").takeUnless { '<' in it }.orEmpty() }
            .ifBlank { "Járat" }
        val destination = row.text("headsign").ifBlank { trip.text("tripHeadsign") }.ifBlank { "Az irány nem ismert" }
        val leg = Leg(route.optString("mode"), estimate, estimate, station, station, label, trip.optString("gtfsId"),
            live, ((estimate-scheduled)/1000).toInt(), emptyList(), "", route.optString("color"),
            trip.optString("trainCategoryName"), destination, route.optString("longName"), routeLogo = RouteLogo.parse(route))
        StopDeparture(trip.optString("gtfsId"), destination, ServiceTime(scheduled, estimate, live),
            point.optString("platformCode").takeUnless { it == "null" }.orEmpty(),
            row.optString("realtimeState") == "CANCELED", leg)
    }.distinctBy { it.key }.sortedBy { it.time.estimate }.take(30)
}
