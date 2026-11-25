package hu.matepazy.menetrendgo

import android.animation.ValueAnimator
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** Rest positions of the live-map sheet, top to bottom. */
enum class MapSheetValue { Full, Half, Peek, Hidden }

/**
 * Map sheet content reports the pixel height of its summary (the part that stays visible when
 * the sheet rests at [MapSheetValue.Peek]). Outside a [MapSheetScaffold] nothing listens.
 */
val LocalMapSheetSummaryHeight = staticCompositionLocalOf<((Int) -> Unit)?> { null }

/**
 * A non-modal sheet over the live map with four rests: full height, [restingFraction] of the
 * height, a peek of the summary that the content reports through [LocalMapSheetSummaryHeight],
 * and hidden. The map above stays interactive; only the way to full height dims it. Drags follow
 * the finger, flings move one rest in their direction, and a spring carries the release velocity.
 * Material's sheet has a single partial rest, so this is built on [AnchoredDraggableState].
 * [content] receives the settled visible height, so map overlays and camera fits keep clear of the sheet,
 * and a reader of the live visible height for overlays that follow a drag
 * frame by frame. Read it inside a snapshot flow or layout block, not in composition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSheetScaffold(
    open: Boolean,
    onDismissRequest: () -> Unit,
    restingFraction: Float,
    /** The sheet has started heading to hidden, before [onDismissRequest] (which may wait for the exit). */
    onLeaving: () -> Unit = {},
    sheetContent: @Composable ColumnScope.(expanded: Boolean) -> Unit,
    content: @Composable (restingHeight: Dp, liveHeight: () -> Dp) -> Unit,
) {
    // A selection that survives recreation rests at once instead of animating from hidden.
    val state = remember { AnchoredDraggableState(if (open) MapSheetValue.Half else MapSheetValue.Hidden) }
    val dismiss by rememberUpdatedState(onDismissRequest)
    val leaving by rememberUpdatedState(onLeaving)
    val isOpen by rememberUpdatedState(open)
    val scope = rememberCoroutineScope()
    var summaryPx by remember { mutableIntStateOf(0) }
    // Whether the sheet has reached a visible rest since it last opened.
    var shown by remember { mutableStateOf(open) }
    val density = LocalDensity.current
    val flingThreshold = with(density) { 125.dp.toPx() }
    val motion = remember { spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) }
    val backProgress = remember { Animatable(0f) }
    val backEasing = remember { CubicBezierEasing(.1f, .1f, 0f, 1f) }
    val motionEnabled = ValueAnimator.areAnimatorsEnabled()

    // Springs from the current offset with the gesture's velocity, retargeting if anchors move.
    // Heading to hidden releases the selection at once: the screen behind returns to its normal
    // mode while the sheet slides away, instead of after the spring's long tail has settled.
    // [dismissEarly] false leaves the release to the settle below, after the sheet has left the screen.
    suspend fun animateTo(target: MapSheetValue, velocity: Float = 0f, dismissEarly: Boolean = true) {
        if (!state.anchors.hasPositionFor(target)) return
        if (target == MapSheetValue.Hidden && isOpen) leaving()
        var startVelocity = velocity
        state.anchoredDrag(target) { anchors, latest ->
            if (dismissEarly && latest == MapSheetValue.Hidden && isOpen) dismiss()
            val from = state.offset.takeUnless { it.isNaN() } ?: anchors.positionOf(latest)
            animate(from, anchors.positionOf(latest), startVelocity, motion) { value, v -> dragTo(value, v); startVelocity = v }
        }
    }
    // A fast fling moves to the next rest in its direction; a slow release to the nearest rest.
    suspend fun settle(velocity: Float) {
        val anchors = state.anchors
        val offset = state.offset.takeUnless { it.isNaN() } ?: return
        var target = anchors.closestAnchor(offset) ?: return
        if (abs(velocity) >= flingThreshold) {
            var best = Float.NaN
            for (i in 0 until anchors.size) {
                val position = anchors.positionAt(i)
                val ahead = if (velocity > 0) position > offset + 1f else position < offset - 1f
                if (ahead && (best.isNaN() || abs(position - offset) < abs(best - offset))) {
                    best = position; target = anchors.anchorAt(i)!!
                }
            }
        }
        animateTo(target, velocity)
    }

    LaunchedEffect(open) {
        // A dismissal from the sheet itself is already sliding away with its gesture's velocity.
        if (!open && state.targetValue == MapSheetValue.Hidden) return@LaunchedEffect
        animateTo(if (open) MapSheetValue.Half else MapSheetValue.Hidden)
    }
    LaunchedEffect(state) {
        // The sheet starts hidden; only reaching hidden after it was shown is a user dismissal.
        snapshotFlow { state.settledValue }.collect {
            if (it != MapSheetValue.Hidden) shown = true
            else if (shown) { if (isOpen) dismiss(); shown = false }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layoutPx = constraints.maxHeight.toFloat()
        val topGap = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() } + AppLayout.RelatedGap
        val halfHeight = maxHeight * restingFraction
        // Drag handle + measured summary + a small gap + the navigation bar the sheet sits behind.
        val peekHeight = with(density) {
            48.dp + summaryPx.toDp() + AppLayout.CompactGap + WindowInsets.safeDrawing.getBottom(this).toDp()
        }.coerceAtMost(halfHeight)
        val fullPx = with(density) { topGap.toPx() }
        val halfPx = layoutPx - with(density) { halfHeight.toPx() }
        val peekPx = layoutPx - with(density) { peekHeight.toPx() }
        val anchors = remember(fullPx, halfPx, peekPx, summaryPx > 0, layoutPx) {
            DraggableAnchors {
                MapSheetValue.Full at fullPx
                MapSheetValue.Half at halfPx
                if (summaryPx > 0) MapSheetValue.Peek at peekPx
                MapSheetValue.Hidden at layoutPx
            }
        }
        SideEffect {
            val target = state.targetValue.takeIf { anchors.hasPositionFor(it) } ?: MapSheetValue.Half
            state.updateAnchors(anchors, target)
        }
        // Dimming and the expanded stop list follow the measured position between half and full height.
        val progress by remember(halfPx, fullPx) { derivedStateOf {
            val offset = state.offset.takeUnless { it.isNaN() } ?: halfPx
            ((halfPx - offset) / (halfPx - fullPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
        } }
        val expanded = open && progress > .98f
        // Back shrinks the sheet toward its visible bottom edge as the gesture moves, as Material's
        // sheets do; a committed Back then collapses full height to half, or slides the sheet away.
        PredictiveBackHandler(open) { events ->
            try {
                events.collect { backProgress.snapTo(if (motionEnabled) backEasing.transform(it.progress) else 0f) }
                val target = if (progress > .5f) MapSheetValue.Half else MapSheetValue.Hidden
                // The handler is disabled once the sheet closes, so the settle runs in the sheet's scope.
                // Like Material's sheets, Back releases the selection only after the exit: the map's
                // rebuild of every vehicle marker would otherwise land on the first frames of the slide.
                scope.launch {
                    if (target == MapSheetValue.Hidden) { animateTo(target, dismissEarly = false); backProgress.snapTo(0f) }
                    else { launch { backProgress.animateTo(0f) }; animateTo(target) }
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { backProgress.animateTo(0f) }
                throw cancelled
            }
        }

        // The settled rest, not the moving offset, so camera fits run once per change. While the
        // sheet rises from hidden it reports the half rest it is heading to, so the first fit of a
        // newly opened vehicle already keeps its route clear of the default sheet.
        val settledHeight = when {
            !open -> 0.dp
            state.settledValue == MapSheetValue.Peek -> peekHeight
            state.settledValue == MapSheetValue.Hidden -> if (shown) 0.dp else halfHeight
            else -> halfHeight
        }
        val liveHeight = remember(layoutPx, density) { {
            val offset = state.offset.takeUnless { it.isNaN() } ?: layoutPx
            with(density) { (layoutPx - offset).coerceAtLeast(0f).toDp() }
        } }
        content(settledHeight, liveHeight)
        if (open && progress > 0f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress }
            .background(BottomSheetDefaults.ScrimColor).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClickLabel = "Részletek összecsukása", onClick = { scope.launch { animateTo(MapSheetValue.Half) } },
            ))

        // The stop list scrolls only at full height: dragging it up first raises the sheet, and
        // dragging down from its top lowers the sheet, as in Material's own sheets.
        val nestedScroll = remember(state) { object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0 && source == NestedScrollSource.UserInput) Offset(0f, state.dispatchRawDelta(available.y))
                else Offset.Zero
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput) Offset(0f, state.dispatchRawDelta(available.y)) else Offset.Zero
            override suspend fun onPreFling(available: Velocity): Velocity {
                val offset = state.offset
                return if (available.y < 0 && !offset.isNaN() && offset > state.anchors.minPosition() + .5f) {
                    settle(available.y); available
                } else Velocity.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                settle(available.y); return available
            }
        } }
        val sheetHeight = maxHeight - topGap
        val sheetShape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
        // Material's predictive-back geometry: 48dp narrower and 24dp shorter at full progress.
        val backDistanceX = with(density) { 48.dp.toPx() }
        val backDistanceY = with(density) { 24.dp.toPx() }
        val sheetPx = with(density) { sheetHeight.toPx() }.coerceAtLeast(1f)
        Surface(
            Modifier.align(Alignment.TopCenter).widthIn(max = BottomSheetDefaults.SheetMaxWidth).fillMaxWidth()
                .height(sheetHeight)
                .offset { IntOffset(0, (state.offset.takeUnless { it.isNaN() } ?: layoutPx).roundToInt()) }
                .graphicsLayer {
                    val p = backProgress.value
                    if (p == 0f) return@graphicsLayer
                    scaleX = 1f - (backDistanceX / size.width).coerceAtMost(1f) * p
                    scaleY = 1f - (backDistanceY / layoutPx).coerceAtMost(1f) * p
                    // The sheet runs below the screen at partial rests; shrink toward the screen's bottom edge.
                    val offset = state.offset.takeUnless { it.isNaN() } ?: layoutPx
                    transformOrigin = TransformOrigin(.5f, ((layoutPx - offset) / sheetPx).coerceIn(0f, 1f))
                }
                .nestedScroll(nestedScroll)
                .draggable(rememberDraggableState { state.dispatchRawDelta(it) }, Orientation.Vertical,
                    onDragStopped = { settle(it) })
                .glassEdge(sheetShape, glassTone(GlassLevel.Floating)),
            shape = sheetShape,
            color = BottomSheetDefaults.ContainerColor,
            shadowElevation = BottomSheetDefaults.Elevation,
        ) {
            // Counter-scale the content so text and icons shrink uniformly while the surface narrows.
            Column(Modifier.fillMaxSize().graphicsLayer {
                val p = backProgress.value
                if (p == 0f) return@graphicsLayer
                val parentX = 1f - (backDistanceX / size.width).coerceAtMost(1f) * p
                val parentY = 1f - (backDistanceY / layoutPx).coerceAtMost(1f) * p
                scaleY = if (parentY > 0f) parentX / parentY else 1f
                transformOrigin = TransformOrigin(.5f, 0f)
            }.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
                val current = state.settledValue
                Box(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    when (current) {
                        MapSheetValue.Full -> collapse("Összecsukás") { scope.launch { animateTo(MapSheetValue.Half) }; true }
                        MapSheetValue.Peek -> expand("Kibontás") { scope.launch { animateTo(MapSheetValue.Half) }; true }
                        else -> {
                            expand("Kibontás") { scope.launch { animateTo(MapSheetValue.Full) }; true }
                            if (summaryPx > 0) collapse("Összecsukás") { scope.launch { animateTo(MapSheetValue.Peek) }; true }
                        }
                    }
                    dismiss("Bezárás") { scope.launch { animateTo(MapSheetValue.Hidden) }; true }
                }, contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() }
                val reportSummary = remember { { px: Int -> summaryPx = px } }
                // Content stays composed while the dismissed sheet slides away, then leaves.
                if (open || shown) CompositionLocalProvider(LocalMapSheetSummaryHeight provides reportSummary) { sheetContent(expanded) }
            }
        }
    }
}
