package hu.matepazy.menetrendgo

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NavigationMenuTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val prefs get() = app.getSharedPreferences("menetrendgo", 0)
    private val store = ViewModelStore()
    private val keys = listOf("nav_liquid", "nav_labels")
    private lateinit var original: Map<String, Any?>

    @Before fun prepare() {
        original = keys.associateWith { prefs.all[it] }
        prefs.edit().also { edit -> keys.forEach(edit::remove) }.commit()
    }

    @After fun restore() {
        store.clear()
        prefs.edit().also { edit -> original.forEach { (key, value) -> if (value is Boolean) edit.putBoolean(key, value) else edit.remove(key) } }.commit()
    }

    private fun launch(): AppState {
        val vm = AppState(app).also { store.put("nav", it) }
        compose.setContent { MenetrendTheme("light") { MenetrendApp(vm, null) } }
        return vm
    }

    @Test fun stillLongPressOpensMenuWithoutSelecting() {
        launch()
        compose.onNodeWithText("Térkép").performTouchInput { longClick() }
        compose.onNodeWithText("Szerkesztés").assertIsDisplayed()
        compose.onNode(hasText("Térkép") and isSelectable()).assertIsNotSelected()
        compose.onNodeWithText("Kiegészítő funkciók").performClick()
        compose.onNodeWithText("Szerkesztés").assertDoesNotExist()
        compose.onNodeWithText("Leszállási jelzés (béta)").assertIsDisplayed()
        compose.onNode(hasText("Beállítások") and isSelectable()).assertIsSelected()
    }

    @Test fun draggingDoesNotOpenMenu() {
        launch()
        compose.onNode(hasText("Tervezés") and isSelectable()).performTouchInput { swipeRight(centerX, centerX + 400f, 300) }
        compose.onNodeWithText("Szerkesztés").assertDoesNotExist()
    }

    @Test fun editSheetTogglesLabelsAndLiquid() {
        val vm = launch()
        compose.onNodeWithText("Térkép").performTouchInput { longClick() }
        compose.onNodeWithText("Szerkesztés").performClick()
        compose.onNodeWithText("Navigációs sáv").assertIsDisplayed()
        compose.onNode(hasText("Folyékony animáció") and isToggleable()).assertIsOn().performClick().assertIsOff()
        compose.onNode(hasText("Feliratok az ikonok alatt") and isToggleable()).assertIsOn().performClick().assertIsOff()
        assertFalse(vm.navLiquid); assertFalse(vm.navLabels)
        compose.runOnIdle { }
        // Without labels the items keep their names for screen readers.
        assertTrue(compose.onAllNodesWithContentDescription("Térkép").fetchSemanticsNodes().isNotEmpty())
        val restored = AppState(app).also { store.put("restored", it) }
        assertFalse(restored.navLiquid); assertFalse(restored.navLabels)
    }
}
