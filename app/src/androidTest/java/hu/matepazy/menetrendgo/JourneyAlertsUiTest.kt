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
import org.junit.Rule
import org.junit.Test
import java.io.File

class JourneyAlertsUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val store = ViewModelStore()
    @After fun cleanup() { store.clear() }

    @Test fun alertsSitInlineInLightTheme() = showAlerts(false, 1f)
    @Test fun alertsWrapInDarkThemeWithLargeText() = showAlerts(true, 1.3f)

    private fun showAlerts(dark: Boolean, scale: Float) {
        val now = System.currentTimeMillis()
        val seconds = now / 1000
        val origin = Station("a", "Budapest-Nyugati", 47.51, 19.06, platform = "10")
        val destination = Station("b", "Szeged, vasútállomás", 46.24, 20.14)
        val delay = MavAlert("mav", "", "Vonat műszaki hibája miatti késés", startSeconds = seconds - 600,
            endSeconds = seconds + 1320, warning = true)
        val diversion = MavAlert("bus", "Módosított útvonalon közlekednek a 8-as, 8Y és 28-as járatok",
            "<p style=\"color: rgb(17,85,204)\"><strong>Július 1-jétől (szerda)</strong> az Óvári Ferenc utcai útépítés miatt a buszok " +
                "nem érintik a <em>Petőfi Színház</em> és a <em>Megyeház tér</em> megállót.</p><p><br></p><ul><li>Pótlás: Iskola utca</li></ul>",
            url = "https://www.volanbusz.hu", startSeconds = 1_782_856_800L)
        val leg = Leg("RAIL", now, now + 142 * 60_000, origin, destination, "714 NAPFÉNY", "1:35051963", true, 0,
            emptyList(), "", headsign = "Szeged", alerts = listOf(delay, diversion))
        val journey = Journey(8520, listOf(leg), fetchedAt = now)
        val vm = AppState(app).also { store.put("alerts", it) }
        compose.setContent { MenetrendTheme(if (dark) "dark" else "light") {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    JourneyScreen(vm, journey, {}, {}, {}, refreshTransfers = false)
                }
            }
        } }
        compose.onNodeWithText("Vonat műszaki hibája miatti késés").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Figyelmeztetés", useUnmergedTree = true).assertExists()
        // Titles only: validity, description and links stay in the drawer.
        compose.onNodeWithText("Érvényes", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Pótlás: Iskola utca", substring = true).assertDoesNotExist()
        capture("alerts-${if (dark) "dark-large" else "light"}")
        compose.onNodeWithText("Módosított útvonalon", substring = true).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Közlemény bezárása").assertIsDisplayed()
        compose.onNodeWithText("Érvényes júl. 1. – visszavonásig").assertIsDisplayed()
        compose.onNodeWithText("Pótlás: Iskola utca", substring = true).assertExists()
        compose.onNodeWithText("Részletek a szolgáltatónál").assertExists()
        capture("alerts-${if (dark) "dark-large" else "light"}-drawer")
        compose.onNodeWithContentDescription("Közlemény bezárása").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Közlemény bezárása").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Vonat műszaki hibája miatti késés").performClick()
        compose.onNodeWithText("Érvényes", substring = true).assertIsDisplayed()
        capture("alerts-${if (dark) "dark-large" else "light"}-drawer-mav")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(app.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
