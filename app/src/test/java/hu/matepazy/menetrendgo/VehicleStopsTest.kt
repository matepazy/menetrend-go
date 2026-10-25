package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate
import java.time.Instant

class VehicleStopsTest {
    private fun stop(name: String, scheduled: Long, arrival: Long=scheduled, departure: Long=arrival, live: Boolean=true) =
        VehicleStop(name,scheduled,arrival,departure,live,"")

    @Test fun delayKeepsAnUpcomingStopEvenAfterItsScheduledTime() {
        val stops=listOf(stop("Elhagyva",60_000),stop("Késik",120_000,300_000),stop("Végállomás",600_000))
        assertEquals(listOf("Késik","Végállomás"),upcomingVehicleStops(stops,240_000).map {it.name})
    }
    @Test fun dwellAndTerminalAreHandled() {
        val stops=listOf(stop("Megálló",120_000,120_000,180_000),stop("Végállomás",300_000))
        assertEquals("Megálló",upcomingVehicleStops(stops,150_000).first().name)
        assertTrue(upcomingVehicleStops(stops,360_000).isEmpty())
    }
    @Test fun strikeThroughRequiresLiveDataAndAVisibleMinuteDifference() {
        assertTrue(stop("Késő",60_000,120_000).changed)
        assertTrue(stop("Korai",120_000,60_000).changed)
        assertFalse(stop("Menetrendi",60_000,120_000,live=false).changed)
        assertFalse(stop("Másodpercek",60_000,65_000).changed)
    }

    @Test fun autumnClockChangeDoesNotFinish1507ElevenAnHourEarly() {
        // Captured from MÁV on 2026-10-25: midnight is still UTC+2,
        // while the afternoon/evening timetable is in UTC+1.
        val detail=JSONObject("""{"stoptimes":[
            {"serviceDay":1792879200,"scheduledArrival":73320,"scheduledDeparture":73320,
             "realtime":true,"realtimeArrival":73339,"realtimeDeparture":73339,
             "stop":{"gtfsId":"kisber","name":"Kisbér"}},
            {"serviceDay":1792879200,"scheduledArrival":75600,"scheduledDeparture":75600,
             "realtime":true,"realtimeArrival":75619,"realtimeDeparture":75619,
             "stop":{"gtfsId":"gyor","name":"Győr"}}]}""")
        val stops=vehicleStops(detail,"2026-10-25")
        assertEquals("20:22",timeLabel(stops.first().scheduled))
        assertEquals("21:00",timeLabel(stops.last().scheduled))
        assertEquals(listOf("Kisbér","Győr"),upcomingVehicleStops(stops,
            Instant.parse("2026-10-25T19:08:00Z").toEpochMilli()).map {it.name})
    }

    @Test fun springClockChangeAndOvernightTimesKeepLocalTimetableHours() {
        assertEquals("20:22",timeLabel(vehicleStopTime(LocalDate.parse("2026-03-29"),73320)))
        assertEquals(Instant.parse("2026-10-26T00:15:00Z").toEpochMilli(),
            vehicleStopTime(LocalDate.parse("2026-10-25"),25*3600+15*60))
        assertEquals(Instant.parse("2026-10-24T18:22:00Z").toEpochMilli(),
            vehicleStopTime(LocalDate.parse("2026-10-24"),73320))
    }

    private val vehicle=Vehicle("trip","1507/11","","COACH",47.4,18.1,vehicleId="selected")
    private fun progress(id: String, status: String="IN_TRANSIT_TO", vehicleId: String="selected", arrival: Long=0): JSONObject =
        JSONObject().put("vehiclePositions",JSONArray().put(JSONObject().put("vehicleId",vehicleId)
            .put("stopRelationship",JSONObject().put("status",status).put("arrivalTime",arrival)
                .put("stop",JSONObject().put("gtfsId",id)))))

    @Test fun liveStopIdentityWinsOverElapsedTimetableAndDuplicateNames() {
        val stops=listOf(stop("Same station",0).copy(id="platform-a"),
            stop("Same station",60_000).copy(id="platform-b"),stop("Terminal",120_000).copy(id="terminal"))
        listOf("IN_TRANSIT_TO","INCOMING_AT","STOPPED_AT").forEach {status ->
            assertEquals(stops.drop(1),upcomingVehicleStops(stops,900_000,progress("platform-b",status),vehicle))
        }
        assertEquals(stops.takeLast(1),upcomingVehicleStops(stops,900_000,progress("terminal"),vehicle))
    }

    @Test fun missingUnknownOrOtherVehiclesProgressFallsBackToTimetable() {
        val stops=listOf(stop("Past",0).copy(id="past"),stop("Next",600_000).copy(id="next"))
        listOf(null,progress("unknown"),progress("past",vehicleId="other"),progress("past",status="UNKNOWN")).forEach {detail ->
            assertEquals(stops.drop(1),upcomingVehicleStops(stops,300_000,detail,vehicle))
        }
    }

    @Test fun repeatedStopIdsUseTheReportedVisitTime() {
        val stops=listOf(stop("Loop",0).copy(id="loop"),stop("Middle",60_000).copy(id="middle"),
            stop("Loop",120_000).copy(id="loop"))
        val detail=progress("loop",arrival=120).put("stoptimes",JSONArray("""[
            {"realtimeArrival":0},{"realtimeArrival":60},{"realtimeArrival":120}]"""))
        assertEquals(stops.takeLast(1),upcomingVehicleStops(stops,300_000,detail,vehicle))
    }
}
