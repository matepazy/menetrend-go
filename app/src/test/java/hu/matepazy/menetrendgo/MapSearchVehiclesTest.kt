package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test

class MapSearchVehiclesTest {
    private val selected = Vehicle("trip:1", "IC 70", "Szeged", "RAIL", 47.4, 19.1)
    private val other = selected.copy(id = "trip:2")

    @Test fun selectionIsolatesExactVehicleEvenWhenNamesMatch() {
        assertEquals(listOf(selected), mapSearchVehicles(listOf(selected, other), selected.id))
    }

    @Test fun refreshUsesLatestPositionAndMissingSelectionKeepsOthersHidden() {
        val updated = selected.copy(lat = 47.5)
        assertEquals(listOf(updated), mapSearchVehicles(listOf(other, updated), selected.id))
        assertTrue(mapSearchVehicles(listOf(other), selected.id).isEmpty())
        assertEquals(listOf(updated), mapSearchVehicles(listOf(updated, other), selected.id))
    }

    @Test fun clearingSelectionRestoresCurrentLayerVehicles() {
        val vehicles = listOf(selected, other)
        assertEquals(vehicles, mapSearchVehicles(vehicles, null))
    }
}
