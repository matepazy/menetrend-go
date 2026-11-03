package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test

class DestinationAlertTest {
    private val target = Station("end", "Leszállóhely", 47.02, 19.0)
    private val leg = Leg("BUS", 0, 600_000, target.copy(lat = 47.0), target,
        "10", "trip", false, 0, emptyList(), "")
    private fun point(metersNorth: Double, metersEast: Double = 0.0) =
        TrackPoint(target.lat + metersNorth / 111_195, target.lon + metersEast / 75_800)

    @Test fun largerCirclesMatchTransportModes() {
        assertEquals(200.0, destinationAlertRadius("BUS"), 0.0)
        assertEquals(200.0, destinationAlertRadius("TRAM"), 0.0)
        assertEquals(300.0, destinationAlertRadius("COACH"), 0.0)
        assertEquals(300.0, destinationAlertRadius("RAIL_REPLACEMENT_BUS"), 0.0)
        assertEquals(500.0, destinationAlertRadius("RAIL"), 0.0)
        assertEquals(500.0, destinationAlertRadius("SUBURBAN_RAILWAY"), 0.0)
    }

    @Test fun detourKeepsTrackingAndAlertsWithoutRouteEvidence() {
        val alert = DestinationAlert(leg)
        val offRoute = point(-300.0, 400.0)
        assertNull(JourneyProgress(leg).estimate(offRoute, 5f, 10f, 0.0, 1.0, 2, false))
        assertFalse(alert.update(offRoute, 5f, 0, 10f, false)!!.shouldAlert)
        val near = alert.update(point(-70.0, 20.0), 5f, 30_000, 10f, false)!!
        assertTrue(near.shouldAlert)
        assertFalse(near.arrived)
        assertFalse(alert.update(point(-50.0), 5f, 32_000, 10f, true)!!.shouldAlert)
    }

    @Test fun onlyAccurateInsideFixesTriggerRegardlessOfSpeedOrTimetable() {
        assertFalse(DestinationAlert(leg).update(point(201.0), 1f, 0, 35f, false)!!.shouldAlert)
        assertFalse(DestinationAlert(leg).update(point(190.0), 20f, 0, 35f, false)!!.shouldAlert)
        assertTrue(DestinationAlert(leg).update(point(80.0), 5f, 0, 0f, false)!!.shouldAlert)
        assertNull(DestinationAlert(leg).update(point(0.0), 150f, 0, 0f, false))
        assertNull(DestinationAlert(leg).update(TrackPoint(Double.NaN, 19.0), 5f, 0, 0f, false))
    }

    @Test fun jumpsAndDuplicateFixesCannotTriggerButRecoveryCan() {
        val alert = DestinationAlert(leg)
        alert.update(point(-2000.0), 5f, 0, null, false)
        assertNull(alert.update(point(0.0), 5f, 1_000, null, false))
        assertNull(alert.update(point(0.0), 5f, 0, null, false))
        assertTrue(alert.update(point(-40.0), 5f, 60_000, null, false)!!.shouldAlert)
    }

    @Test fun warningDoesNotImmediatelyCompleteTheLegAndArrivalSurvivesDetours() {
        val alert = DestinationAlert(leg)
        val first = alert.update(point(-80.0), 5f, 0, 0f, false)!!
        assertTrue(first.shouldAlert)
        assertFalse(first.arrived)
        assertTrue(alert.update(point(-80.0), 5f, 4_000, 0f, true)!!.arrived)
        assertFalse(alert.update(point(-80.0), 5f, 40_000, 0f, true)!!.arrived)
    }


    @Test fun overlappingDestinationCircleWaitsUntilPastPreviousStopGuard() {
        val guardedLeg = leg.copy(intermediate = listOf(target.copy(name = "Previous", lat = point(-150.0).lat)))
        val alert = DestinationAlert(guardedLeg)
        // Already inside the destination circle, but still approaching the previous stop.
        assertFalse(alert.update(point(-180.0), 5f, 0, 0f, false)!!.shouldAlert)
        assertFalse(alert.update(point(-150.0), 5f, 4_000, 0f, false)!!.shouldAlert)
        assertFalse(alert.update(point(-150.0), 5f, 8_000, 0f, false)!!.arrived)
        assertFalse(alert.update(point(-105.0), 10f, 18_000, 0f, false)!!.shouldAlert)
        assertTrue(alert.update(point(-80.0), 5f, 28_000, 0f, false)!!.shouldAlert)
        // Starting or restarting after the guard does not require observing circle entry.
        assertTrue(DestinationAlert(guardedLeg).update(point(-80.0), 5f, 0, 0f, false)!!.shouldAlert)
        assertFalse(alert.update(point(-70.0), 5f, 32_000, 0f, true)!!.shouldAlert)
    }

    @Test fun closeStopsCannotCompleteLegWhileWaitingAtPreviousStop() {
        val guardedLeg = leg.copy(intermediate = listOf(target.copy(lat = point(-60.0).lat)))
        val alert = DestinationAlert(guardedLeg)
        assertFalse(alert.update(point(-60.0), 5f, 0, 0f, false)!!.shouldAlert)
        assertFalse(alert.update(point(-60.0), 5f, 5_000, 0f, false)!!.arrived)
        assertTrue(alert.update(point(-20.0), 5f, 15_000, 0f, false)!!.shouldAlert)
        assertTrue(alert.update(point(-20.0), 5f, 20_000, 0f, true)!!.arrived)
    }

    @Test fun guardUsesLastStopOrBoardingStopAndScalesForCloseStops() {
        assertEquals(leg.from, destinationGuard(leg)!!.stop)
        assertEquals(75.0, destinationGuard(leg)!!.radius, 0.0)
        val prior = target.copy(lat = point(-60.0).lat)
        val guard = destinationGuard(leg.copy(intermediate = listOf(leg.from, prior)))!!
        assertEquals(prior, guard.stop)
        assertEquals(20.0, guard.radius, 0.1)
        assertNull(destinationGuard(leg.copy(from = target)))
    }

    @Test fun samplingTightensBeforeReachingTheCircleEvenAtMaximumSpeed() {
        for (mode in listOf("BUS", "COACH", "RAIL")) {
            val radius = destinationAlertRadius(mode)
            val speed = trackingMaxSpeed(mode)
            assertEquals(20_000L, destinationLocationInterval(mode, radius + speed * 200))
            assertEquals(10_000L, destinationLocationInterval(mode, radius + speed * 100))
            assertEquals(5_000L, destinationLocationInterval(mode, radius + speed * 30))
            assertEquals(2_000L, destinationLocationInterval(mode, radius + speed * 10))
            assertEquals(2_000L, destinationLocationInterval(mode, radius))
            assertEquals(5_000L, destinationLocationInterval(mode, null))
            assertEquals(5_000L, destinationLocationInterval(mode, Double.NaN))
        }
    }
}
