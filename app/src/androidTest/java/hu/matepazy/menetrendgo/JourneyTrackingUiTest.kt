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
    private var originalBeta: Boolean? = null
    private var originalDeveloper: Boolean? = null
    private val now = System.currentTimeMillis()
    private val from = Station("from", "Budapest, Keleti pályaudvar", 47.50, 19.08)
    private val transfer = Station("transfer", "Hatvan, vasútállomás", 47.67, 19.67)
    private val destination = Station("to", "Eger, vasútállomás", 47.89, 20.38)
    private val first = Leg("RAIL", now, now + 40 * 60_000, from, transfer, "IR 80", "", false, 0, emptyList(), "")
    private val second = first.copy(from = transfer, to = destination, route = "IR 87", start = now + 45 * 60_000, end = now + 90 * 60_000)
    private val journey = Journey(5400, listOf(first, second))

    @Before fun setup() {
        JourneyTracking.stop(app)
        val prefs = app.getSharedPreferences("menetrendgo", 0)
        originalBeta = prefs.all["journey_tracking_beta_enabled"] as? Boolean
        originalDeveloper = prefs.all["developer_settings_unlocked"] as? Boolean
        prefs.edit().putBoolean("journey_tracking_beta_enabled", true).commit()
        shell("pm grant ${app.packageName} android.permission.ACCESS_COARSE_LOCATION")
        shell("pm grant ${app.packageName} android.permission.ACCESS_FINE_LOCATION")
        shell("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS")
    }
    @After fun cleanup() {
        JourneyTracking.stop(app); store.clear()
        app.getSharedPreferences("menetrendgo", 0).edit().also { editor ->
            listOf("journey_tracking_beta_enabled" to originalBeta, "developer_settings_unlocked" to originalDeveloper).forEach { (key, value) ->
                if (value == null) editor.remove(key) else editor.putBoolean(key, value)
            }
        }.commit()
    }

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

    @Test fun arrivalPlatformAppearsBelowStopName() {
        val arrival = Station("arrival", "Székesfehérvár", 47.19, 18.42, platform = "4")
        val route = Journey(2400, listOf(first.copy(to = arrival)))
        compose.setContent { MenetrendTheme("light") {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Surface {
                    JourneyTrackingStop(route, 0) {}
                }
            }
        } }
        val name = compose.onNodeWithText("Székesfehérvár").fetchSemanticsNode().boundsInRoot
        val platform = compose.onNodeWithText("4. vágány").fetchSemanticsNode().boundsInRoot
        assertTrue("Platform must appear below the stop name", platform.top >= name.bottom)
        compose.onNodeWithContentDescription("Leszállási jelzés: ${arrival.name}").assertIsDisplayed()
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
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel(
            "journey_progress", "Utazás követése", NotificationManager.IMPORTANCE_LOW))
        showJourney()
        startAlerts()
        compose.waitUntil(5_000) { manager.activeNotifications.any { it.id == JourneyTrackingService.ONGOING_ID } }
        screenshot("tracking-refined-running")
        val notification = manager.activeNotifications.single { it.id == JourneyTrackingService.ONGOING_ID }.notification
        assertTrue(notification.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE != 0)
        val channel = manager.getNotificationChannel(notification.channelId)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        @Suppress("DEPRECATION")
        assertNull(notification.contentView)
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            assertTrue("Tracking must qualify as a Live Update", notification.hasPromotableCharacteristics())
        }
        assertTrue(notification.flags and android.app.Notification.FLAG_ONLY_ALERT_ONCE != 0)
        notification.actions.single { it.title == "Leállítás" }.actionIntent.send()
        compose.waitUntil(5_000) { JourneyTracking.state.value.session == null }
        compose.waitUntil(5_000) { manager.activeNotifications.none { it.id == JourneyTrackingService.ONGOING_ID } }
        assertNull(JourneyTracking.saved(app))
    }

    @Test fun betaIsHiddenByDefaultAndExtraTogglePersistsAndStopsTracking() {
        app.getSharedPreferences("menetrendgo", 0).edit().remove("journey_tracking_beta_enabled").commit()
        val vm = AppState(app).also { store.put("beta-settings", it) }
        assertFalse(vm.journeyTrackingBetaEnabled)
        assertFalse(JourneyTracking.enabled(app))
        assertTrue(runCatching { JourneyTracking.start(app, TrackingSession(journey, 0)) }.isFailure)
        var page by mutableStateOf("journey")
        compose.setContent { MenetrendTheme("light") {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (page) {
                    "settings" -> SettingsScreen(vm)
                    "home" -> SearchScreen(vm, {}, {})
                    else -> JourneyScreen(vm, journey, {}, {}, {})
                }
            }
        } }
        compose.onNodeWithContentDescription("Leszállási jelzés: ${transfer.name}").assertDoesNotExist()
        compose.runOnIdle {
            JourneyTracking.publish(TrackingView(TrackingSession(journey, 0), true))
            page = "home"
        }
        compose.onNodeWithText("Leszállási jelzés aktív").assertDoesNotExist()
        compose.runOnIdle { page = "settings" }
        compose.onNodeWithText("Nincs aktív kiegészítő funkció").assertIsDisplayed()
        compose.onNodeWithText("Kiegészítő funkciók").performClick()
        compose.onNodeWithText("Leszállási jelzés (béta)").assertIsOff().performClick().assertIsOn()
        assertTrue(JourneyTracking.enabled(app))
        val restored = AppState(app).also { store.put("beta-restored", it) }
        assertTrue(restored.journeyTrackingBetaEnabled)
        screenshot("tracking-beta-extra-setting")
        compose.runOnIdle { JourneyTracking.clear(app); page = "journey" }
        startAlerts()
        openAlerts()
        compose.onNodeWithText("Jelzés leállítása").assertIsDisplayed()
        compose.runOnIdle { vm.chooseJourneyTrackingBeta(false) }
        compose.onNodeWithContentDescription("Leszállási jelzés bezárása").assertDoesNotExist()
        compose.onNodeWithContentDescription("Leszállási jelzés: ${transfer.name}").assertDoesNotExist()
        assertNull(JourneyTracking.state.value.session)
        assertNull(JourneyTracking.saved(app))
        val manager = app.getSystemService(NotificationManager::class.java)
        compose.waitUntil(5_000) {
            manager.activeNotifications.none { it.id in listOf(JourneyTrackingService.ONGOING_ID, JourneyTrackingService.ALERT_ID) }
        }
        assertFalse(AppState(app).also { store.put("beta-disabled", it) }.journeyTrackingBetaEnabled)
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

    @Test fun ongoingLiveNotificationShowsMeasuredProgressAndStops() {
        val provider = "gps"
        shell("appops set com.android.shell android:mock_location allow")
        shell("cmd location providers add-test-provider $provider")
        shell("cmd location providers set-test-provider-enabled $provider true")
        try {
            showJourney(); startAlerts()
            val manager = app.getSystemService(NotificationManager::class.java)
            fun notification() = manager.activeNotifications.first { it.id == JourneyTrackingService.ONGOING_ID }.notification
            compose.waitUntil(5_000) { manager.activeNotifications.any { it.id == JourneyTrackingService.ONGOING_ID } }
            assertTrue(notification().extras.getBoolean(android.app.Notification.EXTRA_PROGRESS_INDETERMINATE))
            shell("cmd location providers set-test-provider-location $provider --location 47.585,19.375 --accuracy 5")
            compose.waitUntil(10_000) { JourneyTracking.state.value.estimate != null }
            compose.waitUntil(5_000) {
                !notification().extras.getBoolean(android.app.Notification.EXTRA_PROGRESS_INDETERMINATE)
            }
            assertTrue(notification().extras.getInt(android.app.Notification.EXTRA_PROGRESS) in 40..60)
            assertNull(notification().extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT))
            assertEquals("A következő megállónál szállj le!",
                notification().extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
            assertTrue(notification().extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT).toString().contains("érkezés:"))
            @Suppress("DEPRECATION")
            assertNull(notification().contentView)
            if (android.os.Build.VERSION.SDK_INT >= 36) assertTrue(notification().hasPromotableCharacteristics())
            val postedAt = manager.activeNotifications.first { it.id == JourneyTrackingService.ONGOING_ID }.postTime
            val previousProgressAt = JourneyTracking.state.value.session!!.progressAt
            Thread.sleep(20_100)
            shell("cmd location providers set-test-provider-location $provider --location 47.585,19.375 --accuracy 5")
            compose.waitUntil(10_000) { JourneyTracking.state.value.session!!.progressAt > previousProgressAt }
            assertEquals("Unchanged progress must not repost the notification", postedAt,
                manager.activeNotifications.first { it.id == JourneyTrackingService.ONGOING_ID }.postTime)
            shell("cmd statusbar expand-notifications")
            Thread.sleep(600)
            screenshot("tracking-notification-progress-light")
            shell("cmd uimode night yes")
            shell("settings put system font_scale 1.3")
            screenshot("tracking-notification-progress-dark-large")
        } finally {
            shell("cmd statusbar collapse")
            shell("cmd uimode night no")
            shell("settings put system font_scale 1.0")
            shell("cmd location providers remove-test-provider $provider")
            shell("appops set com.android.shell android:mock_location default")
        }
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
        compose.onNodeWithText("3 perc").assertDoesNotExist()
        compose.onNodeWithText("1 perc").assertDoesNotExist()
        compose.onNodeWithText("Jelzés a megálló közelében").assertIsDisplayed()
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
                "Szállj át, majd erősítsd meg a felszállást.", TrackingEstimate(1000.0, 50.0, 0, false, true)))
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
                        SearchScreen(vm, { opened = it }, {})
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
        val provider = "gps"
        shell("appops set com.android.shell android:mock_location allow")
        shell("cmd location providers add-test-provider $provider")
        shell("cmd location providers set-test-provider-enabled $provider true")
        try {
            // Keep the second simulated leg physically reachable between real five-second fixes.
            val nearbyDestination = destination.copy(lat = transfer.lat + .003, lon = transfer.lon)
            showJourney(journey.copy(legs = listOf(first, second.copy(to = nearbyDestination))))
            startAlerts()
            val manager = app.getSystemService(NotificationManager::class.java)
            assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
                app.checkSelfPermission(android.Manifest.permission.VIBRATE))
            val channel = manager.getNotificationChannel(JourneyTrackingService.ONGOING)
            assertFalse("The live notification must stay silent", channel.shouldVibrate())
            assertNull(channel.sound)
            shell("input keyevent 223")
            assertFalse("Exercise delivery with the screen off",
                app.getSystemService(android.os.PowerManager::class.java).isInteractive)
            shell("cmd location providers set-test-provider-location $provider --location 47.67,19.6728 --accuracy 5")
            try { compose.waitUntil(10_000) { JourneyTracking.state.value.session?.alerted == true } }
            catch (e: Exception) { throw AssertionError("Alert state: ${JourneyTracking.state.value}", e) }
            assertTrue("No separate alighting notification", manager.activeNotifications.none { it.id == JourneyTrackingService.ALERT_ID })
            assertNull("A detour fix must warn even without a route match", JourneyTracking.state.value.estimate)
            val vibrations = shell("dumpsys vibrator_manager") + shell("dumpsys vibrator")
            assertTrue("A direct alighting vibration must be recorded", vibrations.contains(app.packageName) && vibrations.contains("ALARM"))
            Thread.sleep(5_100)
            shell("cmd location providers set-test-provider-location $provider --location 47.67,19.67 --accuracy 5")
            assertFalse(JourneyTracking.state.value.session!!.awaitingBoarding)
            repeat(2) {
                Thread.sleep(5_100)
                shell("cmd location providers set-test-provider-location $provider --location 47.67,19.67 --accuracy 5")
            }
            compose.waitUntil(10_000) { JourneyTracking.state.value.session?.awaitingBoarding == true }
            assertTrue(manager.activeNotifications.none { it.id == JourneyTrackingService.ALERT_ID })
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
            shell("cmd location providers set-test-provider-location $provider --location ${nearbyDestination.lat},${nearbyDestination.lon} --accuracy 5")
            assertNotNull(JourneyTracking.state.value.session)
            repeat(2) {
                Thread.sleep(5_100)
                shell("cmd location providers set-test-provider-location $provider --location ${nearbyDestination.lat},${nearbyDestination.lon} --accuracy 5")
            }
            compose.waitUntil(10_000) { JourneyTracking.state.value.session == null }
            assertTrue("No separate alighting notification", manager.activeNotifications.none { it.id == JourneyTrackingService.ALERT_ID })
            assertNull(JourneyTracking.saved(app))
        } finally {
            shell("input keyevent 224"); shell("wm dismiss-keyguard")
            shell("cmd location providers remove-test-provider $provider")
            shell("appops set com.android.shell android:mock_location default")
        }
    }

    @Test fun oldNotificationCannotStopANewerTrackingSession() {
        showJourney()
        startAlerts()
        val manager = app.getSystemService(NotificationManager::class.java)
        compose.waitUntil(5_000) { manager.activeNotifications.any { it.id == JourneyTrackingService.ONGOING_ID } }
        val oldStop = manager.activeNotifications.single { it.id == JourneyTrackingService.ONGOING_ID }
            .notification.actions.single { it.title == "Leállítás" }.actionIntent
        val previous = JourneyTracking.state.value.session!!
        val newer = previous.copy(startedAt = previous.startedAt + 1)
        compose.runOnIdle { JourneyTracking.start(app, newer) }
        compose.waitUntil(5_000) {
            manager.activeNotifications.any { it.id == JourneyTrackingService.ONGOING_ID &&
                it.notification.extras.getString("trackingSessionToken") == newer.controlToken }
        }
        oldStop.send()
        instrumentation.waitForIdleSync(); Thread.sleep(300)
        assertEquals(newer.controlToken, JourneyTracking.state.value.session?.controlToken)
        manager.activeNotifications.single { it.id == JourneyTrackingService.ONGOING_ID }
            .notification.actions.single { it.title == "Leállítás" }.actionIntent.send()
        compose.waitUntil(5_000) { JourneyTracking.state.value.session == null }
    }

    @Test fun providerEnabledAfterTrackingStartsDeliversTheWarning() {
        val provider = "gps"
        shell("appops set com.android.shell android:mock_location allow")
        shell("cmd location providers add-test-provider $provider")
        shell("cmd location providers set-test-provider-enabled $provider false")
        try {
            showJourney(); startAlerts()
            shell("cmd location providers set-test-provider-enabled $provider true")
            shell("cmd location providers set-test-provider-location $provider --location 47.669,19.67 --accuracy 5")
            try { compose.waitUntil(10_000) { JourneyTracking.state.value.session?.alerted == true } }
            catch (e: Exception) { throw AssertionError("Alert state: ${JourneyTracking.state.value}", e) }
            assertFalse(JourneyTracking.state.value.session!!.awaitingBoarding)
            val manager = app.getSystemService(NotificationManager::class.java)
            assertTrue("No separate alighting notification", manager.activeNotifications.none { it.id == JourneyTrackingService.ALERT_ID })
        } finally {
            shell("cmd location providers remove-test-provider $provider")
            shell("appops set com.android.shell android:mock_location default")
        }
    }

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    @Test fun destinationCircleFollowsTheActiveLegAndDisappearsWhenStopped() {
        val vm = AppState(app).also { store.put("tracking-map", it) }
        compose.setContent { MenetrendTheme("light") { JourneyMapScreen(vm, journey, {}) } }
        compose.runOnIdle { JourneyTracking.publish(TrackingView(TrackingSession(journey, 0), true)) }
        fun findWeb(view: android.view.View): android.webkit.WebView? {
            if (view is android.webkit.WebView) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) {
                findWeb(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        fun js(script: String): String {
            val value = java.util.concurrent.atomic.AtomicReference<String?>()
            instrumentation.runOnMainSync {
                val activity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
                val web = findWeb(activity.window.decorView)
                if (web == null) value.set("null") else web.evaluateJavascript(script) { value.set(it) }
            }
            compose.waitUntil(5_000) { value.get() != null }
            return value.get()!!
        }
        compose.waitUntil(15_000) { js("typeof destinationAlerts !== 'undefined' && destinationAlerts.getLayers().length === 1") == "true" }
        assertEquals("250", js("destinationAlerts.getLayers()[0].getRadius()"))
        assertEquals(transfer.lat.toString(), js("destinationAlerts.getLayers()[0].getLatLng().lat"))
        compose.waitUntil(10_000) { js("routePoints.length >= 4") == "true" }
        js("map.setView([${transfer.lat},${transfer.lon}], 16, {animate:false}); true")
        compose.waitUntil(5_000) { js("map.getZoom()") == "16" }
        Thread.sleep(1_500)
        screenshot("tracking-destination-circle")
        compose.runOnIdle { JourneyTracking.publish(TrackingView(TrackingSession(journey, 1), true)) }
        compose.waitUntil(5_000) { js("destinationAlerts.getLayers()[0]?.getLatLng().lat") == destination.lat.toString() }
        compose.runOnIdle { JourneyTracking.stop(app) }
        compose.waitUntil(5_000) { js("destinationAlerts.getLayers().length") == "0" }
    }

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
