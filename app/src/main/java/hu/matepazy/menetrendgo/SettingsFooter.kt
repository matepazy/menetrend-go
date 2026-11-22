package hu.matepazy.menetrendgo

import android.animation.ValueAnimator
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos

internal const val KnowledgeBaseUrl = "https://menetrendgo.hu/tudasbazis"

/** End of the Settings list: the website's passing vehicles, a short introduction and the knowledge base link. */
@Composable internal fun SettingsFooter(onNameTap: (() -> Unit)?) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val dark = LocalAppDarkTheme.current
    var unavailable by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = AppLayout.ItemGap)) {
        FooterScene()
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
            Row(Modifier.heightIn(min = 48.dp).then(if (onNameTap != null) Modifier.clickable(onClick = onNameTap) else Modifier),
                verticalAlignment = Alignment.CenterVertically) {
                Text("MenetrendGO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("  ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Text("Utazástervezés, élő járműtérkép, megállói indulások és bérletemlékeztetők, fiók és reklámok nélkül.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Text("Független, nem hivatalos alkalmazás, nem áll kapcsolatban a MÁV-val. A menetrendi és járműadatok a MÁVPlusz rendszeréből származnak, hiányozhatnak vagy késhetnek.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        GlassButton(onClick = { unavailable = !openInAppBrowser(context, KnowledgeBaseUrl, dark, colors.surface) },
            container = colors.surface, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, Modifier.size(18.dp))
            Spacer(Modifier.width(AppLayout.CompactGap))
            Text("Kérdésed van?")
        }
    }
    if (unavailable) BrowserUnavailableDialog("A tudásbázis megnyitásához engedélyezz vagy telepíts egy böngészőt.") { unavailable = false }
}

/**
 * A website footer vehicle (menetrendgo-web `public/vehicles`, normalized to 124 units tall): proportional artwork width,
 * speed in vehicle heights per second, and the whole-body motion of its SVG. Trains bob by [bob] units every
 * [period] seconds; buses ([rockX] set) rock about their rear axle at (rockX, 108). [puffs] are exhaust x positions.
 */
internal enum class FooterVehicle(
    @DrawableRes val art: Int, val units: Float, val speed: Float, val bob: Float, val period: Float,
    val rockX: Float = Float.NaN, val puffs: List<Float> = emptyList()
) {
    // Website order: trains and buses alternate where the count allows.
    Flirt(R.drawable.footer_flirt, 1500f, 8f, 1.4f, .46f),
    Inovell(R.drawable.footer_inovell, 430f, 6f, .7f, .9f, rockX = 132f),
    Szili(R.drawable.footer_szili, (2255f / 193f * 124f), 8f, 1.3f, .5f),
    Econell(R.drawable.footer_econell, 430f, 6f, .7f, .85f, rockX = 120f),
    Desiro(R.drawable.footer_desiro, (2280f / 269f * 124f), 8f, 1.2f, .44f,
        puffs = listOf(460.5f / 269f * 124f, 1819.5f / 269f * 124f)),
    Szergej(R.drawable.footer_szergej, (2345f / 185f * 124f), 8f, 1.3f, .5f),
    Bzmot(R.drawable.footer_bzmot, (1160f / 172f * 124f), 8f, 1.2f, .48f),
    Szivarvany(R.drawable.footer_szivarvany, (3785f / 199f * 124f), 8f, 1.3f, .5f);

    val aspect get() = units / 124f
    val bus get() = !rockX.isNaN()
}

private val ExhaustColor = Color(0xFF9AA3AB)

/**
 * Port of the menetrendgo.hu footer without its captions: the FLIRT, Inovell 12, Szili, Econell 12
 * Desiro, Szergej, Bzmot and the rainbow consist take turns driving across the track, trailing speed lines. With animations off, a bus
 * waits at the stop.
 */
@Composable private fun FooterScene() {
    val colors = MaterialTheme.colorScheme
    // The light scheme has no outlineVariant of its own; this is the website's track tone from the same palette.
    val track = if (LocalAppDarkTheme.current) colors.outlineVariant else Color(0xFFC4D4E8)
    val density = LocalDensity.current
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    // Bleed past the page gutters so vehicles enter and leave at the screen edges, as on the website.
    val bleed = Modifier.layout { measurable, constraints ->
        val extra = (AppLayout.PageGutter * 2).roundToPx()
        val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
    }
    BoxWithConstraints(bleed.clipToBounds().clearAndSetSemantics {}) {
        val vh = (maxWidth * .045f + 26.dp).coerceIn(56.dp, 84.dp)
        val width = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { vh.toPx() }
        var vehicle by remember { mutableStateOf(FooterVehicle.Inovell) }
        var x by remember { mutableFloatStateOf(Float.NaN) }
        var clock by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(motion, width, heightPx) {
            if (!motion) { vehicle = FooterVehicle.Inovell; x = (width - heightPx * vehicle.aspect) / 2; return@LaunchedEffect }
            x = Float.NaN
            delay(350)
            var last: FooterVehicle? = null
            while (true) {
                vehicle = (FooterVehicle.entries - setOfNotNull(last)).random().also { last = it }
                val w = heightPx * vehicle.aspect
                val pps = vehicle.speed * heightPx
                val start = withFrameNanos { it }
                do {
                    clock = withFrameNanos { (it - start) / 1e9f }
                    x = -w + pps * clock
                } while (x < width)
                x = Float.NaN
                delay(3200)
            }
        }
        Box(Modifier.fillMaxWidth().height(vh + 24.dp)) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(8.dp).background(track))
            if (!x.isNaN()) {
                if (motion) Wake(colors.outline, vh, x, clock)
                Image(painterResource(vehicle.art), null, contentScale = ContentScale.FillHeight,
                    modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp)
                        .wrapContentWidth(Alignment.Start, unbounded = true).height(vh).width(vh * vehicle.aspect)
                        .graphicsLayer {
                            translationX = x
                            if (!motion) return@graphicsLayer
                            val unit = size.height / 124f
                            if (vehicle.bus) {
                                // CSS `rock … alternate`: rotate -.25° → .2° and lift .7 units, eased both ways.
                                val p = .5f - .5f * cos(PI.toFloat() * clock / vehicle.period)
                                rotationZ = -.25f + .45f * p
                                translationY = -vehicle.bob * unit * p
                                transformOrigin = TransformOrigin(vehicle.rockX / vehicle.units, 108f / 124f)
                            } else translationY = -vehicle.bob * unit * (.5f - .5f * cos(2f * PI.toFloat() * clock / vehicle.period))
                        }
                        .drawWithContent {
                            drawContent()
                            if (motion) vehicle.puffs.forEach { px -> exhaust(px, size.height / 124f, clock) }
                        })
            }
        }
    }
}

