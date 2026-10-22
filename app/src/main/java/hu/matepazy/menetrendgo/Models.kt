package hu.matepazy.menetrendgo

import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter

val Budapest: ZoneId = ZoneId.of("Europe/Budapest")
fun timeLabel(millis: Long): String = Instant.ofEpochMilli(millis).atZone(Budapest).format(DateTimeFormatter.ofPattern("HH:mm"))
fun dayLabel(millis: Long, today: LocalDate = LocalDate.now(Budapest)): String {
    val date=Instant.ofEpochMilli(millis).atZone(Budapest).toLocalDate()
    return when(date) {today->"Ma";today.plusDays(1)->"Holnap";else->date.format(DateTimeFormatter.ofPattern("yyyy. MM. dd."))}
}
fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.array(key: String): List<JSONObject> = optJSONArray(key)?.objects().orEmpty()
data class Station(val id: String, val name: String, val lat: Double, val lon: Double) {
    fun json() = JSONObject().put("id", id).put("name", name).put("lat", lat).put("lon", lon)
    companion object { fun parse(o: JSONObject) = Station(o.optString("id"), o.getString("name"), o.getDouble("lat"), o.getDouble("lon")) }
}
data class Recent(val from: Station, val to: Station) {
    fun json() = JSONObject().put("from", from.json()).put("to", to.json())
}
data class Vehicle(val id: String, val name: String, val headsign: String, val mode: String, val lat: Double, val lon: Double)
data class Leg(val mode: String, val start: Long, val end: Long, val from: Station, val to: Station,
    val route: String, val tripId: String, val realTime: Boolean, val departureDelay: Int,
    val intermediate: List<Station>, val geometry: String) {
    val walking get() = mode == "WALK"
    companion object {
        fun parse(o: JSONObject): Leg {
            val route = o.optJSONObject("route") ?: JSONObject()
            val trip = o.optJSONObject("trip") ?: JSONObject()
            return Leg(o.optString("mode"), o.getLong("startTime"), o.getLong("endTime"),
                Station.parse(o.getJSONObject("from")), Station.parse(o.getJSONObject("to")),
                route.optString("shortName").ifBlank { trip.optString("tripShortName").ifBlank { o.optString("mode") } },
                trip.optString("gtfsId"), o.optBoolean("realTime"), o.optInt("departureDelay"),
                o.array("intermediateStops").map(Station::parse), o.optJSONObject("legGeometry")?.optString("points").orEmpty())
        }
    }
}
data class Journey(val duration: Int, val legs: List<Leg>) {
    val minutes get() = (duration+59)/60
    val start get() = legs.first().start
    val end get() = legs.last().end
    val key get() = legs.joinToString("|") { "${it.tripId}:${it.start}:${it.from.name}" }
    val changes get() = (legs.count { !it.walking } - 1).coerceAtLeast(0)
    companion object { fun parse(o: JSONObject) = Journey(o.optInt("duration"), o.array("legs").map(Leg::parse)) }
}
data class Pass(val id: String, val county: String?, val starts: LocalDate, val expires: LocalDate) {
    val name get() = county?.let { "$it vármegyebérlet" } ?: "Országbérlet"
    fun json() = JSONObject().put("id", id).put("county", county ?: "").put("starts", starts.toString()).put("expires", expires.toString())
    fun validAt(millis: Long): Boolean {
        val day = Instant.ofEpochMilli(millis).atZone(Budapest).toLocalDate()
        return !day.isBefore(starts) && !day.isAfter(expires)
    }
    companion object {
        fun parse(o: JSONObject) = Pass(o.getString("id"), o.optString("county").ifBlank { null }, LocalDate.parse(o.getString("starts")), LocalDate.parse(o.getString("expires")))
    }
}
data class PassAdvice(val title: String, val detail: String, val confirmed: Boolean = false)

/** County boundaries, rail reservation segments and local acceptance are not guessed.
 * Evaluate every leg at travel time, including a journey that crosses midnight.
 */
fun passAdvice(passes: List<Pass>, journey: Journey): PassAdvice {
    if (passes.isEmpty()) return PassAdvice("Nincs mentett bérlet", "A Bérletek oldalon hozzáadhatod a bérletedet.")
    val transit = journey.legs.filterNot { it.walking }
    if (transit.isEmpty()) return PassAdvice("Gyalogos útvonal", "Ehhez a szakaszhoz nem szükséges bérlet.")
    val active = passes.filter { p -> transit.all { p.validAt(it.start) && p.validAt(it.end) } }
    if (active.isEmpty()) return PassAdvice("A mentett bérlet ekkor nem érvényes", "Ellenőrizd az utazás napját és a bérlet kezdő, illetve lejárati dátumát.")
    return PassAdvice("A járati érvényesség nem igazolható", if (active.all { it.county != null })
        "A dátum megfelelő. A teljes útvonal megyehatárai és a járati kivételek nem állnak rendelkezésre; ellenőrizd a hivatalos feltételeket."
        else "A dátum megfelelő. Vasúton helyjegy vagy kiegészítő jegy is kellhet; a helyi és nemzetközi szakaszok feltételei eltérhetnek.")
}
