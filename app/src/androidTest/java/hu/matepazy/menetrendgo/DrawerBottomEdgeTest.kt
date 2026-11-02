package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class DrawerBottomEdgeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shortDrawerFillsBothBottomCorners() = checkBottomEdge(fullHeight = false)

    @Test fun tallDrawerWithThemedShapeFillsBothBottomCorners() = checkBottomEdge(fullHeight = true)

    private fun checkBottomEdge(fullHeight: Boolean) {
        compose.setContent {
            MenetrendTheme("light") {
                val content: @androidx.compose.runtime.Composable ColumnScope.() -> Unit = {
                    Column(if (fullHeight) Modifier.fillMaxSize().padding(24.dp)
                        else Modifier.fillMaxWidth().height(180.dp).padding(24.dp)) {
                        Text("Drawer bottom edge")
                        Spacer(Modifier.weight(1f))
                        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Jelzés indítása") }
                    }
                }
                if (fullHeight) {
                    AppBottomSheet(onDismissRequest = {}, modifier = Modifier.testTag("drawer"),
                        shape = MaterialTheme.shapes.extraLarge, containerColor = Color.White, content = content)
                } else {
                    AppBottomSheet(onDismissRequest = {}, modifier = Modifier.testTag("drawer"),
                        containerColor = Color.White, content = content)
                }
            }
        }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var rendered: Bitmap? = null
        // Compose can be idle before the dialog window reaches the compositor.
        // Wait for the blue action to be visible so a blank host cannot pass.
        compose.waitUntil(5_000) {
            val frame = instrumentation.uiAutomation.takeScreenshot()
            val hasAction = (0 until frame.height step 16).any { y ->
                (0 until frame.width step 16).any { x ->
                    val pixel = frame.getPixel(x, y)
                    android.graphics.Color.red(pixel) < 80 &&
                        android.graphics.Color.green(pixel) in 70..140 &&
                        android.graphics.Color.blue(pixel) > 150
                }
            }
            if (hasAction) rendered = frame else frame.recycle()
            hasAction
        }
        val screenshot = requireNotNull(rendered)
        File(instrumentation.targetContext.getExternalFilesDir(null),
            "drawer-bottom-${if (fullHeight) "tall" else "short"}.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        // Inspect the actual screen edge, including the system navigation area.
        // A short sheet's captureToImage can include its off-screen layout bounds.
        val bounds = compose.onNodeWithTag("drawer").getUnclippedBoundsInRoot()
        val density = instrumentation.targetContext.resources.displayMetrics.density
        val left = (bounds.left.value * density).toInt().coerceAtLeast(0) + 1
        val right = (bounds.right.value * density).toInt().coerceAtMost(screenshot.width) - 2
        val y = screenshot.height - 2
        for ((label, x) in listOf("Left" to left, "Right" to right)) {
            val pixel = screenshot.getPixel(x, y)
            // Android's window/navigation compositing can slightly tint white.
            // The exposed scrim from a rounded corner is much darker (~170).
            assertTrue("$label bottom corner must be filled: $pixel",
                android.graphics.Color.red(pixel) >= 245 &&
                    android.graphics.Color.green(pixel) >= 245 &&
                    android.graphics.Color.blue(pixel) >= 245)
        }
        screenshot.recycle()
    }
}
