package hu.matepazy.menetrendgo

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TransitModelTest {
    private val mav = Provenance.of(ProviderId.Mav, 1_000, DataKind.Live)
    private fun stop(id: String, name: String = "Budapest-Keleti", platform: String = "") =
        Stop(Gid(id), name, 47.5, 19.08, platform = platform, modes = setOf(Mode.RAIL))

    @Test fun gidKeepsProviderColonsInTheLocalPart() {
        val id = Gid.of(ProviderId.Mav, "1:005501234")
        assertEquals("mav:1:005501234", id.raw)
        assertEquals(ProviderId.Mav, id.provider)
        assertEquals("1:005501234", id.local)
        assertEquals("gtfs:hu_volan:123", Gid.of(ProviderId.Gtfs, "hu_volan:123").raw)
    }

    @Test fun gidParseRejectsBareProviderIds() {
        assertEquals(Gid("mav:1:0055"), Gid.parse("mav:1:0055"))
        assertNull(Gid.parse("BKK:F00123"))   // upper case: a bare MÁVPlusz feed prefix, not a namespace
        assertNull(Gid.parse("005501234"))
        assertNull(Gid.parse(":x"))
        assertNull(Gid.parse("mav:"))
    }

    @Test fun legacyIdsBecomeMavExceptUtas() {
        assertEquals(Gid("mav:1:005501234"), Gid.legacy("1:005501234"))
        assertEquals(Gid("mav:BKK:F00123"), Gid.legacy("BKK:F00123"))
        assertEquals(Gid("mav:hkir:hkir_8070"), Gid.legacy("hkir:hkir_8070"))
        assertEquals(Gid("utas:4123"), Gid.legacy("utas:4123"))
        assertNull(Gid.legacy(" "))
    }

    @Test(expected = IllegalArgumentException::class) fun providerIdsAreLowerCaseSlugs() { ProviderId("MÁV") }

    @Test fun otpModesRoundTripAndAliasesResolve() {
        listOf("RAIL", "SUBURBAN_RAILWAY", "TRAMTRAIN", "COACH", "BUS", "RAIL_REPLACEMENT_BUS", "TROLLEYBUS", "TRAM", "SUBWAY", "FERRY", "WALK")
            .forEach { assertEquals(it, ModeMapping.fromOtp(it).otp) }
        assertEquals(Mode.RAIL_REPLACEMENT_BUS, ModeMapping.fromUtas("replacement_bus"))
        assertEquals(Mode.TRAMTRAIN, ModeMapping.fromUtas("TRAM_TRAIN"))
        assertEquals(Mode.OTHER, ModeMapping.fromOtp("AIRPLANE"))
    }

    @Test fun gtfsRouteTypesMatchTheOfflineMapping() {
        assertEquals(Mode.RAIL, ModeMapping.fromGtfsRouteType(2))
        assertEquals(Mode.SUBURBAN_RAILWAY, ModeMapping.fromGtfsRouteType(109))
        assertEquals(Mode.RAIL_REPLACEMENT_BUS, ModeMapping.fromGtfsRouteType(714))
        assertEquals(Mode.COACH, ModeMapping.fromGtfsRouteType(202))
        assertEquals(Mode.TROLLEYBUS, ModeMapping.fromGtfsRouteType(800))
        assertEquals(Mode.BUS, ModeMapping.fromGtfsRouteType(9999))
        assertEquals("SUBURBAN_RAILWAY", gtfsMode(109))
    }

    @Test fun familiesMatchVehicleAggregation() {
        Mode.entries.filter { it.family in setOf(ModeFamily.Rail, ModeFamily.Road, ModeFamily.Urban, ModeFamily.Water) && it != Mode.MONORAIL }
            .forEach { assertEquals(it.name, it.family.ordinal, modeFamily(it.otp)) }
        assertFalse(Mode.SCOOTER.transit)
        assertTrue(Mode.COACH.transit)
    }

    @Test fun gbfsFormFactors() {
        assertEquals(Mode.BICYCLE, ModeMapping.fromGbfsFormFactor("cargo_bicycle"))
        assertEquals(Mode.SCOOTER, ModeMapping.fromGbfsFormFactor("scooter_standing"))
        assertEquals(Mode.MOPED, ModeMapping.fromGbfsFormFactor("moped"))
        assertEquals(Mode.OTHER, ModeMapping.fromGbfsFormFactor("other"))
    }

    @Test fun mergedProvenanceIsAsLiveAsItsLiveliestSource() {
        val merged = Provenance.of(ProviderId.Gtfs, 5_000, DataKind.Offline) + mav
        assertEquals(setOf(ProviderId.Gtfs, ProviderId.Mav), merged.sources)
        assertEquals(DataKind.Live, merged.kind)
        assertEquals(5_000L, merged.fetchedAt)
    }

    @Test fun timePointBridgesServiceTime() {
        val live = TimePoint.of(ServiceTime(60_000, 180_000, true))
        assertEquals(TimeStatus.Realtime, live.status)
        assertEquals(ServiceTime(60_000, 180_000, true), live.serviceTime)
        assertEquals(TimeStatus.Cancelled, TimePoint.of(ServiceTime(60_000, 60_000, false), cancelled = true).status)
        assertEquals(ServiceTime(60_000, 60_000, false), TimePoint(60_000, 90_000).serviceTime)
    }

    @Test fun callsAndTripsFindStopsByAliasOrStation() {
        val platform = stop("mav:1:0055_3", platform = "3").copy(parentId = Gid("mav:1:0055"), aliases = setOf(Gid("gtfs:hu_mav:0055_3")))
        val trip = Trip(TripSummary(Gid("mav:1:777"), TransitRoute(null, "IC 552", "", Mode.RAIL), "Szeged", LocalDate.of(2026, 11, 25)),
            listOf(Call(platform, 0, null, TimePoint(1_000))), mav)
        assertNotNull(trip.callAt(Gid("gtfs:hu_mav:0055_3")))
        assertNotNull(trip.callAt(Gid("mav:1:0055")))
        assertNull(trip.callAt(Gid("mav:1:0099")))
        assertEquals(1_000L, trip.calls.single().time?.scheduled)
    }

    private fun sampleItinerary(): Itinerary {
        val keleti = stop("mav:1:0055", platform = "6")
        val szeged = stop("mav:1:0081", "Szeged")
        val route = TransitRoute(Gid("mav:1:IC"), "IC 552", "Budapest – Szeged", Mode.RAIL, "0066CC", "FFFFFF",
            Agency(null, "MÁV-START"), RouteLogo(0xE001, "FFFFFF", "0066CC"))
        val ride = JourneyLeg.Ride(TripSummary(Gid("mav:1:777"), route, "Szeged", LocalDate.of(2026, 11, 25), "552", "InterCity"),
            Call(keleti, 0, null, TimePoint(1_000, 61_000, TimeStatus.Realtime), platform = "7"),
            Call(szeged, 5, TimePoint(9_000), null, dropoff = Boarding.None),
            listOf(Call(stop("mav:1:0070", "Kecskemét"), 3, TimePoint(5_000), TimePoint(5_060, status = TimeStatus.Skipped))),
            "abc", listOf(MavService("Büfé", 59, "MNR2007", 0, 5, "Budapest-Keleti", "Szeged")),
            listOf(MavAlert("a1", "", "Pályafelújítás", "https://mavcsoport.hu", 10, null, true)))
        val walk = JourneyLeg.Walk(Place.StopPlace(szeged), Place.Address("Dóm tér", 46.25, 20.15, "Tér · Szeged"), 9_000, 12_000)
        return Itinerary(listOf(ride, walk), mav, providerState = "{\"plan\":1}")
    }

    @Test fun itineraryRoundTripsThroughTheCodec() {
        val itinerary = sampleItinerary()
        val decoded = TransitCodec.decodeItinerary(JSONObject(TransitCodec.encode(itinerary).toString()))
        assertEquals(itinerary, decoded)
        assertEquals(itinerary.key, decoded!!.key)
        assertEquals(0, decoded.changes)
        assertEquals(61_000L, decoded.start)
        assertEquals(12_000L, decoded.end)
    }

    @Test fun codecRejectsOtherSchemasAndUnreadableLegs() {
        val json = TransitCodec.encode(sampleItinerary())
        assertNull(TransitCodec.decodeItinerary(JSONObject(json.toString()).put("schema", 1)))
        val broken = JSONObject(json.toString())
        broken.getJSONArray("legs").getJSONObject(0).getJSONObject("trip").put("id", "no-namespace")
        assertNull(TransitCodec.decodeItinerary(broken))
    }

    @Test fun departureAndPlaceRoundTrip() {
        val departure = Departure(TripSummary(Gid("gtfs:hu_volan:t1"), TransitRoute(null, "1020", "", Mode.COACH), "Eger", LocalDate.of(2026, 11, 25)),
            Call(stop("gtfs:hu_volan:s1", "Népliget"), 2, null, TimePoint(3_600_000)), Provenance.of(ProviderId.Gtfs, 7, DataKind.Offline))
        assertEquals(departure, TransitCodec.decodeDeparture(JSONObject(TransitCodec.encode(departure).toString())))
        val address = Place.Address("Margitsziget", 47.52, 19.05)
        assertEquals(address, TransitCodec.decodePlace(TransitCodec.encode(address)))
    }

    @Test(expected = IllegalArgumentException::class) fun currentLocationIsNeverPersisted() {
        TransitCodec.encode(Place.CurrentLocation(47.0, 19.0, 1))
    }

    @Test fun rentalLegsAreReservedButEncodable() {
        val from = Place.Address("A", 47.0, 19.0); val to = Place.Address("B", 47.01, 19.01)
        val leg = JourneyLeg.Rental(Gid("gbfs:bubi"), Mode.BICYCLE, from, to, 0, 600_000)
        assertEquals(leg, TransitCodec.decodeLeg(TransitCodec.encode(leg)))
    }
}
