package hu.matepazy.menetrendgo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Geometry of the phone navigation's liquid selection pill, in pixels of the item row. */
internal object LiquidIndicator {
    fun slotWidth(total: Float, count: Int, gap: Float) = if (count <= 0) 0f else ((total - gap * (count - 1)) / count).coerceAtLeast(0f)
    fun slotLeft(index: Int, slot: Float, gap: Float) = index * (slot + gap)
    /** The slot whose centre is nearest to [center]. */
    fun nearest(center: Float, slot: Float, gap: Float, count: Int) =
        if (count <= 0 || slot + gap <= 0f) 0 else ((center - slot / 2) / (slot + gap)).roundToInt().coerceIn(0, count - 1)
    /** A stretched pill thins and a compressed one swells, so its area reads as conserved. */
    fun heightFactor(width: Float, slot: Float) = if (slot <= 0f) 1f else (1f - .16f * (width - slot) / slot).coerceIn(.84f, 1.06f)
    /** How much of a slot the pill covers, 0..1, relative to the narrower of the two. */
    /** Past [min]/[max] the pill resists, moving at most [limit] further, like stretched liquid. */
    fun rubberBand(x: Float, min: Float, max: Float, limit: Float): Float {
        fun ease(d: Float) = if (limit <= 0f) 0f else limit * (1f - 1f / (d / limit * .55f + 1f))
        return when { x < min -> min - ease(min - x); x > max -> max + ease(x - max); else -> x }
    }
    fun coverage(itemLeft: Float, slot: Float, pillLeft: Float, pillRight: Float): Float {
        val overlap = min(itemLeft + slot, pillRight) - max(itemLeft, pillLeft)
        val basis = min(slot, pillRight - pillLeft)
        return if (basis <= 0f) 0f else (overlap / basis).coerceIn(0f, 1f)
    }
}

/**
 * Whether the phone navigation pill is still travelling. A destination that inflates a platform view
 * (the map) waits for it, so the main thread stays free and the pill's motion never stalls on loading.
 */
internal val LocalNavigationInMotion = staticCompositionLocalOf<() -> Boolean> { { false } }

/** False until the navigation pill has settled (or [timeoutMillis] passed); then stays true. */
@Composable internal fun rememberNavigationSettled(timeoutMillis: Long = 1200): Boolean {
    val moving = LocalNavigationInMotion.current
    var settled by remember { mutableStateOf(!moving()) }
    if (!settled) LaunchedEffect(Unit) {
        withTimeoutOrNull(timeoutMillis) { snapshotFlow { moving() }.first { !it } }
        // One more frame so the pill's last spring step draws before the heavy work starts.
        withFrameNanos { }
        settled = true
    }
    return settled
}

private val LeadEdge = spring<Float>(dampingRatio = .78f, stiffness = 900f)
private val TrailEdge = spring<Float>(dampingRatio = .6f, stiffness = 260f)
// Drag smoothing time constants (seconds): the leading edge hugs the finger, the trailing edge lags.
private const val DragLeadLag = .028f
private const val DragTrailLag = .095f

/**
 * Phone navigation row with one shared selection pill. The edge facing the motion leads on a stiff
 * spring and the other follows softly, so the pill stretches, then settles with a small wobble.
 * While dragged, a per-frame follower keeps the pill under the finger; on release it springs, with
 * the drag's velocity, to the nearest destination.
 */
