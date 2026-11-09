package hu.matepazy.menetrendgo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
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

/** Keeps one dialog mounted while paging and through its exit motion. */
@Composable internal fun FleetPhotoViewer(
    photos: List<ModelPhoto>, initialPage: Int, initialBitmap: ImageBitmap,
    onDismiss: () -> Unit,
) {
    var closing by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { photos.size })
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(start = 8.dp, top = 12.dp, bottom = 12.dp)) {
                        Text(photos[pager.currentPage].caption, Modifier.semantics { heading() },
                            color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        if (photos.size > 1) Text("${pager.currentPage + 1} / ${photos.size}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(onClick = { closing = true }, enabled = !closing) {
                        Icon(Icons.Outlined.Close, "Nagyított fotó bezárása", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(),
                    userScrollEnabled = !closing, key = { photos[it].url }) { index ->
                    ViewerPhoto(photos[index], if (index == initialPage) initialBitmap else null,
                        progress.value, closing, photos.size > 1)
                }
            }
        }
    }
}

@Composable private fun ViewerPhoto(
    photo: ModelPhoto, initialBitmap: ImageBitmap?, progress: Float, closing: Boolean, multiple: Boolean,
) {
    val context = LocalContext.current.applicationContext
    var attempt by remember(photo.url) { mutableIntStateOf(0) }
    val result by produceState<Result<ImageBitmap>?>(initialBitmap?.let { Result.success(it) }, photo.url, attempt) {
        if (initialBitmap == null) {
            value = null
            value = try { Result.success(FleetPhotoStore.load(context, photo.url).asImageBitmap()) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { Result.failure(IllegalStateException("Photo unavailable")) }
        }
    }
    val bitmap = result?.getOrNull()
    if (bitmap != null) ZoomablePhoto(bitmap, photo.caption, progress, closing, multiple)
    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(AppLayout.SheetPadding), horizontalAlignment = Alignment.CenterHorizontally) {
            if (result == null) {
                CircularProgressIndicator()
                Text("Fotó betöltése…", Modifier.padding(top = AppLayout.CompactGap))
            } else {
                Text("A webes fotó nem tölthető be. Ellenőrizd az internetkapcsolatot.")
                TextButton(onClick = { attempt++ }) { Text("Fotó újratöltése") }
            }
        }
    }
}

@Composable private fun ZoomablePhoto(
    bitmap: ImageBitmap, description: String, progress: Float, closing: Boolean, multiple: Boolean,
) {
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

    Column(Modifier.fillMaxSize()) {
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
                if (!closing) awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var transforming = scale > 1.05f
                    var accumulatedZoom = 1f
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break
                        // The final finger-up event has no stable pointer, so its
                        // centroid is unspecified. Never feed it into image translation.
                        if (event.changes.none { it.pressed && it.previousPressed }) continue
                        val zoom = event.calculateZoom()
                        accumulatedZoom *= zoom
                        val centroid = event.calculateCentroid(useCurrent = false)
                        // Leave ordinary one-finger drags to the pager. Claim a pinch
                        // only after touch slop, so two-finger taps do not jump the image.
                        if (event.changes.count { it.pressed } > 1 &&
                            kotlin.math.abs(1f - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false) > viewConfiguration.touchSlop) {
                            transforming = true
                        }
                        if (transforming) {
                            val pan = event.calculatePan()
                            zoomAnimation?.cancel()
                            val next = (scale * zoom).coerceIn(1f, 5f)
                            val center = Offset(viewport.width / 2f, viewport.height / 2f)
                            offset = bounded((centroid - center) - (centroid - center - offset) * (next / scale) + pan, next)
                            scale = next
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }) {
            Image(bitmap, null, Modifier.fillMaxSize().graphicsLayer {
                val entrance = .92f + .08f * progress
                scaleX = scale * entrance
                scaleY = scale * entrance
                translationX = offset.x * progress
                translationY = offset.y * progress
            }, contentScale = ContentScale.Fit)
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (multiple) "Lapozz a fotók között. Két ujjal vagy dupla koppintással nagyíthatsz." else "Két ujjal vagy dupla koppintással nagyíthatsz.", Modifier.weight(1f).padding(start = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = { toggleZoom() }, enabled = !closing) {
                Icon(if (scale > 1.05f) Icons.Outlined.ZoomOut else Icons.Outlined.ZoomIn,
                    if (scale > 1.05f) "Nagyítás visszaállítása" else "Fotó nagyítása",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
