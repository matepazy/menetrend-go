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
                JSONObject().put("gtfsId", id).put("vehiclePositions", JSONArray().put(JSONObject()
                    .put("vehicleId", "test:vehicle").put("vehicleModel", "FLIRT").put("licensePlate", "415-001")
                    .put("lastUpdated", now / 1000)))
                    .put("stoptimes", JSONArray().put(JSONObject()
                    .put("serviceDay", midnight).put("scheduledArrival", seconds).put("scheduledDeparture", seconds)
                    .put("realtimeArrival", seconds + 60).put("realtimeDeparture", seconds + 60).put("realtime", live)
                    .put("stop", JSONObject().put("gtfsId", "end").put("name", "Menetrendi megálló"))))
            })
        } } }
        fun open() {
            compose.waitUntil(5000) { compose.onAllNodesWithText("Célállomás").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Célállomás").performClick()
            compose.waitUntil(5000) {
                compose.onAllNodes(hasContentDescription("Járat részleteinek bezárása") or
                    hasContentDescription("Jármű részleteinek bezárása")).fetchSemanticsNodes().isNotEmpty()
            }
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
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Jármű részleteinek bezárása").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Élő követés · következő megállók").assertDoesNotExist()
        compose.onNodeWithText("Menetrendi megálló → Menetrendi megálló").assertIsDisplayed()
        compose.onNodeWithText("1 perc késés").assertIsDisplayed()
        compose.onNodeWithText("FLIRT").assertHasClickAction()
        compose.onNodeWithText("415-001").assertIsDisplayed()
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
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Jármű részleteinek bezárása").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Menetrendi megálló").assertIsDisplayed()
        compose.onNodeWithText("Menetrendi megálló → Menetrendi megálló").performTouchInput { swipeDown(startY = centerY, endY = centerY + 1100f) }
        compose.waitForIdle()
        // Dismiss using the handle area if the first drag was consumed by content.
        if (compose.onAllNodesWithContentDescription("Jármű részleteinek bezárása").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("Jármű részleteinek bezárása").performTouchInput { swipeDown(endY = 1800f) }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Jármű részleteinek bezárása").fetchSemanticsNodes().isEmpty() }
        assertBoard()
        compose.runOnIdle { assertTrue(requests.all { it == "test:trip|2026-10-31" }) }
    }
}
