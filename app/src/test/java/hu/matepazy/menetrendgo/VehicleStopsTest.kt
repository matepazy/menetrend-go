package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test

class VehicleStopsTest {
    private fun stop(name: String, scheduled: Long, arrival: Long=scheduled, departure: Long=arrival, live: Boolean=true) =
        VehicleStop(name,scheduled,arrival,departure,live,"")

    @Test fun delayKeepsAnUpcomingStopEvenAfterItsScheduledTime() {
        val stops=listOf(stop("Elhagyva",60_000),stop("Késik",120_000,300_000),stop("Végállomás",600_000))
        assertEquals(listOf("Késik","Végállomás"),upcomingVehicleStops(stops,240_000).map {it.name})
    }
    @Test fun dwellAndTerminalAreHandled() {
        val stops=listOf(stop("Megálló",120_000,120_000,180_000),stop("Végállomás",300_000))
        assertEquals("Megálló",upcomingVehicleStops(stops,150_000).first().name)
        assertTrue(upcomingVehicleStops(stops,360_000).isEmpty())
    }
    @Test fun strikeThroughRequiresLiveDataAndAVisibleMinuteDifference() {
        assertTrue(stop("Késő",60_000,120_000).changed)
        assertTrue(stop("Korai",120_000,60_000).changed)
        assertFalse(stop("Menetrendi",60_000,120_000,live=false).changed)
        assertFalse(stop("Másodpercek",60_000,65_000).changed)
    }
}
