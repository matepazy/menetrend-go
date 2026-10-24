package hu.matepazy.menetrendgo

import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter

val Budapest: ZoneId = ZoneId.of("Europe/Budapest")
fun timeLabel(millis: Long): String = Instant.ofEpochMilli(millis).atZone(Budapest).format(DateTimeFormatter.ofPattern("HH:mm"))
fun dayLabel(millis: Long, today: LocalDate = LocalDate.now(Budapest)): String {
    val date=Instant.ofEpochMilli(millis).atZone(Budapest).toLocalDate()
    return when(date) {today->"";today.plusDays(1)->"Holnap";else->date.format(DateTimeFormatter.ofPattern("yyyy. MM. dd."))}
}
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.array(key: String): List<JSONObject> = optJSONArray(key)?.objects().orEmpty()
data class Station(val id: String, val name: String, val lat: Double, val lon: Double,
    val type: String = "", val modes: List<String> = emptyList()) {
    fun json() = JSONObject().put("id", id).put("name", name).put("lat", lat).put("lon", lon)
        .put("type", type).put("modes", JSONArray(modes))
    companion object { fun parse(o: JSONObject): Station {
        val modes=o.optJSONArray("modes")
        return Station(o.optString("id"), o.getString("name"), o.getDouble("lat"), o.getDouble("lon"),
            o.optString("type"), if(modes==null) emptyList() else (0 until modes.length()).map {modes.getString(it)})
    } }
}
data class Recent(val from: Station, val to: Station) {
    fun json() = JSONObject().put("from", from.json()).put("to", to.json())
}
data class Vehicle(val id: String, val name: String, val headsign: String, val mode: String, val lat: Double, val lon: Double,
    val routeColor: String = "", val category: String = "", val routeLongName: String = "",
    val heading: Double? = null, val model: String = "", val registration: String = "", val uicCode: String = "",
    val routeShortName: String = "", val vehicleId: String = "") {
    val modelAndRegistration: String get() {
        val modelName=model.trim()
        val plate=registration.trim()
        return when {
            modelName.isEmpty() -> plate
            plate.isEmpty() -> modelName
            else -> "$modelName ($plate)"
        }
    }
    fun badgeLeg(): Leg {
        val point = Station("", "", lat, lon)
        return Leg(mode, 0, 0, point, point, name, id, true, 0, emptyList(), "", routeColor, category, headsign, routeLongName)
    }
}
data class Leg(val mode: String, val start: Long, val end: Long, val from: Station, val to: Station,
    val route: String, val tripId: String, val realTime: Boolean, val departureDelay: Int,
    val intermediate: List<Station>, val geometry: String,
    val routeColor: String = "", val category: String = "", val headsign: String = "", val routeLongName: String = "", val agencyName: String = "", val arrivalDelay: Int = 0,
    val services: List<MavService> = emptyList()) {
    val walking get() = mode == "WALK"
    val departureTime get() = ServiceTime.fromEstimate(start, departureDelay, realTime && !walking)
    val arrivalTime get() = ServiceTime.fromEstimate(end, arrivalDelay, realTime && !walking)
    companion object {
        fun parse(o: JSONObject): Leg {
            val route = o.optJSONObject("route") ?: JSONObject()
            val trip = o.optJSONObject("trip") ?: JSONObject()
            return Leg(o.optString("mode"), o.getLong("startTime"), o.getLong("endTime"),
                Station.parse(o.getJSONObject("from")), Station.parse(o.getJSONObject("to")),
                route.optString("shortName").ifBlank { trip.optString("tripShortName").ifBlank { o.optString("mode") } },
                trip.optString("gtfsId"), o.optBoolean("realTime"), o.optInt("departureDelay"),
                o.array("intermediateStops").map(Station::parse), o.optJSONObject("legGeometry")?.optString("points").orEmpty(),
                route.optString("color"), trip.optString("trainCategoryName"), trip.optString("tripHeadsign"), route.optString("longName"), route.optJSONObject("agency")?.optString("name").orEmpty(),o.optInt("arrivalDelay"),MavService.parseAll(o))
        }
    }
}
data class Journey(val duration: Int, val legs: List<Leg>, val rawGraphQl: String? = null) {
    val minutes get() = (duration+59)/60
    val start get() = legs.first().start
    val end get() = legs.last().end
    // Result endpoints describe boarding/alighting, excluding access and egress walks.
    val boardingLeg get() = legs.firstOrNull { !it.walking } ?: legs.first()
    val alightingLeg get() = legs.lastOrNull { !it.walking } ?: legs.last()
    val serviceMinutes get() = ((alightingLeg.end - boardingLeg.start + 59_999) / 60_000).toInt()
    val key get() = legs.joinToString("|") { "${it.tripId}:${it.start}:${it.from.name}" }
    val changes get() = (legs.count { !it.walking } - 1).coerceAtLeast(0)
    companion object { fun parse(o: JSONObject) = Journey(o.optInt("duration"), o.array("legs").map(Leg::parse)) }
}
data class Pass(val id: String, val county: String?, val starts: LocalDate, val expires: LocalDate,
                val localCity: String? = null, val thirtyDays: Boolean = expires == starts.plusDays(29)) {
    val name get() = localCity?.let { "$it · helyi bérlet" } ?: county?.let { "$it megyebérlet" } ?: "Országbérlet"
    fun json() = JSONObject().put("id", id).put("county", county ?: "").put("starts", starts.toString()).put("expires", expires.toString())
        .put("localCity", localCity ?: "").put("thirtyDays", thirtyDays)
    fun validAt(millis: Long): Boolean {
        val day = Instant.ofEpochMilli(millis).atZone(Budapest).toLocalDate()
        return !day.isBefore(starts) && !day.isAfter(expires)
    }
    companion object {
        fun parse(o: JSONObject): Pass {
            val starts = LocalDate.parse(o.getString("starts"))
            val expires = LocalDate.parse(o.getString("expires"))
            return Pass(o.getString("id"), o.optString("county").ifBlank { null }, starts, expires,
                o.optString("localCity").ifBlank { null }, o.optBoolean("thirtyDays", expires == starts.plusDays(29)))
        }
    }
}
data class PassAdvice(val title: String, val detail: String, val confirmed: Boolean = false)

