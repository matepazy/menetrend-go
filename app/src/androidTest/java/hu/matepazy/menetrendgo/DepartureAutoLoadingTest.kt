package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DepartureAutoLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bottomLoadsOncePerBatchAndPausesOnFailureOrExhaustion() {
        var count by mutableIntStateOf(12)
        var loading by mutableStateOf(false)
        var failed by mutableStateOf(false)
        var hasMore by mutableStateOf(true)
        var requests = 0
        compose.setContent {
            val state = rememberLazyListState()
            DepartureAutoLoading(state, hasMore && !loading && !failed) {
                requests++
                loading = true
            }
            LazyColumn(Modifier.height(300.dp).testTag("departures"), state = state) {
                items(count, key = { "departure-$it" }) { Text("Departure $it", Modifier.height(100.dp)) }
                item(key = DeparturePaginationKey) { Text("More", Modifier.height(52.dp)) }
            }
        }
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithTag("departures").performScrollToIndex(count)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, requests) }
        // Reaching the footer again while a request is running must not duplicate it.
        compose.onNodeWithTag("departures").performScrollToIndex(0)
        compose.onNodeWithTag("departures").performScrollToIndex(count)
        compose.runOnIdle { assertEquals(1, requests); count += 12 }
        compose.waitForIdle()
        compose.runOnIdle { loading = false }
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithTag("departures").performScrollToIndex(count)
        compose.runOnIdle { assertEquals(2, requests); failed = true; loading = false }
        compose.runOnIdle { assertEquals(2, requests) }
        compose.onNodeWithTag("departures").performScrollToIndex(0)
        compose.onNodeWithTag("departures").performScrollToIndex(count)
        compose.runOnIdle { assertEquals(2, requests); hasMore = false; failed = false }
        compose.runOnIdle { assertEquals(2, requests) }
    }
}
