package hu.matepazy.menetrendgo

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** The right edge stays fixed while the search grows toward the Leaflet zoom control. */
@Composable internal fun MapSearch(
    query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun close() {
        focus.clearFocus()
        keyboard?.hide()
        onQuery("")
        expanded = false
    }
    fun finishEditing() {
        focus.clearFocus()
        keyboard?.hide()
    }
    BackHandler(expanded) { close() }
    LaunchedEffect(expanded) {
        if (expanded) {
            // The editor starts at zero width inside the collapsed pill. Let its first
            // expanded layout land before opening an Android input connection.
            withFrameNanos { }
            requester.requestFocus()
            keyboard?.show()
        }
    }
    BoxWithConstraints(modifier.padding(start = 70.dp, end = 10.dp, top = 10.dp)) {
        val width by animateDpAsState(if (expanded) maxWidth else 48.dp,
            tween(if (expanded) AppMotion.Reveal else AppMotion.Dismiss, easing = AppMotion.State),
            label = "Map search width")
        Column(Modifier.align(Alignment.TopEnd).width(width), horizontalAlignment = Alignment.End) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(CircleShape), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (expanded) { requester.requestFocus(); keyboard?.show() } else expanded = true
                    }) {
                        Icon(Icons.Outlined.Search, "Keresés a térképen")
                    }
                    if (expanded) {
                        BasicTextField(query, { onQuery(it.take(120)) },
                            Modifier.weight(1f).focusRequester(requester).semantics { contentDescription = "Járat vagy jármű keresése" },
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
        }
    }
}
