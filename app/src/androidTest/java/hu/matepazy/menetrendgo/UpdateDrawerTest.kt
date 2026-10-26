package hu.matepazy.menetrendgo

import android.content.Context
import android.graphics.Bitmap
import android.text.Spanned
import android.text.style.ClickableSpan
import io.noties.markwon.core.spans.StrongEmphasisSpan
import android.widget.TextView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class UpdateDrawerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longMarkdownStaysBelowStatusBarWithVisibleActionsAndLargeText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("menetrendgo_updates", Context.MODE_PRIVATE)
        val wasEnabled = prefs.getBoolean("version_check_enabled", true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        // No release checks or downloads: exercise a selected local fixture.
        prefs.edit().putBoolean("version_check_enabled", false).commit()
        try {
            val updates = UpdateController(context, scope)
            updates.select(UpdateState.UpdateAvailable("v9.0.0", """
                ## Újdonságok

                **Gyorsabb keresés** és *átláthatóbb indulások*.

                - Javított frissítési ablak
                - [A kiadás részletei](https://github.com)

                `MenetrendGO` · ~~Korábbi hiba~~

            """.trimIndent() + "\n\n" + (1..50).joinToString("\n\n") { "### Változás $it\n\nRészletes kiadási megjegyzés hosszabb magyar szöveggel." }, "https://example.com/update.apk"))
            var theme by mutableStateOf("light")
            var fontScale by mutableFloatStateOf(1f)
            var safeTop = 0
            compose.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                    safeTop = WindowInsets.safeDrawing.getTop(LocalDensity.current)
                    MenetrendTheme(theme) { UpdateHost(updates) }
                }
            }
            fun assertLayout() {
                compose.waitForIdle()
                assertTrue(safeTop > 0)
                assertTrue(compose.onNodeWithTag("update-drawer").fetchSemanticsNode().boundsInRoot.top > safeTop)
                compose.onNodeWithText("Letöltés és frissítés").assertIsDisplayed()
                compose.onNodeWithText("Emlékeztess holnap").assertIsDisplayed()
            }
            assertLayout()
            onView(isAssignableFrom(TextView::class.java)).check { view, error ->
                if (error != null) throw error
                val text = (view as TextView).text as Spanned
                assertFalse(text.contains("## "))
                assertFalse(text.contains("**"))
                assertTrue(text.getSpans(0, text.length, StrongEmphasisSpan::class.java).isNotEmpty())
                assertEquals(1, text.getSpans(0, text.length, ClickableSpan::class.java).size)
                assertTrue(text.contains("A kiadás részletei"))
                assertFalse(text.contains("](https://github.com)"))
            }
            screenshot("update-light")
            repeat(3) {
                compose.onNodeWithTag("update-notes").performTouchInput { swipeUp() }
                assertLayout()
            }
            compose.runOnIdle { theme = "dark"; fontScale = 1.6f }
            assertLayout()
            screenshot("update-dark-large-text")
            compose.onNodeWithText("Emlékeztess holnap").performClick()
            compose.runOnIdle { assertNull(updates.selected) }
        } finally {
            scope.cancel()
            prefs.edit().putBoolean("version_check_enabled", wasEnabled).commit()
        }
    }

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
