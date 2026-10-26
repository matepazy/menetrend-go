package hu.matepazy.menetrendgo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Compose applies Android's animator duration scale, including Remove animations. */
internal object AppMotion {
    const val Feedback = 150
    const val Reveal = 220
    const val StationFocus = 280
    const val Dismiss = 160
    val Arrival = LinearOutSlowInEasing
    val State = FastOutSlowInEasing
}

/** A small arrival cue, without keeping outgoing screens or their effects alive. */
@Composable internal fun ScreenArrival(route: String, animate: Boolean = true, content: @Composable () -> Unit) {
    key(route) {
        val arrival = remember { Animatable(if (animate) 0f else 1f) }
        LaunchedEffect(Unit) {
            arrival.animateTo(1f, tween(AppMotion.Reveal, easing = AppMotion.Arrival))
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = .6f + .4f * arrival.value
            translationY = 6.dp.toPx() * (1f - arrival.value)
        }) { content() }
    }
}
