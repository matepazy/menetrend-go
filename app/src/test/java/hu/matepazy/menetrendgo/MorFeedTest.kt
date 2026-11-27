package hu.matepazy.menetrendgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** The MenetrendGO build of the Mór local bus feed shares MÁVPlusz stop identity, so MÁVPlusz stops find its buses. */
class MorFeedTest {
    private val day = LocalDate.of(2026, 12, 1)
    private val network = OfflineNetwork(listOf(GtfsCompactor.compact(
        File(javaClass.classLoader!!.getResource("mor-gtfs-menetrendgo.zip")!!.toURI()), "mor", "Mór", day)))
    private val now = OfflineNetwork.epoch(day, 7 * 60)

    @Test fun mavplusStopsFindTheLocalBuses() {
        // As MÁVPlusz returns them: the bus station by its platform, a street pole, the railway station, a shared name.
        val busStation = Station("mav:hkir:hkir_665577_1", "Mór, autóbusz-állomás", 47.369255, 18.215642, "Station")
        val vertes = Station("mav:hkir:hkir_665598_1", "Mór, VÉRTES Áruház", 47.372415, 18.209637)
        val rail = Station("mav:1:005503731_0", "Mór", 47.369167, 18.189167, "Station")
        val estate = Station("mav:hkir:hkir_665596_2", "Mór, Velegi út lakótelep", 47.362511, 18.183757)
        for (station in listOf(busStation, vertes, rail, estate))
            assertTrue(station.name, network.departures(station, now).isNotEmpty())
        val journeys = network.plan(busStation, estate, day.atTime(7, 0), false)
        assertTrue(journeys.isNotEmpty() && journeys.first().legs.any { !it.walking })
    }

    @Test fun searchFindsLocalStopsByTownAndFoldsSharedOnesIntoMavplus() {
        val names = network.searchStops("Mór", limit = 100).map { it.name }
        assertTrue("Mór, Rákóczi Ferenc utca" in names && "Mór, autóbusz-állomás" in names)
        val live = listOf(Station("mav:hkir:hkir_665599_1", "Mór, Zeneiskola", 47.376645, 18.20444))
        val merged = OfflineSupplement.stations(live, network.searchStops("Mór Zeneiskola"))
        assertEquals(listOf("mav:hkir:hkir_665599_1"), merged.map { it.id })
    }
}
