package hu.matepazy.menetrendgo

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class JourneyTransfersUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val store = ViewModelStore()
    @After fun cleanup() { store.clear() }

    @Test fun tightConnectionIsOneLineInLightTheme() = showJourney(false, 1f)
    @Test fun tightConnectionWrapsInDarkThemeWithLargeText() = showJourney(true, 1.3f)

    @Test fun walkingTransferShowsEachStopOnce() = showWalkingTransfer(false, 1f)
    @Test fun walkingTransferWrapsInDarkThemeWithLargeText() = showWalkingTransfer(true, 1.5f)

    @Test fun itineraryKeepsDetailsVisibleInLightTheme() = showItinerary(false, 1f)
    @Test fun itineraryKeepsDetailsVisibleWithLargeDarkText() = showItinerary(true, 1.5f)
    @Test fun longItineraryRouteWrapsWithLargeText() = showItinerary(true, 1.5f, true)

    private fun showItinerary(dark: Boolean, scale: Float, longRoute: Boolean = false) {
        val now = System.currentTimeMillis()
        val origin = Station("origin", "Budapest-Déli", 47.5, 19.0, platform="2")
        val destination = Station("destination", "Székesfehérvár", 47.19, 18.42, platform="1")
        val middle = Station("middle", "Budapest-Kelenföld", 47.46, 19.02, platform="4")
        val route = if(longRoute) "9708 Gyorsvonat hosszú járatnévvel" else "9708 Gyorsvonat"
        val leg = Leg("RAIL", now, now+45*60_000, origin, destination,
            route, "fixture-trip", true, 180, listOf(middle), "",
            category="sebesvonat", headsign="Tapolca", arrivalDelay=180)
        val journey = Journey(2700, listOf(leg), fetchedAt=now)
        val vm = AppState(app).also { store.put("itinerary", it) }
        var openedTrip: Leg? = null
        compose.setContent { MenetrendTheme(if(dark) "dark" else "light") {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                Surface(Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.background) {
                    JourneyScreen(vm, journey, {}, { openedTrip=it }, {}, refreshTransfers=false)
                }
            }
        } }
        compose.onNodeWithText("Tapolca").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Budapest-Kelenföld").assertDoesNotExist()
        compose.onNodeWithText("Járműtípus nem érhető el").assertDoesNotExist()
        compose.onNodeWithText("2. vágány").assertExists()
        compose.onNodeWithText("1. vágány").assertExists()
        compose.onNodeWithText(timeLabel(leg.departureTime.scheduled)).assertExists()
        compose.onNodeWithText(timeLabel(leg.start)).assertExists()
        if(longRoute) {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(route, useUnmergedTree=true).performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("Long route names must wrap", layouts.single().lineCount > 1)
            assertFalse("The full route name must remain visible", layouts.single().hasVisualOverflow)
        }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(app.getExternalFilesDir(null), "itinerary-${if(longRoute) "long-route" else if(dark) "dark-large" else "light"}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        compose.onNodeWithText("1 köztes megálló").performClick()
        compose.onNodeWithText("4. vágány").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 köztes megálló").performClick()
        compose.onNodeWithText("Budapest-Kelenföld").assertDoesNotExist()
        compose.onNodeWithText("Tapolca").assertExists()
        compose.onNodeWithText("Teljes járatmenetrend").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(leg, openedTrip) }
    }

    @Test fun intermediateStopsKeepArrivalAndTrackTogetherThroughoutAnimation() {
        val now = System.currentTimeMillis()
        val origin = Station("origin", "Indulóhely", 47.5, 19.0)
        val destination = Station("destination", "Végállomás", 47.6, 19.1)
        val stops = List(2) { origin.copy(id="middle-$it", name="Köztes hely $it") }
        val leg = Leg("BUS", now, now+12*60_000, origin, destination,
            "24", "test-trip", false, 0, stops, "")
        val journey = Journey(720, listOf(leg), fetchedAt=now)
        val vm = AppState(app).also { store.put("animation", it) }
        compose.setContent { MenetrendTheme("light") {
            Surface(Modifier.fillMaxSize()) {
                JourneyScreen(vm, journey, {}, {}, {}, refreshTransfers=false)
            }
        } }
        val toggle = compose.onNodeWithText("2 köztes megálló")
        val arrival = compose.onNodeWithText("Végállomás")
        val timetable = compose.onNodeWithText("Teljes járatmenetrend")
        compose.onNodeWithText("Köztes hely 0").assertDoesNotExist()
        toggle.performScrollTo()
        val collapsedTop = arrival.fetchSemanticsNode().boundsInRoot.top
        val restingGap = collapsedTop-timetable.fetchSemanticsNode().boundsInRoot.bottom
        compose.mainClock.autoAdvance = false

        fun checkGap() {
            val gap = arrival.fetchSemanticsNode().boundsInRoot.top-
                timetable.fetchSemanticsNode().boundsInRoot.bottom
            assertEquals("Arrival must follow the visible content without an empty track", restingGap, gap, 2f)
        }

        toggle.performClick()
        repeat(8) {
            compose.mainClock.advanceTimeBy(32)
            checkGap()
        }
        val expandedTop = arrival.fetchSemanticsNode().boundsInRoot.top
        assertTrue("Expanding stops must move the arrival down", expandedTop > collapsedTop)
        toggle.performClick()
        repeat(6) {
            compose.mainClock.advanceTimeBy(32)
            checkGap()
        }
        assertEquals(collapsedTop, arrival.fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.mainClock.autoAdvance = true
    }

    private fun showWalkingTransfer(dark: Boolean, scale: Float) {
        val now = System.currentTimeMillis() + 60 * 60_000
        val origin = Station("a", "Kelenföld", 47.46, 19.02)
        val arrival = Station("b", "Ferencváros", 47.47, 19.09, platform="4")
        val boarding = Station("c", "Budapest, Népliget autóbusz-pályaudvar", 47.47, 19.10, platform="13")
        val destination = Station("d", "Kecskemét", 46.9, 19.7)
        val first = Leg("RAIL", now, now+10*60_000, origin, arrival, "S36", "", false, 0, emptyList(), "")
        // Differing planner times must not create repeated transfer stops.
        val walk = first.copy(mode="WALK", start=first.end+60_000, end=first.end+16*60_000, from=arrival, to=boarding)
        val second = first.copy(mode="COACH", start=first.end+23*60_000, end=first.end+93*60_000,
            from=boarding, to=destination, route="1080")
        val journey = Journey(6180, listOf(first, walk, second), fetchedAt=now)
        val vm = AppState(app).also { store.put("walking", it) }
        compose.setContent { MenetrendTheme(if(dark) "dark" else "light") {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                Surface(Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.background) {
                    JourneyScreen(vm, journey, {}, {}, {}, refreshTransfers=false)
                }
            }
        } }
        compose.onAllNodesWithText("Ferencváros").assertCountEquals(1)
        compose.onAllNodesWithText("Népliget autóbusz-pályaudvar").assertCountEquals(1)
        compose.onNodeWithText("15 perc gyaloglás").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("4. vágány").assertExists()
        compose.onNodeWithText("13. kocsiállás").assertExists()
        compose.onNodeWithText(timeLabel(walk.start)).assertDoesNotExist()
        compose.onNodeWithText(timeLabel(walk.end)).assertDoesNotExist()
        val timeLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(timeLabel(first.end)).performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(timeLayouts) }
        assertEquals("Journey times must stay on one line at large font sizes", 1, timeLayouts.single().lineCount)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(app.getExternalFilesDir(null), "walking-${if(dark) "dark-large" else "light"}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun showJourney(dark: Boolean, scale: Float) {
        val now = System.currentTimeMillis()
        val origin = Station("a", "Budapest, Keleti pályaudvar", 47.5, 19.08)
        val arrival = Station("b", "Szolnok, vasútállomás", 47.17, 20.18, platform = "2")
        val boarding = arrival.copy(id="c", platform="5")
        val destination = Station("d", "Debrecen, vasútállomás", 47.52, 21.62)
        val first = Leg("RAIL", now, now+40*60_000, origin, arrival, "IR 80", "", true, 0, emptyList(), "")
        val walk = first.copy(mode="WALK", start=first.end, end=first.end+3*60_000, from=arrival, to=boarding)
        val second = first.copy(start=first.end+4*60_000, end=first.end+90*60_000, from=boarding, to=destination, route="IC 600")
        val journey = Journey(7800, listOf(first, walk, second), fetchedAt=now)
        val vm = AppState(app).also { store.put("transfers", it) }
        compose.setContent { MenetrendTheme(if (dark) "dark" else "light") {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                Surface(Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.background) {
                    JourneyScreen(vm, journey, {}, {}, {}, refreshTransfers=false)
                }
            }
        } }
        compose.onNodeWithText("Szoros átszállás").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("A gyaloglás után", substring=true).assertDoesNotExist()
        compose.onNodeWithText(timeLabel(walk.end)).assertDoesNotExist()
        compose.onNodeWithText("Másik útvonal keresése").assertDoesNotExist()
        compose.onNodeWithText("Lekérdezve:", substring=true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Átszállások frissítése").assertDoesNotExist()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(app.getExternalFilesDir(null), "transfer-${if(dark) "dark-large" else "light"}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun staleConnectionsStayInvisibleAndScheduledTightConnectionsStayMinimal() {
        val now = System.currentTimeMillis()
        val connection = TransferConnection(0, 1, 0)
        var scheduled by mutableStateOf(false)
        compose.setContent { MenetrendTheme("light") {
            Surface {
                val time = ServiceTime(now+60_000, now+60_000, false)
                val estimate = TransferEstimate(time, if(scheduled) now else 0)
                JourneyTransferNotice(connection, TransferTimes(estimate, estimate), now)
            }
        } }
        compose.onNodeWithText("Szoros átszállás").assertDoesNotExist()
        compose.runOnIdle { scheduled=true }
        compose.onNodeWithText("Szoros átszállás").assertIsDisplayed()
        compose.onNodeWithText("Legalább az egyik járathoz", substring=true).assertDoesNotExist()
    }

    @Test fun cancelledServiceNeverShowsAnAvailableTransferBuffer() {
        val now = System.currentTimeMillis()
        val arrival = TransferEstimate(ServiceTime(now+60_000, now+60_000, true), now)
        val departure = TransferEstimate(ServiceTime(now+600_000, now+600_000, true), now, cancelled=true)
        compose.setContent { MenetrendTheme("light") { Surface {
            JourneyTransferNotice(TransferConnection(0, 1, 0), TransferTimes(arrival, departure), now)
        } } }
        compose.onNodeWithText("Kimaradó csatlakozás").assertIsDisplayed()
        compose.onNodeWithText("A gyaloglás után", substring=true).assertDoesNotExist()
        compose.onNodeWithText("Másik útvonal keresése").assertDoesNotExist()
    }

    @Test fun nextReachableServiceIsOneSecondaryLineAndExpiresSilently() {
        val now = System.currentTimeMillis()
        val arrival = TransferEstimate(ServiceTime(now+60_000, now+60_000, true), now)
        val departure = arrival.copy(time=ServiceTime(now+120_000, now+120_000, true))
        val next = TransferAlternative(ServiceTime(now+600_000, now+600_000, false), "IC 600", now)
        var clock by mutableLongStateOf(now)
        compose.setContent { MenetrendTheme("dark") { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(AppLayout.PanelPadding)) {
                JourneyTransferNotice(TransferConnection(0, 1, 0), TransferTimes(arrival, departure, next), clock)
            }
        } } }
        compose.onNodeWithText("Következő: ${timeLabel(next.time.estimate)} · IC 600 (menetrend)").assertIsDisplayed()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(app.getExternalFilesDir(null), "transfer-next.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
        compose.runOnIdle { clock=now+TransferFreshnessMs+1 }
        compose.onNodeWithText("Következő:", substring=true).assertDoesNotExist()
    }
}
