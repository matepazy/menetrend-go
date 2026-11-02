package hu.matepazy.menetrendgo

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class VehicleDrawerExpansionTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    @After fun cleanup() { store.clear() }

    @Test fun vehicleDrawerCanBePulledToSafeTop() = checkExpansion(false)
    @Test fun vehicleDrawerCanBePulledToSafeTopWithLargeDarkText() = checkExpansion(true)

    private fun checkExpansion(dark: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as Application
        val open = mutableStateOf(true)
        var safeTop = 0
        compose.setContent {
            MenetrendTheme(if (dark) "dark" else "light") {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (dark) 1.3f else 1f)) {
                    safeTop = WindowInsets.safeDrawing.getTop(LocalDensity.current)
                    if (open.value) VehicleDrawer(
                        androidx.compose.runtime.remember { AppState(app).also { store.put("vehicle", it) } },
                        // A missing trip ID exercises the real drawer without a network dependency.
                        Vehicle("", "787/17", "Budapest, Széll Kálmán tér → Nagysáp, autóbusz-forduló", "BUS", 47.5, 19.0),
                        dismiss = { open.value = false },
                    )
                }
            }
        }
        val close = compose.onNodeWithContentDescription("Jármű részleteinek bezárása")
        compose.waitForIdle()
        val initialTop = close.fetchSemanticsNode().boundsInRoot.top
        val dialogHeight = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot.height
        assertTrue("Drawer should open at its former 78% maximum", initialTop in dialogHeight * .2f..dialogHeight * .4f)
        capture(app, "initial", dark)
        compose.onNode(isDialog()).performTouchInput {
            swipe(Offset(center.x, initialTop - 20f), Offset(center.x, 0f), durationMillis = 400)
        }
        compose.waitForIdle()
        val expandedTop = close.fetchSemanticsNode().boundsInRoot.top
        assertTrue("Drawer must expand above its former 78% cap", expandedTop < dialogHeight * .2f)
        assertTrue("Close action must stay below the system status bar", expandedTop >= safeTop)
        capture(app, "expanded", dark)
        compose.onNode(isDialog()).performTouchInput {
            swipe(Offset(center.x, expandedTop - 20f), Offset(center.x, initialTop - 20f), durationMillis = 600)
        }
        compose.waitForIdle()
        org.junit.Assert.assertEquals("Downward drag must return to the old maximum height",
            initialTop, close.fetchSemanticsNode().boundsInRoot.top, 2f)
        close.performClick()
        close.assertDoesNotExist()
    }

    private fun capture(app: Application, position: String, dark: Boolean) {
        var rendered: Bitmap? = null
        // Dialog semantics may settle before the compositor presents the sheet.
        compose.waitUntil(5_000) {
            val frame = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val pixel = frame.getPixel(frame.width / 2, frame.height * 3 / 4)
            val visible = android.graphics.Color.blue(pixel) - android.graphics.Color.red(pixel) > 8
            if (visible) rendered = frame else frame.recycle()
            visible
        }
        val bitmap = requireNotNull(rendered)
        File(app.getExternalFilesDir(null), "vehicle-drawer-$position-${if (dark) "dark-large" else "light"}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
