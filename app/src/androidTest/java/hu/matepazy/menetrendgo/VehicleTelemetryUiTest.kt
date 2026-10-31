package hu.matepazy.menetrendgo

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class VehicleTelemetryUiTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun telemetryAgesWithoutARefreshAndWrapsAtLargeFontSizes() {
        var large by mutableStateOf(false)
        val reported=System.currentTimeMillis()/1000-72
        var vehicle by mutableStateOf(Vehicle("test", "S30", "Székesfehérvár", "RAIL", 47.5, 19.0,
            positionUpdatedSeconds=reported, speedMetersPerSecond=20.0))
        compose.setContent {
            MenetrendTheme(if (large) "dark" else "light") {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 1.3f else 1f)) {
                    AppBottomSheet(onDismissRequest={}) {
                        Column(Modifier.fillMaxWidth().padding(24.dp)) {
                            RouteBadge(vehicle.badgeLeg())
                            VehicleTelemetry(vehicle,Modifier.padding(top=8.dp))
                            Text(vehicle.headsign,style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=12.dp))
                        }
                    }
                }
            }
        }
        val ageMatcher=hasText("Pozíció frissítve:",substring=true)
        compose.onNodeWithText("72 km/h").assertIsDisplayed()
        val initial=compose.onNode(ageMatcher).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text
        compose.waitUntil(4000) {
            compose.onNode(ageMatcher).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text!=initial
        }
        capture("light")
        compose.runOnIdle {large=true}
        compose.onNodeWithText("72 km/h").assertIsDisplayed()
        compose.onNode(ageMatcher).assertIsDisplayed()
        val root=compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxBy {it.boundsInRoot.width}.boundsInRoot
        for (node in listOf(compose.onNodeWithText("72 km/h"),compose.onNode(ageMatcher))) {
            val bounds=node.fetchSemanticsNode().boundsInRoot
            assertTrue("Telemetry must fit the sheet",bounds.left>=root.left && bounds.right<=root.right)
        }
        capture("dark-large")
        compose.runOnIdle {vehicle=vehicle.copy(speedMetersPerSecond=0.0)}
        compose.onNodeWithText("0 km/h").assertIsDisplayed()
        compose.runOnIdle {vehicle=vehicle.copy(speedMetersPerSecond=null,positionUpdatedSeconds=null)}
        compose.onNodeWithText("0 km/h").assertDoesNotExist()
        compose.onNodeWithContentDescription("Sebesség").assertDoesNotExist()
        compose.onNodeWithText("A pozíció frissítési ideje ismeretlen").assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null),"vehicle-telemetry-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
        }
        bitmap.recycle()
    }
}
