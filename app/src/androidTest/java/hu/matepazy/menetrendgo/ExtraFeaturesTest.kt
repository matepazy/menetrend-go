package hu.matepazy.menetrendgo

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ExtraFeaturesTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val prefs get() = app.getSharedPreferences("menetrendgo", 0)
    private val store = ViewModelStore()
    private lateinit var original: Map<String, Any?>
    private val keys = listOf("passes_enabled", "passes", "journey_tracking_beta_enabled", "passes_feature_intro_pending")

    @Before fun prepare() {
        original = keys.associateWith { prefs.all[it] }
        prefs.edit().also { edit -> keys.forEach(edit::remove) }.commit()
    }

    @After fun restore() {
        store.clear()
        PassNotifications.saved(app).forEach { PassNotifications.cancel(app, it) }
        prefs.edit().also { edit -> original.forEach { (key, value) ->
            when (value) {
                null -> edit.remove(key)
                is Boolean -> edit.putBoolean(key, value)
                is String -> edit.putString(key, value)
            }
        } }.commit()
        PassNotifications.saved(app).forEach { PassNotifications.schedule(app, it) }
    }

    @Test fun optionalTabsCountsAndPersistence() {
        val vm = AppState(app).also { store.put("extras", it) }
        var theme by mutableStateOf("light")
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                MenetrendTheme(theme) { MenetrendApp(vm, "passes") }
            }
        }
        compose.onNodeWithText("Bérletek").assertDoesNotExist()
        compose.onNodeWithText("Beállítások").performClick()
        compose.onNodeWithText("Nincs aktív kiegészítő funkció").assertIsDisplayed()
        screenshot("extras-home-none")
        compose.onNodeWithText("Kiegészítő funkciók").performClick()
        compose.onNodeWithText("Leszállási jelzés (béta)").assertIsOff()
        compose.onNode(hasText("Bérletek") and isToggleable()).assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("Leszállási jelzés (béta)").performClick().assertIsOn()
        screenshot("extras-light")
        compose.onNodeWithContentDescription("Vissza").performClick()
        compose.onNodeWithText("2 funkció bekapcsolva").assertIsDisplayed()
        screenshot("extras-home-two")
        compose.onNodeWithText("Bérletek").performClick()
        compose.onNodeWithText("Bérlet hozzáadása").assertIsDisplayed()
        compose.onNodeWithText("Beállítások").performClick()
        compose.onNodeWithText("Kiegészítő funkciók").performClick()
        compose.runOnIdle { theme = "dark"; scale = 1.3f }
        compose.onNode(hasText("Leszállási jelzés (béta)") and isToggleable()).assertIsDisplayed()
        compose.onNode(hasText("Bérletek") and isToggleable()).performScrollTo().assertIsDisplayed()
        screenshot("extras-dark-large")
        compose.onNode(hasText("Bérletek") and isToggleable()).performClick().assertIsOff()
        compose.onNodeWithContentDescription("Vissza").performClick()
        compose.onNodeWithText("1 funkció bekapcsolva").assertIsDisplayed()
        compose.onNodeWithText("Bérletek").assertDoesNotExist()
        val restored = AppState(app).also { store.put("restored", it) }
        assertFalse(restored.passesEnabled)
        assertTrue(restored.journeyTrackingBetaEnabled)
    }

    private fun seedSavedPass() = "[{\"id\":\"kept\",\"county\":\"\",\"starts\":\"2026-11-01\",\"expires\":\"2026-11-30\"}]".also {
        prefs.edit().putString("passes", it).commit()
    }

    @Test fun existingPassesOfferEnableAndSurviveDisabling() {
        val saved = seedSavedPass()
        assertFalse(PassNotifications.featureEnabled(app))
        val vm = AppState(app).also { store.put("migration", it) }
        assertTrue(vm.showPassesFeatureIntro)
        assertTrue(AppState(app).also { store.put("recreated", it) }.showPassesFeatureIntro)
        compose.setContent { MenetrendTheme("light") { MenetrendApp(vm, null) } }
        compose.onNodeWithText("A Bérletek kiegészítő funkció lett").assertIsDisplayed()
        screenshot("passes-intro-light")
        compose.onNodeWithText("Bérletek bekapcsolása").performClick()
        compose.onNodeWithText("A Bérletek kiegészítő funkció lett").assertDoesNotExist()
        compose.onNodeWithText("Bérletek").assertIsDisplayed().performClick()
        assertTrue(PassNotifications.featureEnabled(app))
        assertEquals(saved, prefs.getString("passes", null))
        compose.runOnIdle { vm.choosePassesEnabled(false) }
        assertFalse(vm.showPassesFeatureIntro)
        assertEquals(saved, prefs.getString("passes", null))
        assertFalse(AppState(app).also { store.put("restored", it) }.showPassesFeatureIntro)
    }

    @Test fun dismissedIntroStaysDismissedAndKeepsPassesOff() {
        val saved = seedSavedPass()
        prefs.edit().putBoolean("passes_enabled", false).commit()
        val vm = AppState(app).also { store.put("dismiss", it) }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                MenetrendTheme("dark") { MenetrendApp(vm, null) }
            }
        }
        compose.onNodeWithText("Most nem").assertIsDisplayed()
        screenshot("passes-intro-dark-large")
        compose.onNodeWithText("Most nem").performClick()
        compose.onNodeWithText("A Bérletek kiegészítő funkció lett").assertDoesNotExist()
        assertFalse(vm.passesEnabled)
        assertEquals(saved, prefs.getString("passes", null))
        assertFalse(AppState(app).also { store.put("dismiss-restored", it) }.showPassesFeatureIntro)
    }

    @Test fun noIntroForEnabledFeatureOrPassesAddedLater() {
        seedSavedPass()
        prefs.edit().putBoolean("passes_enabled", true).commit()
        assertFalse(AppState(app).also { store.put("enabled", it) }.showPassesFeatureIntro)
        prefs.edit().remove("passes").remove("passes_enabled").remove("passes_feature_intro_pending").commit()
        assertFalse(AppState(app).also { store.put("new-user", it) }.showPassesFeatureIntro)
        seedSavedPass()
        assertFalse(AppState(app).also { store.put("later", it) }.showPassesFeatureIntro)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        File(app.getExternalFilesDir(null), "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
