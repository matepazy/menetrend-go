package hu.matepazy.menetrendgo

import android.animation.ValueAnimator
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

private val BarHeight = 24.dp
private val TrackHeight = 3.dp
private val Headroom = 1.dp
private val EdgeFade = 16.dp

/**
 * The app's progress bar: one of the website's footer vehicles, picked at random for each display, drives along
 * a rounded track. Without [progress] it passes across in a loop, filling the track behind itself and trailing
 * speed lines, and glides in and out through faded track ends. With [progress] the track fills to the exact value
 * while the vehicle drives ahead of it to the end of the line. With animations off the vehicle waits mid-track
 * over an empty track, so nothing reads as partial progress.
 */
@Composable internal fun VehicleProgress(modifier: Modifier = Modifier, progress: Float? = null) {
    val vehicle = remember { FooterVehicle.entries.random() }
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    val resources = LocalResources.current
    val artHeight = with(LocalDensity.current) { (BarHeight - TrackHeight - Headroom).roundToPx() }
    var art by remember(vehicle, artHeight) { mutableStateOf(VehicleArt.cached(vehicle, artHeight)) }
    LaunchedEffect(vehicle, artHeight) { if (art == null) art = VehicleArt.load(resources, vehicle, artHeight) }
    val artAlpha by animateFloatAsState(if (art != null) 1f else 0f, tween(AppMotion.Reveal, easing = AppMotion.Arrival), label = "vehicle arrival")
    val fill = ProgressIndicatorDefaults.linearColor
    val track = ProgressIndicatorDefaults.linearTrackColor
    val wake = MaterialTheme.colorScheme.outline
    // The clock starts once the artwork is ready, so every first pass enters from the left edge.
    var clock by remember { mutableFloatStateOf(0f) }
    if (motion) LaunchedEffect(art != null) {
        if (art == null) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) clock = withFrameNanos { (it - start) / 1e9f }
    }
    val value = progress?.coerceIn(0f, 1f)
    val shown = value?.let { animateFloatAsState(it, tween(AppMotion.Reveal, easing = AppMotion.State), label = "vehicle progress").value }
    val semantics = if (value == null) Modifier.progressSemantics() else Modifier.progressSemantics(value)
    Canvas(modifier.then(semantics).fillMaxWidth().height(BarHeight).clipToBounds()) {
        val line = TrackHeight.toPx()
        val bottom = size.height - line
        val h = artHeight.toFloat()
        val w = art?.width?.toFloat() ?: (h * vehicle.aspect)
        val looping = shown == null && motion
        val nose = when {
            // Ahead of the fill, at least partly in view at 0% and fully arrived at 100%.
            shown != null -> minOf(w, size.width * .35f).let { it + (size.width - it) * shown }
            !motion -> (size.width + w) / 2
            // Twice the footer's pace keeps a pass brief even for the five-car Szili; then a short empty gap.
            else -> vehicle.speed * h * 2f * (clock % ((size.width + w) / (vehicle.speed * h * 2f) + .35f))
        }
        drawRoundRect(track, Offset(0f, bottom), Size(size.width, line), CornerRadius(line / 2))
        // Looping, the fill follows the nose and drains from the left as the vehicle leaves; determinate, it is the value.
        val start = if (looping) ((nose - size.width) / w * size.width).coerceIn(0f, size.width) else 0f
        val end = when { shown != null -> size.width * shown; looping -> nose.coerceIn(0f, size.width); else -> 0f }
        if (end > start) drawRoundRect(fill, Offset(start, bottom), Size(end - start, line), CornerRadius(line / 2))
        val image = art ?: return@Canvas
        val drawVehicle = { vehicleLayer(image, vehicle, nose - w, bottom, h, clock, motion, looping, wake, artAlpha) }
        if (!looping) drawVehicle() else {
            // Fade the ends so vehicles glide in and out rather than being cut by the bar's edge.
            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            drawVehicle()
            val edge = (EdgeFade.toPx() / size.width).coerceAtMost(.25f)
            drawRect(Brush.horizontalGradient(0f to Color.Transparent, edge to Color.Black, 1f - edge to Color.Black, 1f to Color.Transparent),
                blendMode = BlendMode.DstIn)
            drawContext.canvas.restore()
        }
    }
}

/** The vehicle with the footer's motion: trains bob, buses rock about their rear axle, the Desiro puffs exhaust. */
private fun DrawScope.vehicleLayer(image: ImageBitmap, vehicle: FooterVehicle, x: Float, bottom: Float, h: Float, clock: Float,
                                   motion: Boolean, looping: Boolean, wake: Color, alpha: Float) {
    val unit = h / 124f
    if (looping) speedLines(wake.copy(alpha = wake.alpha * alpha), h, bottom, x, clock, scale = .5f)
    translate(x, bottom - h) {
        val p = when { !motion -> 0f; vehicle.bus -> .5f - .5f * cos(PI.toFloat() * clock / vehicle.period)
            else -> .5f - .5f * cos(2f * PI.toFloat() * clock / vehicle.period) }
        val pivot = if (vehicle.bus) Offset(vehicle.rockX * unit, 108f * unit) else Offset.Zero
        rotate(if (vehicle.bus && motion) -.25f + .45f * p else 0f, pivot) {
            translate(0f, -vehicle.bob * unit * p) {
                drawImage(image, alpha = alpha)
                if (motion) vehicle.puffs.forEach { exhaust(it, unit, clock) }
            }
        }
    }
}

/**
 * Footer artwork pre-scaled to the bar's exact pixel height. The source is 372px tall, so drawing it scaled down
 * every frame would shimmer as it moves; decoding with power-of-two subsampling and one filtered resize keeps the
 * edges clean. Decoding runs off the main thread; at most one entry per vehicle and density stays in memory.
 */
private object VehicleArt {
    private val cache = HashMap<Pair<FooterVehicle, Int>, ImageBitmap>()

    fun cached(vehicle: FooterVehicle, height: Int): ImageBitmap? = synchronized(cache) { cache[vehicle to height] }

    suspend fun load(resources: Resources, vehicle: FooterVehicle, height: Int): ImageBitmap = cached(vehicle, height) ?: withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, vehicle.art, bounds)
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= height) sample *= 2
        val raw = BitmapFactory.decodeResource(resources, vehicle.art, BitmapFactory.Options().apply { inSampleSize = sample })
        val scaled = Bitmap.createScaledBitmap(raw, (height * raw.width / raw.height.toFloat()).roundToInt(), height, true)
        if (scaled !== raw) raw.recycle()
        scaled.asImageBitmap().also { synchronized(cache) { cache[vehicle to height] = it } }
    }
}
