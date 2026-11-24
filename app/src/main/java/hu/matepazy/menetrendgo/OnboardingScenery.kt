package hu.matepazy.menetrendgo

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Onboarding is a ride on line 30: the website's FLIRT carries the rider from Budapest-Déli past
 * Lake Velence to Székesfehérvár, one station per step. Landmarks are SVG path data in 400-unit
 * frames (horizon y=196) drawn in theme tones; the vehicles are the menetrendgo.hu footer artwork.
 * Tapping a vehicle makes it hop and honk. With animations off the camera cuts between stations.
 */


private fun svg(d: String, evenOdd: Boolean = false): Path =
    PathParser().parsePathString(d).toPath().also { if (evenOdd) it.fillType = PathFillType.EvenOdd }

private fun rects(xs: Iterable<Number>, y: Float, w: Float, h: Float) = buildString {
    xs.forEach { val x = it.toFloat(); append("M$x $y h$w v$h h-$w Z ") }
}
private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} $cy A$r $r 0 1 0 ${cx + r} $cy A$r $r 0 1 0 ${cx - r} $cy Z"

private object BudapestArt {
    // Gellért Hill with the Liberty Statue, St Stephen's Basilica, Castle Hill with the palace dome and Matthias Church.
    val far = svg(
        "M-30 196 C0 162 36 134 76 130 C112 127 142 152 172 196 Z " +
        "M72 131 L80 131 L79 113 L73 113 Z M74.4 113 L77.6 113 L77.2 102 L78 97 L76.9 94 L75.1 94 L74 97 L74.8 102 Z " +
        circle(76f, 92.6f, 1.3f) + " M75.2 95 L73.6 89.6 L74.3 89.4 L76 94.6 Z M76.8 95 L78.4 89.6 L77.7 89.4 L76 94.6 Z " +
        "M69 89 Q76 84.4 83 89 Q76 87.4 69 89 Z " +
        "M209 196 L209 150 L217 150 L217 196 Z M233 196 L233 150 L241 150 L241 196 Z " +
        "M208 150 C208 142 218 142 218 150 Z M232 150 C232 142 242 142 242 150 Z " +
        "M215 196 L215 160 L235 160 L235 196 Z M212 161 C212 137 238 137 238 161 Z M223 141 L227 141 L226 131 L225 129 L224 131 Z " +
        "M262 196 C272 180 288 172 304 170 L384 168 C400 170 414 180 428 196 Z " +
        "M308 171 L308 154 L394 154 L394 171 Z M342 154 L342 141 L358 141 L358 154 Z " +
        "M339 142 C339 128 361 128 361 142 Z M348 130 L352 130 L351 121 L350 119 L349 121 Z " +
        "M284 178 L284 152 L300 152 L300 178 Z M283 152 L292 144 L301 152 Z M293 152 L293 130 L295.5 113 L298 130 L298 152 Z"
    )
    val farWindows = svg(rects(312..388 step 6, 158f, 2f, 5f) + rects(345..355 step 4, 145f, 1.6f, 6f))
    // The Parliament on the embankment and the Chain Bridge with its two arched towers.
    val mid = svg(
        "M0 182 L160 182 L160 186 L0 186 Z M10 182 L10 160 L150 160 L150 182 Z " +
        "M10 160 L18 152 L62 152 L68 160 Z M92 160 L98 152 L142 152 L150 160 Z " +
        "M68 160 L68 142 L92 142 L92 160 Z M65 143 C65 127 75 118 80 113 C85 118 95 127 95 143 Z M78.8 114 L80 93 L81.2 114 Z " +
        "M56 160 L56 136 L58 126 L60 136 L60 160 Z M100 160 L100 136 L102 126 L104 136 L104 160 Z " +
        "M7 182 L7 150 L9.5 143 L12 150 L12 182 Z M148 182 L148 150 L150.5 143 L153 150 L153 182 Z " +
        listOf(24, 36, 48, 112, 124, 136).joinToString(" ") { "M${it - 1.5f} 152.4 L$it 145 L${it + 1.5f} 152.4 Z" } + " " +
        "M146 172 L318 172 L318 176 L146 176 Z M175 140 L195 140 L195 135 L175 135 Z M255 140 L275 140 L275 135 L255 135 Z"
    )
    val towers = svg(
        "M177 196 L177 138 L193 138 L193 196 Z M181 172 L181 154 C181 148 189 148 189 154 L189 172 Z " +
        "M257 196 L257 138 L273 138 L273 196 Z M261 172 L261 154 C261 148 269 148 269 154 L269 172 Z", evenOdd = true
    )
    val chains = svg("M146 171 Q162 156 178 139 M192 139 Q225 196 258 139 M272 139 Q290 156 318 171")
    val hangers = svg(buildString {
        for (x in 198..252 step 6) { val t = (x - 192) / 66f; append("M$x ${139 + 114 * t * (1 - t)} L$x 172 ") }
        for (x in 152..172 step 6) { val t = (x - 146) / 32f; append("M$x ${171 - 32 * t} L$x 172 ") }
        for (x in 278..312 step 6) { val t = (x - 272) / 46f; append("M$x ${139 + 32 * t} L$x 172 ") }
    })
    val midWindows = svg(rects(14..62 step 5, 165f, 2f, 6f) + rects(96..144 step 5, 165f, 2f, 6f) + rects(71..87 step 4, 146f, 2f, 7f))
    val ripples = svg("M150 186 h18 M206 190 h26 M238 184 h14 M290 189 h22 M330 185 h16 M30 191 h20 M92 189 h16")
}


