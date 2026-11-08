package hu.matepazy.menetrendgo

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CurrentLocationSearchTest {
    private val stop = Station("stop", "Stop", 47.2, 19.1)
    private val old = Station("", "Jelenlegi helyzet", 47.5, 19.0, currentLocationCalculatedAt = 1_000L)
    private val fresh = old.copy(lat = 47.6, lon = 19.2, currentLocationCalculatedAt = 61_001L)

    @Test fun locationIsReusedThroughExactlyOneMinute() = runBlocking {
        for (now in listOf(1_000L, 60_999L, 61_000L)) {
            assertEquals(old to stop, refreshSearchLocations(old, stop, now) { error("Must reuse the fix") })
        }
    }

    @Test fun olderLocationIsRecalculatedAndNewFixIsReused() = runBlocking {
        var calls = 0
        val result = refreshSearchLocations(old, stop, 61_001L) { calls++; fresh }
        assertEquals(fresh to stop, result)
        assertEquals(result, refreshSearchLocations(result.first, result.second, 62_000L) { calls++; fresh })
        assertEquals(1, calls)
    }

    @Test fun swappedCurrentLocationIsAlsoRecalculated() = runBlocking {
        assertEquals(stop to fresh, refreshSearchLocations(stop, old, 61_001L) { fresh })
    }

    @Test fun fixedCoordinatesAndNamedStopsNeverRequestLocation() = runBlocking {
        val point = Station("", "Map point", 47.0, 19.0)
        assertEquals(point to stop, refreshSearchLocations(point, stop, 100_000L) { error("Must keep fixed coordinates") })
    }

    @Test fun failedRefreshDoesNotFallBackToOldCoordinates() = runBlocking {
        val failure = IllegalStateException("Location unavailable")
        try {
            refreshSearchLocations(old, stop, 61_001L) { throw failure }
            fail("Search must stop if the location cannot be refreshed")
        } catch (e: IllegalStateException) {
            assertSame(failure, e)
        }
    }
}
