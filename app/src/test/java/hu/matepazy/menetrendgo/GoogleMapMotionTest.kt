package hu.matepazy.menetrendgo

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Test

class GoogleMapMotionTest {
    @Test fun followsBendInsteadOfCuttingAcrossCorner() {
        val points=listOf(GeoPoint(47.0,19.0),GeoPoint(47.0,19.01),GeoPoint(47.01,19.01))
        val movement=googleVehicleMovement(points,LatLng(47.0,19.005),LatLng(47.005,19.01))!!
        val sample=movement(.5f)
        assertTrue(sample.latitude==47.0 || sample.longitude==19.01)
        assertEquals(47.005,movement(1f).latitude,.00001)
        assertEquals(19.01,movement(1f).longitude,.00001)
    }
    @Test fun rejectsBackwardJitterOffRouteAndLargeJumps() {
        val points=listOf(GeoPoint(47.0,19.0),GeoPoint(47.0,19.5))
        assertNull(googleVehicleMovement(points,LatLng(47.0,19.01),LatLng(47.0,19.0)))
        assertNull(googleVehicleMovement(points,LatLng(48.0,19.0),LatLng(48.0,19.01)))
        assertNull(googleVehicleMovement(points,LatLng(47.0,19.0),LatLng(47.0,19.5)))
        assertNull(googleVehicleMovement(emptyList(),LatLng(47.0,19.0),LatLng(47.0,19.01)))
    }
    @Test fun interruptedAnimationCanContinueFromDisplayedPosition() {
        val points=listOf(GeoPoint(47.0,19.0),GeoPoint(47.0,19.1))
        val first=googleVehicleMovement(points,LatLng(47.0,19.0),LatLng(47.0,19.01))!!
        val displayed=first(.4f)
        val second=googleVehicleMovement(points,displayed,LatLng(47.0,19.02))!!
        assertEquals(displayed.latitude,second(0f).latitude,.000001)
        assertEquals(displayed.longitude,second(0f).longitude,.000001)
    }
}
