package hu.matepazy.menetrendgo

import org.junit.Assert.assertEquals
import org.junit.Test

class DeparturePresentationTest {
    private val now = 1_800_000_030_000L

    @Test fun delayedServiceRemainsUpcomingAfterScheduledDeparture() {
        assertEquals(DeparturePresentation.Upcoming,
            departurePresentation(ServiceTime(now - 600_000, now + 120_000, true), now))
    }

    @Test fun earlyDepartureWarnsEvenWithinCurrentMinute() {
        assertEquals(DeparturePresentation.LeftEarly,
            departurePresentation(ServiceTime(now + 120_000, now - 1, true), now))
        assertEquals(DeparturePresentation.LeftEarly,
            departurePresentation(ServiceTime(now, now - 120_000, true), now))
    }

    @Test fun earlyServiceStillApproachingHasNoWarning() {
        assertEquals(DeparturePresentation.Upcoming,
            departurePresentation(ServiceTime(now + 600_000, now + 120_000, true), now))
        assertEquals(DeparturePresentation.DepartingNow,
            departurePresentation(ServiceTime(now + 600_000, now, true), now))
    }

    @Test fun afterBothTimesPassServiceUsesOrdinaryPastStyle() {
        assertEquals(DeparturePresentation.Past,
            departurePresentation(ServiceTime(now - 1, now - 120_000, true), now))
        assertEquals(DeparturePresentation.Past,
            departurePresentation(ServiceTime(now - 1, now - 2, true), now))
    }

    @Test fun missingLiveDataUsesScheduleAndNeverClaimsEarlyDeparture() {
        assertEquals(DeparturePresentation.Upcoming,
            departurePresentation(ServiceTime(now + 120_000, now - 120_000, false), now))
        assertEquals(DeparturePresentation.Past,
            departurePresentation(ServiceTime(now - 120_000, now + 120_000, false), now))
    }
}
