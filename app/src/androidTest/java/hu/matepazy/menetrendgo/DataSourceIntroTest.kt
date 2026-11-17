package hu.matepazy.menetrendgo

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

class DataSourceIntroTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val prefs get() = app.getSharedPreferences("data_source_migration_test", 0)

    @Before fun prepare() { prefs.edit().clear().commit() }
    @After fun cleanUp() { prefs.edit().clear().commit() }

    @Test fun freshInstallUsesAggregatedWithoutNoticeEvenAfterRecreation() {
        assertEquals(DataSourceMode.Aggregated, initializeDataSourcePreferences(prefs, false))
        assertFalse(prefs.getBoolean("data_source_intro_pending", true))
        initializeDataSourcePreferences(prefs, true)
        assertFalse(prefs.getBoolean("data_source_intro_pending", true))
    }

    @Test fun upgradesWithImplicitOrExplicitOldSourceSwitchOnceAndKeepNoticePending() {
        for (oldSource in listOf(null, "single")) {
            prefs.edit().clear().also { if (oldSource != null) it.putString("data_source_mode", oldSource) }.commit()
            assertEquals(DataSourceMode.Aggregated, initializeDataSourcePreferences(prefs, true))
            assertTrue(prefs.getBoolean("data_source_intro_pending", false))
            assertEquals(DataSourceMode.Aggregated, initializeDataSourcePreferences(prefs, true))
            assertTrue(prefs.getBoolean("data_source_intro_pending", false))
            prefs.edit().putBoolean("data_source_intro_pending", false).putString("data_source_mode", "single").commit()
            assertEquals(DataSourceMode.Single, initializeDataSourcePreferences(prefs, true))
            assertFalse(prefs.getBoolean("data_source_intro_pending", true))
        }
    }

    @Test fun existingAggregatedUsersNeedNoNotice() {
        prefs.edit().putString("data_source_mode", "aggregated").commit()
        assertEquals(DataSourceMode.Aggregated, initializeDataSourcePreferences(prefs, true))
        assertFalse(prefs.getBoolean("data_source_intro_pending", true))
    }

    @Test fun appMigrationShowsDrawerAndDismissalSurvivesRecreation() {
        val appPrefs = app.getSharedPreferences("menetrendgo", 0)
        val keys = listOf("data_source_mode", "data_source_aggregated_default_applied", "data_source_intro_pending", "passes_feature_intro_pending")
        val original = keys.associateWith { appPrefs.all[it] }
        val store = ViewModelStore()
        try {
            appPrefs.edit().remove("data_source_aggregated_default_applied").remove("data_source_intro_pending")
                .putString("data_source_mode", "single").putBoolean("passes_feature_intro_pending", false).commit()
            val vm = AppState(app).also { store.put("initial", it) }
            assertEquals(DataSourceMode.Aggregated, vm.dataSourceMode)
            compose.setContent { MenetrendTheme("light") { MenetrendApp(vm, null) } }
            compose.onNodeWithText("Több élő jármű a térképen").assertIsDisplayed()
            compose.onNodeWithText("Rendben").performClick()
            compose.onNodeWithText("Több élő jármű a térképen").assertDoesNotExist()
            val restored = AppState(app).also { store.put("restored", it) }
            assertFalse(restored.showDataSourceIntro)
            assertEquals(DataSourceMode.Aggregated, restored.dataSourceMode)
            compose.runOnIdle { vm.chooseDataSourceMode(DataSourceMode.Single) }
            val switchedBack = AppState(app).also { store.put("switched-back", it) }
            assertEquals(DataSourceMode.Single, switchedBack.dataSourceMode)
            assertFalse(switchedBack.showDataSourceIntro)
        } finally {
            store.clear()
            appPrefs.edit().also { edit ->
                keys.forEach { key ->
                    when (val value = original[key]) {
                        is String -> edit.putString(key, value)
                        is Boolean -> edit.putBoolean(key, value)
                        else -> edit.remove(key)
                    }
                }
            }.commit()
        }
    }

    @Test fun oldSourceActionIsReachableWithLargeTextAndDarkTheme() {
        var visible by mutableStateOf(true)
        var restoredOldSource = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MenetrendTheme("dark") {
                    if (visible) DataSourceIntroSheet(onDismiss = { visible = false }, onRestore = {
                        restoredOldSource = true
                        visible = false
                    })
                }
            }
        }
        compose.onNodeWithText("Rendben").assertIsDisplayed()
        compose.onNodeWithText("Vissza a régi adatforrásra").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(restoredOldSource) }
        compose.onNodeWithText("Több élő jármű a térképen").assertDoesNotExist()
    }
}
