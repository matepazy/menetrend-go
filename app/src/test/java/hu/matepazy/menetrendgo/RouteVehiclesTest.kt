package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class RouteVehiclesTest {
    private fun at(value: String)=LocalDateTime.parse(value).atZone(Budapest).toInstant().toEpochMilli()
    private val point=Station("","",47.0,19.0)
    private fun leg(id: String, mode: String="RAIL", day: String="2026-10-23") =
        Leg(mode,at("${day}T12:00"),at("${day}T13:00"),point,point,"S1",id,true,0,emptyList(),"")
    private fun vehicle(id: String)=Vehicle(id,"S1","","RAIL",47.0,19.0)
    @Test fun onlySelectedTransitTripIdsAreShown() {
        val journey=Journey(7200,listOf(leg("a"),leg("walk","WALK"),leg("b")))
        assertEquals(listOf("a","b"),routeVehicles(journey,listOf(vehicle("a"),vehicle("other"),vehicle("walk"),vehicle("b")),at("2026-10-23T12:30")).map {it.id})
    }
    @Test fun absentAndFutureVehiclesAreNotInvented() {
        val journey=Journey(3600,listOf(leg("a",day="2026-10-24")))
        assertTrue(routeVehicles(journey,listOf(vehicle("a")),at("2026-10-23T12:30")).isEmpty())
        assertTrue(routeVehicles(journey,emptyList(),at("2026-10-24T12:30")).isEmpty())
    }
    @Test fun overnightServiceRetainsItsPositionAfterMidnight() {
        val journey=Journey(3600,listOf(leg("a").copy(start=at("2026-10-23T23:30"),end=at("2026-10-24T00:30"))))
        assertEquals(1,routeVehicles(journey,listOf(vehicle("a")),at("2026-10-24T00:10")).size)
    }
}