private object CountrysideArt {
    // Frame centred on world x 850 (local 200): Kelenföld's housing estate, Lake Velence, Agárd's station house.
    val far = svg("M-300 196 C-200 180 -100 176 0 182 C100 188 200 172 300 176 C400 180 520 170 700 196 Z")
    val mid = svg(
        "M-240 196 V156 H-206 V196 Z M-198 196 V166 H-150 V196 Z " +
        listOf(-124f, -110f, 556f, 572f, 600f).joinToString(" ") { x -> "M$x 196 C${x - 6} 196 ${x - 6} 162 $x 150 C${x + 6} 162 ${x + 6} 196 $x 196 Z" } + " " +
        "M420 196 V176 H470 V196 Z M414 176 L445 162 L476 176 Z"
    )
    val windows = svg(buildString {
        for (row in 0..4) append(rects(listOf(-236, -229, -222, -215), 160f + row * 7, 3f, 3.6f))
        for (row in 0..3) append(rects(listOf(-194, -186, -178, -170, -162), 170f + row * 6.5f, 4f, 3.4f))
        append(rects(listOf(426, 440, 454), 181f, 6f, 8f))
    })
    val reeds = svg(buildString { (-48..-30 step 3).forEach { append("M$it 196 L${it + 1} 183 ") }; (512..530 step 3).forEach { append("M$it 196 L${it - 1} 183 ") } })
    val hull = svg("M-8 0 L8 0 L5 3 L-5 3 Z")
    val sails = svg("M-1 -1 L-1 -18 L8 -2 Z M-2 -1 L-2 -15 L-9 -2 Z")
    val duck = svg("M-3.4 0 C-3.6 -2.6 1.6 -3.2 2.8 -1.8 L3.2 -3.6 C3.4 -5.4 5.8 -5.4 5.9 -3.8 L7.4 -3.4 L5.8 -2.9 L5.2 -1 C4.8 0.2 3 0.2 -3.4 0 Z")
}

