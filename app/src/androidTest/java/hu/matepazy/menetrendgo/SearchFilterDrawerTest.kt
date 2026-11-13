package hu.matepazy.menetrendgo

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchFilterDrawerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun groupedFiltersToggleResetAndRemainUsableWithLargeText() {
        var selected by mutableStateOf<Set<SearchFilter>>(emptySet())
        var vehicles by mutableStateOf<Set<SearchVehicle>>(emptySet())
        var theme by mutableStateOf("light")
        var dismissed = false
        compose.setContent {
            MenetrendTheme(theme) {
                SearchFilterDrawer(selected,
                    { filter, enabled -> selected = if (enabled) selected + filter else selected - filter },
                    { selected = emptySet(); vehicles = emptySet() }, { dismissed = true },
                    vehicles, { vehicles = toggleSearchVehicle(vehicles, it) }, { vehicles = emptySet() })
            }
        }
        compose.onNodeWithText("Konnektor / USB").assertDoesNotExist()
        compose.onNodeWithText("Wi-Fi").assertDoesNotExist()
        compose.onNodeWithText("Alaphelyzet").assertIsNotEnabled()
        compose.onNodeWithText("Vonatok").assertDoesNotExist()
        screenshot("filters-collapsed-light")
        compose.onNodeWithText("Járművek").performScrollTo().performClick()
        compose.onNodeWithText("Mindegyik").assertIsNotEnabled()
        SearchVehicle.entries.forEach { compose.onNodeWithText(it.title).performScrollTo().assertIsOn() }
        listOf(SearchVehicle.Coach, SearchVehicle.LocalBus, SearchVehicle.TramTrain).forEach {
            compose.onNodeWithText(it.title).performScrollTo().performClick().assertIsOff()
        }
        compose.runOnIdle { assertEquals(setOf(SearchVehicle.Train, SearchVehicle.Hev), vehicles) }
        compose.onNodeWithText("Mindegyik").assertIsEnabled()
        screenshot("filters-vehicles-light")
        compose.onNodeWithText("HÉV").performScrollTo().performClick().assertIsOff()
        compose.onNodeWithText("Vonatok").performScrollTo().assertIsOn().assertIsNotEnabled()
        compose.onNodeWithText("Mindegyik").performClick()
        compose.onNodeWithText("Mindegyik").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Vissza a keresési feltételekhez").performClick()
        compose.onNodeWithText("Vonatok").assertDoesNotExist()
        compose.onNodeWithText("1+ osztály").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("1+ osztály").performClick().assertIsNotSelected()
        compose.onNodeWithText("Légkondicionálás").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Országbérlettel").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("2 feltétel kiválasztva").performScrollTo().assertIsDisplayed()
        screenshot("filters-light")
        compose.runOnIdle { theme = "dark" }
        compose.onNodeWithText("Járművek").performScrollTo().performClick()
        compose.onNodeWithText("Helyközi autóbuszok").performScrollTo().performClick().assertIsOff()
        compose.onNodeWithText("TramTrain").performScrollTo().assertIsOn()
        screenshot("filters-vehicles-bottom")
        compose.onNodeWithContentDescription("Vissza a keresési feltételekhez").performClick()
        compose.onNodeWithText("3 feltétel kiválasztva").performScrollTo().assertIsDisplayed()
        screenshot("filters-dark")
        compose.onNodeWithText("Alaphelyzet").performClick()
        compose.runOnIdle { assertEquals(emptySet<SearchFilter>(), selected); assertEquals(emptySet<SearchVehicle>(), vehicles) }
        compose.onNodeWithText("Járművek").performScrollTo().performClick()
        compose.onNodeWithText("Helyi autóbuszok").performScrollTo().performClick().assertIsOff()
        compose.onNodeWithText("Mindegyik")
        screenshot("filters-vehicles-large-text")
        compose.onNodeWithContentDescription("Vissza a keresési feltételekhez").performClick()
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
        // Dialog rendering and platform ripples settle outside Compose's test clock.
        android.os.SystemClock.sleep(500)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            // Capture the entire window: the sheet has its own dialog root.
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
