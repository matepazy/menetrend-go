package hu.matepazy.menetrendgo

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class VehicleAggregationTest {
    @Test fun aggregatedIsDefaultButExplicitSingleRemainsSelectable() {
        assertEquals(DataSourceMode.Aggregated, DataSourceMode.fromId(null))
        assertEquals(DataSourceMode.Aggregated, DataSourceMode.fromId("unknown"))
        assertEquals(DataSourceMode.Single, DataSourceMode.fromId("single"))
    }

    private val fixture = JSONObject(javaClass.classLoader!!.getResource("utas-vehicles-2026-11-16.json").readText())
    private val now = 1_794_848_700L

    private fun mav(id: String, mode: String, lat: Double, lon: Double, name: String = "", route: String = "",
        plate: String = "", model: String = "", ts: Long = now - 30) =
        Vehicle(id, name, "", mode, lat, lon, routeShortName = route, registration = plate, model = model,
            positionUpdatedSeconds = ts, vehicleId = id)

    private fun utas(id: String, mode: String, lat: Double, lon: Double, name: String = "", route: String = "",
        plate: String = "", model: String = "", ts: Long = now - 10) =
        Vehicle("utas:$id", name, "", mode, lat, lon, routeShortName = route, registration = plate, model = model,
            positionUpdatedSeconds = ts, vehicleId = id, sources = VehicleSources.UTAS)

    @Test fun parsesRealUtasFixtureIncludingVehicleType() {
        val v = parseUtasVehicles(fixture).single()
        assertEquals("trip id translated to MÁVPlusz OTP", "hkir:hkir_8070_154-4", v.id)
        assertEquals("COACH", v.mode)
        assertEquals("Credo EC 12", v.model)
        assertEquals("LKC088", v.registration)
        assertEquals("Credo EC 12 (LKC088)", v.modelAndRegistration)
        assertEquals("8070", v.routeShortName)
        assertEquals("Székesfehérvár, autóbusz-állomás", v.headsign)
        assertEquals("MÁV Személyszállítási Zrt. - Helyközi busz", v.agencyName)
        assertEquals("F6AC00", v.routeColor)
        assertEquals(14.0, v.heading!!, 0.0)
        assertEquals(1_794_848_690L, v.positionUpdatedSeconds)
        assertEquals(v.id, v.mavTripId)
        assertEquals("hkir_8070_154-4", v.utasTrip?.id)
        assertEquals(LocalDate.of(2026, 11, 16), v.utasTrip?.serviceDate)
        assertNull("utas.hu speed units are unknown, never guessed", v.speedMetersPerSecond)
    }

    @Test fun utasErrorStatesAreFailuresNotEmptyMaps() {
        val outOfRange = JSONObject(fixture.toString()).apply { getJSONObject("data").put("outOfRange", true) }
        assertThrows(IllegalStateException::class.java) { parseUtasVehicles(outOfRange) }
        assertThrows(IllegalStateException::class.java) { parseUtasVehicles(JSONObject().put("code", 401)) }
        assertThrows(IllegalStateException::class.java) { parseUtasVehicles(JSONObject().put("code", 200)) }
    }

    @Test fun utasModesMatchAppModes() {
        assertEquals("RAIL_REPLACEMENT_BUS", utasMode("REPLACEMENT_BUS"))
        assertEquals("RAIL", utasMode("rail"))
        assertEquals("TRAMTRAIN", utasMode("TRAMTRAIN"))
        val bus = JSONObject(fixture.toString()).apply {
            getJSONObject("data").getJSONArray("list").getJSONObject(0).put("routeId", "missing").put("vehicleRouteType", "REPLACEMENT_BUS")
        }
        assertEquals("RAIL_REPLACEMENT_BUS", parseUtasVehicles(bus).single().mode)
    }

    @Test fun utasTripIdsTranslateToMavOtpIds() {
        assertEquals("BKK:D2077023511", utasOtpTripId("BKK_D2077023511"))
        assertEquals("1:36437232", utasOtpTripId("mav_36437232"))
        assertEquals("hkir:hkir_8070_154-4", utasOtpTripId("hkir_8070_154-4"))
        assertNull("Miskolc uses another id space", utasOtpTripId("mvk_2715989"))
        assertNull(utasOtpTripId(""))
        assertNull(utasOtpTripId("BKK_"))
    }

    @Test fun untranslatedTripsKeepTheirOriginalLookup() {
        val body = JSONObject(fixture.toString()).apply {
            getJSONObject("data").getJSONArray("list").getJSONObject(0).put("tripId", "mvk_2715989")
        }
        val vehicle = parseUtasVehicles(body).single()
        assertTrue(vehicle.hasTimetable)
        assertNull(vehicle.mavTripId)
        assertEquals("mvk_2715989", vehicle.utasTrip?.id)
    }

    @Test fun vehicleTypeBeatsRouteTypeAndTramsUseRouteNumber() {
        val tram = JSONObject(fixture.toString()).apply {
            getJSONObject("data").getJSONArray("list").getJSONObject(0).put("vehicleRouteType", "TRAM")
        }
        assertEquals("TRAM", parseUtasVehicles(tram).single().mode)
        assertEquals("8070", parseUtasVehicles(tram).single().name)
    }

    @Test fun sameTripMergesEvenFarApartAndIdsStayUnique() {
        val m = mav("BKK:D1", "TRAM", 47.0, 19.0, route = "4")
        val same = Vehicle("BKK:D1", "4", "", "TRAM", 47.05, 19.05, vehicleId = "BKK_V1", positionUpdatedSeconds = now - 5, sources = VehicleSources.UTAS)
        val second = same.copy(vehicleId = "BKK_V2")
        val out = mergeVehicleSources(listOf(m), listOf(same, second), now)
        assertEquals(listOf("BKK:D1", "utas:BKK_V2"), out.map { it.id })
        assertEquals(VehicleSources.MAV or VehicleSources.UTAS, out[0].sources)
        val a = same.copy(id = "BKK:D9"); val b = a.copy(vehicleId = "BKK_V3")
        assertEquals(listOf("BKK:D9", "utas:BKK_V3"), mergeVehicleSources(emptyList(), listOf(a, b), now).map { it.id })
    }

    @Test fun invalidPositionsAreDropped() {
        val list = JSONArray().put(JSONObject().put("vehicleId", "a").put("location", JSONObject().put("lat", 0).put("lon", 0)))
            .put(JSONObject().put("vehicleId", "b"))
            .put(JSONObject().put("location", JSONObject().put("lat", 47.0).put("lon", 19.0)))
        assertTrue(parseUtasVehicles(JSONObject().put("code", 200).put("data", JSONObject().put("list", list))).isEmpty())
    }

    @Test fun samePlateMergesAndTypeComesFromWhicheverSourceHasIt() {
        val m = mav("1:coach", "COACH", 47.161, 18.401, name = "8070", route = "8070", plate = "LKC-088")
        val u = parseUtasVehicles(fixture).single().copy(lat = 47.1615, lon = 18.4018)
        val out = mergeVehicleSources(listOf(m), listOf(u), now)
        val v = out.single()
        assertEquals("MÁV identity kept so the timetable still opens", "1:coach", v.id)
        assertEquals("1:coach", v.mavTripId)
        assertEquals(u.utasTrip, v.utasTrip)
        assertEquals("Credo EC 12", v.model)
        assertEquals("LKC-088", v.registration)
        assertEquals(VehicleSources.MAV or VehicleSources.UTAS, v.sources)
        assertEquals("fresher utas.hu position wins", 47.1615, v.lat, 0.0)
    }

    @Test fun mavTypeIsKeptWhenBothKnowIt() {
        val m = mav("1:t", "RAIL", 47.0, 19.0, name = "IC 552", model = "Stadler KISS", ts = now)
        val u = utas("MAV_552", "RAIL", 47.001, 19.001, name = "552", model = "Other", ts = now - 20)
        val v = mergeVehicleSources(listOf(m), listOf(u), now).single()
        assertEquals("Stadler KISS", v.model)
        assertEquals("older utas.hu fix does not move the vehicle", 47.0, v.lat, 0.0)
    }

    @Test fun trainNumberMergesOnlyNearbyAndOnlyForRail() {
        val m = mav("1:t", "RAIL", 47.0, 19.0, name = "IC 552")
        assertEquals(1, mergeVehicleSources(listOf(m), listOf(utas("a", "RAIL", 47.01, 19.01, name = "552")), now).size)
        assertEquals(2, mergeVehicleSources(listOf(m), listOf(utas("a", "RAIL", 46.0, 20.0, name = "552")), now).size)
        assertEquals(2, mergeVehicleSources(listOf(m), listOf(utas("a", "COACH", 47.0, 19.0, name = "552")), now).size)
        // Coach trip names like "8070/154" must never match by number.
        val coach = mav("1:c", "COACH", 47.0, 19.0, name = "8070/154", route = "8070")
        assertEquals(2, mergeVehicleSources(listOf(coach), listOf(utas("b", "COACH", 47.02, 19.0, name = "8071/154", route = "8071")), now).size)
    }

    @Test fun routeProximityMergeAndEachMavVehicleAbsorbsOnce() {
        val m = mav("1:b", "BUS", 47.0, 19.0, route = "V 120")
        val near = utas("a", "BUS", 47.0005, 19.0, route = "v120")
        val alsoNear = utas("b", "BUS", 47.0006, 19.0, route = "V120")
        val out = mergeVehicleSources(listOf(m), listOf(near, alsoNear), now)
        assertEquals(2, out.size)
        assertEquals(VehicleSources.MAV or VehicleSources.UTAS, out[0].sources)
        assertEquals("utas:b", out[1].id)
    }

    @Test fun staleUtasVehiclesAreDroppedAndUnknownTimesKept() {
        val stale = utas("old", "BUS", 46.0, 18.0, ts = now - UTAS_MAX_AGE_SECONDS - 1)
        val unknown = utas("unknown", "BUS", 46.0, 18.0).copy(positionUpdatedSeconds = null)
        assertEquals(listOf("utas:unknown"), mergeVehicleSources(emptyList(), listOf(stale, unknown), now).map { it.id })
    }

    @Test fun singleSourceListIsUntouched() {
        val list = listOf(mav("1:a", "RAIL", 47.0, 19.0))
        assertSame(list, mergeVehicleSources(list, emptyList(), now))
    }

    @Test fun helpers() {
        assertEquals("LKC088", normalizedPlate("lkc-088"))
        assertEquals("552", trainNumber("IC 0552"))
        assertNull(trainNumber("abc"))
        assertEquals(0, modeFamily("TRAMTRAIN"))
        assertEquals(1, modeFamily("RAIL_REPLACEMENT_BUS"))
        assertEquals(111_195.0, approxDistanceMeters(47.0, 19.0, 48.0, 19.0), 500.0)
        assertEquals(DataSourceMode.Aggregated, DataSourceMode.fromId(null))
        assertEquals(DataSourceMode.Aggregated, DataSourceMode.fromId("aggregated"))
        assertEquals(8, UtasTile.hungary.size)
        assertTrue(UtasTile.hungary.all { it.latSpan <= 0.9 && it.lonSpan <= 0.9 })
    }
}