private object FehervarArt {
    // Bory Castle, the twin-towered Cathedral of St Stephen and the Országalma on Városház tér.
    val far = svg("M-40 196 C20 178 80 172 140 178 C200 184 240 168 300 170 C350 172 390 184 440 196 Z")
    val mid = svg(
        "M22 192 L22 162 L150 162 L150 192 Z " + rects(22..146 step 8, 158f, 4f, 4f) +
        "M14 192 L14 150 L30 150 L30 192 Z M142 192 L142 150 L158 150 L158 192 Z " +
        rects(listOf(14, 20, 26, 142, 148, 154), 146f, 4f, 4f) +
        "M76 192 L76 128 L96 128 L96 192 Z " + rects(listOf(76, 84, 92), 124f, 4f, 4f) + "M80 124 L86 112 L92 124 Z " +
        "M207 192 L207 152 L253 152 L253 192 Z M207 152 L230 139 L253 152 Z " +
        "M199 192 L199 122 L213 122 L213 192 Z M247 192 L247 122 L261 122 L261 192 Z " +
        "M198 123 C198 112 214 112 214 123 Z M246 123 C246 112 262 112 262 123 Z " +
        listOf(0f, 48f).joinToString(" ") { d ->
            "M${204.2f + d} 114 L${207.8f + d} 114 L${207.4f + d} 107 C${209.4f + d} 106 ${209.4f + d} 102.6 ${206 + d} 100.6 " +
            "C${202.6f + d} 102.6 ${202.6f + d} 106 ${204.6f + d} 107 Z M${205.6f + d} 101 L${206 + d} 92 L${206.4f + d} 101 Z"
        } + " " +
        "M312 192 L332 192 L330 182 L314 182 Z M317 182 L327 182 L326 168 L318 168 Z " + circle(322f, 160f, 9f) +
        " M321.2 151 L322.8 151 L322.8 142 L321.2 142 Z M318.5 145.2 L325.5 145.2 L325.5 146.8 L318.5 146.8 Z"
    )
    val windows = svg(
        (34..64 step 10).joinToString(" ") { "M$it 188 L$it 177 C$it 173.5 ${it + 5} 173.5 ${it + 5} 177 L${it + 5} 188 Z" } + " " +
        (104..134 step 10).joinToString(" ") { "M$it 188 L$it 177 C$it 173.5 ${it + 5} 173.5 ${it + 5} 177 L${it + 5} 188 Z" } + " " +
        rects(listOf(21, 149), 158f, 2f, 8f) + rects(listOf(85), 136f, 2f, 10f) +
        "M204 150 L204 140 C204 137.5 208 137.5 208 140 L208 150 Z M252 150 L252 140 C252 137.5 256 137.5 256 140 L256 150 Z " +
        "M226 192 L226 178 C226 173 234 173 234 178 L234 192 Z " + circle(230f, 147f, 2.4f) +
        " M313.4 158.8 L330.6 158.8 L330.6 161.2 L313.4 161.2 Z"
    )
}

private val cloud = svg("M0 0 C0 -6 6 -9 11 -6 C13 -12 23 -13 26 -6 C31 -8 36 -4 35 0 Z")

/** A stop on the ride: its sign and the camera centre in world units. */
internal class OnboardingStation(val name: String, val x: Float)
internal val onboardingStations = listOf(
    OnboardingStation("Budapest-Déli", 160f), OnboardingStation("Kelenföld", 470f),
    OnboardingStation("Velence", 780f), OnboardingStation("Agárd", 1080f),
    OnboardingStation("Dinnyés", 1270f), OnboardingStation("Székesfehérvár", 1480f),
)

private const val BudapestCentre = 200f
private const val CountrysideCentre = 850f
private const val FehervarCentre = 1500f
private const val FrameHeight = 252f
private const val BackTrack = 199f
private const val FrontTrack = 216f
private const val RoadY = 246f
private const val FarDepth = .82f
private const val MidDepth = .92f
private const val TrainHeight = 27f
private const val TrainRest = 96f   // train front, relative to the camera centre
private const val BusHeight = 22f
private const val BusRest = -6f
private val RailBlue = Color(0xFF003A7E)
private val Arrival = CubicBezierEasing(.15f, .7f, .3f, 1f)

