package hu.matepazy.menetrendgo

import org.junit.Assert.assertEquals
import org.junit.Test

class MapSearchStopsTest {
    @Test fun excludesTownAggregatesAndInvalidCoordinatesWithoutMergingDirectionalStops() {
        val outbound = Station("a", "Győr, Városháza", 47.68, 17.63)
        val inbound = outbound.copy(id = "b", lat = 47.681)
        assertEquals(listOf(outbound, inbound), mapSearchStops(listOf(
            outbound.copy(id = "town", name = "Győr*"), outbound.copy(id = "aggregate", type = "MetaStation"),
            outbound, outbound, inbound,
            outbound.copy(id = "bad", lat = Double.NaN), outbound.copy(id = "", lon = 19.0)
        )))
    }
}
