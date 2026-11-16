package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class VehiclePassedStopsTest {
    @get:Rule val compose = createComposeRule()

    /** Three stops are behind the vehicle and three ahead, timed around the current clock. */
    private fun trip(now: Long): JSONObject {
        val day = Instant.ofEpochMilli(now - 2 * 3_600_000).atZone(Budapest).toLocalDate()
        val serviceDay = day.atStartOfDay(Budapest).toEpochSecond()
        val base = now / 1000 - serviceDay
        val stops = JSONArray()
        listOf(-30, -20, -10, 5, 10, 15).forEachIndexed { index, minutes ->
            val name = if (minutes < 0) "Elhagyott ${index + 1}" else "Következő ${index + 1}"
            stops.put(JSONObject().put("serviceDay", serviceDay).put("realtime", false)
                .put("scheduledArrival", base + minutes * 60).put("scheduledDeparture", base + minutes * 60)
                .put("stop", JSONObject().put("name", name).put("gtfsId", "s$index")))
        }
        return JSONObject().put("gtfsId", "trip").put("stoptimes", stops)
    }

    @Test fun passedStopsUnfoldInPlaceWithoutMovingTheSheet() {
        val now = System.currentTimeMillis()
        val detail = trip(now)
        compose.setContent {
            MenetrendTheme("light") {
                MapSheetScaffold(open = true, onDismissRequest = {}, restingFraction = .5f, sheetContent = {
                    VehicleDrawerContent(Vehicle("trip", "47", "Deák Ferenc tér", "TRAM", 47.5, 19.0),
                        detail, java.time.LocalDate.now(Budapest).toString(), now, null, {}, {})
                }) { Box(Modifier.fillMaxSize()) }
            }
        }
        val close = compose.onNodeWithContentDescription("Jármű részleteinek bezárása")
        val toggle = compose.onNodeWithText("3 elhagyott megálló")
        compose.waitForIdle()
        val restingTop = close.fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("Elhagyott 2", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Következő megálló", useUnmergedTree = true).assertIsDisplayed()

        toggle.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Elhagyott 2", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("Unfolding passed stops must not move the sheet", restingTop, close.fetchSemanticsNode().boundsInRoot.top, 1f)

        toggle.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Elhagyott 2", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Következő megálló", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("Folding passed stops must not move the sheet", restingTop, close.fetchSemanticsNode().boundsInRoot.top, 1f)
    }
}
