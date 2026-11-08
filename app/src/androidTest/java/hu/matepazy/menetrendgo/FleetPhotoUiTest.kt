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

    @Test fun enlargedPhotoSupportsZoomAndReturnsToArticle() {
        var dark by mutableStateOf(false)
        compose.setContent {
            MenetrendTheme(if (dark) "dark" else "light") {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (dark) 1.3f else 1f)) {
                    VehicleModelDrawer("STADLER-FLIRT", "RAIL") {}
                }
            }
        }
        val photo = hasContentDescription("Stadler FLIRT ·", substring = true)
        compose.waitUntil(35_000) { compose.onAllNodes(photo).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(photo).performClick()
        val viewer = photo and SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)
        compose.onNode(viewer).assertIsDisplayed().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Nagyítás: 100%"))
        capture("enlarged-light")
        compose.onNode(viewer).performTouchInput { doubleClick(center) }
        compose.onNode(viewer).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Nagyítás: 250%"))
        compose.onNode(viewer).performTouchInput { swipeLeft() }
        capture("zoomed-light")
        compose.onNodeWithContentDescription("Nagyítás visszaállítása").performClick()
        compose.onNode(viewer).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Nagyítás: 100%"))
        compose.onNode(viewer).performTouchInput {
            down(0, center - androidx.compose.ui.geometry.Offset(40f, 0f))
            down(1, center + androidx.compose.ui.geometry.Offset(40f, 0f))
            repeat(8) { step ->
                moveTo(0, center - androidx.compose.ui.geometry.Offset(40f + step * 15f, 0f), delayMillis = 16)
                moveTo(1, center + androidx.compose.ui.geometry.Offset(40f + step * 15f, 0f), delayMillis = 16)
            }
            up(0)
            up(1)
        }
        compose.onNode(viewer).assert(SemanticsMatcher("Pinch enlarges the photo") {
            it.config[SemanticsProperties.StateDescription] != "Nagyítás: 100%"
        })
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Nagyított fotó bezárása").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithContentDescription("Nagyított fotó bezárása").assertExists()
        compose.mainClock.advanceTimeBy(300)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Nagyított fotó bezárása").assertDoesNotExist()
        compose.onNodeWithText("Stadler FLIRT").assertIsDisplayed()
        compose.runOnIdle { dark = true }
        compose.onNode(photo).performClick()
        capture("enlarged-dark-large")
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("Nagyított fotó bezárása").assertDoesNotExist()
        compose.onNodeWithText("Stadler FLIRT").assertIsDisplayed()
    }

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
        // Compose can settle before the dialog window's compositor presents its final frame.
        android.os.SystemClock.sleep(350)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val output = File(context.getExternalFilesDir(null), "fleet-${config.screenWidthDp}-$name.png")
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
