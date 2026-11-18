package hu.matepazy.menetrendgo

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.min

/**
 * The two edges of a liquid pill along its axis, each a damped spring toward its goal. The edge facing
 * the motion leads on a stiff spring and the other trails on a softer, slightly underdamped one, so the
 * pill stretches in transit and settles with a small wobble. Plain Kotlin so the physics is unit-tested.
 */
internal class LiquidEdges(var start: Float = 0f, var end: Float = 0f) {
    var startVelocity = 0f
    var endVelocity = 0f
    /** Direction of the latest goal change; kept through the settle so springs don't swap mid-wobble. */
    var forward = true
    private var goalCenter = Float.NaN

    fun snap(goalStart: Float, goalEnd: Float) {
        start = goalStart; end = goalEnd; startVelocity = 0f; endVelocity = 0f; goalCenter = (goalStart + goalEnd) / 2
    }

    fun step(goalStart: Float, goalEnd: Float, seconds: Float) {
        val center = (goalStart + goalEnd) / 2
        if (goalCenter.isNaN()) goalCenter = (start + end) / 2
        if (abs(center - goalCenter) > .01f) { forward = center > goalCenter; goalCenter = center }
        var left = seconds
        // Substeps keep the stiff leading spring stable at low frame rates.
        while (left > 0f) {
            val dt = min(left, .004f); left -= dt
            val (sk, sc) = if (forward) Trail else Lead
            val (ek, ec) = if (forward) Lead else Trail
            startVelocity += (sk * (goalStart - start) - sc * startVelocity) * dt; start += startVelocity * dt
            endVelocity += (ek * (goalEnd - end) - ec * endVelocity) * dt; end += endVelocity * dt
        }
    }

    fun settled(goalStart: Float, goalEnd: Float) =
        abs(start - goalStart) < .5f && abs(end - goalEnd) < .5f && abs(startVelocity) < 8f && abs(endVelocity) < 8f

    private companion object {
        // Stiffness and damping (unit mass) matching the phone navigation pill's springs.
        val Lead = 900f to 2f * .78f * 30f
        val Trail = 260f to 2f * .6f * 16.12f
    }
}

/** Compose-observable wrapper that runs [LiquidEdges] on the frame clock toward a goal read every frame. */
@Stable internal class LiquidPillMotion {
    private val edges = LiquidEdges()
    var start by mutableFloatStateOf(0f); private set
    var end by mutableFloatStateOf(0f); private set
    var placed by mutableStateOf(false); private set

    /** Runs until cancelled. A null goal means not measured yet. Honors Android's animator duration scale. */
    suspend fun run(goal: () -> Pair<Float, Float>?) {
        while (true) {
            val first = snapshotFlow(goal).filterNotNull().first { !placed || !edges.settled(it.first, it.second) }
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            if (!placed || scale == 0f) { edges.snap(first.first, first.second); publish(); placed = true; continue }
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val seconds = ((now - last) / 1e9f / scale).coerceIn(0f, .05f); last = now
                val g = goal() ?: break
                edges.step(g.first, g.second, seconds)
                if (edges.settled(g.first, g.second)) { edges.snap(g.first, g.second); publish(); break }
                publish()
            }
        }
    }

    private fun publish() { start = edges.start; end = edges.end }

    fun coverage(from: Float, to: Float) = LiquidIndicator.coverage(from, to - from, start, end)
}

/**
 * Draws the pill between [start] and [end] along its axis. Stretch thins it across the axis and compression
 * swells it, by up to the same share as the phone navigation pill, measured against the resting length.
 */
internal fun DrawScope.drawLiquidPill(color: Color, start: Float, end: Float, rest: Float, vertical: Boolean,
    crossStart: Float, crossLength: Float, corner: Float, tone: GlassTone = GlassTone.None) {
    if (rest <= 0f) return
    val center = (start + end) / 2
    val length = maxOf(end - start, rest * .55f)
    val change = (1f - LiquidIndicator.heightFactor(length, rest)) * min(crossLength, rest)
    val cross = crossLength - change
    val crossOffset = crossStart + change / 2
    val radius = min(corner, min(cross, length) / 2)
    val glint = pillGlint(end - start, rest)
    if (vertical) drawGlassPill(color, Offset(crossOffset, center - length / 2), Size(cross, length), radius, tone, glint)
    else drawGlassPill(color, Offset(center - length / 2, crossOffset), Size(length, cross), radius, tone, glint)
}

