package hu.matepazy.menetrendgo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

    @Test fun busRowUsesPublicNumberInAppAndWidget() {
        val station = Station("test:station", "Székesfehérvár, autóbusz-állomás", 47.0, 19.0)
        val now = System.currentTimeMillis()
        val eta = now + 600_000
        val destination = "Kincsesbánya, Petőfi utca"
        val leg = Leg("COACH", eta, eta, station, station, "8092/113", "test:bus",
            false, 0, emptyList(), "", routeColor = "F5A800", routeShortName = "8092")
        val departure = StopDeparture("test:bus", destination, ServiceTime(eta, eta, false), "2", false, leg)
        var widget by mutableStateOf(false)
        var dark by mutableStateOf(false)
        compose.setContent {
            MenetrendTheme(if (dark) "dark" else "light") {
                if (widget) {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier
                            .fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                            .padding(20.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.ui.viewinterop.AndroidView(factory = { context ->
                            val theme = departureThemeContext(context, false)
                            val row = android.widget.RemoteViews(context.packageName, R.layout.stop_board_widget_row).apply {
                                applyDepartureTheme(theme)
                                bindWidgetService(theme, departure)
                                setTextViewText(R.id.widget_countdown, "10 perc")
                                setTextViewText(R.id.widget_detail, "2. vágány / állás")
                            }
                            android.widget.RemoteViews(context.packageName, R.layout.stop_board_widget).apply {
                                applyDepartureTheme(theme)
                                setTextViewText(R.id.widget_station, station.name)
                                setTextViewText(R.id.widget_status, "Frissítve most")
                                addView(R.id.widget_departures, row)
                            }.apply(context, null)
                        })
                    }
                } else {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (dark) 1.3f else 1f)) {
                        StopBoardDrawer(station, {}, fetch = { listOf(departure) })
                    }
                }
            }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText(destination).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Autóbusz 8092").assertIsDisplayed()
        compose.onNodeWithText("8092/113").assertDoesNotExist()
        capture("service-board-light")
        compose.runOnIdle { dark = true }
        compose.onNodeWithText(destination).assertIsDisplayed()
        capture("service-board-dark-large")
        compose.runOnIdle { widget = true; dark = false }
        capture("service-widget-light")
    }

    @Test fun serviceDetailsCloseBackToBoardWithCloseBackAndSwipe() {
        val station = Station("test:station", "Induló állomás", 47.0, 19.0)
        val now = System.currentTimeMillis()
        val departure = StopDeparture("test:trip", "Célállomás", ServiceTime(now + 600000, now + 600000, false), "2", false,
            Leg("RAIL", now, now, station, station, "S30", "test:trip", false, 0, emptyList(), ""), "2026-10-31")
        var boardDismissed = false
        var live = false
        var fail = false
        var hasPosition = false
        var mappedVehicle: Pair<Vehicle, String>? = null
        var dark by mutableStateOf(false)
        val requests = mutableListOf<String>()
        compose.setContent { MenetrendTheme(if (dark) "dark" else "light") {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (dark) 1.3f else 1f)) {
            StopBoardDrawer(station, { boardDismissed = true }, fetch = { listOf(departure) },
                showVehicleOnMap = { vehicle, day -> mappedVehicle = vehicle to day }, fetchTrip = { id, day ->
                requests += "$id|$day"
                if (fail) error("Teszt kapcsolati hiba")
                val serviceDate = LocalDate.now(Budapest)
                val midnight = serviceDate.atStartOfDay(Budapest).toEpochSecond()
                val seconds = (now / 1000 - midnight + 600).toInt()
                JSONObject().put("gtfsId", id).put("vehiclePositions", JSONArray().put(JSONObject()
                    .put("vehicleId", "test:vehicle").put("vehicleModel", "FLIRT").put("licensePlate", "415-001")
                    .apply { if (hasPosition) put("lat", 47.5).put("lon", 19.05) }
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
        compose.onNodeWithContentDescription("Jármű mutatása a térképen").assertDoesNotExist()
        compose.onNodeWithText("Menetrendi megálló").assertIsDisplayed()
        capture("departure-service-scheduled-phone")
        compose.onNodeWithContentDescription("Járat részleteinek bezárása").performClick()
        assertBoard()
        compose.runOnIdle { live = true; dark = true }
        open()
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Jármű részleteinek bezárása").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Élő követés · következő megállók").assertDoesNotExist()
        compose.onNodeWithText("Menetrendi\u00a0megálló").assertIsDisplayed()
        compose.onNodeWithContentDescription("Jármű mutatása a térképen").assertDoesNotExist()
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
        compose.onNodeWithText("Menetrendi\u00a0megálló").performTouchInput { swipeDown(startY = centerY, endY = centerY + 1100f) }
        compose.waitForIdle()
        // Dismiss using the handle area if the first drag was consumed by content.
        if (compose.onAllNodesWithContentDescription("Jármű részleteinek bezárása").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("Jármű részleteinek bezárása").performTouchInput { swipeDown(endY = 1800f) }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("Jármű részleteinek bezárása").fetchSemanticsNodes().isEmpty() }
        assertBoard()
        compose.runOnIdle { hasPosition = true }
        open()
        val map = compose.onNodeWithContentDescription("Jármű mutatása a térképen")
        val close = compose.onNodeWithContentDescription("Jármű részleteinek bezárása")
        map.assertIsDisplayed()
        assertTrue(map.fetchSemanticsNode().boundsInRoot.right < close.fetchSemanticsNode().boundsInRoot.left)
        capture("departure-service-map-action")
        map.performClick()
        assertBoard()
        compose.runOnIdle {
            val (vehicle, day) = requireNotNull(mappedVehicle)
            assertEquals(departure.tripId, vehicle.id)
            assertEquals("test:vehicle", vehicle.vehicleId)
            assertEquals(47.5, vehicle.lat, 0.0)
            assertEquals(19.05, vehicle.lon, 0.0)
            assertEquals(departure.serviceDay, day)
            assertTrue(requests.all { it == "test:trip|2026-10-31" })
        }
    }
}
