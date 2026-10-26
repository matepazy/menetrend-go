package hu.matepazy.menetrendgo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Focus shares the planner's app-wide scrim and frees the full map toolbar width. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun MapSearch(
    query: String, onQuery: (String) -> Unit, refreshing: Boolean, onRefresh: () -> Unit,
    modifier: Modifier = Modifier, focused: Boolean = false, onFocusChange: (Boolean) -> Unit = {}
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val spotlight = LocalStationFocus.current
    fun close() {
        onFocusChange(false)
        focus.clearFocus()
        keyboard?.hide()
        onQuery("")
        expanded = false
    }
    fun finishEditing() {
        onFocusChange(false)
        focus.clearFocus()
        keyboard?.hide()
    }
    BackHandler(expanded) { if(focused) finishEditing() else close() }
    val dismiss by rememberUpdatedState<() -> Unit>({ finishEditing() })
    if(focused) DisposableEffect(Unit) {
        spotlight.dismiss={dismiss()}
        onDispose {spotlight.dismiss=null;spotlight.bounds=null}
    }
    val keyboardVisible=WindowInsets.isImeVisible
    var keyboardWasVisible by remember {mutableStateOf(false)}
    LaunchedEffect(focused,keyboardVisible) {
        if(!focused) keyboardWasVisible=false
        else if(keyboardVisible) keyboardWasVisible=true
        else if(keyboardWasVisible) finishEditing()
    }
    LaunchedEffect(expanded,focused) {
        if (expanded && focused) {
            // The editor starts at zero width inside the collapsed pill. Let its first
            // expanded layout land before opening an Android input connection.
            withFrameNanos { }
            requester.requestFocus()
            keyboard?.show()
        }
    }
    BoxWithConstraints(modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
        val width by animateDpAsState(if (expanded) {
                if(focused) maxWidth else (maxWidth-56.dp).coerceAtLeast(48.dp)
            } else 48.dp,
            tween(if (focused) AppMotion.StationFocus else AppMotion.Dismiss, easing = AppMotion.Arrival),
            label = "Map search width")
        Column(Modifier.align(Alignment.TopEnd).width(width), horizontalAlignment = Alignment.End) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface,
                modifier=Modifier.onGloballyPositioned {if(focused) spotlight.bounds=it.boundsInWindow()}) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CircleShape), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        expanded = true
                        onFocusChange(true)
                        if(focused) {requester.requestFocus();keyboard?.show()}
                    }) {
                        Icon(Icons.Outlined.Search, "Keresés a térképen")
                    }
                    if (expanded) {
                        BasicTextField(query, { onQuery(it.take(120)) },
                            Modifier.weight(1f).focusRequester(requester).onFocusChanged {if(it.isFocused) onFocusChange(true)}
                                .semantics { contentDescription = "Járat vagy jármű keresése" },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { finishEditing() }),
                            decorationBox = { editor ->
                                Box {
                                    if (query.isEmpty()) Text("Járat, jármű…", maxLines = 1,
                                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    editor()
                                }
                            })
                        IconButton(onClick = { close() }) { Icon(Icons.Outlined.Close, "Keresés bezárása") }
                    }
                }
            }
            AnimatedVisibility(!focused,enter=fadeIn(tween(AppMotion.Feedback)),exit=fadeOut(tween(AppMotion.Feedback))) {
                Surface(modifier=Modifier.padding(top=AppLayout.CompactGap),shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                    IconButton(onClick = onRefresh, enabled = !refreshing && !focused, modifier = Modifier.size(48.dp)
                        .semantics { contentDescription = if (refreshing) "Járműpozíciók frissítése folyamatban" else "Járműpozíciók frissítése" }) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Refresh, contentDescription = null)
                    }
                }
            }
        }
    }
}
