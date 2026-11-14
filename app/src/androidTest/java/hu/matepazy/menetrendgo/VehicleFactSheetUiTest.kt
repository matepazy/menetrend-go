package hu.matepazy.menetrendgo

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class VehicleFactSheetUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hevPhotoFactsSourcesAndBackWorkWithLargeDarkText() = inspect("HÉV MXA", "HEV", true)
    @Test fun busPhotoFactsAndSourcesRemainReadable() = inspect("Ikarus C56", "COACH", false)
    @Test fun foldingSeatsAndScopeRemainReadableWithLargeDarkText() = inspect("Credo EN 9,5", "COACH", true, true)
    @Test fun standingDensityAndSourcesRemainReadable() = inspect("Credobus Econell 12 Next", "COACH", false, true)

    private fun inspect(model: String, mode: String, dark: Boolean, standingRequired: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val catalog = VehicleModelCatalog.load(context.assets)
        val profile = requireNotNull(findVehicleModel(catalog, model))
        assertEquals(1, profile.photos.size)
        assertTrue(profile.specifications.size >= 3)
        if (standingRequired) assertTrue(profile.specifications.any { it.first == "Állóhelyek" })
        compose.setContent {
            MenetrendTheme(if (dark) "dark" else "light") {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (dark) 1.3f else 1f)) {
                    VehicleModelDrawer(model, mode) {}
                }
            }
        }
        val photo = hasContentDescription("${profile.title} ·", substring = true)
        compose.waitUntil(35_000) { compose.onAllNodes(photo).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(photo).assertCountEquals(1)
        capture(profile.id, "photo")
        compose.onNode(photo).performClick()
        compose.onNodeWithContentDescription("Nagyított fotó bezárása").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("Nagyított fotó bezárása").assertDoesNotExist()
        val scroll = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        scroll.performScrollToNode(hasText("Adatlap"))
        compose.onNodeWithText("Adatlap").assertIsDisplayed()
        val sheet = vehicleFactSheet(profile.specifications)
        sheet.figures.forEach { figure ->
            val tile = hasText(figure.kind.label) and hasText(figure.figure ?: figure.text)
            scroll.performScrollToNode(tile)
            compose.onNode(tile).assertIsDisplayed()
        }
        if (sheet.figures.isNotEmpty()) capture(profile.id, "figures")
        sheet.specifications.forEach { (label, value) ->
            val row = hasText(label) and hasText(value.main)
            scroll.performScrollToNode(row)
            compose.onNode(row).assertIsDisplayed()
        }
        capture(profile.id, "facts")
        scroll.performScrollToNode(hasText("Érdekességek"))
        compose.onNodeWithText("Érdekességek").assertIsDisplayed()
        capture(profile.id, "curiosities")
        scroll.performScrollToNode(hasText("Fotó és források"))
        compose.onNodeWithText("Fotó és források").performClick()
        scroll.performScrollToNode(hasText("Az ismertető forrásai"))
        compose.onNodeWithText("Az ismertető forrásai").assertIsDisplayed()
        compose.onNodeWithText("${profile.photos.single().license} · átméretezett fotó").assertExists()
        scroll.performScrollToNode(hasText(profile.sources.last().first))
        compose.onNodeWithText(profile.sources.last().first).assertIsDisplayed()
        capture(profile.id, "sources")
    }

    private fun capture(id: String, state: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(350)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = context.resources.configuration
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "fact-sheet-${config.screenWidthDp}-$id-$state.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
