package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun JourneyRefreshLayout(
    refreshKey: Any,
    onRefresh: suspend () -> Unit,
    content: @Composable (refreshError: String?) -> Unit
) {
    var refreshing by remember(refreshKey) { mutableStateOf(false) }
    var error by remember(refreshKey) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val state = rememberPullToRefreshState()
    val refresh: () -> Unit = {
        if (!refreshing) {
            refreshing = true
            error = null
            scope.launch {
                try {
                    // Render the loading state even when the request completes within one frame.
                    withFrameNanos { }
                    coroutineScope {
                        // Keep the release confirmation legible without delaying the actual request.
                        launch { delay(900) }
                        try {
                            onRefresh()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            error = "Nem sikerült minden élő adatot frissíteni. A korábbi adatok láthatók. Húzd le az újrapróbáláshoz."
                        }
                    }
                } finally {
                    refreshing = false
                }
            }
        }
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = refresh,
        state = state,
        modifier = Modifier.fillMaxSize().semantics {
            customActions = listOf(CustomAccessibilityAction("Utazás frissítése") {
                refresh()
                true
            })
        },
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
            )
        }
    ) { content(error) }
}
