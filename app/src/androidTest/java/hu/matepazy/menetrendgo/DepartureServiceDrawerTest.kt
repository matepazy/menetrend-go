package hu.matepazy.menetrendgo

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DepartureServiceDrawerTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun serviceDetailsCloseBackToBoardWithCloseBackAndSwipe() {
        val station = Station("test:station", "Induló állomás", 47.0, 19.0)
        val now = System.currentTimeMillis()
        val departure = StopDeparture("test:trip", "Célállomás", ServiceTime(now + 600000, now + 600000, false), "2", false,
            Leg("RAIL", now, now, station, station, "S30", "test:trip", false, 0, emptyList(), ""), "2026-10-31")
        var boardDismissed = false
        var live = false
        var fail = false
        var dark by mutableStateOf(false)
        val requests = mutableListOf<String>()
        compose.setContent { MenetrendTheme(if (dark) "dark" else "light") {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (dark) 1.3f else 1f)) {
            StopBoardDrawer(station, { boardDismissed = true }, fetch = { listOf(departure) }, fetchTrip = { id, day ->
                requests += "$id|$day"
                if (fail) error("Teszt kapcsolati hiba")
                val serviceDate = LocalDate.now(Budapest)
                val midnight = serviceDate.atStartOfDay(Budapest).toEpochSecond()
                val seconds = (now / 1000 - midnight + 600).toInt()
                JSONObject().put("gtfsId", id).put("stoptimes", JSONArray().put(JSONObject()
                    .put("serviceDay", midnight).put("scheduledArrival", seconds).put("scheduledDeparture", seconds)
                    .put("realtimeArrival", seconds + 60).put("realtimeDeparture", seconds + 60).put("realtime", live)
                    .put("stop", JSONObject().put("gtfsId", "end").put("name", "Menetrendi megálló"))))
            })
        } } }
        fun open() {
            compose.waitUntil(5000) { compose.onAllNodesWithText("Célállomás").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Célállomás").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Járat részleteinek bezárása").fetchSemanticsNodes().isNotEmpty() }
        }
        fun assertBoard() {
            compose.onNodeWithContentDescription("Indulási tábla bezárása").assertIsDisplayed()
            compose.runOnIdle { assertFalse(boardDismissed) }
        }
        open()
        compose.onNodeWithText("Tervezett menetrend · 2026-10-31").assertIsDisplayed()
        compose.onNodeWithText("Menetrendi megálló").assertIsDisplayed()
        capture("departure-service-scheduled-phone")
        compose.onNodeWithContentDescription("Járat részleteinek bezárása").performClick()
        assertBoard()
        compose.runOnIdle { live = true; dark = true }
        open()
        compose.onNodeWithText("Élő követés · következő megállók").assertIsDisplayed()
        compose.onNodeWithText("Következő megálló").assertIsDisplayed()
        capture("departure-service-live-dark-large-phone")
        pressBack()
        assertBoard()
        capture("departure-service-returned-board-phone")
        compose.runOnIdle { fail = true }
        open()
        compose.onNodeWithText("Teszt kapcsolati hiba").assertIsDisplayed()
        compose.runOnIdle { fail = false }
        compose.onNodeWithText("Újrapróbálás").performClick()
        compose.onNodeWithText("Menetrendi megálló").assertIsDisplayed()
        compose.onNodeWithText("Élő követés · következő megállók").performTouchInput { swipeDown(startY = centerY, endY = centerY + 1100f) }
        compose.waitForIdle()
        // Dismiss using the handle area if the first drag was consumed by content.
        if (compose.onAllNodesWithContentDescription("Járat részleteinek bezárása").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("Járat részleteinek bezárása").performTouchInput { swipeDown(endY = 1800f) }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Járat részleteinek bezárása").fetchSemanticsNodes().isEmpty() }
        assertBoard()
        compose.runOnIdle { assertTrue(requests.all { it == "test:trip|2026-10-31" }) }
    }
}
