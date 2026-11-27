package hu.matepazy.menetrendgo

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineSupplementTest {
    private val base = 1_800_000_000_000L
    private fun min(m: Int) = base + m * 60_000L
    private fun station(id: String, name: String, lat: Double = 47.4, lon: Double = 18.2) = Station(id, name, lat, lon)
    private fun leg(route: String, from: Int, to: Int, mode: String = "BUS", trip: String = route, headsign: String = "Vég") =
        Leg(mode, min(from), min(to), station("a", "A"), station("b", "B"), route, trip, false, 0, emptyList(), "", headsign = headsign)
    private fun departure(route: String, at: Int, offline: Boolean, trip: String = "$route@$at", headsign: String = "Vég") =
        StopDeparture(trip, headsign, ServiceTime(min(at), min(at), false), "", false, leg(route, at, at, trip = trip, headsign = headsign), offline = offline)
    private fun journey(offline: Boolean, vararg legs: Leg) =
        Journey(((legs.last().end - legs.first().start) / 1000).toInt(), legs.toList(), offline = offline)

    @Test fun offlineStopsJoinOnlyWhereNoLiveStopOfTheSameNameIsNearby() {
        val live = listOf(station("mav:1", "Mór, autóbusz-állomás"))
        val offline = listOf(station("gtfs:9", "Mor, autobusz allomas", 47.401), station("gtfs:10", "Mór, Kodály utca"),
            station("gtfs:11", "Mór, autóbusz-állomás", 47.5))
        assertEquals(listOf("mav:1", "gtfs:10", "gtfs:11"), OfflineSupplement.stations(live, offline).map { it.id })
    }

    @Test fun offlineDeparturesFillInWithoutRepeatingLiveOnesOrRunningPastAFullBoard() {
        val live = listOf(departure("7215", 10, false), departure("7220", 40, false))
        val offline = listOf(departure("7215", 10, true, trip = "x"), departure("1", 20, true),
            departure("2", 40, true, headsign = "vég"), departure("3", 90, true))
        assertEquals(listOf("7215", "1", "7220", "3"), OfflineSupplement.departures(live, offline).map { it.leg.route })
        assertEquals(listOf("7215", "1", "7220"), OfflineSupplement.departures(live, offline, limit = 2).map { it.leg.route })
        assertEquals(listOf("1", "3"), OfflineSupplement.departures(emptyList(), offline.drop(1).filter { it.leg.route != "2" }).map { it.leg.route })
    }

    @Test fun offlineJourneysAddOnlyWhatLiveOnesDoNotCover() {
        val live = listOf(journey(false, leg("7215", 0, 30)), journey(false, leg("7215", 60, 90)))
        val duplicate = journey(true, leg("7215", 0, 30, trip = "other"))
        val beaten = journey(true, leg("1", 0, 35))
        val better = journey(true, leg("1", 10, 25))
        val late = journey(true, leg("1", 120, 130))
        val train = journey(true, leg("S10", 20, 40, mode = "RAIL"))
        val merged = OfflineSupplement.journeys(live, listOf(duplicate, beaten, better, late, train))
        assertEquals(listOf(min(0), min(10), min(20), min(60), min(120)), merged.map { it.start })
        assertEquals(listOf(min(0), min(10), min(60)),
            OfflineSupplement.journeys(live, listOf(better, train), setOf(SearchVehicle.LocalBus)).map { it.start })
        assertEquals(listOf(min(10), min(120)), OfflineSupplement.journeys(emptyList(), listOf(better, late)).map { it.start })
    }

    @Test fun anOfflineJourneyOnARunALiveJourneyUsesIsDroppedEvenBetweenOtherStops() {
        // The screenshot case: live 1845 from VÉRTES Áruház; offline the same bus from the hospital to another stop.
        val live = listOf(journey(false, leg("1845", 0, 32, trip = "hkir:777")))
        val sameTrip = journey(true, leg("1845", 3, 29, trip = "777"))
        val sameLineOverlapping = journey(true, leg("1845", 3, 29, trip = "other"))
        val withLocalBus = journey(true, leg("2", -20, -5), leg("1845", 3, 29, trip = "777"))
        assertEquals(listOf(min(-20), min(0)),
            OfflineSupplement.journeys(live, listOf(sameTrip, sameLineOverlapping, withLocalBus)).map { it.start })
    }

    @Test fun journeysOnRunsTheProviderHasNeverAddToALiveAnswer() {
        val volan = journey(true, leg("8365", 35, 55, trip = "hkir:8365"), leg("8093", 58, 75, trip = "hkir:8093"))
        val withLocal = journey(true, leg("2", 30, 40, trip = "mor:2"), leg("8093", 58, 75, trip = "hkir:8093"))
        assertEquals(listOf(min(30)), OfflineSupplement.journeys(emptyList(), listOf(volan, withLocal),
            providerTrips = setOf("hkir:8365", "hkir:8093")).map { it.start })
    }

    @Test fun pagingFollowsProviderJourneysWhenThereAreAny() {
        val live = listOf(journey(false, leg("7215", 0, 30)), journey(false, leg("7215", 60, 90)))
        val offline = listOf(journey(true, leg("1", 120, 130)), journey(true, leg("1", -30, -20)))
        assertEquals(min(61), OfflineSupplement.pageEdge(live + offline, earlier = false))
        assertEquals(min(29), OfflineSupplement.pageEdge(live + offline, earlier = true))
        assertEquals(min(121), OfflineSupplement.pageEdge(offline, earlier = false))
    }
}
