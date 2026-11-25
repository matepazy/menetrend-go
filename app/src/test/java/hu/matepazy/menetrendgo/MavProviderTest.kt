package hu.matepazy.menetrendgo

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MavProviderTest {
    private val requests = mutableListOf<HttpRequest>()
    private fun provider(code: Int = 200, body: (HttpRequest) -> String = { "{}" }) =
        MavProvider(MavClient { request -> requests += request; HttpResponse(code, if (code in 200..299) body(request) else "") }) { 42L }

    @After fun resetProviderState() = OfflineStatus.providerReachable()

    private fun row(trip: String, scheduled: Long, live: Long = scheduled, realtime: Boolean = true, state: String = "UPDATED") = JSONObject("""{
        "serviceDay":1700000000,"scheduledDeparture":$scheduled,"realtimeDeparture":$live,"realtime":$realtime,
        "pickupType":"SCHEDULED","realtimeState":"$state","headsign":"Szeged",
        "stop":{"gtfsId":"1:0055_6","name":"Budapest-Nyugati","lat":47.51,"lon":19.05,"platformCode":"6"},
        "trip":{"gtfsId":"$trip","tripShortName":"IC 712","trainCategoryName":"InterCity","route":{"shortName":"IC","longName":"Budapest – Szeged","mode":"RAIL","color":"0066CC"}}}""")
    private fun departuresBody(vararg rows: JSONObject) =
        JSONObject().put("data", JSONObject().put("stop", JSONObject().put("stoptimesWithoutPatterns", JSONArray(rows.toList())))).toString()

    @Test fun departuresMatchTheExistingParserAndCarryProvenance() = runBlocking {
        val rows = arrayOf(row("1:712", 3600, 3900), row("1:714", 7200, realtime = false), row("1:716", 10800, state = "CANCELED"))
        val result = provider { departuresBody(*rows) }.departures(DepartureQuery(Gid("mav:1:0055"), 1_700_000_000_000L))
        val ok = result as ProviderResult.Ok
        val legacy = parseStopDepartures(JSONObject(departuresBody(*rows)).getJSONObject("data"), 1_700_000_000_000L)
        assertEquals(legacy.size, ok.value.size)
        legacy.zip(ok.value).forEach { (old, new) ->
            assertEquals(Gid("mav:${old.tripId}"), new.trip.id)
            assertEquals(old.time, new.call.departure!!.serviceTime)
            assertEquals(old.cancelled, new.call.cancelled)
            assertEquals(old.platform, new.call.platform)
            assertEquals(old.headsign, new.trip.headsign)
            assertEquals(LocalDate.parse(old.serviceDay), new.trip.serviceDay)
            assertEquals(Mode.RAIL, new.trip.route.mode)
            assertEquals(Gid("mav:1:0055_6"), new.call.stop.id)
        }
        assertEquals(DataKind.Live, ok.value[0].provenance.kind)
        assertEquals(DataKind.Scheduled, ok.value[1].provenance.kind)
        assertEquals(DataKind.Live, ok.provenance.kind)
        assertEquals(setOf(ProviderId.Mav), ok.provenance.sources)
        assertEquals(42L, ok.provenance.fetchedAt)
        // The provider id leaves the request: MÁVPlusz sees its own id.
        assertTrue(requests.single().body!!.contains("\\\"1:0055\\\""))
        assertEquals("https://mavplusz.hu/", requests.single().headers["Referer"])
    }

    @Test fun foreignIdsAreNotCoveredAndNeverReachTheNetwork() = runBlocking {
        val p = provider()
        assertEquals(ProviderResult.NotCovered, p.departures(DepartureQuery(Gid("gtfs:hu_volan:1"), 0)))
        assertEquals(ProviderResult.NotCovered, p.trip(Gid("utas:42"), LocalDate.of(2026, 11, 25)))
        assertEquals(ProviderResult.NotCovered, p.stopsNear(52.5, 13.4, 500))
        assertTrue(requests.isEmpty())
    }

    @Test fun serverErrorsAreUnavailableNotThrown() = runBlocking {
        val result = provider(503).departures(DepartureQuery(Gid("mav:1:0055"), 1))
        assertTrue(result is ProviderResult.Unavailable)
        assertTrue((result as ProviderResult.Unavailable).reason.contains("503"))
        assertTrue(OfflineStatus.providerDown)
    }

    @Test fun malformedAnswersAreUnavailable() = runBlocking {
        val result = provider { """{"errors":[{"message":"x"}]}""" }.departures(DepartureQuery(Gid("mav:1:0055"), 1))
        assertTrue(result is ProviderResult.Unavailable)
    }

    private val tripJson = JSONObject("""{"gtfsId":"1:712","tripHeadsign":"Szeged","tripShortName":"712","trainCategoryName":"InterCity",
        "route":{"shortName":"IC","longName":"Budapest – Szeged","mode":"RAIL","color":"0066CC","textColor":"FFFFFF","agency":{"name":"MÁV-START"}},
        "infoServices":[{"name":"Büfé","fontCode":59,"fontCharSet":"MNR2007","displayable":true}],
        "stoptimes":[
          {"serviceDay":null,"realtime":true,"realtimeState":"UPDATED","stop":{"gtfsId":"1:0055","name":"Budapest-Nyugati","lat":47.51,"lon":19.05,"platformCode":"6"},
           "scheduledArrival":36000,"realtimeArrival":36000,"scheduledDeparture":36000,"realtimeDeparture":36120},
          {"realtime":true,"realtimeState":"UPDATED","stop":{"gtfsId":"1:0070","name":"Kecskemét","lat":46.9,"lon":19.69},
           "scheduledArrival":40000,"realtimeArrival":40300,"scheduledDeparture":40060,"realtimeDeparture":40360},
          {"realtime":false,"stop":{"gtfsId":"1:0081","name":"Szeged","lat":46.24,"lon":20.14},
           "scheduledArrival":45000,"scheduledDeparture":null},
          {"realtime":true,"stop":{"name":"broken"}}]}""")

    @Test fun tripTimesMatchTripStopTime() {
        val day = LocalDate.of(2026, 11, 8)
        val trip = parseTripJson(tripJson, day, ProviderId.Mav, Provenance.of(ProviderId.Mav, 1, DataKind.Scheduled))
        assertEquals(3, trip.calls.size)   // the stop without coordinates is dropped, not guessed
        fun at(seconds: Long) = vehicleStopTime(day, seconds)
        assertEquals(listOf(ServiceTime(at(36000), at(36120), true), ServiceTime(at(40060), at(40360), true),
            ServiceTime(at(45000), at(45000), false)), trip.calls.map { it.time!!.serviceTime })
        assertNull(trip.calls.last().departure)
        assertEquals(Gid("mav:1:712"), trip.id)
        assertEquals("MÁV-START", trip.summary.route.agency?.name)
        assertEquals(DataKind.Live, trip.provenance.kind)
        assertEquals("Büfé", trip.services.single().name)
        assertNotNull(trip.callAt(Gid("mav:1:0070")))
    }

    @Test fun tripRequestsAreAnsweredCanonically() = runBlocking {
        val body = JSONObject().put("data", JSONObject().put("trip", tripJson)).toString()
        val result = provider { body }.trip(Gid("mav:1:712"), LocalDate.of(2026, 11, 8)) as ProviderResult.Ok
        assertEquals("IC", result.value.summary.route.shortName)
        assertEquals(DataKind.Live, result.provenance.kind)
    }

    @Test fun journeysBecomeItinerariesThatSurviveTheCodec() {
        val nyugati = Station("1:0055", "Budapest-Nyugati", 47.51, 19.05, platform = "6")
        val szeged = Station("1:0081", "Szeged", 46.24, 20.14)
        val dom = Station("osm:node/1", "Dóm tér", 46.25, 20.15, detail = "Tér · Szeged")
        val ride = Leg("RAIL", 36_120_000, 45_000_000, nyugati, szeged, "IC 712", "1:712", true, 120,
            listOf(Station("1:0070", "Kecskemét", 46.9, 19.69)), "geo", category = "InterCity", headsign = "Szeged", routeShortName = "IC")
        val walk = Leg("WALK", 45_000_000, 45_600_000, szeged, dom, "WALK", "", false, 0, emptyList(), "")
        val itinerary = Journey(9480, listOf(ride, walk), itineraryJson = "{}", fetchedAt = 7).toItinerary(99)
        val boarding = itinerary.legs[0] as JourneyLeg.Ride
        assertEquals(ride.departureTime, boarding.board.departure!!.serviceTime)
        assertEquals(ride.arrivalTime, boarding.alight.arrival!!.serviceTime)
        assertEquals(Gid("mav:1:0070"), boarding.intermediate.single().stop.id)
        val end = (itinerary.legs[1] as JourneyLeg.Walk).to
        assertEquals(Place.Address("Dóm tér", 46.25, 20.15, "Tér · Szeged"), end)
        assertEquals(DataKind.Live, itinerary.provenance.kind)
        assertEquals(7L, itinerary.provenance.fetchedAt)
        assertEquals("{}", itinerary.providerState)
        assertEquals(itinerary, TransitCodec.decodeItinerary(JSONObject(TransitCodec.encode(itinerary).toString())))
        val offline = Journey(1, listOf(ride.copy(realTime = false)), offline = true).toItinerary(99)
        assertEquals(DataKind.Offline, offline.provenance.kind)
        assertEquals(ProviderId.Gtfs, (offline.legs[0] as JourneyLeg.Ride).trip.id.provider)
    }

    @Test fun placesRoundTripToStations() {
        val stop = Station("1:0055", "Budapest-Nyugati", 47.51, 19.05, modes = listOf("RAIL"), platform = "6")
        assertEquals(stop, stop.toPlace().toStation())
        val osm = Station("osm:node/1", "Dóm tér", 46.25, 20.15, detail = "Tér")
        assertEquals(Station("", "Dóm tér", 46.25, 20.15, detail = "Tér"), osm.toPlace().toStation())
        val here = Station("", "Jelenlegi hely", 47.0, 19.0, currentLocationCalculatedAt = 5)
        assertTrue(here.toPlace() is Place.CurrentLocation)
        assertEquals(5L, here.toPlace().toStation().currentLocationCalculatedAt)
        val foreign = Place.StopPlace(Stop(Gid("gtfs:hu_volan:1"), "Eger", 47.9, 20.37))
        assertEquals("", foreign.toStation().id)
    }

    @Test fun mergedVehiclesKeepEveryContributor() {
        val vehicle = Vehicle("1:712", "IC 712", "Szeged", "RAIL", 47.0, 19.5, routeShortName = "IC", registration = "V43-1234",
            positionUpdatedSeconds = 1_763_000_000, sources = VehicleSources.MAV or VehicleSources.UTAS)
        val position = vehicle.toVehiclePosition(Provenance.of(ProviderId.Mav, 1, DataKind.Live))
        assertEquals(Gid("mav:1:712"), position.id)
        assertEquals(Gid("mav:1:712"), position.tripId)
        assertEquals(setOf(ProviderId.Mav, ProviderId.Utas), position.provenance.sources)
        assertEquals("V43-1234", position.plate)
        val utasOnly = vehicle.copy(id = "utas:99", sources = VehicleSources.UTAS, utasTrip = UtasTripRef("mvk_5", LocalDate.of(2026, 11, 25), "99"))
            .toVehiclePosition(Provenance.of(ProviderId.Utas, 1, DataKind.Live))
        assertEquals(Gid("utas:99"), utasOnly.id)
        assertEquals(Gid("utas:mvk_5"), utasOnly.tripId)
        assertEquals(LocalDate.of(2026, 11, 25), utasOnly.serviceDay)
    }
}
