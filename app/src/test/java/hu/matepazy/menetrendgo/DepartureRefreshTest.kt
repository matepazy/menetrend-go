package hu.matepazy.menetrendgo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DepartureRefreshTest {
    private val day = LocalDate.of(2026, 11, 9).atStartOfDay(Budapest).toEpochSecond()
    private val station = Station("stop", "Szolnok", 47.17, 20.18)
    private val leg = Leg("RAIL", (day + 36_000) * 1000, (day + 39_600) * 1000,
        station, station, "IR", "trip", false, 0, emptyList(), "")
    private fun trip(delay: Int, live: Boolean = true) = JSONObject().put("gtfsId", "trip")
        .put("stoptimes", JSONArray(listOf(36_000, 39_600).map { seconds ->
            JSONObject().put("serviceDay", day).put("scheduledDeparture", seconds)
                .put("scheduledArrival", seconds).put("realtimeDeparture", seconds + delay)
                .put("realtimeArrival", seconds + delay).put("realtime", live)
                .put("stop", JSONObject().put("gtfsId", station.id))
        }))

    @Test fun updatesLoadedPagesWithOneRequestPerServiceAndKeepsStableKeysAndWalking() = runBlocking {
        val walk = leg.copy(mode = "WALK", tripId = "", start = leg.start - 60_000, end = leg.start)
        val original = listOf(Journey(3600, listOf(leg)), Journey(3660, listOf(walk, leg)))
        var requests = 0
        val updated = refreshDepartureJourneys(original) { _, _ -> requests++; trip(180) }
        assertEquals(1, requests)
        assertEquals(original.map { it.key }, updated.map { it.key })
        assertEquals(walk, updated[1].legs[0])
        updated.forEach {
            assertEquals(leg.start + 180_000, it.boardingLeg.start)
            assertEquals(leg.end + 180_000, it.alightingLeg.end)
            assertEquals(leg.start, it.boardingLeg.departureTime.scheduled)
            assertEquals(180, it.boardingLeg.departureDelay)
            assertTrue(it.boardingLeg.realTime)
        }
        val scheduled = refreshDepartureJourneys(updated) { _, _ -> trip(0, live = false) }
        assertEquals(original.map { it.key }, scheduled.map { it.key })
        assertEquals(leg.start, scheduled[0].boardingLeg.start)
        assertFalse(scheduled[0].boardingLeg.realTime)
    }

    @Test fun failureOrUnmatchedStopsRetainExistingEstimates() = runBlocking {
        val original = listOf(Journey(3600, listOf(leg.copy(start = leg.start + 120_000,
            end = leg.end + 120_000, realTime = true, departureDelay = 120, arrivalDelay = 120))))
        assertEquals(original, refreshDepartureJourneys(original) { _, _ -> error("offline") })
        assertEquals(original, refreshDepartureJourneys(original) { _, _ ->
            JSONObject().put("gtfsId", "another-trip")
        })
    }

    @Test(expected = CancellationException::class)
    fun cancellationStopsRefresh() { runBlocking {
        refreshDepartureJourneys(listOf(Journey(3600, listOf(leg)))) { _, _ -> throw CancellationException() }
    } }
}
