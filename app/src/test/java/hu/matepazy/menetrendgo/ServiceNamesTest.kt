package hu.matepazy.menetrendgo

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceNamesTest {
    @Test fun translatesProviderCategories() {
        assertEquals("Személyvonat", hungarianServiceName("Passenger train", "RAIL"))
        assertEquals("Személyvonat", hungarianServiceName("passanger train", "RAIL"))
        assertEquals("Gyorsvonat", hungarianServiceName("Fast train", "RAIL"))
        assertEquals("Sebesvonat", hungarianServiceName("Semi-fast train", "RAIL"))
        assertEquals("Vonatpótló autóbusz", hungarianServiceName("", "RAIL_REPLACEMENT_BUS"))
        assertEquals("InterCity", hungarianServiceName("InterCity", "RAIL"))
    }
}
