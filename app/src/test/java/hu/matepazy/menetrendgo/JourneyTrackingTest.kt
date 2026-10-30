package hu.matepazy.menetrendgo

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JourneyTrackingTest {
    private fun station(name: String, lat: Double, lon: Double) = Station(name, name, lat, lon)
    private val from = station("Indulás", 47.0, 19.0)
    private val middle = station("Köztes", 47.01, 19.0)
    private val to = station("Érkezés", 47.02, 19.0)
    private fun leg(from: Station = this.from, to: Station = this.to, stops: List<Station> = listOf(middle)) =
        Leg("BUS", 1_000_000, 1_600_000, from, to, "10", "trip", false, 0, stops, "")

    @Test fun approachingDestinationAlertsOnceAndCountsStops() {
        val engine = JourneyProgress(leg())
        val beginning = engine.estimate(from.trackPoint(), 10f, 5f, null, 0.0, 2, false)!!
        assertFalse(beginning.shouldAlert)
        assertEquals(2, beginning.stopsRemaining)
        val near = engine.estimate(TrackPoint(47.018, 19.0), 10f, 5f, beginning.progress, 400.0, 2, false)!!
        assertTrue(near.shouldAlert)
        assertEquals(1, near.stopsRemaining)
        assertFalse(engine.estimate(TrackPoint(47.019, 19.0), 10f, 5f, near.progress, 30.0, 2, true)!!.shouldAlert)
        assertTrue(engine.estimate(to.trackPoint(), 10f, 0f, near.progress, 60.0, 2, true)!!.arrived)
    }

    @Test fun passingNearDestinationEarlyDoesNotAlert() {
        val destination = station("Végállomás", 47.0, 19.0005)
        val loop = leg(from, destination, listOf(station("Kanyar", 47.03, 19.0), station("Vissza", 47.03, 19.01)))
        val result = JourneyProgress(loop).estimate(from.trackPoint(), 10f, 5f, null, 0.0, 2, false)!!
        assertTrue(result.remainingMeters > 6000)
        assertFalse(result.shouldAlert)
        assertFalse(result.arrived)
    }

    @Test fun rejectsBadAccuracyOffRouteAndImpossibleJumps() {
        val engine = JourneyProgress(leg())
        assertNull(engine.estimate(to.trackPoint(), 400f, 5f, null, 0.0, 2, false))
        assertNull(engine.estimate(TrackPoint(48.0, 20.0), 10f, 5f, null, 0.0, 2, false))
        assertNull(engine.estimate(to.trackPoint(), 10f, 5f, 0.0, 1.0, 2, false))
    }

    @Test fun progressDoesNotRegressWithGpsJitter() {
        val engine = JourneyProgress(leg())
        val first = engine.estimate(middle.trackPoint(), 10f, 5f, null, 0.0, 2, false)!!
        val jitter = engine.estimate(TrackPoint(47.0098, 19.0), 10f, 5f, first.progress, 5.0, 2, false)!!
        assertEquals(first.progress, jitter.progress, 0.01)
    }

    @Test fun warningLeadPreferenceChangesTrigger() {
        val engine = JourneyProgress(leg())
        val point = TrackPoint(47.012, 19.0)
        assertFalse(engine.estimate(point, 10f, 5f, null, 0.0, 1, false)!!.shouldAlert)
        assertTrue(engine.estimate(point, 10f, 5f, null, 0.0, 3, false)!!.shouldAlert)
    }

    @Test fun stationaryNearDestinationStillWarns() {
        val result = JourneyProgress(leg()).estimate(TrackPoint(47.018, 19.0), 10f, 0f, null, 0.0, 2, false)!!
        assertTrue(result.shouldAlert)
        assertNull(result.secondsRemaining)
    }

    @Test fun sessionRestoresProgressAlertsAndTransferState() {
        val walk = leg().copy(mode = "WALK")
        val journey = Journey(1200, listOf(leg(), walk, leg().copy(tripId = "second")))
        val session = TrackingSession(journey, 0, 3, true, true, 1250.0, 1_000_000)
        val restored = TrackingSession.parse(JSONObject(session.json().toString()))
        assertEquals(session.journey.key, restored.journey.key)
        assertEquals(2, restored.nextLegIndex)
        assertTrue(restored.alerted)
        assertTrue(restored.awaitingBoarding)
        assertEquals(1250.0, restored.progress!!, 0.01)
        assertEquals(3, restored.warningMinutes)
    }

    @Test fun malformedGeometryFallsBackToOrderedStops() {
        assertTrue(decodeTrackingGeometry("~").isEmpty())
        assertTrue(JourneyProgress(leg().copy(geometry = "~")).length > 2000)
        val decoded = decodeTrackingGeometry("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, decoded.size)
        assertEquals(38.5, decoded.first().lat, 0.00001)
    }

    @Test fun liveArrivalRequiresExactTripStopOccurrenceAndRealtime() {
        val trip = JSONObject("""{"gtfsId":"trip","stoptimes":[
            {"serviceDay":0,"scheduledArrival":1600,"realtimeArrival":1700,"realtime":true,
             "stop":{"name":"Érkezés","lat":47.02,"lon":19.0}},
            {"serviceDay":0,"scheduledArrival":3600,"realtimeArrival":3700,"realtime":true,
             "stop":{"name":"Érkezés","lat":47.02,"lon":19.0}}]}""")
        assertEquals(1_700_000L, trackingArrival(trip, leg()))
        assertNull(trackingArrival(trip, leg().copy(tripId = "different")))
        trip.getJSONArray("stoptimes").getJSONObject(0).put("realtime", false)
        assertNull(trackingArrival(trip, leg()))
    }
}
