package hu.matepazy.menetrendgo

import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints

internal class StationFocusState {
    var bounds by mutableStateOf<Rect?>(null)
    var dismiss by mutableStateOf<(() -> Unit)?>(null)
}

internal val LocalStationFocus = staticCompositionLocalOf { StationFocusState() }

/** One scrim covers the complete app, leaving the inline editor and its results clear. */
@Composable internal fun StationFocusOverlay(content: @Composable () -> Unit) {
    val state=remember {StationFocusState()}
    var origin by remember {mutableStateOf(Offset.Zero)}
    var lastBounds by remember {mutableStateOf<Rect?>(null)}
    SideEffect {state.bounds?.let {lastBounds=it}}
    val scrimAlpha by animateFloatAsState(if(state.dismiss!=null) .48f else 0f,
        tween(if(state.dismiss!=null) AppMotion.StationFocus else AppMotion.Dismiss,easing=AppMotion.Arrival),label="Station focus scrim")
    CompositionLocalProvider(LocalStationFocus provides state) {
        Box(Modifier.fillMaxSize().onGloballyPositioned {origin=it.positionInWindow()}
            .drawWithContent {
                drawContent()
                if(scrimAlpha>0f) {
                    val scrim=Path().apply {
                        fillType=PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero,size))
                        (state.bounds ?: lastBounds)?.translate(-origin)?.let {bounds ->
                            addRoundRect(RoundRect(bounds,CornerRadius(28.dp.toPx())))
                        }
                    }
                    drawPath(scrim,Color.Black.copy(alpha=scrimAlpha))
                }
            }) {
            content()
            if(state.dismiss!=null) {
                // Four real hit targets cover everything except the editor. They sit above
                // navigation and page controls, so an outside tap only dismisses the search.
                Layout(content={
                    repeat(4) {
                        Box(Modifier.clickable(interactionSource=remember {MutableInteractionSource()},indication=null,
                            onClickLabel="Keresés bezárása",onClick={state.dismiss?.invoke()}))
                    }
                },modifier=Modifier.fillMaxSize()) {measurables,constraints ->
                    val width=constraints.maxWidth
                    val height=constraints.maxHeight
                    val bounds=state.bounds?.translate(-origin) ?: Rect.Zero
                    val left=bounds.left.toInt().coerceIn(0,width)
                    val right=bounds.right.toInt().coerceIn(left,width)
                    val top=bounds.top.toInt().coerceIn(0,height)
                    val bottom=bounds.bottom.toInt().coerceIn(top,height)
                    val regions=listOf(Rect(0f,0f,width.toFloat(),top.toFloat()),
                        Rect(0f,top.toFloat(),left.toFloat(),bottom.toFloat()),
                        Rect(right.toFloat(),top.toFloat(),width.toFloat(),bottom.toFloat()),
                        Rect(0f,bottom.toFloat(),width.toFloat(),height.toFloat()))
                    val children=measurables.zip(regions).map {(child,area) ->
                        child.measure(Constraints.fixed(area.width.toInt(),area.height.toInt())) to area
                    }
                    layout(width,height) {children.forEach {(child,area) ->child.place(area.left.toInt(),area.top.toInt())}}
                }
            }
        }
    }
}
