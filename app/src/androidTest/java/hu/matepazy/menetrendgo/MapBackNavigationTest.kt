package hu.matepazy.menetrendgo

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

class MapBackNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val store = ViewModelStore()
    private var vm: AppState? = null
    private var wasEnabled = false

    @After fun cleanup() {
        vm?.chooseJourneyTrackingBeta(wasEnabled)
        store.clear()
    }

    @Test fun lightMapBackIncludesNavigationBeforeCommit() = verify("light")
    @Test fun darkMapBackIncludesNavigationBeforeCommit() = verify("dark")

    private fun verify(theme: String) {
        val state = AppState(app).also { store.put("map-back", it); vm = it }
        wasEnabled = state.journeyTrackingBetaEnabled
        state.chooseJourneyTrackingBeta(true)
        val now = System.currentTimeMillis()
        val journey = Journey(600, listOf(Leg("RAIL", now, now + 600_000,
            Station("a", "Budapest-Nyugati", 47.51, 19.06),
            Station("b", "Zugló", 47.51, 19.09), "Tesztjárat", "", false, 0, emptyList(), "")))
        lateinit var dispatcher: OnBackPressedDispatcher
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            MenetrendTheme(theme) {
                MenetrendApp(state, null, trackingSession = TrackingSession(journey, 0).json().toString())
            }
        }
        compose.onNodeWithText("Útvonal a térképen").performScrollTo()
        val bar = compose.onNodeWithText("Beállítások").fetchSemanticsNode().boundsInRoot
        // Sample the quiet surface just above the label, away from icons and text.
        val sampleX = (bar.left - 4).toInt()
        val sampleY = (bar.top - 4).toInt()
        val expected = compose.onRoot().captureToImage().asAndroidBitmap().getPixel(sampleX, sampleY)
        compose.onNodeWithText("Útvonal a térképen").performClick()
        compose.onNodeWithText("Beállítások").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        fun start() {
            compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, BackEventCompat.EDGE_LEFT)) }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(200f, 300f, .8f, BackEventCompat.EDGE_LEFT)) }
            compose.mainClock.advanceTimeByFrame()
        }
        start()
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Beállítások").assertDoesNotExist()
        start()
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(128, ignoreFrameDuration = true)
        // The live destination has not changed yet. Its shell must already be visible
        // in the predictive preview, whose semantics intentionally stay hidden.
        compose.onNodeWithText("Beállítások").assertDoesNotExist()
        val preview = compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.getExternalFilesDir(null), "map-back-$theme-preview.png").outputStream().use {
            preview.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val actual = preview.getPixel(sampleX, sampleY)
        val channels = listOf(0, 8, 16)
        assertTrue("Navigation surface must be present before Back commits", channels.all {
            abs((expected shr it and 255) - (actual shr it and 255)) < 12
        })
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("Beállítások").assertIsDisplayed()
        compose.onNodeWithText("Útvonal a térképen").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Vissza az utazás részleteihez").performClick()
        compose.onNodeWithText("Beállítások").assertIsDisplayed()
    }
}
