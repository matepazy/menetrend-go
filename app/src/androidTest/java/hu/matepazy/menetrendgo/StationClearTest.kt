package hu.matepazy.menetrendgo

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StationClearTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application

    @Test fun clearSelectedAndTypedStationsIndependently() {
        lateinit var vm: AppState
        val from = Station("clear-from", "Budapest-Keleti", 47.5, 19.08)
        val to = Station("clear-to", "Győr", 47.68, 17.63)
        var theme by mutableStateOf("light")
        var fontScale by mutableFloatStateOf(1f)
        compose.runOnIdle { vm = AppState(app); vm.from = from; vm.to = to }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MenetrendTheme(theme) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        SearchScreen(vm) {}
                    }
                }
            }
        }
        capture("selected")
        compose.onNodeWithContentDescription("Honnan? mező törlése").performClick()
        compose.runOnIdle { assertNull(vm.from); assertEquals(to, vm.to) }
        compose.onAllNodes(isEditable()).assertCountEquals(0)
        compose.onNodeWithContentDescription("Honnan? mező törlése").assertDoesNotExist()
        compose.onNodeWithText("Járatok keresése").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Hová? mező törlése").performClick()
        compose.runOnIdle { assertNull(vm.to); vm.from = from; vm.to = to }

        // Clearing in the editor must also remove the underlying selection.
        compose.onNodeWithText("Budapest-Keleti").performClick()
        compose.onNodeWithContentDescription("Honnan? mező törlése").performClick()
        compose.onNode(isEditable()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))).assertIsFocused()
        compose.runOnIdle { assertNull(vm.from); assertEquals(to, vm.to) }
        compose.onNode(isEditable()).performTextInput("B")
        compose.onNodeWithContentDescription("Honnan? mező törlése").performClick()
        compose.onNode(isEditable()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))).assertIsFocused()
        compose.onNodeWithContentDescription("Honnan? mező törlése").assertDoesNotExist()
        compose.onNodeWithContentDescription("Megállókeresés bezárása").performClick()
        compose.runOnIdle { assertNull(vm.from); vm.from = from }
        compose.onNodeWithText("Győr").performClick()
        compose.onNodeWithContentDescription("Hová? mező törlése").performClick()
        compose.onNode(isEditable()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))).assertIsFocused()
        compose.runOnIdle { assertEquals(from, vm.from); assertNull(vm.to) }
        compose.onNodeWithContentDescription("Megállókeresés bezárása").performClick()
        compose.runOnIdle { vm.to = to; theme = "dark"; fontScale = 1.3f }
        capture("dark-large-text")
        compose.onNodeWithText("Győr").performClick()
        capture("editor-dark-large-text")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(600)
        File(app.getExternalFilesDir(null), "station-clear-$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
