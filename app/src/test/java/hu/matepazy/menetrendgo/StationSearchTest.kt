package hu.matepazy.menetrendgo

import org.junit.Assert.assertEquals
import org.junit.Test

class StationSearchTest {
    private fun stop(id: String, name: String, type: String = "", modes: List<String> = emptyList()) =
        Station(id, name, 47.37, 18.21, type, modes)

    @Test fun townAndTerminalsPrecedeHistoryAndKeepProviderOrderForTies() {
        val rail = stop("rail", "Mór", "Station", listOf("RAIL"))
        val city = stop("city", "MÓR*", "MetaStation")
        val bus = stop("bus", "Mór, autóbusz-állomás")
        val other = stop("other", "Budapest, Jókai Mór utca")
        val local = stop("local", "Mór, VÉRTES Áruház")
        val stops = listOf(rail, city, other, bus, local)
        assertEquals(listOf(city, rail, bus, other, local), rankStations("Mór", stops, emptyMap()))
        assertEquals(listOf(city, rail, bus, local, other), rankStations("mor", stops, mapOf("local" to 20, "other" to 2)))
        assertEquals(listOf(city, rail, bus, other, local), rankStations("MÓR", stops, mapOf("other" to 200)))
    }

    @Test fun terminalRulesOnlyApplyToTheExactTown() {
        val otherTown = stop("other", "Mórahalom, autóbusz-állomás")
        val local = stop("local", "Mór, kórház bejárati út")
        val rail = stop("rail", "Mór, vasútállomás")
        assertEquals(listOf(rail, local, otherTown), rankStations("Mór", listOf(otherTown, local, rail), mapOf("local" to 1)))
    }

    @Test fun specificQueriesKeepProviderOrderUnlessThereIsUsage() {
        val first = stop("a", "Budapest, Jókai Mór utca")
        val second = stop("b", "Mór, VÉRTES Áruház")
        assertEquals(listOf(first, second), rankStations("Mór utca", listOf(first, second, first), emptyMap()))
        assertEquals(listOf(second, first), rankStations("Mór utca", listOf(first, second), mapOf("b" to 3)))
    }

    @Test fun cacheKeysIgnoreAccentsCaseAndRepeatedSpaces() {
        assertEquals("mori ut", stationSearchKey("  MÓRI   út  "))
    }
}
