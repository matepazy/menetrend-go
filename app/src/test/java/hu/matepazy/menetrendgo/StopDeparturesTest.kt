package hu.matepazy.menetrendgo

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StopDeparturesTest {
    @Test fun clockTimeAppearsOnlyBeyondFifteenMinutes() {
        val now = java.time.Instant.parse("2026-10-31T12:00:00Z").toEpochMilli()
        assertNull(departureClockTime(now + 15 * 60_000L, now))
        assertEquals("13:15", departureClockTime(now + 15 * 60_000L + 1, now))
        assertEquals("14:00", departureClockTime(now + 60 * 60_000L, now))
        assertNull(departureClockTime(now + 60 * 60_000L, now, cancelled = true))
        assertNull(departureClockTime(now - 1, now))
        assertNull(departureClockTime(now + 60 * 60_000L, now + 45 * 60_000L))
    }
    @Test fun countdownStatusUsesLiveEvidenceAndTenMinuteDelayBoundary() {
        val scheduled = 1700000000000L
        fun status(delay: Long, live: Boolean = true) = departureStatus(ServiceTime(scheduled, scheduled + delay, live))
        assertEquals(DepartureStatus.Scheduled, status(900000, false))
        assertEquals(DepartureStatus.Live, status(0))
        assertEquals(DepartureStatus.Live, status(-60000))
        assertEquals(DepartureStatus.MinorDelay, status(1))
        assertEquals(DepartureStatus.MinorDelay, status(599999))
        assertEquals(DepartureStatus.MajorDelay, status(600000))
        assertEquals(DepartureStatus.MajorDelay, status(600001))
    }
    @Test fun countdownUsesMostForImminentServicesAndRoundsRemainingMinutesUp() {
        val now = 1700000000000L
        assertEquals("Most", departureCountdown(now, now))
        assertEquals("Most", departureCountdown(now + 59999, now))
        assertEquals("1 perc", departureCountdown(now + 60000, now))
        assertEquals("2 perc", departureCountdown(now + 60001, now))
        assertEquals("61 perc", departureCountdown(now + 3600001, now))
    }
    @Test fun widgetCacheRetainsAbsoluteTimesAndOfficialServiceLogo() {
        val station = Station("stop", "Stop", 47.0, 19.0)
        val logo = RouteLogo(358, "00A0E3", "FFFFFF")
        val time = ServiceTime(1700000100000, 1700000160000, true)
        val leg = Leg("RAIL", time.estimate, time.estimate, station, station, "4116 személyvonat", "trip", true, 60,
            emptyList(), "", routeLogo = logo)
        val original = StopDeparture("trip", "Pusztaszabolcs", time, "11", false, leg)
        val restored = cachedDeparture(JSONObject(original.cacheJson().toString()))
        assertEquals(original.time, restored.time)
        assertEquals(logo, restored.leg.routeLogo)
        assertEquals(original.leg.route, restored.leg.route)
        assertEquals(original.headsign, restored.headsign)
        assertEquals(original.platform, restored.platform)
    }
    @Test fun parentStationsCollapsePlatformsButDirectionalStopsRemainSeparate() {
        val data = JSONObject("""{"stopsByRadius":{"edges":[
            {"node":{"stop":{"gtfsId":"rail:1_2","name":"Platform 2","lat":47.0,"lon":19.0,"parentStation":{"gtfsId":"rail:1","name":"Station","lat":47.0,"lon":19.0}}}},
            {"node":{"stop":{"gtfsId":"rail:1_3","name":"Platform 3","lat":47.0,"lon":19.0,"parentStation":{"gtfsId":"rail:1","name":"Station","lat":47.0,"lon":19.0}}}},
            {"node":{"stop":{"gtfsId":"bus:east","name":"Stop","lat":47.0,"lon":19.0}}},
            {"node":{"stop":{"gtfsId":"bus:west","name":"Stop","lat":47.0,"lon":19.001}}}
        ]}}""")
        val stops = parseMapStops(data)
        assertEquals(listOf("rail:1", "bus:east", "bus:west"), stops.map { it.id })
        assertEquals("Station", stops.first().name)
    }
    private fun row(trip: String, scheduled: Long, live: Long = scheduled, realtime: Boolean = true,
        pickup: String = "SCHEDULED", state: String = "UPDATED") = JSONObject("""{
        "serviceDay":1700000000,"scheduledDeparture":$scheduled,"realtimeDeparture":$live,"realtime":$realtime,
        "pickupType":"$pickup","realtimeState":"$state","headsign":"Destination",
        "stop":{"gtfsId":"stop:1","name":"Station","lat":47,"lon":19,"platformCode":"2"},
        "trip":{"gtfsId":"$trip","tripShortName":"S10","route":{"shortName":"S10","mode":"RAIL"}}} """)
    @Test fun departuresExcludeArrivalsAndPastServicesRetainDelaysCancellationsAndNextServiceDay() {
        val rows = org.json.JSONArray(listOf(row("arrival", 100, pickup="NONE"), row("past", 5),
            row("delayed", 10, 100), row("delayed", 10, 100), row("cancelled", 200, state="CANCELED"),
            row("tomorrow", 90000, realtime=false)))
        val result = parseStopDepartures(JSONObject().put("stop", JSONObject().put("stoptimesWithoutPatterns", rows)), 1700000050000)
        assertEquals(listOf("delayed", "cancelled", "tomorrow"), result.map { it.tripId })
        assertTrue(result[0].time.changed)
        assertEquals(1700000010000, result[0].time.scheduled)
        assertEquals(1700000100000, result[0].time.estimate)
        assertTrue(result[1].cancelled)
        assertFalse(result[2].time.realtime)
        assertEquals("2", result[0].platform)
    }
    @Test fun missingStationFailsRatherThanBecomingAnEmptyBoard() {
        assertThrows(IllegalStateException::class.java) { parseStopDepartures(JSONObject("""{"stop":null}"""), 1000) }
    }
    @Test fun queryEscapesProviderIdentityAndUsesEpochSeconds() {
        val query = MavQueries.stopDepartures("stop:\"x", 1700000000123)
        assertTrue(query.contains("stop:\\\"x"))
        assertTrue(query.contains("startTime:1700000000"))
        assertTrue(query.contains("pickupType"))
        assertThrows(IllegalArgumentException::class.java) { MavQueries.nearbyStops(Double.NaN, 19.0, 1000) }
        assertThrows(IllegalArgumentException::class.java) { MavQueries.nearbyStops(47.0, 19.0, 1000000) }
    }
}
