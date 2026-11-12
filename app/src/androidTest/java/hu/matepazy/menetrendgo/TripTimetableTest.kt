package hu.matepazy.menetrendgo

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

class TripTimetableTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fullTimetableShowsScheduledAndLiveTimesTogether() = checkTimetable(false)

    @Test fun fullTimetableSupportsDarkLargeText() = checkTimetable(true)

    @Test fun movingVehicleAppearsOnTheLine() = checkTimetable(false,"IN_TRANSIT_TO")

    @Test fun movingVehicleStaysCenteredWithLargeText() = checkTimetable(true,"IN_TRANSIT_TO")

    @Test fun stoppedVehicleAppearsAtItsStop() = checkTimetable(true,"STOPPED_AT")

    private fun checkTimetable(dark: Boolean, status: String? = null) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ViewModelStore()
        lateinit var vm: AppState
        compose.runOnIdle {
            vm = AppState(context.applicationContext as Application).also { store.put("trip", it) }
            val detail = JSONObject("""{"tripHeadsign":"Célállomás","route":{"shortName":"S10","mode":"RAIL","color":"00A0E3"},"stoptimes":[
                {"scheduledDeparture":36000,"realtimeDeparture":36300,"realtime":true,
                 "stop":{"gtfsId":"origin","name":"Késő megálló","platformCode":"2"}},
                {"scheduledDeparture":36600,"realtimeDeparture":36600,"realtime":true,
                 "stop":{"gtfsId":"next","name":"Pontos megálló"}},
                {"scheduledArrival":37200,"realtimeArrival":37500,"realtime":false,
                 "stop":{"name":"Menetrendi végállomás"}},
                {"stop":{"name":"Időadat nélküli végállomás"}}
            ]}""")
            if(status!=null) {
                detail.put("vehiclePositions",org.json.JSONArray().put(JSONObject().put("vehicleId","vehicle")
                    .put("stopRelationship",JSONObject().put("status",status).put("stop",JSONObject().put("gtfsId","next")))))
                val vehiclesField=AppState::class.java.getDeclaredField("vehicles\$delegate").apply {isAccessible=true}
                @Suppress("UNCHECKED_CAST")
                (vehiclesField.get(vm) as MutableState<List<Vehicle>>).value = listOf(
                    Vehicle("test","S10","Célállomás","RAIL",47.5,19.0,
                        category=if(status=="STOPPED_AT") "Zónázó" else "InterRegio",vehicleId="vehicle"))
            }
            val field = AppState::class.java.getDeclaredField("detail\$delegate").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            (field.get(vm) as MutableState<JSONObject?>).value = detail
        }
        var openedMap=false
        try {
            compose.setContent {
                MenetrendTheme(if(dark) "dark" else "light") {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,if(dark) 1.3f else 1f)) {
                        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
                            TripScreen(vm, "test", "2026-11-08", {}, {openedMap=true})
                        }
                    }
                }
            }
            compose.onNodeWithText("10:00").assertIsDisplayed()
            compose.onNodeWithText("10:05").assertIsDisplayed()
            compose.onAllNodesWithText("10:10").assertCountEquals(1)
            compose.onNodeWithText("10:20").assertIsDisplayed()
            compose.onNodeWithText("10:25").assertDoesNotExist()
            compose.onNodeWithText("2. vágány").assertIsDisplayed()
            compose.onNodeWithText("Következő megálló").assertDoesNotExist()
            compose.onNodeWithText("Menetrendi érkezés").assertIsDisplayed()
            compose.onNodeWithText("Időadat nélküli végállomás").assertIsDisplayed()
            compose.onAllNodesWithText("Élő járműpozíció",substring=true).assertCountEquals(0)
            compose.onNodeWithText("Jármű a térképen").assertDoesNotExist()
            val marker=compose.onNodeWithContentDescription("Jármű a térképen")
            if(status==null) marker.assertDoesNotExist() else {
                marker.assertIsDisplayed()
                val markerY=marker.fetchSemanticsNode().boundsInRoot.center.y
                val stopY=compose.onNodeWithText("Pontos megálló").fetchSemanticsNode().boundsInRoot.center.y
                if(status=="STOPPED_AT") org.junit.Assert.assertEquals(stopY,markerY,12f)
                else {
                    val previousY=compose.onNodeWithText("Késő megálló").fetchSemanticsNode().boundsInRoot.center.y
                    org.junit.Assert.assertEquals("Moving vehicle must sit halfway between station dots",
                        (previousY+stopY)/2,markerY,12f)
                }
            }
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val file = java.io.File(context.getExternalFilesDir(null), "trip-timetable-${status ?: if(dark) "dark-large" else "light"}.png")
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            if(status!=null) {
                marker.performClick()
                compose.runOnIdle {org.junit.Assert.assertTrue(openedMap)}
            }
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }
}
