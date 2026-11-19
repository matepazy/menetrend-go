package hu.matepazy.menetrendgo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * A long press that only fires while the finger stays put (within half the drag slop), so it never
 * competes with dragging the navigation pill. Observed in the initial pass, before the items' own
 * click handling; once it fires, the rest of the gesture is consumed so no tab is selected and no drag starts.
 */
internal fun Modifier.stillLongPress(onLongPress: (Offset) -> Unit) = pointerInput(Unit) {
    val tolerance = viewConfiguration.touchSlop / 2
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val interrupted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (event.changes.size > 1 || change == null || !change.pressed ||
                    (change.position - down.position).getDistance() > tolerance) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") true
        }
        if (interrupted == null) {
            onLongPress(down.position)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

/** Centres the popup above the press point, clamped to the window; flips below when there is no room above. */
private class AbovePress(private val press: IntOffset, private val clearance: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val px = anchorBounds.left + press.x
        val py = anchorBounds.top + press.y
        val x = (px - popupContentSize.width / 2).coerceIn(margin, (windowSize.width - margin - popupContentSize.width).coerceAtLeast(margin))
        val above = py - clearance - popupContentSize.height
        return IntOffset(x, if (above >= margin) above else py + clearance)
    }
}

/**
 * Hosts a navigation surface (phone bar or rail) with its long-press menu: Szerkesztés opens the
 * navigation look sheet, Kiegészítő funkciók jumps to that Settings page.
 */
@Composable internal fun NavigationLongPressHost(
    vm: AppState,
    preview: List<Pair<String, ImageVector>>,
    onExtras: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val menu = remember { MutableTransitionState(false) }
    var press by remember { mutableStateOf(IntOffset.Zero) }
    var editing by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    Box(modifier.stillLongPress { at ->
        press = IntOffset(at.x.roundToInt(), at.y.roundToInt())
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        menu.targetState = true
    }) {
        content()
        if (menu.currentState || menu.targetState) {
            val provider = remember(press, density) {
                with(density) { AbovePress(press, 44.dp.roundToPx(), AppLayout.ItemGap.roundToPx()) }
            }
            Popup(popupPositionProvider = provider, onDismissRequest = { menu.targetState = false }, properties = PopupProperties(focusable = true)) {
                AnimatedVisibility(menu,
                    enter = fadeIn(tween(AppMotion.Feedback)) + scaleIn(tween(AppMotion.Reveal, easing = AppMotion.Arrival), initialScale = .86f, transformOrigin = TransformOrigin(.5f, 1f)),
                    exit = fadeOut(tween(AppMotion.Dismiss)) + scaleOut(tween(AppMotion.Dismiss), targetScale = .94f, transformOrigin = TransformOrigin(.5f, 1f))) {
                    val shape = RoundedCornerShape(24.dp)
                    GlassSurface(Modifier.padding(8.dp).shadow(6.dp, shape).width(IntrinsicSize.Max).widthIn(min = 220.dp), shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, level = GlassLevel.Floating) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            NavigationMenuRow(Icons.Outlined.Edit, "Szerkesztés") { menu.targetState = false; editing = true }
                            NavigationMenuRow(Icons.Outlined.Extension, "Kiegészítő funkciók") { menu.targetState = false; onExtras() }
                        }
                    }
                }
            }
        }
    }
    if (editing) NavigationEditSheet(vm, preview) { editing = false }
}

@Composable private fun NavigationMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp).heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The navigation look sheet: a working preview of the bar above its two switches. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable private fun NavigationEditSheet(vm: AppState, preview: List<Pair<String, ImageVector>>, dismiss: () -> Unit) {
    AppBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().padding(start = AppLayout.SheetPadding, end = AppLayout.SheetPadding, bottom = AppLayout.SheetPadding),
            verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
            Text("Navigációs sáv", style = MaterialTheme.typography.headlineSmall)
            var selected by remember { mutableIntStateOf(0) }
            val largeFont = LocalDensity.current.fontScale > 1.15f
            GlassSurface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                LiquidNavigationBar(preview, selected, largeFont, onSelect = { selected = it }, liquid = vm.navLiquid, labels = vm.navLabels)
            }
            SettingsGroup { NavigationSwitches(vm) }
        }
    }
}

/** The two navigation look switches, shared by the long-press sheet and Megjelenés. */
@Composable internal fun NavigationSwitches(vm: AppState, heading: Boolean = false) {
    if (heading) {
        Text("Navigációs sáv", Modifier.padding(start = AppLayout.PanelPadding, end = AppLayout.PanelPadding, top = AppLayout.PanelPadding),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
    NavigationSwitchRow("Folyékony animáció", "A kijelölés nyúlik és hullámzik váltáskor, és a sávon végighúzva is válthatsz. Kikapcsolva azonnal ugrik.",
        vm.navLiquid, vm::chooseNavLiquid)
    HorizontalDivider(Modifier.padding(horizontal = AppLayout.PanelPadding), color = MaterialTheme.colorScheme.outlineVariant)
    NavigationSwitchRow("Feliratok az ikonok alatt", "Kikapcsolva csak az ikonok látszanak.", vm.navLabels, vm::chooseNavLabels)
}

@Composable private fun NavigationSwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(AppLayout.PanelPadding),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * An icon drawn heavier for the label-less navigation: the vector is painted in a small ring of offsets
 * ([weight] across), which thickens every line evenly while keeping counters (the gear's hole, the bookmark's inside) open.
 */
@Composable internal fun HeavyIcon(icon: ImageVector, contentDescription: String?, size: Dp, tint: Color, modifier: Modifier = Modifier, weight: Dp = .7.dp) {
    val painter = rememberVectorPainter(icon)
    Box(modifier.size(size)
        .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription; role = Role.Image } else Modifier)
        .drawBehind {
            val r = weight.toPx() / 2
            val filter = ColorFilter.tint(tint)
            for (i in 0 until 8) {
                val a = i * PI / 4
                translate((cos(a) * r).toFloat(), (sin(a) * r).toFloat()) { with(painter) { draw(this@drawBehind.size, colorFilter = filter) } }
            }
        })
}
