package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class JourneyPresentationTest {
    private val station = Station("station", "Station", 47.0, 19.0)
    private fun leg(mode: String = "RAIL", category: String = "", route: String = "915",
                    start: Long = 0, end: Long = 60_000, longName: String = "") =
        Leg(mode, start, end, station, station, route, "trip", false, 0, emptyList(), "",
            routeColor = "FFFFFF", category = category, routeLongName = longName)

    @Test fun endpointTimesExcludeAccessAndEgressWalksButKeepTransfers() {
        val first = leg(start = 120_000, end = 600_000)
        val last = leg(start = 900_000, end = 1_800_000)
        val journey = Journey(2100, listOf(leg("WALK"), first,
            leg("WALK", start = 600_000, end = 750_000), last,
            leg("WALK", start = 1_800_000, end = 2_100_000)))
        assertSame(first, journey.boardingLeg)
        assertSame(last, journey.alightingLeg)
        assertEquals(28, journey.serviceMinutes)
        assertEquals(35, journey.minutes)
        assertEquals(0L, journey.start)
        assertEquals(2_100_000L, journey.end)
        val walking = Journey(60, listOf(leg("WALK")))
        assertEquals(walking.start, walking.boardingLeg.start)
        assertEquals(walking.end, walking.alightingLeg.end)
    }

    @Test fun tomorrowUsesBudapestCalendarRatherThanElapsedHours() {
        val today = LocalDate.of(2026, 10, 24)
        fun at(value: String) = LocalDateTime.parse(value).atZone(Budapest).toInstant().toEpochMilli()
        assertEquals("", dayLabel(at("2026-10-24T23:59"), today))
        assertEquals("Holnap", dayLabel(at("2026-10-25T00:01"), today))
        assertEquals("2026. 10. 26.", dayLabel(at("2026-10-26T00:01"), today))
    }

    @Test fun railCategoryOverridesGraphQlColorAndRecognizesNamesAndLineCodes() {
        assertEquals("29803C", trainCategoryColor(leg(category = "InterRégió")))
        assertEquals("29803C", trainCategoryColor(leg(category = "sebesvonat")))
        assertEquals("C82227", trainCategoryColor(leg(category = "Ex")))
        assertEquals("274F96", trainCategoryColor(leg(route = "855 TÓPART InterCity")))
        assertEquals("274F96", trainCategoryColor(leg(category = "EuroCity")))
        assertEquals("FECC00", trainCategoryColor(leg(route = "Z30")))
        assertEquals("00A0E3", trainCategoryColor(leg(longName = "S30")))
        assertEquals("000000", trainCategoryColor(leg(category = "unknown")))
        assertNull(trainCategoryColor(leg(mode = "COACH", category = "IR")))
    }
}
