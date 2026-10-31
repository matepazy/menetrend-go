package hu.matepazy.menetrendgo

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PassesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val prefs get() = app.getSharedPreferences("menetrendgo", 0)
    private val store = ViewModelStore()
    private var original: String? = null
    private val today = LocalDate.now(Budapest)
    private val country = Pass("passes-ui-country", null, today.minusDays(10), today.plusDays(12))
    private val county = Pass("passes-ui-county", "Szabolcs-Szatmár-Bereg", today.minusDays(28), today.plusDays(1))
    private val future = Pass("passes-ui-future", "Fejér", today.plusDays(4), today.plusDays(33))
    private val expired = Pass("passes-ui-expired", "Vas", today.minusDays(40), today.minusDays(11))
    private lateinit var vm: AppState
    private var theme by mutableStateOf("light")
    private var fontScale by mutableFloatStateOf(1f)

    @Before fun prepare() {
        original = prefs.getString("passes", null)
        prefs.edit().putString("passes", "[]").commit()
        vm = AppState(app).also { store.put("passes", it) }
    }

    @After fun restore() {
        listOf(country, county, future, expired).forEach { PassNotifications.cancel(app, it) }
        store.clear()
        prefs.edit().also { if (original == null) it.remove("passes") else it.putString("passes", original) }.commit()
        PassNotifications.saved(app).forEach { PassNotifications.schedule(app, it) }
    }

    private fun render() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MenetrendTheme(theme) {
                    Surface(Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
                            Box(Modifier.widthIn(max=760.dp).fillMaxSize()) {PassesScreen(vm)}
                        }
                    }
                }
            }
        }
    }

    @Test fun expiryHierarchyMenusAndExpiredDisclosure() {
        compose.runOnIdle { listOf(country, county, future, expired).forEach(vm::addPass) }
        render()
        compose.onNodeWithText("Holnap jár le").assertIsDisplayed()
        compose.onNodeWithText("Még 12 napig érvényes").assertIsDisplayed()
        compose.onNodeWithText(expired.name).assertDoesNotExist()
        screenshot("passes-light")
        compose.onNodeWithContentDescription("${country.name} műveletei").performClick()
        compose.onNodeWithText("Szerkesztés").performClick()
        compose.onNodeWithText("Bérlet szerkesztése").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bezárás").performClick()
        compose.onNodeWithText("Lejárt bérletek (1)").performScrollTo().performClick()
        compose.onNodeWithText(expired.name).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("${expired.name} műveletei").performClick()
        compose.onNodeWithText("Törlés").performClick()
        compose.onNodeWithText("Törlöd a bérletet?").assertIsDisplayed()
        compose.onNodeWithText("Mégse").performClick()
        compose.runOnIdle { assertTrue(vm.passes.any { it.id == expired.id }) }
        compose.onNodeWithContentDescription("${expired.name} műveletei").performClick()
        compose.onNodeWithText("Törlés").performClick()
        compose.onNodeWithText("Törlés").performClick()
        compose.runOnIdle { assertFalse(vm.passes.any { it.id == expired.id }) }
    }

    @Test fun darkLargeTextEmptyUpcomingAndInformationSheets() {
        theme = "dark"; fontScale = 1.6f
        render()
        compose.onNodeWithText("Bérlet hozzáadása").assertIsDisplayed()
        screenshot("passes-empty-dark-large")
        compose.onNodeWithText("Bérlet hozzáadása").performClick()
        compose.onNodeWithText("Új bérlet").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bezárás").performClick()
        compose.runOnIdle { listOf(country, county, future).forEach(vm::addPass) }
        screenshot("passes-dark-large")
        compose.onNodeWithText("Érvényes ettől: ${passDate(future.starts)}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Emlékeztetők").performScrollTo().performClick()
        compose.onNodeWithText(PassReminder.Expired.title).performScrollTo().assertExists()
        screenshot("passes-reminders-dark-large")
        compose.onNodeWithContentDescription("Bezárás").performClick()
        compose.onNodeWithText("Nem utazási igazolvány").performScrollTo().performClick()
        compose.onNodeWithText("Ország- és megyebérlet feltételei").performScrollTo().assertIsDisplayed()
    }

    @Test fun allNineteenCountyOutlinesRender() {
        assertEquals(Counties.toSet(), countyPassIcons.keys)
        compose.setContent {
            MenetrendTheme("light") {
                Surface {
                    Column(Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()).padding(16.dp)) {
                        PassIcon(country)
                        Counties.chunked(4).forEach { counties ->
                            Row { counties.forEach { name -> PassIcon(country.copy(county = name)) } }
                        }
                    }
                }
            }
        }
        screenshot("passes-all-county-icons")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        File(app.getExternalFilesDir(null), "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
