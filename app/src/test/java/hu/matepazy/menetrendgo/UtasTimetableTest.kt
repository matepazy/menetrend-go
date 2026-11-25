package hu.matepazy.menetrendgo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class UtasTimetableTest {
    private val body = JSONObject(javaClass.classLoader!!.getResource("utas-trip-details-2026-11-19.json").readText())
    private val date = LocalDate.of(2026, 11, 19)
    private val id = "hkir_8086_684-5"
    private val vehicle = Vehicle(
        "1:mav-id", "8086", "", "COACH", 47.205643, 18.384314,
        vehicleId = "mav-vehicle", utasTrip = UtasTripRef(id, date, "hkir_AOIF467"),
    )

    @Test fun displaysScheduledAndPredictedTimesFromTheTripEnvelope() {
        val detail = parseUtasTrip(body, id, date)
        val stops = detail.vehicleStops()
        assertEquals(25, stops.size)
        assertEquals(1795106100000L, stops.first().scheduled)
        assertEquals(1795106314000L, stops.first().arrival)
        assertEquals("Székesfehérvár, autóbusz-állomás", stops.last().name)
        assertEquals(1795108781000L, stops.last().arrival)
        assertEquals("Székesfehérvár, Hosszú temető", stops[22].name)
        assertEquals(1795108481000L, stops[22].departure)
        assertEquals(VehicleStopProgress(22, false), vehicleStopProgress(stops, detail, vehicle))
        assertNull(vehicleStopProgress(stops, detail, vehicle.copy(utasTrip = vehicle.utasTrip!!.copy(vehicleId = "other"))))
    }

    @Test fun scheduleOnlyPredictionsDoNotReplaceScheduledTimes() {
        body.getJSONObject("data").getJSONObject("entry").getJSONArray("stopTimes").getJSONObject(0)
            .put("predictionScheduled", true)
        val origin = parseUtasTrip(body, id, date).vehicleStops().first()
        assertFalse(origin.realtime)
        assertEquals(origin.scheduled, origin.arrival)
        assertEquals(origin.scheduled, origin.departure)
    }

    @Test fun anEmptyOrMismatchedTripCannotBecomeTheDisplayedTimetable() {
        assertThrows(IllegalStateException::class.java) { parseUtasTrip(body, id, date.plusDays(1)) }
        body.getJSONObject("data").getJSONObject("entry").put("stopTimes", JSONArray())
        assertThrows(IllegalStateException::class.java) { parseUtasTrip(body, id, date) }
    }

    @Test fun timetableFallbackRunsOnProviderFailureButNotCancellation() = runBlocking {
        val backup = JSONObject()
        assertSame(backup, loadVehicleTimetable({ error("Unavailable") }, { backup }))
        assertSame(backup, loadVehicleTimetable(null, { backup }))
        try {
            loadVehicleTimetable({ throw CancellationException() }, { error("Fallback used after cancellation") })
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Cancellation must reach the caller when the user switches vehicles.
        }
    }
}
