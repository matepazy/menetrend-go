package hu.matepazy.menetrendgo

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceNamesTest {
    @Test fun busBadgePrefersPublicRouteAndRetainsRawTripLabel() {
        val bus = Vehicle("trip", "1845/113", "Kincsesbánya, Petőfi utca", "BUS", 0.0, 0.0)
        assertEquals("1845", bus.displayRoute)
        assertEquals("1845/113", bus.name)
        assertEquals("100E", bus.copy(routeShortName = "100E").displayRoute)
        assertEquals("1845", bus.copy(mode = "COACH").displayRoute)
        assertEquals("1845", bus.copy(mode = "RAIL_REPLACEMENT_BUS").displayRoute)
        assertEquals("918 BAKONY InterCity", bus.copy(mode = "RAIL", name = "918 BAKONY InterCity",
            routeShortName = "IC").displayRoute)
    }

    @Test fun nullProviderCategoryUsesTransportMode() {
        listOf("null", " NULL ", "").forEach { category ->
            assertEquals("Autóbusz", hungarianServiceName(category, "BUS"))
            assertEquals("Autóbusz", hungarianServiceName(category, "COACH"))
            assertEquals("Vonat", hungarianServiceName(category, "RAIL"))
        }
    }

    @Test fun translatesProviderCategories() {
        assertEquals("Személyvonat", hungarianServiceName("Passenger train", "RAIL"))
        assertEquals("Személyvonat", hungarianServiceName("passanger train", "RAIL"))
        assertEquals("Gyorsvonat", hungarianServiceName("Fast train", "RAIL"))
        assertEquals("Sebesvonat", hungarianServiceName("Semi-fast train", "RAIL"))
        assertEquals("Vonatpótló autóbusz", hungarianServiceName("", "RAIL_REPLACEMENT_BUS"))
        assertEquals("InterCity", hungarianServiceName("InterCity", "RAIL"))
    }
    @Test fun embeddedCategoryKeepsTheServiceNumberAndName() {
        assertEquals("8224 Személyvonat",hungarianServiceLabel("8224 Passenger train"))
        assertEquals("491 Sebesvonat",hungarianServiceLabel("491 Semi-fast train"))
        assertEquals("823 RIPPL-RÓNAI InterCity",hungarianServiceLabel("823 RIPPL-RÓNAI InterCity"))
    }
}
