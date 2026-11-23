package hu.matepazy.menetrendgo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.Dp
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

private fun topHighlightBrush(color: Color, height: Float = Float.POSITIVE_INFINITY) =
    Brush.verticalGradient(0f to color, .35f to color.copy(alpha = 0f), endY = height)

@Composable @ReadOnlyComposable internal fun glassTone(level: GlassLevel = GlassLevel.Panel): GlassTone =
    glassToneOn(MaterialTheme.colorScheme.surface, level)

/** Light caught by a container painted [container] instead of the theme surface (snackbars on inverse surface). */
internal fun glassToneOn(container: Color, level: GlassLevel = GlassLevel.Panel) =
    GlassTone(if (dark(container)) Color.White.copy(alpha = .09f * level.light) else Color.White)

/** The liquid selection pill catches the same light over its pale-blue fill. */
@Composable @ReadOnlyComposable internal fun pillGlassTone(): GlassTone =
    GlassTone(Color.White.copy(alpha = if (dark(MaterialTheme.colorScheme.surface)) .13f else .8f))

/** A 1dp line just inside the top edge, fading out over the upper third of [size]. */
private fun DrawScope.drawTopHighlight(outline: (Size) -> Path, tone: GlassTone, origin: Offset, size: Size, boost: Float = 1f) {
    val w = 1.dp.toPx()
    if (tone.highlight.alpha <= 0f || size.width <= w || size.height <= w) return
    val color = tone.highlight.copy(alpha = (tone.highlight.alpha * boost).coerceIn(0f, 1f))
    val fade = topHighlightBrush(color, size.height)
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

/** The top highlight alone, over a Material control that paints its own container (buttons, tonal icons). */
internal fun Modifier.glassHighlight(shape: Shape, tone: GlassTone) = drawWithCache {
    fun outline(s: Size) = Path().apply { addOutline(shape.createOutline(s, layoutDirection, this@drawWithCache)) }
    onDrawWithContent { drawContent(); drawTopHighlight(::outline, tone, Offset.Zero, size) }
}

/** The Material border slot follows the painted shape inside the minimum touch target. */
internal fun glassBorder(tone: GlassTone) =
    BorderStroke(1.dp, topHighlightBrush(tone.highlight))

/**
 * Light caught by tonal and outlined Material controls (buttons, icon buttons, chips): the pill's strength,
 * so pale-blue containers read like the selection pills; disabled controls keep only a trace of it.
 */
@Composable @ReadOnlyComposable internal fun controlGlassTone(enabled: Boolean = true): GlassTone {
    val light = pillGlassTone().highlight
    return GlassTone(if (enabled) light else light.copy(alpha = light.alpha * .38f))
}

/** Tonal icon buttons have no border slot, so draw the highlight inside their clipped container. */
@Composable internal fun GlassTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: GlassTone = controlGlassTone(enabled),
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    content: @Composable () -> Unit,
) {
    FilledTonalIconButton(onClick, modifier, enabled, shape = CircleShape, colors = colors) {
        Box(Modifier.fillMaxSize().glassHighlight(CircleShape, tone), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/**
 * The highlight for a container Material places and moves itself (modal sheets), drawn from a full-width strip at
 * its top edge as if over [depth] of the container, so the line fades down the sides exactly like [GlassSurface].
 */
internal fun Modifier.glassEdge(shape: Shape, tone: GlassTone, depth: Dp = 96.dp) = drawWithCache {
    fun outline(s: Size) = Path().apply { addOutline(shape.createOutline(s, layoutDirection, this@drawWithCache)) }
    val virtual = Size(size.width, depth.toPx())
    onDrawWithContent { drawContent(); drawTopHighlight(::outline, tone, Offset.Zero, virtual) }
}

/** Light caught by a filled button: softer than on milky surfaces, so the royal blue stays solid. */
@Composable @ReadOnlyComposable internal fun buttonGlassTone(enabled: Boolean = true): GlassTone =
    GlassTone(Color.White.copy(alpha = (if (dark(MaterialTheme.colorScheme.surface)) .22f else .38f) * (if (enabled) 1f else .38f)))

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

/** [glassToneOn] for a control, keeping 38% of the light while disabled. */
private fun controlTone(container: Color, enabled: Boolean): GlassTone {
    val light = glassToneOn(container).highlight
    return GlassTone(if (enabled) light else light.copy(alpha = light.alpha * .38f))
}

/**
 * The secondary action in the glass material: a solid lifted pill that catches the top highlight, with no outline.
 * [container] steps one tone away from what the button sits on: the default suits panels and sheets, while
 * buttons placed directly on the page ground pass the milky surface.
 */
@Composable internal fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) = Button(onClick, modifier, enabled, shape,
    ButtonDefaults.buttonColors(containerColor = container, contentColor = MaterialTheme.colorScheme.primary),
    elevation = null, border = glassBorder(controlTone(container, enabled)), contentPadding = contentPadding, content = content)

/**
 * A filter chip in the glass material: unselected chips are solid lifted pills, selected ones take the pale-blue
 * pill fill; neither has an outline, and both catch the top highlight.
 */
@Composable internal fun GlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val colors = MaterialTheme.colorScheme
    FilterChip(selected, onClick, label, modifier,
        leadingIcon = leadingIcon, shape = shape,
        colors = FilterChipDefaults.filterChipColors(containerColor = container, labelColor = colors.onSurface,
            selectedContainerColor = colors.primaryContainer, selectedLabelColor = colors.onPrimaryContainer),
        border = glassBorder(if (selected) pillGlassTone() else controlTone(container, true)))
}
