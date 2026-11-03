package hu.matepazy.menetrendgo

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class DrawerGestureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expandableDrawerPredictiveBackShrinksCancelsAndAnimatesBeforeDismissal() {
        lateinit var dispatcher: OnBackPressedDispatcher
        val open = mutableStateOf(true)
        compose.setContent {
            MenetrendTheme("light") {
                if (open.value) AppExpandableBottomSheet(
                    onDismissRequest = { open.value = false }, initialHeightFraction = .78f,
                ) {
                    dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                    Box(Modifier.fillMaxWidth().height(200.dp).testTag("backSheet"))
                }
            }
        }
        compose.waitForIdle()
        val sheet = compose.onNodeWithTag("backSheet")
        val original = sheet.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        fun progress(edge: Int) {
            compose.runOnUiThread {
                dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 300f, 0f, edge))
            }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnUiThread {
                dispatcher.dispatchOnBackProgressed(BackEventCompat(200f, 300f, .7f, edge))
            }
            compose.mainClock.advanceTimeByFrame()
        }
        progress(BackEventCompat.EDGE_LEFT)
        val preview = sheet.fetchSemanticsNode().boundsInRoot
        assertTrue("Back preview must shrink the sheet", preview.width < original.width)
        assertEquals("Back must shrink content uniformly without squeezing it",
            preview.width / original.width, preview.height / original.height, .002f)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals("Cancelled Back must restore the sheet", original.width,
            sheet.fetchSemanticsNode().boundsInRoot.width, 1f)
        assertTrue(open.value)
        progress(BackEventCompat.EDGE_RIGHT)
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame()
        assertTrue("The dialog must remain until the sheet finishes sliding out", open.value)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) { !open.value }
        sheet.assertDoesNotExist()
    }

    @Test fun expandableDrawerSupportsNestedScrollingAndSwipeDismissal() {
        var dismissed = false
        val open = mutableStateOf(true)
        compose.setContent {
            MenetrendTheme("light") {
                if (open.value) AppExpandableBottomSheet(onDismissRequest = { dismissed = true; open.value = false }, initialHeightFraction = .78f) {
                    Text("Vehicle header", Modifier.testTag("vehicleHeader"))
                    LazyColumn(Modifier.weight(1f).testTag("vehicleStops")) {
                        items(100) { Text("Stop $it", Modifier.padding(24.dp)) }
                    }
                }
            }
        }
        compose.waitForIdle()
        val header = compose.onNodeWithTag("vehicleHeader")
        val initialTop = header.fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("vehicleStops").performTouchInput { swipeUp(durationMillis = 300) }
        val expandedTop = header.fetchSemanticsNode().boundsInRoot.top
        assertTrue("Scrolling stops must expand the sheet first", expandedTop < initialTop)
        repeat(2) {
            compose.onNodeWithTag("vehicleStops").performTouchInput { swipeUp(durationMillis = 100) }
            assertEquals(expandedTop, header.fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        compose.onNodeWithTag("vehicleStops").performScrollToIndex(0)
        compose.onNodeWithTag("vehicleStops").performTouchInput { swipeDown(durationMillis = 300) }
        compose.waitUntil(5_000) { dismissed }
    }

    @Test fun vehicleArticleUsesFullHeightAndClosesBackToItsParentDrawer() {
        compose.setContent {
            MenetrendTheme("light") {
                var open by remember { mutableStateOf(true) }
                AppBottomSheet(onDismissRequest = {}) {
                    Text("Parent vehicle drawer")
                    if (open) VehicleModelDrawer("Stadler FLIRT") { open = false }
                }
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("A járműről").fetchSemanticsNodes().isNotEmpty()
        }
        val title = compose.onNodeWithText("Stadler FLIRT")
        val initialTop = title.fetchSemanticsNode().boundsInRoot.top
        // The heading sits immediately beneath the safe top and native drag handle,
        // rather than starting at the former 78%-height position.
        val rootHeight = compose.onAllNodes(isRoot()).fetchSemanticsNodes().maxOf { it.boundsInRoot.height }
        assertTrue("Vehicle article still has a fractional height cap", initialTop < rootHeight * .2f)
        repeat(3) {
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .performTouchInput { swipeUp(durationMillis = 100) }
            assertEquals(initialTop, title.fetchSemanticsNode().boundsInRoot.top, 1f)
        }
        compose.onNodeWithText("Fotó és források").performScrollTo().performClick()
        compose.onNodeWithText("Az ismertető forrásai").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Járműtípus bemutatásának bezárása").performClick()
        compose.onNodeWithText("Parent vehicle drawer").assertIsDisplayed()
        title.assertDoesNotExist()
    }

    @Test fun fullHeightDrawerStaysBelowStatusBarWhileScrollingAndStillDismisses() {
        var safeTop = 0
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                safeTop = WindowInsets.safeDrawing.getTop(LocalDensity.current)
                val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                AppBottomSheet(onDismissRequest = { dismissed = true }, sheetState = state,
                    modifier = Modifier.testTag("drawer")) {
                    LazyColumn(Modifier.fillMaxWidth().testTag("article")) {
                        items(100) { Text("Article paragraph $it", Modifier.padding(24.dp)) }
                    }
                }
            }
        }
        compose.waitForIdle()
        assertTrue("Device must have a status-bar inset", safeTop > 0)
        fun assertSafeTop() {
            val top = compose.onNodeWithTag("drawer").fetchSemanticsNode().boundsInRoot.top
            assertTrue("Drawer moved into status bar: $top < $safeTop", top >= safeTop - 1f)
        }
        assertSafeTop()
        repeat(4) {
            compose.onNodeWithTag("article").performTouchInput { swipeUp(durationMillis = 100) }
            assertSafeTop()
        }
        // A small downward drag followed by an upward fling used to change the top inset
        // and expanded anchor while the sheet was settling.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("drawer").performTouchInput {
            down(center.copy(y = 20f))
            moveBy(androidx.compose.ui.geometry.Offset(0f, 80f), delayMillis = 100)
            moveBy(androidx.compose.ui.geometry.Offset(0f, -300f), delayMillis = 32)
            up()
        }
        repeat(90) {
            compose.mainClock.advanceTimeByFrame()
            assertSafeTop()
        }
        compose.mainClock.autoAdvance = true
        // Scroll back to the start without flinging the sheet closed.
        compose.onNodeWithTag("article").performScrollToIndex(0)
        compose.onNodeWithTag("article").performTouchInput { swipeDown(durationMillis = 150) }
        compose.waitUntil(5_000) { dismissed }
    }
}
