package hu.matepazy.menetrendgo

import android.app.Application
import android.app.NotificationManager
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class JourneyTrackingUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val store = ViewModelStore()
    private val now = System.currentTimeMillis()
    private val from = Station("from", "Budapest, Keleti pályaudvar", 47.50, 19.08)
    private val transfer = Station("transfer", "Hatvan, vasútállomás", 47.67, 19.67)
    private val destination = Station("to", "Eger, vasútállomás", 47.89, 20.38)
    private val first = Leg("RAIL", now, now + 40 * 60_000, from, transfer, "IR 80", "", false, 0, emptyList(), "")
    private val second = first.copy(from = transfer, to = destination, route = "IR 87", start = now + 45 * 60_000, end = now + 90 * 60_000)
    private val journey = Journey(5400, listOf(first, second))

    @Before fun setup() {
        JourneyTracking.stop(app)
        shell("pm grant ${app.packageName} android.permission.ACCESS_COARSE_LOCATION")
        shell("pm grant ${app.packageName} android.permission.ACCESS_FINE_LOCATION")
        shell("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS")
    }
    @After fun cleanup() { JourneyTracking.stop(app); store.clear() }

    private fun showJourney(route: Journey = journey) {
        val vm = AppState(app).also { store.put("tracking-ui", it) }
        compose.setContent { MenetrendTheme("light") {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) { JourneyScreen(vm, route, {}, {}, {}) }
                }
            }
        } }
    }

    private fun openAlerts(stop: Station = transfer) {
        compose.onNodeWithContentDescription("Leszállási jelzés: ${stop.name}").performScrollTo().performClick()
    }
    private fun startAlerts() {
        openAlerts()
        compose.onNodeWithText("Jelzés indítása").performScrollTo().performClick()
        compose.waitUntil(8_000) { JourneyTracking.state.value.running }
        compose.onNodeWithContentDescription("Leszállási jelzés bezárása").assertDoesNotExist()
    }

    @Test fun startsForegroundServiceAndStopsFromNotification() {
        showJourney()
        startAlerts()
        val manager = app.getSystemService(NotificationManager::class.java)
        compose.waitUntil(5_000) { manager.activeNotifications.any { it.id == JourneyTrackingService.ONGOING_ID } }
        screenshot("tracking-refined-running")
        val notification = manager.activeNotifications.single { it.id == JourneyTrackingService.ONGOING_ID }.notification
        assertTrue(notification.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE != 0)
        notification.actions.single { it.title == "Leállítás" }.actionIntent.send()
        compose.waitUntil(5_000) { JourneyTracking.state.value.session == null }
        compose.waitUntil(5_000) { manager.activeNotifications.none { it.id == JourneyTrackingService.ONGOING_ID } }
        assertNull(JourneyTracking.saved(app))
    }

    @Test fun serviceRecreationRestoresSessionAndAlertState() {
        showJourney()
        compose.runOnIdle { JourneyTracking.start(app, TrackingSession(journey, 0, alerted = true, progress = 100.0)) }
        compose.waitUntil(8_000) { JourneyTracking.state.value.running }
        app.stopService(android.content.Intent(app, JourneyTrackingService::class.java))
        compose.waitUntil(5_000) { !JourneyTracking.state.value.running }
        assertTrue(JourneyTracking.saved(app)!!.alerted)
        compose.runOnIdle { JourneyTracking.publish(TrackingView()); JourneyTracking.load(app) }
        openAlerts()
        compose.onNodeWithText("Jelzés folytatása").performScrollTo().performClick()
        compose.waitUntil(8_000) { JourneyTracking.state.value.running }
        assertTrue(JourneyTracking.state.value.session!!.alerted)
        assertEquals(100.0, JourneyTracking.state.value.session!!.progress!!, 0.01)
    }

    @Test fun timetableLeadsAndSetupIsScopedToTheTappedStop() {
        val vm = AppState(app).also { store.put("tracking-ui", it) }
        var dark by mutableStateOf(false)
        var large by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, if (large) 1.3f else 1f)) {
                MenetrendTheme(if (dark) "dark" else "light") {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) { JourneyScreen(vm, journey, {}, {}, {}) }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Budapest").assertIsDisplayed()
        compose.onNodeWithText("IR 80").assertIsDisplayed()
        compose.onNodeWithText("Jelzés indítása").assertDoesNotExist()
        screenshot("tracking-refined-schedule-light")
        openAlerts()
        compose.onNodeWithText(transfer.name).assertIsDisplayed()
        compose.onNodeWithText("3 perc").performClick().assertIsSelected()
        screenshot("tracking-refined-sheet-light")
        compose.onNodeWithContentDescription("Leszállási jelzés bezárása").performClick()
        compose.runOnIdle { dark = true; large = true }
        compose.onNodeWithText("Utazás részletei").performScrollTo()
        screenshot("tracking-refined-schedule-dark-large")
        openAlerts(destination)
        compose.onNodeWithText(destination.name).assertIsDisplayed()
        compose.onNodeWithText("Jelzés indítása").performScrollTo().assertIsDisplayed()
        screenshot("tracking-refined-sheet-dark-large")
        compose.onNodeWithContentDescription("Leszállási jelzés bezárása").performClick()
        compose.runOnIdle {
            JourneyTracking.publish(TrackingView(TrackingSession(journey, 0, alerted = true, awaitingBoarding = true), true,
                "Szállj át, majd erősítsd meg a felszállást.", TrackingEstimate(1000.0, 50.0, 0, 10, false, true)))
        }
        compose.onNodeWithText("Felszálltam").performScrollTo().assertIsDisplayed()
        screenshot("tracking-refined-transfer-dark-large")
        openAlerts()
        compose.onNodeWithText("Kb. 1 perc · helyzet alapján").assertDoesNotExist()
        compose.onNodeWithText("Jelzés leállítása").performScrollTo().performClick()
        assertNull(JourneyTracking.state.value.session)
    }

    @Test fun sharedTransferStopHasOneContextualBell() {
        showJourney(journey.copy(legs = listOf(first, second.copy(start = first.end))))
        compose.onAllNodesWithContentDescription("Leszállási jelzés: ${transfer.name}").assertCountEquals(1)
        openAlerts()
        compose.onNodeWithText(transfer.name).assertIsDisplayed()
    }

    @Test fun anotherStopsBellKeepsItsDestinationWhenAnAlertIsAlreadyActive() {
        showJourney()
        compose.runOnIdle { JourneyTracking.publish(TrackingView(TrackingSession(journey, 0), true, "Helymeghatározás…")) }
        openAlerts(destination)
        compose.onNodeWithText(destination.name).assertIsDisplayed()
        compose.onNodeWithText("Már aktív a jelzés: IR 80 → ${transfer.name}. Új jelzés előtt állítsd le a jelenlegit.").assertIsDisplayed()
        compose.onNodeWithText("Jelzés indítása").assertDoesNotExist()
        screenshot("tracking-refined-other-stop-active")
        compose.onNodeWithText("Aktív jelzés leállítása").performClick()
        compose.onNodeWithText(destination.name).assertIsDisplayed()
        compose.onNodeWithText("Jelzés indítása").assertIsDisplayed()
        assertNull(JourneyTracking.state.value.session)
    }

    @Test fun homeKeepsPlanningBeforeTheActiveAlertEntry() {
        val vm = AppState(app).also { store.put("tracking-home", it) }
        vm.from = from; vm.to = destination
        var opened: Journey? = null
        JourneyTracking.publish(TrackingView(TrackingSession(journey, 0), true, "Helymeghatározás…"))
        compose.setContent { MenetrendTheme("light") {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                        SearchScreen(vm, {}, { opened = it }, {})
                    }
                }
            }
        } }
        val searchButton = compose.onNodeWithText("Járatok keresése").fetchSemanticsNode().boundsInRoot
        val activeEntry = compose.onNodeWithText("Leszállási jelzés aktív").fetchSemanticsNode().boundsInRoot
        assertTrue(searchButton.bottom <= activeEntry.top)
        screenshot("tracking-refined-home")
        compose.onNodeWithText("Leszállási jelzés aktív").performClick()
        compose.runOnIdle { assertEquals(journey.key, opened?.key) }
    }

    @Test fun futureDepartureExplainsWhyTrackingCannotStartYet() {
        showJourney(Journey(2400, listOf(first.copy(start = now + 2 * 60 * 60_000, end = now + 3 * 60 * 60_000))))
        openAlerts()
        compose.onNodeWithText("Jelzés indítása").performScrollTo().performClick()
        compose.onNodeWithText("A jelzést felszálláskor indítsd el, legkorábban indulás előtt 30 perccel.").performScrollTo().assertIsDisplayed()
        assertFalse(JourneyTracking.state.value.running)
    }

    @Test fun locationAlertsWhileScreenOffThenTracksNextLegAndFinishes() {
        val provider = "menetrendgo_test"
        shell("appops set com.android.shell android:mock_location allow")
        shell("cmd location providers add-test-provider $provider")
        shell("cmd location providers set-test-provider-enabled $provider true")
        try {
            showJourney()
            startAlerts()
            val manager = app.getSystemService(NotificationManager::class.java)
            shell("input keyevent 223")
            shell("cmd location providers set-test-provider-location $provider --location 47.669,19.67 --accuracy 5")
            compose.waitUntil(10_000) { JourneyTracking.state.value.session?.alerted == true }
            compose.waitUntil(5_000) { manager.activeNotifications.any { it.id == JourneyTrackingService.ALERT_ID } }
            val firstAlert = manager.activeNotifications.single { it.id == JourneyTrackingService.ALERT_ID }.postTime
            Thread.sleep(5_100)
            shell("cmd location providers set-test-provider-location $provider --location 47.67,19.67 --accuracy 5")
            compose.waitUntil(10_000) { JourneyTracking.state.value.session?.awaitingBoarding == true }
            assertEquals(firstAlert, manager.activeNotifications.single { it.id == JourneyTrackingService.ALERT_ID }.postTime)
            compose.waitUntil(5_000) {
                manager.activeNotifications.any { it.id == JourneyTrackingService.ONGOING_ID && it.notification.actions.any { action -> action.title == "Felszálltam" } }
            }
            assertNull(JourneyTracking.state.value.estimate)
            shell("input keyevent 224")
            shell("wm dismiss-keyguard")
            compose.onNodeWithText("Felszálltam").performScrollTo().assertIsDisplayed()
            screenshot("tracking-refined-real-transfer")
            compose.onNodeWithText("Felszálltam").performClick()
            compose.waitUntil(8_000) { JourneyTracking.state.value.session?.legIndex == 1 }
            assertFalse(JourneyTracking.state.value.session!!.alerted)
            Thread.sleep(5_100)
            shell("cmd location providers set-test-provider-location $provider --location 47.89,20.38 --accuracy 5")
            compose.waitUntil(10_000) { JourneyTracking.state.value.session == null }
            compose.waitUntil(5_000) { manager.activeNotifications.any { it.id == JourneyTrackingService.ALERT_ID } }
            assertNull(JourneyTracking.saved(app))
        } finally {
            shell("input keyevent 224"); shell("wm dismiss-keyguard")
            shell("cmd location providers remove-test-provider $provider")
            shell("appops set com.android.shell android:mock_location default")
        }
    }

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        // Compose idleness can precede the emulator compositor's final presented frame.
        Thread.sleep(300)
        File(app.getExternalFilesDir(null), "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
