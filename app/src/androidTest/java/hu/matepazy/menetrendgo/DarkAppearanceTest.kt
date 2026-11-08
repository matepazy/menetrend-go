package hu.matepazy.menetrendgo

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class DarkAppearanceTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application

    @Test fun darkPalettesKeepReadableRolePairsAndOledCardsDistinctFromBlackCanvas() {
        for (scheme in listOf(Dark, Oled)) {
            val pairs = listOf(
                scheme.onBackground to scheme.background, scheme.onSurface to scheme.surface,
                scheme.onSurfaceVariant to scheme.surfaceContainerHighest,
                scheme.onPrimary to scheme.primary, scheme.onPrimaryContainer to scheme.primaryContainer,
                scheme.onSecondary to scheme.secondary, scheme.onSecondaryContainer to scheme.secondaryContainer,
                scheme.onTertiary to scheme.tertiary, scheme.onTertiaryContainer to scheme.tertiaryContainer,
                scheme.onError to scheme.error, scheme.onErrorContainer to scheme.errorContainer,
                scheme.inverseOnSurface to scheme.inverseSurface,
                scheme.primary to scheme.surface, scheme.onSurfaceVariant to scheme.surfaceVariant)
            pairs.forEach { (ink, ground) ->
                assertTrue("Unreadable role pair $ink / $ground",
                    ColorUtils.calculateContrast(ink.toArgb(), ground.toArgb()) >= 4.5)
            }
            assertTrue(ColorUtils.calculateContrast(scheme.outline.toArgb(), scheme.surface.toArgb()) >= 3.0)
        }
        assertEquals(android.graphics.Color.BLACK, Oled.background.toArgb())
        assertNotEquals(Oled.background.toArgb(), Oled.surface.toArgb())
        assertTrue(Oled.surface.luminance() > Oled.background.luminance())
        assertEquals(0f, Oled.surfaceTint.alpha)
    }

    @Test fun appearanceSelectionPersistsAndSearchUsesEachPalette() {
        val prefs = app.getSharedPreferences("menetrendgo", 0)
        val original = prefs.getString("theme", null)
        val vm = AppState(app)
        var search by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        try {
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                    MenetrendTheme(vm.theme) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            if (search) SearchScreen(vm, {}, {}) else SettingsScreen(vm)
                        }
                    }
                }
            }
            for ((choice, label) in listOf("dark" to "Sötét", "oled" to "OLED sötét")) {
                compose.onNodeWithText("Megjelenés").performClick()
                compose.onNodeWithText(label).performScrollTo().performClick()
                compose.onNodeWithText(label).assertIsSelected()
                compose.runOnIdle {
                    assertEquals(choice, prefs.getString("theme", null))
                    val selected = departureThemeContext(app)
                    assertEquals(0x20, selected.resources.configuration.uiMode and 0x30)
                }
                screenshot("appearance-$choice")
                compose.runOnIdle { search = true }
                compose.onNodeWithText("Hová indulunk?").assertExists()
                screenshot("search-$choice")
                compose.runOnIdle { search = false; scale = 1.3f }
            }
        } finally {
            prefs.edit().apply { if (original == null) remove("theme") else putString("theme", original) }.commit()
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        File(app.getExternalFilesDir(null), "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