private enum class Cast(val vehicle: FooterVehicle, val honk: String) {
    Flirt(FooterVehicle.Flirt, "Tűű-tűű!"), Szili(FooterVehicle.Szili, "Huhúú!"),
    Econell(FooterVehicle.Econell, "Tü-tüü!"), Inovell(FooterVehicle.Inovell, "Píp-píp!")
}

@Composable internal fun OnboardingScenery(step: Int, departing: Boolean, onDeparted: () -> Unit, modifier: Modifier = Modifier) {
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    val camera = remember { Animatable(onboardingStations[step].x) }
    val trainLead = remember { Animatable(if (motion) -560f else TrainRest) }
    var clock by remember { mutableFloatStateOf(0f) }
    val departed by rememberUpdatedState(onDeparted)
    LaunchedEffect(step) {
        val target = onboardingStations[step].x
        if (motion) camera.animateTo(target, tween(1800, easing = FastOutSlowInEasing)) else camera.snapTo(target)
    }
    LaunchedEffect(Unit) { trainLead.animateTo(TrainRest, tween(2600, easing = Arrival)) }
    LaunchedEffect(departing) {
        if (!departing) return@LaunchedEffect
        if (motion) trainLead.animateTo(760f, tween(1100, easing = FastOutLinearInEasing))
        departed()
    }
    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) clock = withFrameNanos { (it - start) / 1e9f }
    }
    val art = Cast.entries.associateWith { ImageBitmap.imageResource(it.vehicle.art) }
    val hops = remember { FloatArray(Cast.entries.size) { -9f } }
    val hits = remember { mutableMapOf<Cast, Rect>() }
    val bus = remember { floatArrayOf(Float.NaN, 0f) }
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val dark = LocalAppDarkTheme.current
    val skyTop = scheme.primaryContainer
    val skyBottom = lerp(scheme.primaryContainer, scheme.background, .72f)
    val far = lerp(skyBottom, scheme.outline, .26f)
    val mid = lerp(skyBottom, scheme.outline, .5f)
    val lit = if (dark) lerp(mid, Color(0xFFFFD58A), .6f) else lerp(skyBottom, mid, .12f)
    val water = lerp(scheme.primaryContainer, scheme.primary, if (dark) .18f else .26f)
    val ground = scheme.surfaceContainerHigh
    val track = scheme.onSurfaceVariant
    val road = scheme.surfaceContainerHighest
    val bubble = scheme.surface
    val ink = scheme.onSurface
    val measurer = rememberTextMeasurer()
    Canvas(modifier.clipToBounds()
        .semantics { contentDescription = "Vonat érkezik ${onboardingStations[step].name} állomásra." }
        .pointerInput(Unit) {
            detectTapGestures { tap ->
                Cast.entries.reversed().firstOrNull { hits[it]?.contains(tap) == true }?.let {
                    hops[it.ordinal] = clock
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }) {
        val cam = camera.value
        val s = minOf(size.width / 220f, size.height / FrameHeight)
        val half = size.width / s / 2f
        val top = size.height - FrameHeight * s
        fun y(units: Float) = top + units * s
        fun x(world: Float) = (world - cam + half) * s
        fun hop(c: Cast) = ((clock - hops[c.ordinal]) / .5f).let { if (it in 0f..1f) sin(PI.toFloat() * it) else 0f }
        drawRect(Brush.verticalGradient(listOf(skyTop, skyBottom), startY = 0f, endY = y(196f)))
        // Each art group sits in a frame whose local x=200 is world [centre], moving at [depth] of the camera.
        fun group(centre: Float, depth: Float, block: DrawScope.() -> Unit) =
            withTransform({ translate(((centre - cam) * depth + half - 200f) * s, top); scale(s, s, Offset.Zero) }, block)

        group(CountrysideCentre, .35f) {
            listOf(-560f to 54f, -250f to 26f, 60f to 44f, 330f to 64f, 620f to 30f, 900f to 52f, 1180f to 36f,
                -420f to -130f, -60f to -60f, 200f to -150f, 480f to -90f, 760f to -170f, 1040f to -40f).forEach { (cx, cy) ->
                withTransform({ translate(cx + clock * 2f, cy) }) { drawPath(cloud, scheme.surface.copy(alpha = if (dark) .16f else .85f)) }
            }
        }
        group(BudapestCentre, FarDepth) { drawPath(BudapestArt.far, far); drawPath(BudapestArt.farWindows, lit) }
        group(CountrysideCentre, FarDepth) { drawPath(CountrysideArt.far, far) }
        group(FehervarCentre, FarDepth) { drawPath(FehervarArt.far, far) }
        group(BudapestCentre, MidDepth) {
            drawRect(water, Offset(-60f, 180f), Size(520f, 16f))
            drawPath(BudapestArt.ripples, lit, style = Stroke(.8f))
            drawPath(BudapestArt.mid, mid); drawPath(BudapestArt.towers, mid)
            drawPath(BudapestArt.chains, mid, style = Stroke(1.6f)); drawPath(BudapestArt.hangers, mid, style = Stroke(.5f))
            drawPath(BudapestArt.midWindows, lit)
        }
        group(CountrysideCentre, MidDepth) {
            drawRect(water, Offset(-40f, 186f), Size(560f, 10f))
            drawPath(CountrysideArt.reeds, mid, style = Stroke(.8f))
            drawPath(CountrysideArt.mid, mid); drawPath(CountrysideArt.windows, lit)
            listOf(40f, 250f).forEachIndexed { i, bx ->
                withTransform({ translate(bx + sin(clock * .3f + i) * 6f, 189f); rotate(sin(clock * 1.6f + i * 2f) * 3f, Offset.Zero) }) {
                    drawPath(CountrysideArt.hull, mid); drawPath(CountrysideArt.sails, scheme.surface)
                }
            }
            listOf(120f, 130f, 380f).forEachIndexed { i, dx ->
                withTransform({ translate(dx, 192f + sin(clock * 2.4f + i * 1.7f) * .5f) }) { drawPath(CountrysideArt.duck, mid) }
            }
        }
        group(FehervarCentre, MidDepth) { drawPath(FehervarArt.mid, mid); drawPath(FehervarArt.windows, lit) }

        // Ground, two tracks and the road.
        drawRect(ground, Offset(0f, y(196f)), Size(size.width, size.height - y(196f)))
        val left = cam - half
        listOf(BackTrack, FrontTrack).forEach { rail ->
            var sleeper = floor(left / 6f) * 6f
            while (sleeper < cam + half + 6f) { drawRect(track.copy(alpha = .4f), Offset(x(sleeper), y(rail)), Size(2.6f * s, 2.2f * s)); sleeper += 6f }
            drawLine(track, Offset(0f, y(rail)), Offset(size.width, y(rail)), 1.2f * s)
        }
        drawRect(road, Offset(0f, y(RoadY - 18f)), Size(size.width, 22f * s))
        var dash = floor(left / 16f) * 16f
        while (dash < cam + half + 16f) { drawRect(ground, Offset(x(dash), y(RoadY - 9f)), Size(7f * s, .9f * s)); dash += 16f }

        // Station boards on tall posts, above the train.
        onboardingStations.forEach { st ->
            val bx = st.x - 40f
            if (abs(bx - cam) > half + 60f) return@forEach
            drawLine(track, Offset(x(bx - 24f), y(166f)), Offset(x(bx - 24f), y(BackTrack)), .9f * s)
            drawLine(track, Offset(x(bx + 24f), y(166f)), Offset(x(bx + 24f), y(BackTrack)), .9f * s)
            val label = measurer.measure(st.name, TextStyle(color = Color.White, fontSize = (6.2f * s).toSp(), fontWeight = FontWeight.Bold))
            val w = maxOf(60f * s, label.size.width + 10f * s)
            drawRoundRect(RailBlue, Offset(x(bx) - w / 2, y(154f)), Size(w, 13f * s), CornerRadius(2f * s))
            drawText(label, topLeft = Offset(x(bx) - label.size.width / 2f, y(160.5f) - label.size.height / 2f))
        }

        // A parked train stands still: its running bob is ~1 px at this size and reads as a twitch.
        fun vehicle(c: Cast, front: Float, contact: Float, height: Float, parked: Boolean = false) {
            val v = c.vehicle
            val h = height * s
            val w = h * v.aspect
            val l = x(front) - w
            val b = y(contact)
            hits[c] = Rect(l, b - h, l + w, b)
            if (l > size.width || l + w < 0f) return
            val unit = h / 124f
            withTransform({
                translate(l, b - h - hop(c) * h * .32f)
                if (!motion) return@withTransform
                if (v.bus) {
                    val p = .5f - .5f * cos(PI.toFloat() * clock / v.period)
                    rotate(-.25f + .45f * p - hop(c) * 2f, Offset(v.rockX * unit, 108f * unit))
                    translate(0f, -v.bob * unit * p)
                } else if (!parked) translate(0f, -v.bob * unit * (.5f - .5f * cos(2f * PI.toFloat() * clock / v.period)))
            }) {
                drawImage(art.getValue(c), dstSize = IntSize(w.roundToInt(), h.roundToInt()), filterQuality = FilterQuality.High)
                if (motion) v.puffs.forEach { exhaust(it, unit, clock) }
            }
        }

        vehicle(Cast.Szili, BudapestCentre + 70f, BackTrack, 21f, parked = true)
        val train = cam + trainLead.value
        val moving = motion && (abs(camera.velocity) > 20f || trainLead.isRunning)
        if (moving) speedLines(scheme.outline, TrainHeight * s, y(FrontTrack), x(train) - TrainHeight * s * FooterVehicle.Flirt.aspect, clock)
        vehicle(Cast.Flirt, train, FrontTrack, TrainHeight)
        // The Econell races the train: it follows the camera with a lag and catches up at each station.
        val target = cam + BusRest
        if (bus[0].isNaN()) bus[0] = if (motion) cam - 420f else target
        bus[0] += (target - bus[0]) * (1f - exp(-(clock - bus[1]).coerceIn(0f, .1f) * 1.5f))
        bus[1] = clock
        vehicle(Cast.Inovell, FehervarCentre + 80f, RoadY, BusHeight)
        vehicle(Cast.Econell, bus[0], RoadY, BusHeight)

        // Honk bubbles above tapped vehicles.
        Cast.entries.forEach { c ->
            val age = clock - hops[c.ordinal]
            val r = hits[c] ?: return@forEach
            if (age !in 0f..1.4f) return@forEach
            val label = measurer.measure(c.honk, TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold))
            val pad = 10f * density
            val bw = label.size.width + pad * 2; val bh = label.size.height + pad
            val cx = (minOf(r.right, size.width) - bw / 2 - 12f * density).coerceIn(bw / 2 + 4f, size.width - bw / 2 - 4f)
            val by = r.top - bh - 12f * density - (1f - minOf(age / .2f, 1f)) * 8f * density
            val alpha = if (age > 1.1f) (1.4f - age) / .3f else 1f
            drawRoundRect(bubble.copy(alpha = alpha), Offset(cx - bw / 2, by), Size(bw, bh), CornerRadius(bh / 2))
            drawPath(Path().apply { moveTo(cx - 6f * density, by + bh - 1f); lineTo(cx + 6f * density, by + bh - 1f); lineTo(cx + 2f * density, by + bh + 8f * density); close() }, bubble.copy(alpha = alpha))
            drawText(label, topLeft = Offset(cx - label.size.width / 2f, by + pad / 2), alpha = alpha)
        }
    }
}
