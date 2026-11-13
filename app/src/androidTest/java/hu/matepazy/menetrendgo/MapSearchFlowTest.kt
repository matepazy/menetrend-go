package hu.matepazy.menetrendgo

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class MapSearchFlowTest {
    @get:Rule val compose = createComposeRule()
    private val station = Station("stop:keleti", "Budapest, Keleti pályaudvar", 47.5, 19.08, modes = listOf("RAIL"))
    private val vehicle = Vehicle("trip:1", "IC 70", "Szeged", "RAIL", 47.4, 19.1, model = "FLIRT")
    private var vehiclePicks = 0
    private var stopPicks = 0
    private var pickedStop: Station? = null
    private var selectedVehicle by mutableStateOf<Vehicle?>(null)

    private fun show(search: suspend (String) -> List<Station> = { listOf(station) },
                     cached: suspend (String) -> List<Station> = { emptyList() }, theme: String = "light") {
        compose.setContent {
            var query by remember { mutableStateOf("") }
            var focused by remember { mutableStateOf(false) }
            MenetrendTheme(theme) {
                StationFocusOverlay {
                    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                        MapSearch(query, { query = it }, false, {}, focused = focused,
                            onFocusChange = { focused = it }, vehicles = listOf(vehicle),
                            searchStops = search, cachedStops = cached,
                            selectedVehicleName = selectedVehicle?.name,
                            onClearSelection = { selectedVehicle = null },
                            onVehicle = { vehiclePicks++; selectedVehicle = it }, onStop = { stopPicks++; pickedStop = it })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Keresés a térképen").performClick()
        compose.onNodeWithContentDescription("Jármű vagy megálló keresése").assertIsFocused()
    }
    private fun type(value: String) = compose.onNodeWithContentDescription("Jármű vagy megálló keresése").performTextReplacement(value)
    private fun waitFor(text: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "map-search").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun typingDoesNotSelectAndVehicleSelectionClosesSearch() {
        show()
        type("IC70")
        waitFor("IC 70")
        assertEquals(0, vehiclePicks)
        assertEquals(0, stopPicks)
        compose.onNodeWithText("IC 70").performClick()
        assertEquals(1, vehiclePicks)
        compose.onNodeWithText("IC 70").assertIsDisplayed()
        compose.onNodeWithContentDescription("Keresés törlése").assertIsDisplayed()
        assertEquals(vehicle, selectedVehicle)
        capture("selected-vehicle")
        compose.onNodeWithText("IC 70").performClick()
        compose.onNodeWithContentDescription("Keresés bezárása").performClick()
        assertEquals(vehicle, selectedVehicle)
        compose.onNodeWithContentDescription("Keresés törlése").performClick()
        compose.runOnIdle { assertNull(selectedVehicle) }
        compose.onNodeWithContentDescription("Keresés a térképen").assertIsDisplayed()
    }

    @Test fun repeatedExpansionAndDismissalRestoreAnEmptyFocusedEditor() {
        show()
        repeat(3) {
            type("Keleti")
            compose.onNodeWithContentDescription("Keresés bezárása").performClick()
            compose.onNodeWithContentDescription("Keresés a térképen").performClick()
            compose.onNodeWithContentDescription("Jármű vagy megálló keresése")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
                .assertIsFocused()
        }
    }

    @Test fun longResultsStayCompactAndLastResultRemainsSelectable() {
        val stops = (1..25).map { station.copy(id = "stop:$it", name = "Keleti megálló $it") }
        show(search = { stops })
        type("Keleti")
        waitFor("Megállók és állomások · 25")
        val panel = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Térkép keresése"))
        val rootHeight = compose.onRoot().fetchSemanticsNode().boundsInRoot.height
        assertTrue(panel.fetchSemanticsNode().boundsInRoot.height < rootHeight * .7f)
        compose.onNodeWithText(stops.last().name).assertDoesNotExist()
        capture("compact-results")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(stops.last().name))
        compose.onNodeWithText(stops.last().name).performClick()
        compose.runOnIdle { assertEquals(stops.last(), pickedStop) }
    }

    @Test fun stopSelectionRetainsIdentityAndClearKeepsEditorOpen() {
        show()
        type("Keleti")
        waitFor(station.name)
        capture("light-results")
        compose.onNodeWithContentDescription("Keresés törlése").performClick()
        compose.onNodeWithContentDescription("Jármű vagy megálló keresése")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))).assertIsFocused()
        type("Keleti")
        waitFor(station.name)
        compose.onNodeWithText(station.name).performClick()
        assertEquals(1, stopPicks)
        assertEquals(station, pickedStop)
        assertEquals(0, vehiclePicks)
    }

    @Test fun cachedStopsSurviveFailureAndRetryRecovers() {
        var calls = 0
        show(search = { if (++calls == 1) error("offline") else listOf(station) }, cached = { listOf(station) })
        type("Keleti")
        waitFor("A megállók frissítése nem sikerült. A mentett találatok láthatók.")
        compose.onNodeWithText(station.name).assertIsDisplayed()
        compose.onNodeWithText("Újrapróbálás").performClick()
        waitFor("Megállók és állomások · 1")
        assertEquals(2, calls)
    }

    @Test fun obsoleteStopResponseCannotReplaceNewQueryAndDarkEmptyStateRenders() {
        show(search = { query -> if (query == "old") { delay(1500); listOf(station) } else emptyList() }, theme = "dark")
        type("old")
        type("new")
        waitFor("Nincs találat. Próbálj másik nevet vagy járatszámot.")
        compose.onNodeWithText(station.name).assertDoesNotExist()
        capture("dark-empty")
    }
}