@Composable internal fun LiquidNavigationBar(
    items: List<Pair<String, ImageVector>>,
    selected: Int,
    largeFont: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onMotion: (Boolean) -> Unit = {},
    liquid: Boolean = true,
    labels: Boolean = true,
) {
    val currentLiquid by rememberUpdatedState(liquid)
    val gap = with(LocalDensity.current) { 4.dp.toPx() }
    val corner = with(LocalDensity.current) { 26.dp.toPx() }
    var width by remember { mutableFloatStateOf(0f) }
    val count = items.size
    val slot = LiquidIndicator.slotWidth(width, count, gap)
    val left = remember { Animatable(0f) }
    val right = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragCenter by remember { mutableFloatStateOf(0f) }
    var dragDirection by remember { mutableFloatStateOf(0f) }
    // Edge velocities (px/s) at release, handed to the settle springs so motion stays continuous.
    var release by remember { mutableStateOf(0f to 0f) }
    var target by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    val selectedPos = selected.coerceIn(0, (count - 1).coerceAtLeast(0))

    LaunchedEffect(selectedPos, slot) {
        if (slot <= 0f) return@LaunchedEffect
        val l = LiquidIndicator.slotLeft(selectedPos, slot, gap)
        if (!placed) { left.snapTo(l); right.snapTo(l + slot); placed = true }
        if (!dragging) target = l to l + slot
    }
    LaunchedEffect(Unit) {
        snapshotFlow { target }.filterNotNull().collectLatest { (l, r) ->
            val movingRight = l + r > left.value + right.value
            val (lv, rv) = release.also { release = 0f to 0f }
            // Without the liquid motion the pill jumps straight to its destination.
            if (!currentLiquid) { left.snapTo(l); right.snapTo(r); return@collectLatest }
            coroutineScope {
                launch { left.animateTo(l, if (movingRight) TrailEdge else LeadEdge, lv) }
                launch { right.animateTo(r, if (movingRight) LeadEdge else TrailEdge, rv) }
            }
        }
    }
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        target = null
        var last = withFrameNanos { it }
        while (dragging) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceIn(.001f, .05f); last = now
            val half = slot / 2
            val movingRight = dragDirection >= 0f
            // Frame-rate independent exponential follow: no overshoot and no restarts between touch events.
            fun step(from: Float, to: Float, lag: Float) = from + (to - from) * (1f - exp(-dt / lag))
            val l = step(left.value, dragCenter - half, if (movingRight) DragTrailLag else DragLeadLag)
            val r = step(right.value, dragCenter + half, if (movingRight) DragLeadLag else DragTrailLag)
            release = (l - left.value) / dt to (r - right.value) / dt
            left.snapTo(l); right.snapTo(r)
        }
    }

    val haptics = LocalHapticFeedback.current
    val currentSelected by rememberUpdatedState(selectedPos)
    val currentSlot by rememberUpdatedState(slot)
    val currentMotion by rememberUpdatedState(onMotion)
    // Flag the motion in the same snapshot as the selection, before the destination composes.
    val currentSelect by rememberUpdatedState<(Int) -> Unit> { index ->
        if (index != selectedPos) onMotion(true)
        onSelect(index)
    }
    LaunchedEffect(Unit) {
        snapshotFlow {
            val tolerance = currentSlot * .02f + 1f
            val l = LiquidIndicator.slotLeft(currentSelected, currentSlot, gap)
            !placed || !dragging && abs(left.value - l) < tolerance && abs(right.value - l - currentSlot) < tolerance &&
                abs(left.velocity) < currentSlot * .6f && abs(right.velocity) < currentSlot * .6f
        }.collect { currentMotion(!it) }
    }
    val pill = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .65f)
    val pillTone = pillGlassTone()
    // Held under the finger, the lens catches a little more light.
    val held by animateFloatAsState(if (dragging) .45f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "Navigation pill glint")
    val activeTint = MaterialTheme.colorScheme.primary
    val idleTint = MaterialTheme.colorScheme.onSurfaceVariant

    Row(modifier.fillMaxWidth().padding(6.dp).height(IntrinsicSize.Min)
        .onSizeChanged { width = it.width.toFloat() }
        .drawBehind {
            if (!placed) return@drawBehind
            // Keep a readable pill even when the trailing edge overshoots during the settle.
            val center = (left.value + right.value) / 2
            val half = max(right.value - left.value, slot * .55f) / 2
            // Pulled past either end, the pill presses flat against the bar's edge instead of leaving it.
            val pad = 6.dp.toPx()
            val l = max(center - half, -pad); val r = min(center + half, size.width + pad)
            val w = r - l
            val h = size.height * LiquidIndicator.heightFactor(w, slot)
            drawGlassPill(pill, Offset(l, (size.height - h) / 2), Size(w, h), min(corner, h / 2), pillTone,
                max(pillGlint(right.value - left.value, slot), held))
        }
        .pointerInput(count, liquid) {
            if (!liquid) return@pointerInput
            val tracker = VelocityTracker()
            var grab = 0f
            var detent = 0
            fun follow(finger: Float) {
                dragCenter = LiquidIndicator.rubberBand(finger - grab, currentSlot / 2, width - currentSlot / 2, currentSlot * .3f)
                val now = LiquidIndicator.nearest(dragCenter, currentSlot, gap, count)
                if (now != detent) { detent = now; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            }
            fun settle(index: Int) {
                dragging = false
                val l = LiquidIndicator.slotLeft(index, currentSlot, gap)
                target = l to l + currentSlot
                if (index != currentSelected) currentSelect(index)
            }
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    tracker.resetTracking()
                    // Grabbing the pill keeps the touch point under the finger; elsewhere the pill glides over.
                    val center = (left.value + right.value) / 2
                    grab = if (abs(offset.x - center) <= currentSlot / 2) offset.x - center else 0f
                    detent = currentSelected; dragDirection = 0f
                    follow(offset.x); dragging = true
                },
                onDragEnd = {
                    // A flick carries the pill a little further than the finger lifted.
                    val projected = dragCenter + tracker.calculateVelocity().x * .08f
                    settle(LiquidIndicator.nearest(projected, currentSlot, gap, count))
                },
                onDragCancel = { settle(currentSelected) },
            ) { change, dx ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                if (dx != 0f) dragDirection = dx
                follow(change.position.x)
            }
        }
        .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { index, (label, icon) ->
            val cover = if (placed) LiquidIndicator.coverage(LiquidIndicator.slotLeft(index, slot, gap), slot, left.value, right.value)
                else if (index == selectedPos) 1f else 0f
            val tint = lerp(idleTint, activeTint, cover)
            val press = remember { MutableInteractionSource() }
            val pressed by press.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) .94f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "Navigation press")
            Column(Modifier.weight(1f).fillMaxHeight().graphicsLayer { scaleX = scale; scaleY = scale }
                .selectable(selected = index == selectedPos, interactionSource = press, indication = null, role = Role.Tab, onClick = { currentSelect(index) })
                .padding(horizontal = 2.dp, vertical = 8.dp).heightIn(min = if (largeFont) 64.dp else 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                // Without labels the icon carries the item alone, so it grows and gains weight.
                if (labels) Icon(icon, null, Modifier.size(24.dp), tint = tint)
                else HeavyIcon(icon, label, 28.dp, tint)
                if (labels) {
                    Spacer(Modifier.height(5.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, color = tint)
                }
            }
        }
    }
}
