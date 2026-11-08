package hu.matepazy.menetrendgo

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TripStopTimeTest {
    private val day = "2026-11-08"
    private fun at(seconds: Long) = vehicleStopTime(LocalDate.parse(day), seconds)

    @Test fun departureUsesScheduledAndLivePairRatherThanArrivalOrDelayField() {
        val stop = JSONObject("""{"scheduledDeparture":36000,"realtimeDeparture":36300,
            "scheduledArrival":35940,"realtimeArrival":36060,"departureDelay":0,"realtime":true}""")
        assertEquals(ServiceTime(at(36000), at(36300), true), tripStopTime(stop, day))
    }

    @Test fun unavailableLiveDataKeepsScheduledTime() {
        val stop = JSONObject("""{"scheduledDeparture":36000,"realtimeDeparture":36300,"realtime":false}""")
        assertEquals(ServiceTime(at(36000), at(36000), false), tripStopTime(stop, day))
        stop.put("realtime", true).put("realtimeDeparture", JSONObject.NULL)
        assertEquals(ServiceTime(at(36000), at(36000), false), tripStopTime(stop, day))
    }

    @Test fun arrivalFallbackPreservesServiceDayAndMidnightRollover() {
        val stop = JSONObject("""{"scheduledDeparture":null,"scheduledArrival":86280,
            "realtimeArrival":86580,"realtime":true}""")
            .put("serviceDay", LocalDate.parse(day).atStartOfDay(Budapest).toEpochSecond())
        val time = tripStopTime(stop, "2026-11-09")!!
        assertEquals(ServiceTime(at(86280), at(86580), true), time)
        assertTrue(time.crossesDay)
        assertNull(tripStopTime(JSONObject(), day))
    }
}
