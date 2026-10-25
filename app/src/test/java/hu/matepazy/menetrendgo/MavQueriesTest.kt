package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class MavQueriesTest {
    private val searchTime = LocalDateTime.of(2026,10,26,11,0)
    private val budapest = Station("OSM:Budapest","BUDAPEST*",47.4978789,19.0402383,
        "MetaStation",listOf("RAIL","COACH"))
    private val fehervar = Station("OSM:Székesfehérvár","SZÉKESFEHÉRVÁR*",47.1910169,18.410811,
        "MetaStation",listOf("RAIL","COACH"))

    @Test fun cityWideEndpointsUseProviderGroupsInsteadOfCityCenterCoordinates() {
        listOf(fehervar to budapest, budapest to fehervar).forEach { (from,to) ->
            listOf(false,true).forEach { arrive ->
                val query = MavQueries.plan(from,to,searchTime,arrive)
                assertTrue(query.contains("fromPlace:${MavQueries.quote("${from.name}::${from.id}")}"))
                assertTrue(query.contains("toPlace:${MavQueries.quote("${to.name}::${to.id}")}"))
                assertFalse(query.contains("from:{lat:"))
                assertFalse(query.contains("to:{lat:"))
                assertTrue(query.contains("arriveBy:$arrive"))
            }
        }
    }

    @Test fun savedCitySelectionsKeepTheirRoutingIdentity() {
        val restored = Station.parse(budapest.json())
        assertEquals(MavQueries.plan(budapest,fehervar,searchTime,false),
            MavQueries.plan(restored,fehervar,searchTime,false))
    }

    @Test fun specificStationsKeepTheirIdentityAndUnnamedPointsUseCoordinates() {
        val deli = Station("1:005510025_0","Budapest-Déli",47.500278,19.024444)
        val point = Station("","My location",47.2,18.4)
        val query = MavQueries.plan(deli,point,searchTime,false)
        assertTrue(query.contains("fromPlace:\"Budapest-Déli::1:005510025_0\""))
        assertTrue(query.contains("to:{lat:47.2,lon:18.4}"))
    }

    @Test fun endpointNamesAndIdentifiersAreEscaped() {
        val station = Station("id\"\\\n","name\"\\\n",47.0,19.0)
        val query = MavQueries.plan(station,budapest,searchTime,false)
        assertTrue(query.contains("fromPlace:${MavQueries.quote("${station.name}::${station.id}")}"))
    }

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
