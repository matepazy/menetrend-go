package hu.matepazy.menetrendgo

import org.json.JSONObject

/** The general map's optional layer never hides coaches or rail replacement buses. */
internal fun mapLayerVehicles(vehicles: List<Vehicle>, showLocalBuses: Boolean): List<Vehicle> =
    if (showLocalBuses) vehicles else vehicles.filter { it.mode != "BUS" }

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
        v.text("vehicleModel"), v.text("licensePlate"), v.text("uicCode"), routeName, v.text("vehicleId"))
}
