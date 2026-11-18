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
import android.view.WindowManager
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Where the tapped photo sits on screen, so the viewer can grow the same picture out of it.
 * [bounds] is the thumbnail's image box in screen pixels; [corner] its clip radius in pixels.
 */
@Immutable internal data class PhotoOrigin(val bounds: Rect, val corner: Float)

/** Keeps one dialog mounted while paging and through its exit motion. */
@Composable internal fun FleetPhotoViewer(
    photos: List<ModelPhoto>, initialPage: Int, initialBitmap: ImageBitmap,
    origin: PhotoOrigin? = null, onShown: () -> Unit = {}, onDismiss: () -> Unit,
) {
    var closing by remember { mutableStateOf(false) }
    // The dialog window draws a few frames after the tap. Until it has drawn the photo
    // exactly over the thumbnail, the article keeps showing its own copy and nothing moves.
    var placed by remember { mutableStateOf(origin == null) }
    val progress = remember { Animatable(0f) }
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { photos.size })
    LaunchedEffect(placed) { if (placed) onShown() }
    // Never leave the viewer stuck invisible if the window is slow to place its content.
    LaunchedEffect(Unit) { delay(500); placed = true }
    LaunchedEffect(closing, placed) {
        if (!closing && !placed) return@LaunchedEffect
        // The photo travels across the screen, so it gets the longer spatial timing;
        // without an origin it keeps the plain arrival and dismissal timings.
        val duration = when {
            origin == null -> if (closing) AppMotion.Dismiss else AppMotion.Reveal
            closing -> AppMotion.Reveal
            else -> AppMotion.StationFocus
        }
        progress.animateTo(if (closing) 0f else 1f, tween(duration, easing = AppMotion.State))
        if (closing) onDismiss()
    }
    Dialog(onDismissRequest = { closing = true }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        val view = LocalView.current
        val lightSurface = MaterialTheme.colorScheme.surface.luminance() > .5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                // The backdrop and photo carry the whole transition; the platform's
                // dialog fade and dim would otherwise flash over the article first.
                window.setWindowAnimations(0)
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightSurface
                    isAppearanceLightNavigationBars = lightSurface
                }
            }
        }
        val surface = MaterialTheme.colorScheme.surface
        Box(Modifier.fillMaxSize().drawBehind { drawRect(surface, alpha = progress.value) }) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().graphicsLayer { alpha = progress.value }
                    .padding(horizontal = 8.dp), verticalAlignment = Alignment.Top) {
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
                    // Only the opened photo has a place to return to; after paging away
                    // the viewer closes with the plain fade.
                    val home = index == initialPage
                    ViewerPhoto(photos[index], if (home) initialBitmap else null,
                        { progress.value }, closing, photos.size > 1,
                        origin.takeIf { home && pager.currentPage == initialPage }) { placed = true }
                }
            }
        }
    }
}

@Composable private fun ViewerPhoto(
    photo: ModelPhoto, initialBitmap: ImageBitmap?, progress: () -> Float, closing: Boolean, multiple: Boolean,
    origin: PhotoOrigin?, onPlaced: () -> Unit,
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
    if (bitmap != null) ZoomablePhoto(bitmap, photo.caption, progress, closing, multiple, origin, onPlaced)
    else Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress() }, contentAlignment = Alignment.Center) {
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

/** The rectangle a [ContentScale.Fit] image occupies, centred inside [frame]. */
private fun fitted(bitmap: ImageBitmap, frame: Rect): Rect {
    val fit = minOf(frame.width / bitmap.width, frame.height / bitmap.height)
    val size = Size(bitmap.width * fit, bitmap.height * fit)
    return Rect(Offset(frame.center.x - size.width / 2f, frame.center.y - size.height / 2f), size)
}

/** Rounds the fitted picture, not the whole viewport, while it still matches the thumbnail. */
private data class PhotoClip(val rect: Rect, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}

@Composable private fun ZoomablePhoto(
    bitmap: ImageBitmap, description: String, progress: () -> Float, closing: Boolean, multiple: Boolean,
    origin: PhotoOrigin?, onPlaced: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var viewportOnScreen by remember { mutableStateOf(Offset.Unspecified) }
    val positioned = viewportOnScreen.isSpecified
    LaunchedEffect(positioned) {
        if (!positioned) return@LaunchedEffect
        // Let one frame draw the photo over the thumbnail before the article hides its copy.
        withFrameNanos {}
        withFrameNanos {}
        onPlaced()
    }
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
            .onGloballyPositioned { viewportOnScreen = it.positionOnScreen() }
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
                val p = progress()
                // Closing from a zoomed view first eases the zoom out with the rest of the motion.
                val zoom = 1f + (scale - 1f) * p
                val target = fitted(bitmap, Rect(Offset.Zero, size))
                val placed = viewportOnScreen.isSpecified && target.width > 0f
                if (origin == null || !placed) {
                    val entrance = .92f + .08f * p
                    scaleX = zoom * entrance
                    scaleY = zoom * entrance
                    translationX = offset.x * p
                    translationY = offset.y * p
                    // Until the viewport is placed on screen the origin cannot be matched;
                    // stay invisible for that frame rather than flash the full-size photo.
                    alpha = if (origin == null) p else 0f
                    clip = false
                    return@graphicsLayer
                }
                // Shared element: the fitted picture starts exactly on the thumbnail's
                // fitted picture and grows into place, keeping its aspect ratio throughout.
                val source = fitted(bitmap, origin.bounds.translate(-viewportOnScreen))
                val grow = lerp(source.width / target.width, 1f, p)
                scaleX = grow * zoom
                scaleY = grow * zoom
                translationX = lerp(source.center.x - target.center.x, 0f, p) + offset.x * p
                translationY = lerp(source.center.y - target.center.y, 0f, p) + offset.y * p
                alpha = 1f
                // Thumbnail corners only show where the picture fills its frame.
                val fills = source.width >= origin.bounds.width - 1f && source.height >= origin.bounds.height - 1f
                val radius = if (fills) origin.corner * (1f - p) else 0f
                clip = radius > 0f
                shape = PhotoClip(target, radius / grow)
            }, contentScale = ContentScale.Fit)
        }
        Row(Modifier.fillMaxWidth().graphicsLayer { alpha = progress() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically,
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
