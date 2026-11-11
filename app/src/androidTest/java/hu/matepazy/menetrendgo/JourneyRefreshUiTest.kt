package hu.matepazy.menetrendgo

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class JourneyRefreshUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pullRefreshesOnceAndRetainsContentUntilCompletion() = verifyPull(false, 1f)
    @Test fun pullRefreshesInDarkThemeWithLargeText() = verifyPull(true, 1.5f)

    @Test fun immediatelyCompletedRefreshStillShowsLoadingAfterRelease() {
        var calls = 0
        show(refresh = { calls++ }) { Text("Meglévő utazás") }
        pull()
        compose.runOnIdle { assertEquals(1, calls) }
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
        compose.onNodeWithText("Meglévő utazás").assertIsDisplayed()
        awaitRefreshFinished()
    }

    private fun verifyPull(dark: Boolean, fontScale: Float) {
        var calls = 0
        var value by mutableStateOf("Korábbi érkezés")
        val release = CompletableDeferred<Unit>()
        show(dark, fontScale, refresh = {
            calls++
            release.await()
            value = "Friss érkezés"
        }) { Text(value) }
        pull()
        compose.runOnIdle { assertEquals(1, calls) }
        compose.onNodeWithText("Korábbi érkezés").assertIsDisplayed()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
        pull()
        compose.runOnIdle { assertEquals(1, calls) }
        capture(if (dark) "dark-large-refreshing" else "light-refreshing")
        release.complete(Unit)
        awaitRefreshFinished()
        compose.onNodeWithText("Friss érkezés").assertIsDisplayed()
        compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
        capture(if (dark) "dark-large-complete" else "light-complete")
    }

    @Test fun failedRefreshKeepsContentAndAllowsRetry() {
        var calls = 0
        show(refresh = { calls++; if (calls == 1) error("offline") }) { Text("Meglévő utazás") }
        pull()
        compose.onNodeWithText("Meglévő utazás").assertIsDisplayed()
        compose.onNodeWithText("Nem sikerült minden élő adatot frissíteni.", substring = true).assertIsDisplayed()
        awaitRefreshFinished()
        pull()
        compose.runOnIdle { assertEquals(2, calls) }
        awaitRefreshFinished()
        compose.onNodeWithText("Nem sikerült minden élő adatot frissíteni.", substring = true).assertDoesNotExist()
    }

    @Test fun downwardScrollAwayFromTopDoesNotRefresh() {
        var calls = 0
        show(refresh = { calls++ }, longList = true) { Text("Utazás") }
        compose.onNodeWithTag("journey-list").performScrollToIndex(20)
        pull()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    private fun show(dark: Boolean = false, fontScale: Float = 1f, refresh: suspend () -> Unit,
        longList: Boolean = false, body: @Composable () -> Unit) {
        compose.setContent {
            MenetrendTheme(if (dark) "dark" else "light") {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        JourneyRefreshLayout(Unit, refresh) { error ->
                            LazyColumn(Modifier.fillMaxSize().testTag("journey-list"), contentPadding = screenContentPadding()) {
                                item { PageTitle("Utazás részletei", null, {}) }
                                if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error) }
                                item { body() }
                                if (longList) items(40) { Text("Megálló $it", Modifier.height(80.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pull() {
        compose.onNodeWithTag("journey-list").performTouchInput {
            swipe(Offset(centerX, height * .2f), Offset(centerX, height * .85f), 650)
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(instrumentation.targetContext.getExternalFilesDir(null), "journey-pull-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun awaitRefreshFinished() {
        compose.waitUntil(3_000) {
            compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
                .fetchSemanticsNodes().isEmpty()
        }
    }
}