/** The Desiro's diesel puffs: three staggered circles per stack that rise back, grow and fade every .9s. */
internal fun DrawScope.exhaust(x: Float, unit: Float, clock: Float) {
    listOf(0f, .3f, .6f).forEach { lead ->
        val p = ((clock + lead) / .9f) % 1f
        val eased = 1f - (1f - p) * (1f - p)
        val alpha = if (p < .2f) .45f * p / .2f else .45f * (1f - (p - .2f) / .8f)
        drawCircle(ExhaustColor.copy(alpha = alpha), 3.4f * unit * (.5f + 1.2f * eased),
            Offset((x - 26f * eased) * unit, (14f - 9f * eased) * unit))
    }
}

/** Three speed lines trailing the vehicle, each fading in and stretching out every .42s, as on the website. */
@Composable private fun BoxScope.Wake(color: Color, vh: Dp, x: Float, clock: Float) {
    Canvas(Modifier.matchParentSize()) { speedLines(color, vh.toPx(), size.height - 8.dp.toPx(), x, clock) }
}

/** The website's speed lines behind a vehicle [h] px tall whose rear is at [rear] and bottom at [bottom]; [scale] shrinks the strokes for small vehicles. */
internal fun DrawScope.speedLines(color: Color, h: Float, bottom: Float, rear: Float, clock: Float, scale: Float = 1f) {
    val line = 3.dp.toPx() * scale
    val top = bottom - h + h * .22f
    val right = rear - 6.dp.toPx() * scale
    listOf(.5f to 0f, .8f to .14f, .35f to .28f).forEachIndexed { i, (length, lead) ->
        val p = ((clock + lead) / .42f) % 1f
        val alpha = if (p < .35f) .45f * p / .35f else .45f * (1f - (p - .35f) / .65f)
        val full = h * length
        val w = full * (.4f + .6f * p)
        val left = right - full / 2 - w / 2 + (10f - 46f * p).dp.toPx() * scale
        drawRoundRect(color.copy(alpha = alpha), Offset(left, top + i * (h * .13f + line)), Size(w, line), CornerRadius(line / 2))
    }
}
