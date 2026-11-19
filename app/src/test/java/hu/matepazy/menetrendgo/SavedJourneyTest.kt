package hu.matepazy.menetrendgo

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class SavedJourneyTest {
    private fun millis(day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(Budapest).toInstant().toEpochMilli()
    private val a = Station("1", "Budapest-Keleti", 47.5, 19.08)
    private val b = Station("2", "Szeged", 46.25, 20.14)
    private fun leg(start: Long, end: Long, delay: Int = 0) = Leg("RAIL", start, end, a, b, "IC 712", "trip:712", delay != 0,
        delay, emptyList(), "", "0066CC", "InterCity", "Szeged", arrivalDelay = delay, agencyName = "MÁV")
    private fun journey(day: Int, delay: Int = 0) =
        Journey(8_000, listOf(leg(millis(day, 8, 0) + delay * 1000L, millis(day, 10, 22) + delay * 1000L, delay)))

    @Test fun roundTripDropsLiveDelaysButKeepsIdentity() {
        val live = journey(21, delay = 300)
        val restored = SavedJourney.parse(JSONObject(SavedJourney.of(live).copy(name = "Nagyi", note = "3. kocsi").json().toString()))
        assertEquals(live.key, restored.key)
        assertEquals("Nagyi", restored.name)
        assertEquals("3. kocsi", restored.note)
        val leg = restored.journey.legs.single()
        assertFalse(leg.realTime)
        assertEquals(millis(21, 8, 0), leg.start)
        assertEquals("IC 712", leg.route)
        assertEquals("trip:712", leg.tripId)
        assertEquals(0L, restored.journey.fetchedAt)
    }

    @Test fun providerItineraryIsStoredVerbatim() {
        val itinerary = JSONObject(itineraryJson(journey(21)).toString()).put("extra", "kept")
        val saved = SavedJourney.of(journey(21).copy(itineraryJson = itinerary.toString()))
        assertEquals("kept", saved.json().getJSONObject("itinerary").getString("extra"))
    }

    @Test fun titleFallsBackToEndpoints() {
        assertEquals("Budapest-Keleti → Szeged", SavedJourney.of(journey(21)).title)
    }

    @Test fun upcomingFirstThenPastMostRecentFirst() {
        val now = millis(20, 12, 0)
        val items = listOf(journey(18), journey(25), journey(19), journey(21)).map { SavedJourney.of(it) }
        val days = sortSavedJourneys(items, now).map { java.time.Instant.ofEpochMilli(it.departure).atZone(Budapest).dayOfMonth }
        assertEquals(listOf(21, 25, 19, 18), days)
    }

    @Test fun dayLabels() {
        val today = LocalDate.of(2026, 11, 20)
        assertEquals("Ma", savedJourneyDay(millis(20, 8, 0), today))
        assertEquals("Holnap", savedJourneyDay(millis(21, 8, 0), today))
        assertEquals("Tegnap", savedJourneyDay(millis(19, 8, 0), today))
        assertTrue(savedJourneyDay(millis(25, 8, 0), today).startsWith("nov. 25."))
    }
}
