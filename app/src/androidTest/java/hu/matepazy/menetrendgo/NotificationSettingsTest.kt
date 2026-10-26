package hu.matepazy.menetrendgo

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class NotificationSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application
    private val prefs get() = app.getSharedPreferences("menetrendgo", 0)
    private val store = ViewModelStore()
    private lateinit var original: Map<String, Any?>
    private val pass = Pass("notification-settings-test", null, LocalDate.now(Budapest), LocalDate.now(Budapest).plusDays(12))

    @Before fun prepare() {
        val keys = PassReminder.entries.map { it.preferenceKey } + "passes"
        original = keys.associateWith { prefs.all[it] }
        prefs.edit().also { editor ->
            keys.forEach(editor::remove)
            editor.putString("passes", JSONArray(listOf(pass.json())).toString())
        }.commit()
    }

    @After fun restore() {
        store.clear()
        PassNotifications.cancel(app, pass)
        prefs.edit().also { editor ->
            original.forEach { (key, value) ->
                when (value) {
                    null -> editor.remove(key)
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                }
            }
        }.commit()
        PassNotifications.saved(app).forEach { PassNotifications.schedule(app, it) }
    }

    @Test fun switchesAreIndependentPersistAndRemainUsableInBothThemesAndLargeText() {
        val vm = AppState(app).also { store.put("settings", it) }
        var theme by mutableStateOf("light")
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MenetrendTheme(theme) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.widthIn(max = 760.dp)) { SettingsScreen(vm) }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Értesítések").performClick()
        PassReminder.entries.forEach { compose.onNodeWithText(it.title).assertIsOn() }
        compose.onNodeWithText(PassReminder.ThreeDays.title).performClick().assertIsOff()
        compose.onNodeWithText(PassReminder.OneDay.title).assertIsOn()
        compose.onNodeWithText(PassReminder.Expired.title).assertIsOn()
        screenshot("notification-settings-light")
        compose.runOnIdle { theme = "dark"; fontScale = 1.6f }
        compose.onNodeWithText(PassReminder.Expired.title).performScrollTo().performClick().assertIsOff()
        screenshot("notification-settings-dark-large")
        compose.onNodeWithText("Android értesítésbeállítások").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            val restored = AppState(app).also { store.put("restored", it) }
            assertEquals(setOf(PassReminder.OneDay), restored.passReminders)
            PassNotifications.setEnabled(app, PassReminder.OneDay, false)
            assertTrue(PassNotifications.enabledReminders(app).isEmpty())
            PassNotifications.setEnabled(app, PassReminder.ThreeDays, true)
            assertEquals(setOf(PassReminder.ThreeDays), PassNotifications.enabledReminders(app))
        }
    }

    @Test fun disablingDismissesOnlyThatCategoryAndBlocksAlreadyDeliveredAlarms() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            shell("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS")
        }
        PassNotifications.channel(app)
        assertTrue("Test device must allow the pass notification channel", PassNotifications.systemEnabled(app))
        val manager = app.getSystemService(NotificationManager::class.java)
        PassNotifications.notify(app, pass.id, 3)
        PassNotifications.notify(app, pass.id, 1)
        val threeDaysId = (pass.id + 3).hashCode()
        val oneDayId = (pass.id + 1).hashCode()
        compose.waitUntil(5_000) { manager.activeNotifications.any { it.id == threeDaysId } && manager.activeNotifications.any { it.id == oneDayId } }
        PassNotifications.setEnabled(app, PassReminder.ThreeDays, false)
        PassNotifications.notify(app, pass.id, 3)
        compose.waitUntil(5_000) { manager.activeNotifications.none { it.id == threeDaysId } }
        assertTrue(manager.activeNotifications.any { it.id == oneDayId })
        PassNotifications.setEnabled(app, PassReminder.ThreeDays, true)
        PassNotifications.notify(app, pass.id, 3)
        compose.waitUntil(5_000) { manager.activeNotifications.any { it.id == threeDaysId } }
    }

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        File(app.getExternalFilesDir(null), "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
