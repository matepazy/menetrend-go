package hu.matepazy.menetrendgo

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VehiclePositionsTest {
    private fun position(mode: String = "BUS", tripName: Any = JSONObject.NULL) = JSONObject()
        .put("lat", 47.5).put("lon", 19.1).put("label", "internal-label")
        .put("trip", JSONObject().put("gtfsId", "BKK:trip").put("tripShortName", tripName)
            .put("route", JSONObject().put("mode", mode).put("shortName", "294E")))

    @Test fun localBusesUsePublicRouteNumberEvenWithoutTripName() {
        assertEquals("294E", parseVehiclePosition(position())!!.name)
        assertEquals("294E", parseVehiclePosition(position(tripName = "internal-trip"))!!.name)
    }

    @Test fun railKeepsItsTripNumber() {
        assertEquals("1234", parseVehiclePosition(position("RAIL", "1234"))!!.name)
    }

    @Test fun operatorNameSurvivesForCitySpecificModelPhotos() {
        val json = position()
        json.getJSONObject("trip").getJSONObject("route")
            .put("agency", JSONObject().put("name", "MÁV Személyszállítási Zrt. - Székesfehérvár"))
        assertEquals("MÁV Személyszállítási Zrt. - Székesfehérvár", parseVehiclePosition(json)!!.agencyName)
        assertEquals("", parseVehiclePosition(position())!!.agencyName)
    }

    @Test fun localBusLayerPreservesOtherModesAndExactTripLookup() {
        val vehicles = listOf("BUS", "COACH", "RAIL_REPLACEMENT_BUS", "RAIL", "SUBURBAN_RAILWAY", "TRAMTRAIN")
            .map { mode -> parseVehiclePosition(position(mode))!!.copy(id = mode) }
        assertEquals(vehicles.drop(1), mapLayerVehicles(vehicles, false))
        assertEquals(vehicles, mapLayerVehicles(vehicles, true))
        assertEquals(listOf(vehicles.first()), vehicleForTrip(vehicles, "BUS"))
    }

    @Test fun vehicleFiltersToggleIndividualModes() {
        val vehicles = listOf("BUS", "COACH", "RAIL_REPLACEMENT_BUS", "RAIL", "SUBURBAN_RAILWAY", "TRAMTRAIN")
            .map { mode -> parseVehiclePosition(position(mode))!!.copy(id = mode) }
        assertEquals(
            listOf("RAIL", "SUBURBAN_RAILWAY"),
            mapLayerVehicles(vehicles, setOf(MapVehicleFilter.Rail, MapVehicleFilter.SuburbanRailway)).map { it.id }
        )
        assertTrue(mapLayerVehicles(vehicles, emptySet()).isEmpty())
    }

    @Test fun positionsWithoutTripsOrCoordinatesAreIgnored() {
        assertNull(parseVehiclePosition(position().put("trip", JSONObject.NULL)))
        assertNull(parseVehiclePosition(position().put("lat", JSONObject.NULL)))
    }
}
