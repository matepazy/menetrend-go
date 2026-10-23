package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ServiceTimeTest {
    private fun at(time: String)=LocalDateTime.parse(time).atZone(Budapest).toInstant().toEpochMilli()
    @Test fun departureAndArrivalUseTheirOwnDelays() {
        val station=Station("","",47.0,19.0)
        val leg=Leg("RAIL",600_000,1_800_000,station,station,"S","trip",true,120,emptyList(),"",arrivalDelay=300)
        assertEquals(480_000L,leg.departureTime.scheduled)
        assertEquals(1_500_000L,leg.arrivalTime.scheduled)
        assertTrue(leg.departureTime.changed)
        assertTrue(leg.arrivalTime.changed)
        assertFalse(leg.copy(realTime=false).departureTime.changed)
        assertEquals(600_000L,leg.copy(realTime=false).departureTime.scheduled)
        assertFalse(leg.copy(mode="WALK").arrivalTime.changed)
    }
    @Test fun earlyArrivalAndMidnightArePreserved() {
        val early=ServiceTime.fromEstimate(600_000,-120,true)
        assertEquals(720_000L,early.scheduled)
        assertTrue(early.changed)
        val midnight=ServiceTime.fromEstimate(at("2026-10-24T00:03"),300,true)
        assertEquals(at("2026-10-23T23:58"),midnight.scheduled)
        assertTrue(midnight.crossesDay)
    }
    @Test fun unchangedTimesAndSubMinuteChangesAreSingleTimes() {
        assertFalse(ServiceTime(60_000,60_000,true).changed)
        assertFalse(ServiceTime(60_000,65_000,true).changed)
        assertFalse(ServiceTime(60_000,120_000,false).changed)
    }
}
