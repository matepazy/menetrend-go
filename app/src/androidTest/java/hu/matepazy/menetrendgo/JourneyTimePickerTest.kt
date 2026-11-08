package hu.matepazy.menetrendgo

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class JourneyTimePickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pressingModeDoesNotAddBackgroundHighlight() {
        withPicker { _ ->
            val departure=compose.onNodeWithText("Indulás")
            val arrival=compose.onNodeWithText("Érkezés")
            fun background(node: SemanticsNodeInteraction) = node.captureToImage().toPixelMap().let {
                it[it.width/2, it.height/4]
            }
            val idle=background(arrival)
            val selected=background(departure)
            arrival.performTouchInput { down(center) }
            try {
                compose.waitForIdle()
                assertEquals("Holding an unselected mode must not add a gray overlay", idle, background(arrival))
                assertEquals(selected, background(departure))
            } finally {
                arrival.performTouchInput { up() }
            }
            arrival.assertIsSelected()
            assertEquals("Selection settles on the blue pill", selected, background(arrival))
        }
    }

    @Test fun selectionPillTravelsAcrossTheTrack() {
        withPicker { _ ->
            val track=compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)
                and hasAnyDescendant(hasText("Indulás")))
            fun centerColor() = track.captureToImage().toPixelMap().let { it[it.width/2, it.height/2] }
            val idle=centerColor()
            val blue=compose.onNodeWithText("Indulás").captureToImage().toPixelMap().let {
                it[it.width/2, it.height/4]
            }
            compose.mainClock.autoAdvance=false
            try {
                compose.onNodeWithText("Érkezés").performClick()
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeBy(96)
                assertEquals("One blue pill crosses the middle during selection", blue, centerColor())
                compose.mainClock.advanceTimeBy(2_000)
                assertEquals("The middle clears once the pill reaches arrival", idle, centerColor())
                compose.onNodeWithText("Érkezés").assertIsSelected()
            } finally {
                compose.mainClock.autoAdvance=true
            }
        }
    }

    @Test fun clockSelectionConfirmsArrival() {
        withPicker { vm ->
            compose.onNodeWithContentDescription("22", substring = true).performClick()
            compose.waitUntil(3_000) {
                compose.onAllNodesWithContentDescription("30", substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("30", substring = true).performClick()
            compose.onNodeWithText("Érkezés").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithText("Indulás").assertIsNotSelected()
            compose.onNodeWithText("Kész").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(LocalDateTime.of(2026, 11, 10, 22, 30), vm.dateTime)
                assertFalse(vm.useNow)
                assertTrue(vm.arriveBy)
            }
        }
    }

    @Test fun departureSelectionConfirmsWithoutChangingTime() {
        withPicker { vm ->
            compose.onNodeWithText("Érkezés").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithText("Indulás").performClick().assertIsSelected()
            compose.onNodeWithText("Érkezés").assertIsNotSelected()
            compose.onNodeWithText("Kész").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(LocalDateTime.of(2026, 11, 10, 21, 1), vm.dateTime)
                assertFalse(vm.arriveBy)
                assertFalse(vm.useNow)
            }
        }
    }

    @Test fun cancelDiscardsEdits() {
        withPicker { vm ->
            compose.onNodeWithText("Érkezés").performScrollTo().performClick()
            compose.onNodeWithText("Mégse").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(LocalDateTime.of(2026, 11, 10, 21, 1), vm.dateTime)
                assertFalse(vm.arriveBy)
                assertFalse(vm.useNow)
            }
        }
    }

    @Test fun leaveNowResetsArrival() {
        withPicker { vm ->
            compose.onNodeWithText("Érkezés").performScrollTo().performClick()
            compose.onNodeWithText("Indulás most").performScrollTo().performClick()
            compose.runOnIdle {
                assertTrue(vm.useNow)
                assertFalse(vm.arriveBy)
            }
        }
    }

    private fun withPicker(check: (AppState) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ViewModelStore()
        lateinit var vm: AppState
        val open = mutableStateOf(true)
        compose.runOnIdle {
            vm = AppState(context.applicationContext as Application).also { store.put("picker", it) }
            vm.dateTime = LocalDateTime.of(2026, 11, 10, 21, 1)
            vm.useNow = false
            vm.arriveBy = false
        }
        try {
            compose.setContent { MenetrendTheme(InstrumentationRegistry.getArguments().getString("theme") ?: "light") {
                if (open.value) JourneyTimePicker(vm) { open.value = false }
            } }
            compose.onNodeWithText("Indulás").assertIsSelected()
            compose.onNodeWithText("Érkezés").assertIsNotSelected()
            compose.onNodeWithText("Idő beírása").assertDoesNotExist()
            compose.onNodeWithText("Ekkorra érkezzek meg").assertDoesNotExist()
            compose.waitForIdle()
            val screenshot = java.io.File(context.getExternalFilesDir(null), "journey-time-picker.png")
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
                screenshot.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            check(vm)
        } finally {
            compose.runOnIdle { store.clear() }
        }
    }
}
