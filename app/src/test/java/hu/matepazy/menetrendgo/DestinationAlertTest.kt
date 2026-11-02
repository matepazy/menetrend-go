package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test

class DestinationAlertTest {
    private val target = Station("end", "Leszállóhely", 47.02, 19.0)
    private val leg = Leg("BUS", 0, 600_000, target.copy(lat = 47.0), target,
        "10", "trip", false, 0, emptyList(), "")
    private fun point(metersNorth: Double, metersEast: Double = 0.0) =
        TrackPoint(target.lat + metersNorth / 111_195, target.lon + metersEast / 75_800)

    @Test fun fixedCirclesMatchTheAgreedModeSizes() {
        assertEquals(100.0, destinationAlertRadius("BUS"), 0.0)
        assertEquals(100.0, destinationAlertRadius("TRAM"), 0.0)
        assertEquals(150.0, destinationAlertRadius("COACH"), 0.0)
        assertEquals(150.0, destinationAlertRadius("RAIL_REPLACEMENT_BUS"), 0.0)
        assertEquals(250.0, destinationAlertRadius("RAIL"), 0.0)
        assertEquals(250.0, destinationAlertRadius("SUBURBAN_RAILWAY"), 0.0)
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
        assertFalse(DestinationAlert(leg).update(point(101.0), 1f, 0, 35f, false)!!.shouldAlert)
        assertFalse(DestinationAlert(leg).update(point(90.0), 20f, 0, 35f, false)!!.shouldAlert)
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
