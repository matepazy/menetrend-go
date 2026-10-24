package hu.matepazy.menetrendgo

import android.animation.ValueAnimator
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sign

private val BackEasing = CubicBezierEasing(.1f, .1f, 0f, 1f)

/** Material main-container geometry, bounded by the space exposed by scaling. */
internal fun backTranslationX(width: Float, edgeGap: Float, progress: Float): Float =
    (width * .05f - edgeGap).coerceAtLeast(0f) * progress

/** The parent stays visible until commit; cancellation restores the current destination. */
@Composable fun PredictiveBackPane(
    enabled: Boolean,
    onBack: () -> Unit,
    preview: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var completion by remember { mutableFloatStateOf(0f) }
    var edge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var touchDelta by remember { mutableFloatStateOf(0f) }
    var swiping by remember { mutableStateOf(false) }
    val currentBack by rememberUpdatedState(onBack)
    val motionEnabled = ValueAnimator.areAnimatorsEnabled()
    PredictiveBackHandler(enabled=enabled) { events ->
        var initialY: Float? = null
        try {
            events.collect { event ->
                if (initialY == null) initialY=event.touchY
                edge=event.swipeEdge
                touchDelta=event.touchY-initialY!!
                progress=BackEasing.transform(event.progress.coerceIn(0f,1f))
                swiping=true
            }
            if (swiping && motionEnabled) {
                animate(0f,1f,animationSpec=tween(AppMotion.Dismiss,easing=AppMotion.Arrival)) { value, _ -> completion=value }
            }
            currentBack()
        } catch (cancelled: CancellationException) {
            // The gesture's coroutine is cancelled by Android. Let its short return
            // animation finish before resetting the preview, rather than snapping.
            if (swiping && motionEnabled) withContext(NonCancellable) {
                animate(progress,0f,animationSpec=tween(AppMotion.Reveal,easing=AppMotion.State)) { value, _ -> progress=value }
            }
            throw cancelled
        } finally {
            progress=0f
            completion=0f
            touchDelta=0f
            swiping=false
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (swiping && motionEnabled) Box(Modifier.fillMaxSize()
            .clearAndSetSemantics {}
            .graphicsLayer {
                val scale=.98f+.02f*completion
                scaleX=scale; scaleY=scale
            }) { preview() }
        Box(Modifier.fillMaxSize().graphicsLayer {
            val fraction=if(motionEnabled) progress else 0f
            val scale=1f-.1f*fraction
            scaleX=scale; scaleY=scale
            translationX=backTranslationX(size.width,8.dp.toPx(),fraction) *
                (if(edge==BackEventCompat.EDGE_LEFT) 1f else -1f)
            val availableY=((size.height-size.height*scale)/2-8.dp.toPx()).coerceAtLeast(0f)
            translationY=availableY.coerceAtMost(24.dp.toPx()) *
                (abs(touchDelta)/size.height.coerceAtLeast(1f)).coerceIn(0f,1f) * sign(touchDelta)
            alpha=1f-completion
            shape=RoundedCornerShape((28*fraction).dp)
            clip=fraction>0f
        }.background(MaterialTheme.colorScheme.background)) { content() }
    }
}