/** A liquid selection over options laid out in one parent along one axis. */
@Stable internal class LiquidSelection(val vertical: Boolean) {
    internal val bounds = mutableStateMapOf<Int, Rect>()
    internal val motion = LiquidPillMotion()
    internal var selected by mutableIntStateOf(-1)

    internal fun goal(): Pair<Float, Float>? = bounds[selected]?.let { if (vertical) it.top to it.bottom else it.left to it.right }

    /** 0..1: how much of option [index] the pill covers, for blending content tint. */
    fun coverage(index: Int): Float {
        val b = bounds[index] ?: return if (index == selected) 1f else 0f
        if (!motion.placed) return if (index == selected) 1f else 0f
        return if (vertical) motion.coverage(b.top, b.bottom) else motion.coverage(b.left, b.right)
    }
}

@Composable internal fun rememberLiquidSelection(selected: Int, vertical: Boolean): LiquidSelection {
    val selection = remember(vertical) { LiquidSelection(vertical) }
    SideEffect { selection.selected = selected }
    LaunchedEffect(selection) { selection.motion.run(selection::goal) }
    return selection
}

/** Registers a direct child of the [liquidSelectionHost] layout as option [index]. */
internal fun Modifier.liquidOption(selection: LiquidSelection, index: Int) = onPlaced {
    val r = it.boundsInParent()
    if (selection.bounds[index] != r) selection.bounds[index] = r
}

/**
 * Draws the selection pill behind the options; apply it last in the parent's chain so it shares the children's
 * coordinate space. [base] paints each option's resting container underneath, for option cards that the pill
 * must flow over; [crossInset] keeps the pill inside full-bleed rows.
 */
internal fun Modifier.liquidSelectionHost(selection: LiquidSelection, color: Color, corner: Dp,
    base: Color? = null, baseCorner: Dp = corner, crossInset: Dp = 0.dp, tone: GlassTone = GlassTone.None) = drawBehind {
    if (base != null) selection.bounds.values.forEach {
        drawRoundRect(base, it.topLeft, it.size, CornerRadius(baseCorner.toPx()))
    }
    val chosen = selection.bounds[selection.selected] ?: return@drawBehind
    val m = selection.motion
    if (!m.placed) return@drawBehind
    val inset = crossInset.toPx()
    if (selection.vertical) drawLiquidPill(color, m.start, m.end, chosen.height, true, chosen.left + inset, chosen.width - 2 * inset, corner.toPx(), tone)
    else drawLiquidPill(color, m.start, m.end, chosen.width, false, chosen.top + inset, chosen.height - 2 * inset, corner.toPx(), tone)
}

/**
 * Tap-only segmented control in the bottom navigation's anatomy: a milky rounded bar of equal pill options
 * with the liquid selection pill tinting whichever option it covers.
 */
@Composable internal fun LiquidSegmentedBar(
    options: List<Pair<String, ImageVector?>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    role: Role = Role.RadioButton,
    minHeight: Dp = 48.dp,
) {
    val colors = MaterialTheme.colorScheme
    val selection = rememberLiquidSelection(selected, vertical = false)
    GlassSurface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = container) {
        Row(Modifier.fillMaxWidth().padding(6.dp).height(IntrinsicSize.Min).selectableGroup()
            .liquidSelectionHost(selection, colors.primaryContainer.copy(alpha = .65f), 26.dp, tone = pillGlassTone()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { index, (label, icon) ->
                val tint = lerp(colors.onSurfaceVariant, colors.primary, selection.coverage(index))
                val press = remember { MutableInteractionSource() }
                val pressed by press.collectIsPressedAsState()
                val scale by animateFloatAsState(if (pressed) .94f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "Segment press")
                Row(Modifier.weight(1f).fillMaxHeight().heightIn(min = minHeight).liquidOption(selection, index)
                    .graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(26.dp))
                    // The travelling pill is the press feedback; a grey ripple on top of it would muddy the selection.
                    .selectable(selected = index == selected, interactionSource = press, indication = null, role = role, onClick = { onSelect(index) })
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = tint); Spacer(Modifier.width(AppLayout.CompactGap)) }
                    Text(label, style = MaterialTheme.typography.labelLarge, color = tint, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}
