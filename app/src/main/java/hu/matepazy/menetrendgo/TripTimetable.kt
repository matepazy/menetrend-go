package hu.matepazy.menetrendgo

/** The drawer's stop row: scheduled and estimated arrival, and the departure (dwell) estimate. */
internal fun Call.vehicleStop(): VehicleStop? {
    val first = arrival ?: departure ?: return null
    val leaving = departure ?: first
    return VehicleStop(stop.name, first.scheduled, first.estimate, leaving.estimate,
        arrival?.status == TimeStatus.Realtime || departure?.status == TimeStatus.Realtime, platform, stop.id.local, stopSequence)
}

/** Calls without any time are left out, as the provider gives no evidence for them. */
fun Trip.vehicleStops(): List<VehicleStop> = calls.mapNotNull { it.vehicleStop() }

/** The trip's vehicle as a map vehicle, when the provider reported where it is. */
internal fun Trip.liveVehicle(position: TripVehicle): Vehicle? {
    val lat = position.lat ?: return null; val lon = position.lon ?: return null
    val route = summary.route
    val name = (if (route.mode == Mode.BUS) route.shortName else summary.shortName)
        .ifBlank { summary.shortName }.ifBlank { route.shortName }.ifBlank { position.label }.ifBlank { "Járat" }
    return Vehicle(id.local, hungarianServiceLabel(name), summary.headsign, route.mode.otp, lat, lon, route.color,
        summary.category, route.longName, position.heading, position.model, position.plate, position.uicCode, route.shortName,
        position.id, route.agency?.name.orEmpty(), route.logo, position.updatedSeconds, position.speedMetersPerSecond)
}
