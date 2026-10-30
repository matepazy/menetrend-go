package hu.matepazy.menetrendgo

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class UpdateSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun betaWarningPrecedesSavingAndDismissalKeepsCurrentChannel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("menetrendgo_updates", Context.MODE_PRIVATE)
        val original = prefs.all
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        prefs.edit().putBoolean("version_check_enabled", false).putString("version_check_channel", "release").commit()
        try {
            val updates = UpdateController(context, scope)
            var theme by mutableStateOf("light")
            var scale by mutableFloatStateOf(1f)
            var installedVersion by mutableStateOf("v1.2.0")
            compose.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, scale)) {
                    MenetrendTheme(theme) {
                        androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(WindowInsets.safeDrawing.asPaddingValues()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                PageTitle("Frissítések")
                                UpdateSettings(updates, installedVersion)
                            }
                        }
                    }
                }
            }
            fun screenshot(name: String) {
                compose.waitForIdle()
                val file = File(context.getExternalFilesDir(null), "$name.png")
                file.outputStream().use {
                    instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                // Keep captures available after Gradle uninstalls the fixture app.
                instrumentation.uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/$name.png").use {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
                }
            }
            screenshot("settings-updates-light")
            compose.onNodeWithText("Indításkor").assertDoesNotExist()
            compose.onNodeWithText("A frissítésről").assertDoesNotExist()
            compose.onNodeWithText("Elérhető verziók").assertDoesNotExist()
            compose.onNodeWithText("Frissítési csatorna").performClick()
            compose.onNodeWithText("Béta").performClick()
            compose.onNodeWithTag("beta-consent-drawer").assertIsDisplayed()
            compose.onNodeWithText("Béta kiadások kipróbálása").assertIsDisplayed()
            compose.onNodeWithText("Hibákra is számíts").assertIsDisplayed()
            compose.onNodeWithText("Visszatérés stabil verzióra").assertIsDisplayed()
            compose.runOnIdle { assertEquals("release", updates.channel) }
            screenshot("settings-beta-warning")
            instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            compose.waitForIdle()
            compose.runOnIdle { assertEquals("release", updates.channel) }
            compose.onNodeWithText("Kész").assertIsDisplayed()
            compose.onNodeWithText("Béta").performClick()
            compose.onNodeWithText("Megértettem, engedélyezem a bétát").performClick()
            compose.runOnIdle { assertEquals("pre-release", updates.channel) }
            compose.onNodeWithText("Béta").assertIsDisplayed()
            // Channel choice alone must not expose version management on a stable build.
            compose.onNodeWithText("Elérhető verziók").assertDoesNotExist()
            compose.runOnIdle { installedVersion = "v1.3.0-beta1" }
            compose.onNodeWithText("Elérhető verziók").assertIsDisplayed()
            compose.runOnIdle { theme = "dark"; scale = 1.6f; updates.chooseChannel("release") }
            compose.onNodeWithText("Frissítési csatorna").performScrollTo().performClick()
            compose.onNodeWithText("Béta").performClick()
            compose.onNodeWithText("Megértettem, engedélyezem a bétát").assertIsDisplayed()
            compose.onNodeWithText("Mégse").assertIsDisplayed()
            screenshot("settings-beta-dark-large")
            compose.onNodeWithText("Mégse").performClick()
            compose.runOnIdle { assertEquals("release", updates.channel) }
        } finally {
            scope.cancel()
            val editor = prefs.edit().clear()
            original.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                    is Long -> editor.putLong(key, value)
                    is Int -> editor.putInt(key, value)
                }
            }
            editor.commit()
        }
    }
}
