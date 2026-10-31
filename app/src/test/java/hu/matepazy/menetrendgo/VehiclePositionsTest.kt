package hu.matepazy.menetrendgo

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VehiclePositionsTest {
    @Test fun providerTimestampSurvivesParsingWithoutInventingMissingTimes() {
        assertEquals(1_792_924_339L, parseVehiclePosition(position().put("lastUpdated",1_792_924_339L))!!.positionUpdatedSeconds)
        assertNull(parseVehiclePosition(position())!!.positionUpdatedSeconds)
        assertNull(parseVehiclePosition(position().put("lastUpdated",JSONObject.NULL))!!.positionUpdatedSeconds)
        assertNull(parseVehiclePosition(position().put("lastUpdated",0))!!.positionUpdatedSeconds)
    }

    @Test fun positionAgeUsesHungarianUnitsAndKeepsAgingWhenRefreshFails() {
        val reported=1_792_924_339L
        for (elapsed in listOf(0L, 1L, 59L, 60L, 61L, 3599L, 3600L, 172800L)) {
            assertEquals("Pozíció frissítve: $elapsed másodperce",vehiclePositionAge(reported,reported+elapsed))
        }
        assertEquals("Pozíció frissítve: 0 másodperce",vehiclePositionAge(reported,reported-10))
        assertEquals("A pozíció frissítési ideje ismeretlen",vehiclePositionAge(null,reported))
    }

    @Test fun speedUsesProviderMetersPerSecondAndPreservesStoppedVehicles() {
        val moving=parseVehiclePosition(position().put("speed",20.5))!!
        assertEquals(20.5,moving.speedMetersPerSecond!!,0.0)
        assertEquals("74 km/h",vehicleSpeedLabel(moving.speedMetersPerSecond))
        assertEquals("0 km/h",vehicleSpeedLabel(parseVehiclePosition(position().put("speed",0))!!.speedMetersPerSecond))
        assertEquals("36 km/h",vehicleSpeedLabel(10.0))
    }

    @Test fun missingAndInvalidSpeedsNeverBecomeZero() {
        for (value in listOf(JSONObject.NULL,-1,"invalid","NaN","Infinity")) {
            assertNull(parseVehiclePosition(position().put("speed",value))!!.speedMetersPerSecond)
        }
        assertNull(parseVehiclePosition(position())!!.speedMetersPerSecond)
        assertNull(vehicleSpeedLabel(null))
        assertNull(vehicleSpeedLabel(-1.0))
        assertNull(vehicleSpeedLabel(Double.NaN))
        assertNull(vehicleSpeedLabel(Double.POSITIVE_INFINITY))
    }

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
