package hu.matepazy.menetrendgo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Reuses the decoded photo. The dialog stays mounted until its exit motion finishes. */
@Composable internal fun FleetPhotoViewer(bitmap: ImageBitmap, description: String, onDismiss: () -> Unit) {
    var closing by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    var zoomAnimation by remember { mutableStateOf<Job?>(null) }

    fun bounded(value: Offset, zoom: Float): Offset {
        if (viewport.width == 0 || viewport.height == 0) return Offset.Zero
        val fit = minOf(viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height)
        val x = ((bitmap.width * fit * zoom - viewport.width) / 2f).coerceAtLeast(0f)
        val y = ((bitmap.height * fit * zoom - viewport.height) / 2f).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }

    fun toggleZoom(focus: Offset = Offset(viewport.width / 2f, viewport.height / 2f)) {
        zoomAnimation?.cancel()
        val startScale = scale
        val startOffset = offset
        val endScale = if (scale > 1.05f) 1f else 2.5f
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val endOffset = bounded((focus - center) - (focus - center - offset) * (endScale / scale), endScale)
        zoomAnimation = scope.launch {
            animate(0f, 1f, animationSpec = tween(AppMotion.Reveal, easing = AppMotion.State)) { value, _ ->
                scale = startScale + (endScale - startScale) * value
                offset = bounded(startOffset + (endOffset - startOffset) * value, scale)
            }
        }
    }

    LaunchedEffect(closing) {
        progress.animateTo(if (closing) 0f else 1f,
            tween(if (closing) AppMotion.Dismiss else AppMotion.Reveal, easing = AppMotion.State))
        if (closing) onDismiss()
    }
    Dialog(onDismissRequest = { closing = true }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        val view = LocalView.current
        val lightSurface = MaterialTheme.colorScheme.surface.luminance() > .5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightSurface
                    isAppearanceLightNavigationBars = lightSurface
                }
            }
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value }
            .background(MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Fotó", Modifier.weight(1f).padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { closing = true }) {
                        Icon(Icons.Outlined.Close, "Nagyított fotó bezárása", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()
                    .onSizeChanged { viewport = it; offset = bounded(offset, scale) }
                    .semantics {
                        contentDescription = description
                        stateDescription = "Nagyítás: ${(scale * 100).toInt()}%"
                        customActions = listOf(CustomAccessibilityAction(if (scale > 1.05f) "Nagyítás visszaállítása" else "Fotó nagyítása") {
                            toggleZoom(); true
                        })
                    }
                    .pointerInput(closing) {
                        if (!closing) detectTapGestures(onDoubleTap = { toggleZoom(it) })
                    }
                    .pointerInput(closing) {
                        if (!closing) detectTransformGestures { centroid, pan, zoom, _ ->
                            zoomAnimation?.cancel()
                            val next = (scale * zoom).coerceIn(1f, 5f)
                            val center = Offset(viewport.width / 2f, viewport.height / 2f)
                            offset = bounded((centroid - center) - (centroid - center - offset) * (next / scale) + pan, next)
                            scale = next
                        }
                    }) {
                    Image(bitmap, null, Modifier.fillMaxSize().graphicsLayer {
                        val entrance = .92f + .08f * progress.value
                        scaleX = scale * entrance
                        scaleY = scale * entrance
                        translationX = offset.x * progress.value
                        translationY = offset.y * progress.value
                    }, contentScale = ContentScale.Fit)
                }
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Két ujjal vagy dupla koppintással nagyíthatsz.", Modifier.weight(1f).padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { toggleZoom() }, enabled = !closing) {
                        Icon(if (scale > 1.05f) Icons.Outlined.ZoomOut else Icons.Outlined.ZoomIn,
                            if (scale > 1.05f) "Nagyítás visszaállítása" else "Fotó nagyítása",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
