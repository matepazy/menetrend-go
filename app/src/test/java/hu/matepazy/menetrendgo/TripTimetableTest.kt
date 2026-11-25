package hu.matepazy.menetrendgo

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TripTimetableTest {
    private val day = LocalDate.of(2026, 11, 8)
    private fun at(seconds: Long) = vehicleStopTime(day, seconds)
    private val trip = mavTrip(JSONObject("""{"gtfsId":"1:552","tripShortName":"552","tripHeadsign":"Szeged","trainCategoryName":"InterCity",
        "route":{"shortName":"IC","longName":"Budapest – Szeged","mode":"RAIL","color":"0066CC","agency":{"name":"MÁV-START"}},
        "stoptimes":[
          {"realtime":true,"stop":{"gtfsId":"1:a","name":"A","lat":47.0,"lon":19.0},"scheduledDeparture":36000,"realtimeDeparture":36060},
          {"realtime":true,"stop":{"gtfsId":"1:b","name":"B","lat":46.9,"lon":19.5,"platformCode":"2"},
           "scheduledArrival":37000,"realtimeArrival":37120,"scheduledDeparture":37060,"realtimeDeparture":37180},
          {"realtime":false,"stop":{"gtfsId":"1:c","name":"C","lat":46.2,"lon":20.1},"scheduledArrival":40000}],
        "vehiclePositions":[{"vehicleId":"v1","label":"552","lat":46.95,"lon":19.3,"heading":360,"speed":22.5,"lastUpdated":1762590000,
          "vehicleModel":"Taurus","licensePlate":"470 010","uicCode":"91 55 0470 010-1",
          "stopRelationship":{"status":"STOPPED_AT","stop":{"gtfsId":"1:b"},"arrivalTime":37120}}]}"""), day.toString())

    @Test fun stopRowsKeepArrivalDwellAndTerminalTimes() {
        val stops = trip.vehicleStops()
        assertEquals(VehicleStop("A", at(36000), at(36060), at(36060), true, "", "1:a"), stops[0])
        assertEquals(VehicleStop("B", at(37000), at(37120), at(37180), true, "2", "1:b"), stops[1])
        assertEquals(VehicleStop("C", at(40000), at(40000), at(40000), false, "", "1:c"), stops[2])
    }

    @Test fun tripVehiclesCarryIdentityPositionAndStopProgress() {
        val v = trip.vehicles.single()
        assertEquals("v1", v.id)
        assertEquals(0.0, v.heading!!, 0.0)
        assertEquals(VehicleStopStatus.StoppedAt, v.stopStatus)
        assertEquals(Gid("mav:1:b"), v.stopId)
        assertEquals(at(37120), v.stopArrival)
        val map = Vehicle("1:552", "552", "", "RAIL", 0.0, 0.0, vehicleId = "v1")
        assertEquals(VehicleStopProgress(1, true), vehicleStopProgress(trip.vehicleStops(), trip, map))
    }

    @Test fun liveVehicleNamesTrainsByNumberAndBusesByLine() {
        val train = trip.liveVehicle(trip.vehicles.single())!!
        assertEquals("1:552", train.id)
        assertEquals("552", train.name)
        assertEquals("Taurus", train.model)
        assertEquals("470 010", train.registration)
        assertEquals(1762590000L, train.positionUpdatedSeconds)
        val bus = trip.copy(summary = trip.summary.copy(route = trip.summary.route.copy(shortName = "260", mode = Mode.BUS)))
        assertEquals("260", bus.liveVehicle(trip.vehicles.single())!!.name)
        assertNull(trip.liveVehicle(trip.vehicles.single().copy(lat = null)))
    }

    @Test fun cancelledRowsAreLiveEvidenceButNotRealtimeEstimates() {
        val cancelled = mavTrip(JSONObject("""{"gtfsId":"1:9","stoptimes":[{"realtime":true,"realtimeState":"CANCELED",
            "stop":{"gtfsId":"1:a","name":"A"},"scheduledDeparture":36000,"realtimeDeparture":36000}]}"""), day.toString())
        val call = cancelled.calls.single()
        assertTrue(call.cancelled)
        assertFalse(cancelled.vehicleStops().single().realtime)
    }
}
