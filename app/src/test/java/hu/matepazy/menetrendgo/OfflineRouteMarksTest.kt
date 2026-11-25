package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test

// Shapes from the Transitous MÁV feed (2026-11-25): line marks live in route_long_name, categories in trip_short_name.
class OfflineRouteMarksTest {
    @Test fun trainCategoriesGetTheirServiceMarks() {
        assertEquals(RouteLogo(474, "274F96", "FEFEFE"), OfflineRouteMarks.logo("RAIL", "", "IC BALATON", "InterCity 873"))
        assertEquals(473, OfflineRouteMarks.logo("RAIL", "", "Gy BALATON", "gyorsvonat 873")?.code)
        assertEquals(484, OfflineRouteMarks.logo("RAIL", "", "Sz 1330/1337", "SZ 1330")?.code)
        assertEquals(482, OfflineRouteMarks.logo("RAIL", "", "S FENYVES", "SV 8897")?.code)
        assertEquals(477, OfflineRouteMarks.logo("RAIL", "", "IR CÍVIS", "IR 6203")?.code)
        assertEquals(480, OfflineRouteMarks.logo("RAIL", "", "RJ HUNGÁRIA", "")?.code)
        assertEquals("Gyorsvonat", OfflineRouteMarks.serviceName("RAIL", "Gy BALATON", "gyorsvonat 873"))
    }

    @Test fun lineMarksWinOverTheCategory() {
        assertEquals(RouteLogo(390, "B0CB1F", "FFFFFF"), OfflineRouteMarks.logo("RAIL", "", "G43", "SZ 3521"))
        assertEquals(RouteLogo(335, "00A0E3", "FFFFFF"), OfflineRouteMarks.logo("RAIL", "", "S10", "SZ 4421"))
        assertEquals(RouteLogo(383, "000000", "FECC00"), OfflineRouteMarks.logo("RAIL", "", "Z30", "SZ 4523"))
        assertEquals(541, OfflineRouteMarks.logo("RAIL", "", "IR85", "IR 3039")?.code)
        assertEquals(377, OfflineRouteMarks.logo("RAIL_REPLACEMENT_BUS", "", "S780", "autóbusz 33519")?.code)
        assertEquals(440, OfflineRouteMarks.logo("SUBURBAN_RAILWAY", "H5", "", "")?.code)
    }

    @Test fun unknownNamesAndOtherModesKeepTheirText() {
        assertNull(OfflineRouteMarks.logo("RAIL", "", "", "S-Bahn 4700"))
        assertNull(OfflineRouteMarks.logo("RAIL", "Kü", "Kü OLMÜTZ EXPRESSZ", "különvonat 13100"))
        assertNull(OfflineRouteMarks.logo("RAIL", "", "", "4421"))
        assertNull(OfflineRouteMarks.logo("BUS", "", "S10", "SZ 1"))
        assertEquals("", OfflineRouteMarks.serviceName("BUS", "", "SZ 1"))
        assertEquals(RouteLogo(536, "FFFFFF", "C70078"), OfflineRouteMarks.logo("RAIL_REPLACEMENT_BUS", "", "Pótló 162/165", "autóbusz 162"))
    }
}
