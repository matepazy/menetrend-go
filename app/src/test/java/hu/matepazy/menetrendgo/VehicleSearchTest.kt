package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test

class VehicleSearchTest {
    private val train = Vehicle("trip:42", "708", "Szeged", "RAIL", 47.0, 19.0,
        category = "InterCity", routeLongName = "Budapest – Szeged", model = "FLIRT",
        uicCode = "91 55 0431 123-4", routeShortName = "IC 70", vehicleId = "engine42")
    private val bus = Vehicle("trip:9", "1090", "Győr", "COACH", 47.6, 17.6,
        registration = "ABC-123", model = "Credo Econell", routeShortName = "1090")
    private val search = VehicleSearch(listOf(train, bus))

    @Test fun matchesServiceAndRouteNumbers() {
        assertEquals(listOf(train), search.filter("708"))
        assertEquals(listOf(train), search.filter("ic70"))
        assertEquals(listOf(bus), search.filter("1090"))
    }
    @Test fun matchesVehicleIdentifiersIgnoringPunctuation() {
        assertEquals(listOf(bus), search.filter("abc123"))
        assertEquals(listOf(train), search.filter("915504311234"))
        assertEquals(listOf(train), search.filter("engine42"))
    }
    @Test fun combinesTermsAcrossFieldsAndIgnoresAccentsAndCase() {
        assertEquals(listOf(bus), search.filter(" GYOR   econell "))
        assertEquals(listOf(train), search.filter("budapest FLIRT 708"))
        assertTrue(search.filter("gyor flirt").isEmpty())
    }
    @Test fun emptyQueryRestoresAllVehiclesAndUnknownQueryMatchesNothing() {
        assertEquals(listOf(train, bus), search.filter("  "))
        assertTrue(search.filter("nincsilyen").isEmpty())
    }
}
