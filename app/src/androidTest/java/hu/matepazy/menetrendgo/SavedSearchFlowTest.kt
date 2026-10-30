package hu.matepazy.menetrendgo

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SavedSearchFlowTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val from = Station("test-from", "Budapest-Keleti", 47.5, 19.08)
    private val to = Station("test-to", "Győr", 47.68, 17.63)

    @Test fun saveReopenEditLaunchAndDeleteRoute() {
        val prefs = app.getSharedPreferences("menetrendgo", 0)
        val keys = listOf("saved_searches", "show_saved_searches", "recents")
        val backup = keys.associateWith { prefs.all[it] }
        prefs.edit().remove("saved_searches").putBoolean("show_saved_searches", false)
            .putString("recents", "[${Recent(from, to).json()}]").commit()
        try {
            lateinit var vm: AppState
            var planning by mutableStateOf(false)
            var theme by mutableStateOf("light")
            var fontScale by mutableFloatStateOf(1f)
            var launches = 0
            compose.runOnIdle { vm = AppState(app); vm.from = from; vm.to = to }
            compose.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                    MenetrendTheme(theme) {
                        Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                            if (planning) SearchScreen(vm, {}, { launches++ })
                            else {
                                val list = rememberLazyListState()
                                ResultsScreen(vm, remember(list) { ResultsNavigationState(list) }, { planning = true }, {})
                            }
                        }
                    }
                }
            }
            compose.onNodeWithContentDescription("Keresés mentése").performClick()
            compose.onNodeWithText("Mentés").assertIsNotEnabled()
            compose.onNodeWithText("Név").performTextInput("Munkába")
            compose.onNodeWithContentDescription("Munka").performClick().assertIsSelected()
            compose.onNodeWithText("Név").performImeAction()
            screenshot("editor-light")
            compose.onNodeWithText("Mentés").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(1, vm.savedSearches.size)
                vm = AppState(app)
                assertEquals("Munkába", vm.savedSearches.single().name)
                assertEquals(SavedSearchIcon.Work, vm.savedSearches.single().icon)
                planning = true
            }
            compose.onNodeWithText("Munkába").performScrollTo().assertIsDisplayed()
            screenshot("planning-light")
            compose.onNodeWithText("Legutóbbi").performScrollTo().performClick()
            compose.onNodeWithText("Előzmények törlése").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(1, vm.savedSearches.size); assertTrue(vm.recents.isEmpty()) }
            compose.onNodeWithText("Mentett").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Munkába szerkesztése").performScrollTo().performClick()
            compose.onNodeWithText("Név").performTextReplacement("Haza")
            compose.onNodeWithContentDescription("Otthon").performClick()
            compose.onNodeWithText("Mentés").performScrollTo().performClick()
            compose.runOnIdle {
                vm.dateTime = LocalDateTime.of(2030, 1, 1, 12, 30); vm.useNow = false; vm.arriveBy = true
            }
            compose.onNodeWithText("Haza").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(1, launches); assertTrue(vm.useNow); assertFalse(vm.arriveBy)
                assertEquals(from, vm.from); assertEquals(to, vm.to)
                assertEquals(LocalDateTime.now(Budapest).toLocalDate(), vm.dateTime.toLocalDate())
                vm.saveSearch(from, to, "Haza", SavedSearchIcon.Home)
                assertEquals(1, vm.savedSearches.size)
                theme = "dark"; fontScale = 1.3f
            }
            compose.onNodeWithText("Haza").performScrollTo()
            screenshot("planning-dark-large-text")
            compose.onNodeWithContentDescription("Haza szerkesztése").performScrollTo().performClick()
            compose.onNodeWithText("Keresés törlése").performScrollTo()
            screenshot("editor-dark-large-text")
            compose.onNodeWithText("Keresés törlése").performClick()
            compose.onAllNodesWithText("Mégse").onLast().performClick()
            compose.runOnIdle { assertEquals(1, vm.savedSearches.size) }
            compose.onNodeWithText("Keresés törlése").performClick()
            compose.onNodeWithText("Törlés", useUnmergedTree = true).performClick()
            compose.onNodeWithText("A gyakori útjaid helye").performScrollTo().assertIsDisplayed()
            compose.runOnIdle { assertTrue(AppState(app).savedSearches.isEmpty()) }
        } finally {
            prefs.edit().also { editor -> keys.forEach { key ->
                when (val value = backup[key]) {
                    null -> editor.remove(key)
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                }
            } }.commit()
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Dialog windows, the keyboard switcher and ripples settle on the platform clock.
        android.os.SystemClock.sleep(1500)
        File(app.getExternalFilesDir(null), "saved-search-$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
