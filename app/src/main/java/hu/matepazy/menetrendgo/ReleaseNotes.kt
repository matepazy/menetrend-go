package hu.matepazy.menetrendgo

import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import kotlin.math.roundToInt

/** Native, accessible Markdown text; Compose owns the surrounding sheet's scrolling. */
@Composable
internal fun ReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val renderer = remember(context) {
        Markwon.builder(context).usePlugin(StrikethroughPlugin.create()).build()
    }
    val rendered = remember(renderer, markdown) { renderer.toMarkdown(markdown) }
    val style = MaterialTheme.typography.bodyMedium
    val density = LocalDensity.current
    val textSize = with(density) { style.fontSize.toPx() }
    val lineHeight = with(density) { style.lineHeight.toPx() }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                setPadding(0, 0, 0, 0)
                includeFontPadding = false
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { view ->
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
            view.setLineHeight(lineHeight.roundToInt())
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            renderer.setParsedMarkdown(view, rendered)
        },
    )
}
