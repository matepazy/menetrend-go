package hu.matepazy.menetrendgo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * Look-alikes of the real screens, one per onboarding station, floating in the sky above the ride.
 * They copy the shipped layouts (search card, vehicle drawer, stop widget, pass card) but do nothing;
 * the sample values are illustrations and the whole mock is hidden from screen readers.
 */

/** Shows the mock for [step], scaled down (never up) so it fits in [maxHeight]. */
@Composable internal fun OnboardingFeatureMock(step: Int, maxHeight: Dp, modifier: Modifier = Modifier) {
    AnimatedContent(step, modifier.clearAndSetSemantics { }.fitHeight(maxHeight), transitionSpec = {
        (fadeIn(tween(AppMotion.Reveal, 260)) + slideInVertically(tween(420, 260, AppMotion.Arrival)) { it / 6 } +
            scaleIn(tween(420, 260, AppMotion.Arrival), initialScale = .92f)) togetherWith fadeOut(tween(AppMotion.Dismiss))
    }, contentAlignment = Alignment.TopCenter, label = "Feature mock") { i ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            when (i) {
                0 -> WelcomeMock()
                1 -> PlanMock()
                2 -> VehicleMock()
                3 -> WidgetMock()
                else -> PassMock()
            }
        }
    }
}

private fun Modifier.fitHeight(max: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Int.MAX_VALUE))
    val k = (max.toPx() / placeable.height).coerceIn(.55f, 1f)
    layout(placeable.width, (placeable.height * k).roundToInt()) {
        placeable.placeWithLayer(0, 0) { scaleX = k; scaleY = k; transformOrigin = TransformOrigin(.5f, 0f) }
    }
}

@Composable private fun MockPanel(modifier: Modifier = Modifier, shape: Dp = 32.dp, color: Color = MaterialTheme.colorScheme.surface, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.widthIn(max = 380.dp).fillMaxWidth().glassHighlight(RoundedCornerShape(shape), glassToneOn(color, GlassLevel.Floating)),
        shape = RoundedCornerShape(shape), color = color, shadowElevation = 6.dp) {
        Column(content = content)
    }
}

@Composable private fun WelcomeMock() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(Modifier.glassHighlight(RoundedCornerShape(36.dp), buttonGlassTone()), shape = RoundedCornerShape(36.dp), color = Color(0xFF2864D9), shadowElevation = 6.dp) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(120.dp))
        }
        Text("MenetrendGO", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

// Tervezés: the search card with both stops chosen.
@Composable private fun PlanMock() {
    val scheme = MaterialTheme.colorScheme
    MockPanel {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    StopField("A", "Honnan?", "Budapest-Déli")
                    Row(Modifier.padding(start = 15.dp)) { Box(Modifier.width(2.dp).height(14.dp).background(scheme.outline.copy(alpha = .5f))) }
                    StopField("B", "Hová?", "Székesfehérvár")
                }
                Icon(Icons.Outlined.SwapVert, null, Modifier.size(24.dp), tint = scheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceTile(Icons.Outlined.CalendarToday, "Dátum", "Ma", Modifier.weight(1f))
                ChoiceTile(Icons.Outlined.Schedule, "Indulás", "Most", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(scheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.FilterList, null, tint = scheme.primary)
                }
                Row(Modifier.weight(1f).height(48.dp).background(scheme.primary, CircleShape), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = scheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Járatok keresése", style = MaterialTheme.typography.titleMedium, color = scheme.onPrimary)
                }
            }
        }
    }
}

