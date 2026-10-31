package hu.matepazy.menetrendgo

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRefreshTest {
    private val now = 1700000000000L
    private fun departure(at: Long, cancelled: Boolean = false): StopDeparture {
        val station = Station("stop", "Station", 47.0, 19.0)
        return StopDeparture("trip", "Destination", ServiceTime(at, at, true), "", cancelled,
            Leg("RAIL", at, at, station, station, "S10", "trip", true, 0, emptyList(), ""))
    }
    @Test fun refreshesAfterFiveMinutesEvenWithNoUpcomingServices() {
        assertEquals(now + 300000, widgetRefreshAt(now, now, false, emptyList(), now))
        assertEquals(now, widgetRefreshAt(now - 300001, now - 300001, false, emptyList(), now))
    }
    @Test fun earliestDepartureRollsOffWithoutTriggeringANetworkRefresh() {
        val departures = listOf(departure(now + 240000), departure(now + 60000))
        assertEquals(now + 300000, widgetRefreshAt(now, now, false, departures, now))
        assertEquals(now + 300000, widgetRefreshAt(now, now, false, departures, now + 61000))
        assertEquals(1, upcomingWidgetDepartures(departures, now + 61000).size)
    }
    @Test fun exhaustedReserveRequiresAnImmediateFetch() {
        assertEquals(now, widgetRefreshAt(now - 120000, now - 120000, false, listOf(departure(now - 30000)), now))
    }
    @Test fun cancelledServiceDoesNotTriggerADepartureRefresh() {
        assertEquals(now + 300000, widgetRefreshAt(now, now, false, listOf(departure(now + 1000, true), departure(now + 600000)), now))
    }
    @Test fun failuresHaveABoundedRetryInsteadOfAnImmediateFetchLoop() {
        assertEquals(now + 60000, widgetRefreshAt(now - 400000, now, true, listOf(departure(now - 30000)), now))
        assertEquals(now + 60000, widgetRefreshAt(0, now, true, emptyList(), now))
    }
    @Test fun newWidgetFetchesImmediately() {
        assertEquals(now, widgetRefreshAt(0, 0, false, emptyList(), now))
    }
    @Test fun localCountdownTicksDoNotRequestNewDataBeforeARefreshTrigger() {
        val departures = listOf(departure(now + 600000))
        assertEquals("10 perc", departureCountdown(departures.first().time.estimate, now))
        assertEquals("9 perc", departureCountdown(departures.first().time.estimate, now + 60000))
        assertEquals(now + 300000, widgetRefreshAt(now, now, false, departures, now))
        assertEquals(now + 300000, widgetRefreshAt(now, now, false, departures, now + 60000))
    }
    @Test fun cachedReserveRevealsServicesBeyondTheOldFiveRowLimit() {
        val reserve = (1..10).map { departure(now + it * 30000).copy(tripId = "trip-$it") }
        val upcoming = upcomingWidgetDepartures(reserve, now + 31000)
        assertEquals(9, upcoming.size)
        assertEquals("trip-2", upcoming.first().tripId)
        assertEquals("trip-10", upcoming.last().tripId)
        assertEquals(now + 300000, widgetRefreshAt(now, now, false, reserve, now + 31000))
    }
    @Test fun clockRollbackDoesNotPostponeRefreshBeyondTheNormalInterval() {
        assertEquals(now, widgetRefreshAt(now + 3600000, now + 3600000, false, emptyList(), now))
        assertEquals(now + 60000, widgetRefreshAt(now + 3600000, now + 3600000, true, emptyList(), now))
    }
}
