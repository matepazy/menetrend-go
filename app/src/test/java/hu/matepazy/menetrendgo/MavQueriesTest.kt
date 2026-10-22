package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class MavQueriesTest {
    @Test fun literalsEscapeProviderIdentifiers() {
        assertEquals("\"a\\\"b\\\\c\\n\\t\\u0001\"",MavQueries.quote("a\"b\\c\n\t\u0001"))
        val query=MavQueries.trip("a\"} injected", "2026-10-22")
        assertTrue(query.contains("id:\"a\\\"} injected\""))
    }
    @Test fun planKeepsHungarianWallTimeAndArrivalDirection() {
        val query=MavQueries.plan(47.37,18.2,47.19,18.41,LocalDateTime.of(2026,10,23,0,15),true)
        assertTrue(query.contains("date:\"2026-10-23\",time:\"00:15\",arriveBy:true"))
        assertTrue(query.contains("numItineraries:12"))
    }
    @Test fun invalidCoordinatesNeverBecomeQueries() {
        listOf(Double.NaN,Double.POSITIVE_INFINITY,91.0).forEach {lat->
            try {MavQueries.plan(lat,18.0,47.0,19.0,LocalDateTime.now(),false);fail("Accepted invalid latitude")}
            catch(expected: IllegalArgumentException) { }
        }
    }
    @Test fun tripRejectsMalformedServiceDay() {
        try {MavQueries.trip("trip", "tomorrow");fail("Accepted invalid date")}
        catch(expected: java.time.format.DateTimeParseException) { }
    }
}
