package hu.matepazy.menetrendgo

/** Match every term across the available service and vehicle fields, independent of the viewport. */
internal class VehicleSearch(vehicles: List<Vehicle>) {
    private val entries = vehicles.map { vehicle ->
        vehicle to listOf(vehicle.name, vehicle.routeShortName, vehicle.routeLongName,
            vehicle.headsign, vehicle.category, hungarianServiceName(vehicle.category, vehicle.mode),
            vehicle.mode, vehicle.model, vehicle.registration, vehicle.uicCode, vehicle.vehicleId, vehicle.id)
            .map(::vehicleSearchKey)
    }

    fun filter(query: String): List<Vehicle> {
        val terms = stationSearchKey(query).split(' ').map(::vehicleSearchKey).filter { it.isNotEmpty() }
        return entries.filter { (_, fields) -> terms.all { term -> fields.any { term in it } } }.map { it.first }
    }
}

private fun vehicleSearchKey(value: String) = stationSearchKey(value).replace(Regex("[^\\p{L}\\p{N}]"), "")
