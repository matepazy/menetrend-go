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

    @Test fun speedSpikeCannotTriggerAnEarlyWarning() {
        val motion = TrackingMotion("BUS")
        fun fix(progress: Double) = TrackingEstimate(progress, 4000.0 - progress, 4, 1, true, false)
        assertFalse(motion.update(fix(0.0), 0, 35f, 5f, 2, false).shouldAlert)
        assertFalse(motion.update(fix(25.0), 5_000, 5f, 5f, 2, false).shouldAlert)
        val filtered = motion.update(fix(50.0), 10_000, 35f, 5f, 2, false)
        assertFalse(filtered.shouldAlert)
        assertTrue(filtered.secondsRemaining!! > 120)
        assertNull(JourneyProgress(leg()).estimate(middle.trackPoint(), 5f, 1000f, null, 0.0, 2, false)!!.secondsRemaining)
    }

    @Test fun motionCanEstimatePaceWithoutProviderSpeed() {
        val motion = TrackingMotion("BUS")
        var result: TrackingEstimate? = null
        for (i in 0..3) result = motion.update(TrackingEstimate(i * 100.0, 900.0 - i * 100, 1, null, false, false),
            i * 5_000L, null, 5f, 2, false)
        assertEquals(30L, result!!.secondsRemaining)
        assertTrue(result.shouldAlert)
    }

    @Test fun arrivalNeedsTwoFreshSlowFixesAndResetsAfterLoss() {
        val motion = TrackingMotion("RAIL")
        val atStop = TrackingEstimate(1000.0, 20.0, 0, null, true, true)
        assertFalse(motion.update(atStop, 0, 0f, 5f, 2, false).arrived)
        assertFalse(motion.update(atStop, 1_000, 0f, 5f, 2, true).arrived)
        assertTrue(motion.update(atStop, 5_000, 0f, 5f, 2, true).arrived)
        motion.reset()
        assertFalse(motion.update(atStop, 10_000, 0f, 5f, 2, true).arrived)
        assertFalse(motion.update(atStop, 50_000, 0f, 5f, 2, true).arrived)
    }

    @Test fun fastOrReversingVehicleDoesNotFinishJustBeforeStop() {
        val motion = TrackingMotion("RAIL")
        val atStop = TrackingEstimate(1000.0, 50.0, 0, null, true, true)
        motion.update(atStop, 0, 20f, 5f, 2, false)
        assertFalse(motion.update(atStop, 5_000, 20f, 5f, 2, true).arrived)
        motion.reset()
        motion.update(atStop, 0, null, 5f, 2, false)
        assertFalse(motion.update(atStop.copy(matchedProgress = 900.0), 5_000, null, 5f, 2, true).arrived)
    }

    @Test fun crossingFinalStopBetweenFixesIsDetectedOnlyAfterRecentApproach() {
        val engine = JourneyProgress(leg())
        val beyond = TrackPoint(47.0218, 19.0)
        val passed = engine.estimate(beyond, 5f, 20f, engine.length - 200, 10.0, 2, false)!!
        assertTrue(passed.passedDestination)
        assertTrue(TrackingMotion("BUS").update(passed, 10_000, 20f, 5f, 2, false).arrived)
        assertNull(engine.estimate(beyond, 5f, 20f, null, 10.0, 2, false))
        assertNull(engine.estimate(beyond, 5f, 20f, engine.length - 200, 120.0, 2, false))
        assertNull(engine.estimate(TrackPoint(47.0218, 19.01), 5f, 20f, engine.length - 200, 10.0, 2, false))
    }

    @Test fun implausibleBusJumpAndOutOfRangeCoordinatesAreRejected() {
        val engine = JourneyProgress(leg())
        assertNull(engine.estimate(TrackPoint(47.006, 19.0), 5f, 10f, 0.0, 5.0, 2, false))
        assertNull(engine.estimate(TrackPoint(95.0, 19.0), 5f, 10f, null, 5.0, 2, false))
        val unrelatedGeometry = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        assertEquals(engine.length, JourneyProgress(leg().copy(geometry = unrelatedGeometry)).length, .01)
    }

    @Test fun earlyPassBeyondDestinationOnShortLoopDoesNotCompleteTheLeg() {
        val loop = leg(station("Start", 47.0195, 19.0), to,
            listOf(station("Out", 47.019, 19.0), station("Back", 47.021, 19.0001)))
        val result = JourneyProgress(loop).estimate(TrackPoint(47.0192, 19.0), 5f, 5f, 0.0, 5.0, 2, false)!!
        assertFalse(result.passedDestination)
        assertFalse(result.arrived)
    }

    @Test fun movingAwayDoesNotCreateAFalseCountdown() {
        val motion = TrackingMotion("BUS")
        var result: TrackingEstimate? = null
        for (i in 0..3) result = motion.update(
            TrackingEstimate(1000.0, 2000.0, 3, null, false, false, matchedProgress = 1000.0 - i * 100),
            i * 5_000L, 20f, 5f, 2, false)
        assertNull(result!!.secondsRemaining)
        assertFalse(result.shouldAlert)
    }

    @Test fun liveWarningFallbackRequiresFreshLiveDataAndRecentNearbyLocation() {
        val session = TrackingSession(Journey(600, listOf(leg())), 0)
        val now = 1_000_000L
        assertTrue(liveArrivalWarning(session, now + 60_000, 20_000, 45_000, 1500.0, now))
        assertFalse(liveArrivalWarning(session, null, 0, 45_000, 1500.0, now))
        assertFalse(liveArrivalWarning(session, now + 60_000, 120_000, 45_000, 1500.0, now))
        assertFalse(liveArrivalWarning(session, now + 60_000, 20_000, 180_000, 1500.0, now))
        assertFalse(liveArrivalWarning(session, now + 60_000, 20_000, 45_000, 9000.0, now))
        assertFalse(liveArrivalWarning(session, now + 600_000, 20_000, 45_000, 1500.0, now))
        assertFalse(liveArrivalWarning(session.copy(alerted = true), now, 0, 45_000, 1500.0, now))
        assertFalse(liveArrivalWarning(session.copy(awaitingBoarding = true), now, 0, 45_000, 1500.0, now))
    }

    @Test fun notificationControlsAreBoundToExactSessionAndLeg() {
        val journey = Journey(1200, listOf(leg(), leg().copy(tripId = "next")))
        val session = TrackingSession(journey, 0)
        assertNotEquals(session.controlToken, session.copy(legIndex = 1).controlToken)
        assertNotEquals(session.controlToken, session.copy(startedAt = session.startedAt + 1).controlToken)
        val differentTarget = journey.copy(legs = listOf(leg().copy(to = middle), journey.legs[1]))
        assertNotEquals(journey.trackingKey, differentTarget.trackingKey)
        assertNotEquals(session.controlToken, session.copy(journey = differentTarget).controlToken)
        assertEquals(session.controlToken, TrackingSession.parse(session.json()).controlToken)
    }
}
