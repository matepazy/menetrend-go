package hu.matepazy.menetrendgo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class JourneyTransfersTest {
    private val now = 1_800_000_000_000L
    private val station = Station("stop", "Szolnok, vasútállomás", 47.17, 20.18)
    private fun leg(start: Long, end: Long, mode: String = "RAIL") =
        Leg(mode, start, end, station, station, "IR 80", "trip", true, 0, emptyList(), "")
    private fun times(arrival: Long, departure: Long, live: Boolean = true) = TransferTimes(
        TransferEstimate(ServiceTime(arrival, arrival, live), now),
        TransferEstimate(ServiceTime(departure, departure, live), now))

    @Test fun sumsOnlyInterchangeWalksAndDoesNotCountWaitingOrAccessWalks() {
        val journey = Journey(0, listOf(leg(now, now+60_000, "WALK"), leg(now+60_000, now+120_000),
            leg(now+180_000, now+240_000, "WALK"), leg(now+240_000, now+360_000, "WALK"),
            leg(now+600_000, now+900_000), leg(now+900_000, now+960_000, "WALK")))
        assertEquals(listOf(TransferConnection(1, 4, 180_000)), journeyTransfers(journey))
        assertEquals(300_000L, assessTransfer(journeyTransfers(journey).single(), times(now+120_000, now+600_000), now).remainingMillis)
        assertTrue(journeyTransfers(Journey(0, listOf(leg(now, now+60_000)))).isEmpty())
    }

    @Test fun classifiesTightMissedAndAvailableWithoutRoundingAwayShortfalls() {
        val connection = TransferConnection(0, 2, 180_000)
        assertEquals(TransferRisk.Missed, assessTransfer(connection, times(now+60_000, now+239_999), now).risk)
        assertEquals(TransferRisk.Tight, assessTransfer(connection, times(now+60_000, now+240_000), now).risk)
        assertEquals(TransferRisk.Available, assessTransfer(connection, times(now+60_000, now+420_000), now).risk)
    }

    @Test fun scheduledStaleFailedUnknownAndPastNeverClaimALiveConnection() {
        val connection = TransferConnection(0, 1, 0)
        val live = times(now+60_000, now+120_000)
        assertEquals(TransferRisk.Scheduled, assessTransfer(connection, times(now+60_000, now+120_000, false), now).risk)
        assertEquals(TransferRisk.Unknown, assessTransfer(connection, live, now+TransferFreshnessMs+1).risk)
        assertEquals(TransferRisk.Unknown, assessTransfer(connection, live.copy(arrival=live.arrival.copy(failed=true)), now).risk)
        assertEquals(TransferRisk.Unknown, assessTransfer(connection, live.copy(arrival=live.arrival.copy(fetchedAt=0)), now).risk)
        assertEquals(TransferRisk.Cancelled, assessTransfer(connection, live.copy(departure=live.departure.copy(cancelled=true)), now).risk)
        assertEquals(TransferRisk.Past, assessTransfer(connection, times(now-60_000, now-1), now).risk)
    }

    private val day = LocalDate.of(2026, 10, 25).atStartOfDay(Budapest).toEpochSecond()
    private fun entry(seconds: Long = 90_000) = JSONObject().put("serviceDay", day).put("scheduledArrival", seconds)
        .put("realtimeArrival", seconds+120).put("realtime", true).put("stop", station.json().put("gtfsId", station.id))
    private fun trip(vararg entries: JSONObject) = JSONObject().put("gtfsId", "trip").put("stoptimes", JSONArray(entries.toList()))

    @Test fun directJourneyRefreshesBothEndpointsWithOneRequestAndSkipsWalking() = runBlocking {
        val start = (day + 89_000) * 1000
        val end = (day + 90_000) * 1000
        val journey = Journey(1000, listOf(leg(start - 60_000, start, "WALK"), leg(start, end)))
        var requests = 0
        val refreshed = refreshJourneyLegTimes(journey, emptyMap()) { _, _ ->
            requests++
            trip(entry(89_000).put("scheduledDeparture", 89_000).put("realtimeDeparture", 89_060), entry())
        }
        assertEquals(setOf(1), refreshed.keys)
        assertEquals(1, requests)
        assertEquals(start + 60_000, refreshed.getValue(1).departure.time.estimate)
        assertEquals(end + 120_000, refreshed.getValue(1).arrival.time.estimate)
    }

    @Test fun journeyRefreshRetainsLastKnownTimesOnFailureAndPropagatesCancellation() = runBlocking {
        val journey = Journey(60, listOf(leg((day + 89_000) * 1000, (day + 90_000) * 1000)))
        val previous = mapOf(0 to times(now, now + 60_000))
        val refreshed = refreshJourneyLegTimes(journey, previous) { _, _ -> error("Offline") }
        assertEquals(previous.getValue(0).arrival.time, refreshed.getValue(0).arrival.time)
        assertEquals(previous.getValue(0).departure.time, refreshed.getValue(0).departure.time)
        assertTrue(refreshed.getValue(0).arrival.failed)
        assertTrue(refreshed.getValue(0).departure.failed)
        try {
            refreshJourneyLegTimes(journey, previous) { _, _ -> throw CancellationException() }
            fail("Cancellation must stop the refresh")
        } catch (_: CancellationException) { }
    }

    @Test fun matchesExactServiceDayAndStopOccurrenceBeyondMidnightAcrossDst() {
        val arrival = (day+90_000)*1000
        val leg = leg(arrival-60_000, arrival)
        val result = transferStopEstimate(trip(entry(), entry(80_000)), leg, true, now)!!
        assertEquals(arrival+120_000, result.time.estimate)
        assertNull(transferStopEstimate(trip(entry()).put("gtfsId", "other"), leg, true, now))
        assertNull(transferStopEstimate(trip(entry().put("serviceDay", day-86400)), leg, true, now))
        assertNull(transferStopEstimate(trip(entry().put("stop", station.json().put("gtfsId", "other"))), leg, true, now))
        assertNull(transferStopEstimate(trip(entry(), entry()), leg, true, now))
    }

    @Test fun missingRealtimeFallsBackToScheduleAndCancellationIsPreserved() {
        val arrival = (day+90_000)*1000
        val leg = leg(arrival-60_000, arrival)
        val result = transferStopEstimate(trip(entry().put("realtimeArrival", JSONObject.NULL).put("realtimeState", "CANCELED")), leg, true, now)!!
        assertFalse(result.time.realtime)
        assertEquals(arrival, result.time.estimate)
        assertTrue(result.cancelled)
        val departure = entry().put("scheduledDeparture", 89_940).put("realtimeDeparture", 90_000)
        assertEquals(arrival, transferStopEstimate(trip(departure), leg, false, now)!!.time.estimate)
    }

    @Test fun onwardSearchUsesLaterOfArrivalAndNowAndRoundsForward() {
        val expected = java.time.Instant.ofEpochMilli(now+60_000).atZone(Budapest).toLocalDateTime()
        assertEquals(expected, onwardSearchTime(now+1, now))
        assertEquals(expected, onwardSearchTime(now-120_000, now+1))
        assertEquals(expected, onwardSearchTime(now+60_000, now))
    }

    @Test fun normalAndUnverifiableConnectionsAddNoWarning() {
        val connection = TransferConnection(0, 1, 0)
        assertNull(transferWarning(connection, times(now+60_000, now+600_000), now))
        assertNull(transferWarning(connection, times(now+60_000, now+120_000), now+TransferFreshnessMs+1))
        assertEquals("Szoros átszállás", transferWarning(connection, times(now+60_000, now+120_000, false), now))
    }

    @Test fun nextServiceAccountsForWalkingAndBufferAndRejectsOriginalOrMultiVehicleRoutes() {
        val original = leg(now+60_000, now+600_000)
        val walk = leg(now, now+180_000, "WALK")
        val tooSoon = original.copy(tripId="early", start=now+299_999)
        val next = original.copy(tripId="next", start=now+300_000)
        val later = original.copy(tripId="later", start=now+600_000)
        val plans = listOf(Journey(0,listOf(walk,later)), Journey(0,listOf(walk,tooSoon)),
            Journey(0,listOf(walk,next)), Journey(0,listOf(original)), Journey(0,listOf(next,later)),
            Journey(0,listOf(next.copy(mode="BUS"))))
        val result = reachableTransferCandidates(plans,original,now,now)
        assertEquals(listOf("next","later"), result.map { it.leg.tripId })
        assertEquals(now+300_000, result.first().readyAt)
        assertTrue(reachableTransferCandidates(listOf(Journey(0,listOf(next))), original, now, now+600_000).isEmpty())
    }
}
