package hu.matepazy.menetrendgo

import android.Manifest
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CurrentDepartureLocationTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
        }
    }

    @Suppress("DEPRECATION")
    @Test fun departureUsesFreshCoordinatesAndDestinationKeepsItsPicker() {
        val manager = context.getSystemService(LocationManager::class.java)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        shell("appops set ${context.packageName} android:mock_location allow")
        val enabled = manager.isLocationEnabled
        shell("cmd location set-location-enabled true")
        try {
            manager.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false, true, true, true, 1, 1)
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 47.500278; longitude = 19.024444; accuracy = 5f
                time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            })
            lateinit var vm: AppState
            var theme by mutableStateOf("light")
            var fontScale by mutableFloatStateOf(1f)
            compose.runOnIdle { vm = AppState(context.applicationContext as Application) }
            compose.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                    MenetrendTheme(theme) { StationFocusOverlay { SearchScreen(vm, {}, {}) } }
                }
            }
            compose.onNodeWithText("Honnan?").performClick()
            compose.onNodeWithText("Jelenlegi helyzet").assertIsDisplayed()
            screenshot("location-phone-light")
            compose.runOnIdle { theme = "dark"; fontScale = 1.3f }
            screenshot("location-phone-dark-large-text")
            compose.onNodeWithText("Jelenlegi helyzet").performClick()
            compose.waitUntil(25_000) { vm.from != null }
            compose.runOnIdle {
                assertEquals(47.500278, vm.from!!.lat, .00001)
                assertEquals(19.024444, vm.from!!.lon, .00001)
                assertEquals("", vm.from!!.id)
                assertNull(vm.to)
                val query = MavQueries.plan(vm.from!!, Station("destination", "Destination", 47.2, 18.4),
                    java.time.LocalDateTime.of(2026, 10, 30, 12, 0), false)
                assertTrue(query.contains("from:{lat:47.500278,lon:19.024444}"))
                assertTrue(query.contains("toPlace:"))
            }
            compose.onNodeWithContentDescription("Megállókeresés bezárása").assertDoesNotExist()
            compose.onNodeWithText("Hová?").performClick()
            // Only the already selected departure remains; destination offers no location row.
            compose.onAllNodesWithText("Jelenlegi helyzet").assertCountEquals(1)
        } finally {
            manager.removeTestProvider(LocationManager.GPS_PROVIDER)
            shell("appops set ${context.packageName} android:mock_location default")
            shell("cmd location set-location-enabled $enabled")
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val file = File(context.getExternalFilesDir(null), "$name.png")
        file.outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        // connectedAndroidTest uninstalls the app afterward, including its external files.
        shell("cp ${file.absolutePath} /data/local/tmp/$name.png")
    }
}
