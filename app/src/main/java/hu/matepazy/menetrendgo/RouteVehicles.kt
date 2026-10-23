package hu.matepazy.menetrendgo

import java.time.Instant

/** Match the selected trips, never a route number shared by unrelated or next-day services. */
fun routeVehicles(journey: Journey, vehicles: List<Vehicle>, now: Long): List<Vehicle> {
    val today=Instant.ofEpochMilli(now).atZone(Budapest).toLocalDate()
    val tripIds=journey.legs.filterNot {it.walking}.filter {leg ->
        val first=Instant.ofEpochMilli(leg.departureTime.scheduled).atZone(Budapest).toLocalDate()
        val last=Instant.ofEpochMilli(maxOf(leg.arrivalTime.scheduled,leg.end)).atZone(Budapest).toLocalDate()
        !today.isBefore(first) && !today.isAfter(last)
    }.map {it.tripId}.filter {it.isNotBlank()}.toSet()
    return vehicles.filter {it.id in tripIds}
}
