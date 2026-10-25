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
    LocalBus("local_bus", "map_local_buses", "Helyi autóbuszok", "Városi autóbuszjáratok élő pozíciói", false, setOf("BUS")),
    SuburbanRailway("suburban_railway", "map_suburban_railway", "HÉV", "Budapesti elővárosi vasútvonalak", true, setOf("SUBURBAN_RAILWAY")),
    TramTrain("tramtrain", "map_tramtrain", "TramTrain", "Szeged–Hódmezővásárhely vasútvillamos", true, setOf("TRAMTRAIN", "TRAM")),
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
        if (showLocalBuses) MapVehicleFilter.entries.toSet() else MapVehicleFilter.defaultFilters
    )

internal fun parseVehiclePosition(v: JSONObject): Vehicle? {
    fun JSONObject.text(key: String) = if (isNull(key)) "" else optString(key).trim()
        .takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
    val trip = v.optJSONObject("trip") ?: return null
    val route = trip.optJSONObject("route")
    val id = trip.text("gtfsId")
    if (id.isBlank() || v.isNull("lat") || v.isNull("lon")) return null
    val mode = route?.text("mode").orEmpty()
    val routeName = route?.text("shortName").orEmpty()
    val tripName = trip.text("tripShortName")
    val name = (if (mode == "BUS") routeName else tripName)
        .ifBlank { tripName }.ifBlank { routeName }.ifBlank { v.text("label") }.ifBlank { "Járat" }
    return Vehicle(id, hungarianServiceLabel(name), trip.text("tripHeadsign"), mode,
        v.getDouble("lat"), v.getDouble("lon"), route?.text("color").orEmpty(),
        trip.text("trainCategoryName"), route?.text("longName").orEmpty(),
        v.optDouble("heading").takeIf { it.isFinite() && it >= 0 && it <= 360 }?.rem(360.0),
        v.text("vehicleModel"), v.text("licensePlate"), v.text("uicCode"), routeName, v.text("vehicleId"),
        route?.optJSONObject("agency")?.text("name").orEmpty(),route?.let(RouteLogo::parse),
        v.optLong("lastUpdated",0L).takeIf {it>0})
}

/** Provider timestamps are Unix seconds, independent of when the map was fetched. */
internal fun vehiclePositionAge(updatedSeconds: Long?, nowSeconds: Long): String {
    if(updatedSeconds==null || updatedSeconds<=0) return "A pozíció frissítési ideje ismeretlen"
    val elapsed=(nowSeconds-updatedSeconds).coerceAtLeast(0)
    val age=when {
        elapsed<60 -> "most"
        elapsed<3600 -> "${elapsed/60} perccel ezelőtt"
        elapsed<86_400 -> "${elapsed/3600} órával ezelőtt"
        else -> "${elapsed/86_400} nappal ezelőtt"
    }
    return "Pozíció frissítve: $age"
}