@Composable private fun StopField(mark: String, label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(32.dp).border(2.dp, scheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Text(mark, style = MaterialTheme.typography.labelLarge, color = scheme.primary, fontWeight = FontWeight.Bold)
        }
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable private fun ChoiceTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.background(scheme.surfaceContainerLow, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = scheme.primary)
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private val CoachAmber = Color(0xFFF5A800)
private val DelayAmber = Color(0xFF9A6200)
private val MapLand = Color(0xFFD7F0DC)
private val MapWater = Color(0xFF9FD3F2)
private val MapRoad = Color(0xFFFFFFFF)

// Térkép: a live coach on its route with the top of its vehicle drawer.
@Composable private fun VehicleMock() {
    val scheme = MaterialTheme.colorScheme
    MockPanel {
        Box(Modifier.fillMaxWidth().height(104.dp)) {
            Canvas(Modifier.matchParentSize().background(MapLand)) {
                val w = size.width; val h = size.height
                drawOval(MapWater, Offset(w * .36f, h * .38f), androidx.compose.ui.geometry.Size(w * .28f, h * .3f))
                drawLine(MapRoad, Offset(0f, h * .2f), Offset(w, h * .55f), 4.dp.toPx())
                drawLine(MapRoad, Offset(w * .7f, 0f), Offset(w * .55f, h), 3.dp.toPx())
                val route = Path().apply { moveTo(w * .06f, h * .8f); lineTo(w * .32f, h * .72f); lineTo(w * .52f, h * .76f); lineTo(w * .7f, h * .45f); lineTo(w * .95f, h * .18f) }
                drawPath(route, CoachAmber, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                listOf(.06f to .8f, .2f to .76f, .32f to .72f, .7f to .45f, .82f to .32f, .95f to .18f).forEach { (x, y) ->
                    drawCircle(Color.White, 3.6.dp.toPx(), Offset(w * x, h * y)); drawCircle(CoachAmber, 3.6.dp.toPx(), Offset(w * x, h * y), style = Stroke(1.8.dp.toPx()))
                }
            }
            Box(Modifier.align(Alignment.TopStart).offset(x = 150.dp, y = 50.dp).size(34.dp).background(CoachAmber, CircleShape).border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center) { Icon(Icons.Outlined.DirectionsBus, null, Modifier.size(20.dp), tint = Color(0xFF3A3A39)) }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("7302", Modifier.background(CoachAmber, CircleShape).padding(horizontal = 12.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1B))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                Text("Székesfehérvár", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text("3 perc késés", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = if (LocalAppDarkTheme.current) Color(0xFFFFC266) else DelayAmber)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Update, null, Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                Text("Pozíció frissítve: 12 másodperce", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Icon(Icons.Outlined.Speed, null, Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
                Text("62 km/h", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Text("Credo Econell 12 Next", style = MaterialTheme.typography.bodyMedium, color = scheme.primary, textDecoration = TextDecoration.Underline)
        }
    }
}

// Indulások kéznél: the home-screen stop widget (panel #EDF4FA / 28dp, departure cards 24dp).
@Composable private fun WidgetMock() {
    val scheme = MaterialTheme.colorScheme
    MockPanel(shape = 28.dp, color = scheme.background) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Székesfehérvár", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Frissítve 1 perce", fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.Refresh, null, Modifier.size(22.dp), tint = scheme.onSurfaceVariant)
            }
            WidgetRow("Z30", "Budapest-Déli", "2. vágány / állás", "4 perc")
            WidgetRow("32", "Vasútállomás", "", "11 perc")
        }
    }
}

@Composable private fun WidgetRow(service: String, destination: String, detail: String, countdown: String) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(scheme.surface, RoundedCornerShape(24.dp)).padding(14.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(service, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(R.drawable.ic_service_direction), null, Modifier.size(14.dp), tint = scheme.onSurface)
                Text(destination, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            if (detail.isNotEmpty()) Text(detail, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        }
        Text(countdown, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// Bérletfigyelő: a pass card as on the Bérletek screen, plus its expiry reminder.
@Composable private fun PassMock() {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 380.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MockPanel {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.pass_hungary), null, Modifier.padding(top = 4.dp).size(40.dp), colorFilter = ColorFilter.tint(scheme.onSurface))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Országbérlet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Még 3 napig érvényes", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = scheme.error)
                    Text("Lejárat: ${passDate(LocalDate.now(Budapest).plusDays(3))}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
        MockPanel(shape = 24.dp, color = scheme.surfaceContainerHigh) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_notification), null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(scheme.primary))
                Column {
                    Text("MenetrendGO · 9:00", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Text("3 nap múlva lejár a bérleted", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("Országbérlet · ${LocalDate.now(Budapest).plusDays(3)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}
