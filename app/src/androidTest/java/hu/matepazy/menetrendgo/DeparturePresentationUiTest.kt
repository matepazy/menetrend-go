package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class DeparturePresentationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun warningStaysBesideLiveTimeAndClearsWithFreshData() {
        val now = System.currentTimeMillis()
        val from = Station("from", "Budapest, Nyugati pályaudvar", 47.0, 19.0)
        val to = Station("to", "Vác, vasútállomás", 47.0, 19.0)
        val leg = Leg("RAIL", now - 120_000, now + 3_600_000, from, to, "S70", "trip",
            true, -600, emptyList(), "")
        var early by mutableStateOf(true)
        var dark by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.3f)) {
                MenetrendTheme(if(dark) "dark" else "light") {
                    Surface {
                        Box(Modifier.fillMaxWidth().padding(20.dp)) {
                            JourneyEndpoints(Journey(3600,listOf(leg)),leftEarly=early)
                        }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(LeftEarlyDescription).assertIsDisplayed()
        compose.onNodeWithText(timeLabel(leg.start)).assertIsDisplayed()
        capture("departure-early-light")
        compose.runOnIdle { dark=true }
        compose.onNodeWithContentDescription(LeftEarlyDescription).assertIsDisplayed()
        capture("departure-early-dark")
        compose.runOnIdle { early=false }
        compose.onNodeWithContentDescription(LeftEarlyDescription).assertDoesNotExist()
        compose.onNodeWithText(timeLabel(leg.start)).assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
