package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class SearchVehiclesTest {
    private val a = Station("a", "Budapest", 47.5, 19.0)
    private val b = Station("b", "Szeged", 46.2, 20.1)
    private fun leg(mode: String) = Leg(mode, 0, 60_000, a, b, "", "trip", false, 0, emptyList(), "")
    private fun journey(vararg modes: String) = Journey(60, modes.map(::leg))

    @Test fun switchesStartWithAllTypesAndKeepAtLeastOneEnabled() {
        val withoutTrain = toggleSearchVehicle(emptySet(), SearchVehicle.Train)
        assertEquals(SearchVehicle.entries.toSet() - SearchVehicle.Train, withoutTrain)
        assertEquals(emptySet<SearchVehicle>(), toggleSearchVehicle(withoutTrain, SearchVehicle.Train))
        assertEquals(setOf(SearchVehicle.Train), toggleSearchVehicle(setOf(SearchVehicle.Train), SearchVehicle.Train))
        assertEquals(setOf(SearchVehicle.Train, SearchVehicle.Hev), toggleSearchVehicle(setOf(SearchVehicle.Train), SearchVehicle.Hev))
    }

    @Test fun queryRestrictsModesButRetainsWalkingAndServiceConditions() {
        val time = LocalDateTime.of(2026, 10, 31, 12, 0)
        val query = MavQueries.plan(a, b, time, true, setOf(SearchFilter.AirConditioning),
            setOf(SearchVehicle.Train, SearchVehicle.Hev))
        assertTrue(query.contains("transportModes:[{mode:RAIL},{mode:SUBURBAN_RAILWAY},{mode:WALK}]"))
        assertTrue(query.contains("arriveBy:true"))
        assertTrue(query.contains(SearchFilter.AirConditioning.providerSearchCode!!))
        assertTrue(MavQueries.plan(a, b, time, false).contains(
            "transportModes:[{mode:RAIL},{mode:COACH},{mode:BUS},{mode:SUBURBAN_RAILWAY},{mode:TRAMTRAIN},{mode:WALK}]"))
        assertTrue(MavQueries.plan(a, b, time, false, vehicles = setOf(SearchVehicle.Coach, SearchVehicle.LocalBus))
            .contains("transportModes:[{mode:COACH},{mode:BUS},{mode:WALK}]"))
    }

    @Test fun everyTransitLegMustUseAnAllowedType() {
        val rail = journey("WALK", "RAIL", "WALK")
        val mixed = journey("RAIL", "COACH")
        val hev = journey("RAIL", "SUBURBAN_RAILWAY")
        val journeys = listOf(rail, mixed, hev, journey("WALK"), journey("RAIL_REPLACEMENT_BUS"))
        assertEquals(listOf(rail), filterJourneyVehicles(journeys, setOf(SearchVehicle.Train)))
        assertEquals(listOf(rail, hev), filterJourneyVehicles(journeys, setOf(SearchVehicle.Train, SearchVehicle.Hev)))
        assertEquals(journeys, filterJourneyVehicles(journeys, emptySet()))
    }
}
