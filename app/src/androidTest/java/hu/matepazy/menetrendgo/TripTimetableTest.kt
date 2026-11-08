package hu.matepazy.menetrendgo

import android.app.Application
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

    @Test fun fullTimetableShowsScheduledAndLiveTimesTogether() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ViewModelStore()
        lateinit var vm: AppState
        compose.runOnIdle {
            vm = AppState(context.applicationContext as Application).also { store.put("trip", it) }
            val detail = JSONObject("""{"tripHeadsign":"Célállomás","stoptimes":[
                {"scheduledDeparture":36000,"realtimeDeparture":36300,"realtime":true,
                 "stop":{"name":"Késő megálló","platformCode":"2"}},
                {"scheduledDeparture":36600,"realtimeDeparture":36600,"realtime":true,
                 "stop":{"name":"Pontos megálló"}},
                {"scheduledArrival":37200,"realtimeArrival":37500,"realtime":false,
                 "stop":{"name":"Menetrendi végállomás"}}
            ]}""")
            val field = AppState::class.java.getDeclaredField("detail\$delegate").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            (field.get(vm) as MutableState<JSONObject?>).value = detail
        }
        try {
            compose.setContent { MenetrendTheme("light") { TripScreen(vm, "test", "2026-11-08", {}, {}) } }
            compose.onNodeWithText("10:00").assertIsDisplayed()
            compose.onNodeWithText("10:05").assertIsDisplayed()
            compose.onAllNodesWithText("10:10").assertCountEquals(1)
            compose.onNodeWithText("10:20").assertIsDisplayed()
            compose.onNodeWithText("10:25").assertDoesNotExist()
            compose.onNodeWithText("Vágány / kocsiállás: 2").assertIsDisplayed()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val file = java.io.File(context.getExternalFilesDir(null), "trip-timetable-times.png")
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }
}
