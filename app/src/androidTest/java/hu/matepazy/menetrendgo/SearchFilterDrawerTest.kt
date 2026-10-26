package hu.matepazy.menetrendgo

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchFilterDrawerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun groupedFiltersToggleResetAndRemainUsableWithLargeText() {
        var selected by mutableStateOf<Set<SearchFilter>>(emptySet())
        var theme by mutableStateOf("light")
        var fontScale by mutableFloatStateOf(1f)
        var dismissed = false
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MenetrendTheme(theme) {
                    SearchFilterDrawer(selected,
                        { filter, enabled -> selected = if (enabled) selected + filter else selected - filter },
                        { selected = emptySet() }, { dismissed = true })
                }
            }
        }
        compose.onNodeWithText("Konnektor / USB").assertDoesNotExist()
        compose.onNodeWithText("Wi-Fi").assertDoesNotExist()
        compose.onNodeWithText("Alaphelyzet").assertIsNotEnabled()
        compose.onNodeWithText("1+ osztály").performClick().assertIsSelected()
        compose.onNodeWithText("1+ osztály").performClick().assertIsNotSelected()
        compose.onNodeWithText("Légkondicionálás").performClick().assertIsSelected()
        compose.onNodeWithText("Országbérlettel").performClick().assertIsSelected()
        compose.onNodeWithText("2 feltétel kiválasztva").assertIsDisplayed()
        screenshot("filters-light")
        compose.runOnIdle { theme = "dark" }
        screenshot("filters-dark")
        compose.onNodeWithText("Alaphelyzet").performClick()
        compose.runOnIdle { assertEquals(emptySet<SearchFilter>(), selected); fontScale = 1.6f }
        compose.onNodeWithText("Háló- / fekvőhely").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("A szűrésről").performScrollTo().performClick()
        compose.onNodeWithText("Az országbérlet és az 1. osztály külön feltétel.", substring = true)
            .performScrollTo().assertIsDisplayed()
        screenshot("filters-large-text")
        compose.onNodeWithText("Kész").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, dismissed) }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            // Capture the entire window: the sheet has its own dialog root.
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
