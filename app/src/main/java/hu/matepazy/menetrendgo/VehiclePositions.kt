package hu.matepazy.menetrendgo

import org.json.JSONObject

enum class MapVehicleFilter(
    val id: String,
    val prefKey: String,
    val title: String,
    val summary: String,
    val defaultEnabled: Boolean,
    val modes: Set<String>
) {
    Rail("rail", "map_rail", "Vonatok", "Személy-, gyors- és InterCity-vonatok", true, setOf("RAIL")),
    Coach("coach", "map_coaches", "Helyközi autóbuszok", "Regionális és távolsági autóbuszjáratok", true, setOf("COACH")),
    LocalBus("local_bus", "map_local_buses", "Helyi autóbuszok", "Városi autóbuszjáratok élő pozíciói", true, setOf("BUS")),
    Trolleybus("trolleybus", "map_trolleybuses", "Trolibuszok", "Városi trolibuszjáratok élő pozíciói", true, setOf("TROLLEYBUS")),
    Tram("tram", "map_trams", "Villamosok", "Városi villamosjáratok élő pozíciói", true, setOf("TRAM")),
    SuburbanRailway("suburban_railway", "map_suburban_railway", "HÉV", "Budapesti elővárosi vasútvonalak", true, setOf("SUBURBAN_RAILWAY")),
    TramTrain("tramtrain", "map_tramtrain", "TramTrain", "Szeged–Hódmezővásárhely vasútvillamos", true, setOf("TRAMTRAIN")),
    ReplacementBus("replacement_bus", "map_replacement_buses", "Vonatpótló autóbuszok", "Vágányzári és rendkívüli pótlóbuszok", true, setOf("RAIL_REPLACEMENT_BUS"));

    companion object {
        val defaultFilters: Set<MapVehicleFilter> = entries.filter { it.defaultEnabled }.toSet()
        fun forMode(mode: String): MapVehicleFilter? = entries.firstOrNull { mode in it.modes }
    }
}

/** Filters general-map vehicles by the active mode switches while keeping unknown modes visible. */
internal fun mapLayerVehicles(
    vehicles: List<Vehicle>,
    enabledFilters: Set<MapVehicleFilter>
): List<Vehicle> = vehicles.filter { vehicle ->
    val filter = MapVehicleFilter.forMode(vehicle.mode)
    filter == null || filter in enabledFilters
}

/** The general map's optional layer never hides coaches or rail replacement buses. */
internal fun mapLayerVehicles(vehicles: List<Vehicle>, showLocalBuses: Boolean): List<Vehicle> =
    mapLayerVehicles(
        vehicles,
        if (showLocalBuses) MapVehicleFilter.entries.toSet()
        else MapVehicleFilter.entries.toSet() - setOf(MapVehicleFilter.LocalBus, MapVehicleFilter.Trolleybus)
    )

internal fun parseVehiclePosition(v: JSONObject): Vehicle? =
    v.optJSONObject("trip")?.let { parseVehiclePosition(v, it) }

internal fun parseVehiclePosition(v: JSONObject, trip: JSONObject): Vehicle? {
    fun JSONObject.text(key: String) = if (isNull(key)) "" else optString(key).trim()
        .takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
    val route = trip.optJSONObject("route")
    val id = trip.text("gtfsId")
    val lat = v.optDouble("lat")
    val lon = v.optDouble("lon")
    if (id.isBlank() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    val mode = route?.text("mode").orEmpty()
    val routeName = route?.text("shortName").orEmpty()
    val tripName = trip.text("tripShortName")
    val name = (if (mode == "BUS") routeName else tripName)
        .ifBlank { tripName }.ifBlank { routeName }.ifBlank { v.text("label") }.ifBlank { "Járat" }
    return Vehicle(id, hungarianServiceLabel(name), trip.text("tripHeadsign"), mode,
        lat, lon, route?.text("color").orEmpty(),
        trip.text("trainCategoryName"), route?.text("longName").orEmpty(),
        v.optDouble("heading").takeIf { it.isFinite() && it >= 0 && it <= 360 }?.rem(360.0),
        v.text("vehicleModel"), v.text("licensePlate"), v.text("uicCode"), routeName, v.text("vehicleId"),
        route?.optJSONObject("agency")?.text("name").orEmpty(),route?.let(RouteLogo::parse),
        v.optLong("lastUpdated",0L).takeIf {it>0},
        v.optDouble("speed").takeIf {it.isFinite() && it>=0})
}

/** Keeps words like "Vörösvári út" together: a line may only break after ",", ";", "/" or a dash. */
internal fun breakAtSymbols(name: String): String=buildString(name.length) {
    name.forEachIndexed {i,c ->
        val prev=name.substring(0,i).trimEnd().lastOrNull()
        append(if(c==' ' && prev!=null && prev !in ",;/-–") ' ' else c)
    }
}

/** Provider timestamps are Unix seconds, independent of when the map was fetched. */
internal fun vehiclePositionAge(updatedSeconds: Long?, nowSeconds: Long): String {
    if(updatedSeconds==null || updatedSeconds<=0) return "A pozíció frissítési ideje ismeretlen"
    val elapsed=(nowSeconds-updatedSeconds).coerceAtLeast(0)
    return "Pozíció frissítve: $elapsed másodperce"
}

/** OTP reports speed in m/s; retain a reported zero and omit unavailable/invalid values. */
internal fun vehicleSpeedLabel(speedMetersPerSecond: Double?): String? = speedMetersPerSecond
    ?.takeIf {it.isFinite() && it>=0}
    ?.let {it*3.6}
    ?.takeIf {it.isFinite()}
    ?.let {"${kotlin.math.round(it).toLong()} km/h"}
