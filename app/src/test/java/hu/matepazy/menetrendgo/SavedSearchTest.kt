package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test

class SavedSearchTest {
    private val from = Station("a", "Budapest-Keleti", 47.5, 19.08, "Stop", listOf("RAIL"))
    private val to = Station("b", "Győr", 47.68, 17.63)

    @Test fun roundTripPreservesStationsNameAndIconWithoutTime() {
        val saved = SavedSearch(from, to, "Munkába", SavedSearchIcon.Work)
        assertEquals(saved, SavedSearch.parse(saved.json()))
        assertEquals(setOf("from", "to", "name", "icon"), saved.json().keys().asSequence().toSet())
    }

    @Test fun unknownIconFallsBackWithoutLosingRoute() {
        val json = SavedSearch(from, to, "Haza", SavedSearchIcon.Home).json().put("icon", "future-icon")
        assertEquals(SavedSearchIcon.Bookmark, SavedSearch.parse(json).icon)
    }

    @Test fun rejectsTemporaryPositionMissingEndpointsAndSameStation() {
        assertFalse(canSaveRoute(from.copy(id = ""), to))
        assertFalse(canSaveRoute(from, to.copy(id = "")))
        assertFalse(canSaveRoute(from, from.copy(name = "Other name")))
        assertFalse(canSaveRoute(null, to))
        assertTrue(canSaveRoute(from, to))
    }

    @Test fun invalidSavedRecordsAreRejected() {
        assertTrue(runCatching { SavedSearch.parse(SavedSearch(from, to, "  ", SavedSearchIcon.Home).json()) }.isFailure)
        assertTrue(runCatching { SavedSearch.parse(SavedSearch(from.copy(id=""), to, "Haza", SavedSearchIcon.Home).json()) }.isFailure)
    }
}
