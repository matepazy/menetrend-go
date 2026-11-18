package hu.matepazy.menetrendgo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * The app's restrained liquid-glass material: solid surfaces with one soft highlight caught on the top edge,
 * fading out down the sides. There is no bottom edge, sheen or border, so contents and pills stay centred.
 */
@Immutable internal data class GlassTone(val highlight: Color) {
    companion object { val None = GlassTone(Color.Transparent) }
}

/** Panels sit on the ground; floating chrome (navigation, map controls) catches a little more light. */
internal enum class GlassLevel(val light: Float) { Panel(1f), Floating(1.35f) }

private fun dark(surface: Color) = surface.luminance() < .4f

@Composable @ReadOnlyComposable internal fun glassTone(level: GlassLevel = GlassLevel.Panel): GlassTone {
    val surface = MaterialTheme.colorScheme.surface
    return GlassTone(if (dark(surface)) Color.White.copy(alpha = .09f * level.light) else Color.White)
}

/** The liquid selection pill catches the same light over its pale-blue fill. */
@Composable @ReadOnlyComposable internal fun pillGlassTone(): GlassTone =
    GlassTone(Color.White.copy(alpha = if (dark(MaterialTheme.colorScheme.surface)) .13f else .8f))

/** A 1dp line just inside the top edge, fading out over the upper third of [size]. */
private fun DrawScope.drawTopHighlight(outline: (Size) -> Path, tone: GlassTone, origin: Offset, size: Size, boost: Float = 1f) {
    val w = 1.dp.toPx()
    if (tone.highlight.alpha <= 0f || size.width <= w || size.height <= w) return
    val color = tone.highlight.copy(alpha = (tone.highlight.alpha * boost).coerceIn(0f, 1f))
    val fade = Brush.verticalGradient(0f to color, .35f to color.copy(alpha = 0f), startY = 0f, endY = size.height)
    translate(origin.x + w / 2, origin.y + w / 2) { drawPath(outline(Size(size.width - w, size.height - w)), fade, style = Stroke(w)) }
}

/**
 * Paints [fill] in [shape] under the content and the top highlight over it. Use on a [Surface] whose own colour
 * is transparent (see [GlassSurface]) so the fill stays under the content.
 */
internal fun Modifier.liquidGlass(shape: Shape, fill: Color, tone: GlassTone) = drawWithCache {
    fun outline(s: Size) = Path().apply { addOutline(shape.createOutline(s, layoutDirection, this@drawWithCache)) }
    val body = outline(size)
    onDrawWithContent {
        drawPath(body, fill)
        drawContent()
        drawTopHighlight(::outline, tone, Offset.Zero, size)
    }
}

/**
 * A solid Material [Surface] in the glass material. Content colour, clipping, ripple and semantics stay
 * Material's; only the container painting changes.
 */
@Composable internal fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    color: Color = MaterialTheme.colorScheme.surface,
    level: GlassLevel = GlassLevel.Panel,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val contentColor = contentColorFor(color)
    val glass = modifier.liquidGlass(shape, color, glassTone(level))
    if (onClick != null) Surface(onClick = onClick, modifier = glass, shape = shape, color = Color.Transparent, contentColor = contentColor, content = content)
    else Surface(modifier = glass, shape = shape, color = Color.Transparent, contentColor = contentColor, content = content)
}

/**
 * Draws a glass pill: its fill and the soft top highlight. [glint] (0..1) brightens the highlight while the
 * pill stretches or is dragged, as if the liquid lens catches more light in motion.
 */
internal fun DrawScope.drawGlassPill(color: Color, topLeft: Offset, size: Size, radius: Float, tone: GlassTone, glint: Float = 0f) {
    drawRoundRect(color, topLeft, size, CornerRadius(radius))
    if (tone == GlassTone.None) return
    drawTopHighlight({ s -> Path().apply {
        addRoundRect(RoundRect(0f, 0f, s.width, s.height, CornerRadius((radius - (size.width - s.width) / 2).coerceAtLeast(0f))))
    } }, tone, topLeft, size, 1f + .8f * glint.coerceIn(0f, 1f))
}

/** How strongly a pill of [length] glints against its resting [rest] length: stretch reads as motion. */
internal fun pillGlint(length: Float, rest: Float) = if (rest <= 0f) 0f else (kotlin.math.abs(length - rest) / rest * 2.5f).coerceIn(0f, 1f)
