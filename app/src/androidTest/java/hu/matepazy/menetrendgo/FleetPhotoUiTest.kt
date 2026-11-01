package hu.matepazy.menetrendgo

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class FleetPhotoUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remotePhotoAndCreditsRemainReadableWithLargeText() {
        var large by mutableStateOf(false)
        compose.setContent {
            MenetrendTheme(if (large) "dark" else "light") {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 1.3f else 1f)) {
                    VehicleModelDrawer("STADLER-FLIRT", "RAIL") {}
                }
            }
        }
        val photo = hasContentDescription("Stadler FLIRT ·", substring = true)
        compose.waitUntil(35_000) { compose.onAllNodes(photo).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(photo).assertIsDisplayed()
        capture("light")
        compose.runOnIdle { large = true }
        compose.onNode(photo).assertIsDisplayed()
        capture("dark-large")
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText("Fotó és források"))
        compose.onNodeWithText("Fotó és források").performClick()
        compose.onNodeWithText("Az ismertető forrásai").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("CC BY-SA 4.0 · átméretezett fotó").assertExists()
        capture("credits")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = File(context.cacheDir, "fleet-photos").listFiles().orEmpty()
        assertTrue("Downloaded image must be cached", files.isNotEmpty())
        assertTrue(files.size <= FleetPhotoCache.MAX_PHOTOS)
        assertTrue(files.sumOf { it.length() } <= FleetPhotoCache.MAX_BYTES)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = context.resources.configuration
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val output = File(context.getExternalFilesDir(null), "fleet-${config.screenWidthDp}-$name.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
