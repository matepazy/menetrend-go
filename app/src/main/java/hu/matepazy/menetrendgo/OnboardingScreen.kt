package hu.matepazy.menetrendgo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/* First run: one short line per station of the ride; skippable at any point. */

private class Stop(val title: String, val line: String)
private val stops = listOf(
    Stop("Szia! Indulhatunk?", "Menetrend, élő térkép és indulási táblák a MÁV-hálózat vonataira és buszaira. Fiók és reklám nélkül."),
    Stop("Tervezz!", "Add meg, honnan hová és mikor utazol. A találatoknál látod az átszállásokat és a késéseket, a kereséseidet pedig elmentheted."),
    Stop("Élő térkép", "A vonatok és buszok valós időben mozognak. Koppints egy járműre: látod a késését, a következő megállóit és a járműtípust."),
    Stop("Indulások kéznél", "Bármely megálló indulási tábláját kiteheted widgetként a kezdőképernyőre, így az app megnyitása nélkül is látod."),
    Stop("Offline menetrendek", "Tölts le menetrendeket a Beállításokban, hogy internet nélkül is tervezhess és megnézhesd az indulásokat. Az élő késésekhez internet kell."),
    Stop("Bérletfigyelő", "Rögzítsd a bérleteidet, és szólunk, mielőtt lejárnak. Bekapcsolás: Beállítások › Kiegészítő funkciók."),
    // The terminus: both documents must be accepted before the train leaves.
    Stop("Végállomás", "Indulás előtt fogadd el a licencszerződést és a felhasználási feltételeket. Koppints egy kártyára az elolvasásához."),
)
private val consentStep = stops.lastIndex

/**
 * First run, ending in the legal consent stop. [consentOnly] shows just that stop, without Back or Kihagyás,
 * for users who finished onboarding before the terms existed or changed. [onFinish] runs once the train departs,
 * which is only possible with both documents accepted.
 */
@Composable internal fun OnboardingFlow(
    termsAccepted: Boolean,
    consentOnly: Boolean = false,
    onReplayDismiss: (() -> Unit)? = null,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(if (consentOnly) consentStep else 0) }
    var departing by remember { mutableStateOf(false) }
    var licenseOk by rememberSaveable { mutableStateOf(termsAccepted) }
    var termsOk by rememberSaveable { mutableStateOf(termsAccepted) }
    var reading by rememberSaveable { mutableStateOf<LegalDoc?>(null) }
    val last = step == consentStep
    val station = step.coerceAtMost(onboardingStations.lastIndex)
    val canLeave = !last || (licenseOk && termsOk)
    BackHandler(enabled = step > 0 && !departing && !consentOnly) { step-- }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            OnboardingScenery(station, departing, onFinish, Modifier.fillMaxSize())
            // The feature mock floats in the sky, above the landmarks (the scene frame is 252 units, 220 wide).
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val s = minOf(maxWidth / 220f, maxHeight / 252f)
                val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp
                val room = (maxHeight - s * 252f + s * 92f - top - 8.dp).coerceAtLeast(120.dp)
                OnboardingFeatureMock(step, room, Modifier.padding(top = top, start = AppLayout.SheetPadding, end = AppLayout.SheetPadding).fillMaxWidth())
            }
            Row(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(8.dp), horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                if (!last) TextButton({ step = consentStep }, Modifier.heightIn(min = 48.dp)) { Text("Kihagyás") }
                if (onReplayDismiss != null) IconButton(onClick = onReplayDismiss) { Icon(Icons.Outlined.Close, "Újrajátszás bezárása") }
            }
        }
        Surface(Modifier.fillMaxWidth().glassEdge(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), glassTone(GlassLevel.Floating)),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), color = MaterialTheme.colorScheme.surface) {
            Box(contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(AppLayout.SheetPadding), verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
                    RouteStrip(station)
                    AnimatedContent(step, transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (fadeIn(tween(AppMotion.Reveal, 120)) + slideInHorizontally(tween(AppMotion.Reveal, 120)) { it / 8 * dir }) togetherWith
                            fadeOut(tween(AppMotion.Dismiss))
                    }, label = "Onboarding stop") { i ->
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
                            Text(stops[i].title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(stops[i].line, minLines = if (i == consentStep) 1 else 3, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (i == consentStep) Column(Modifier.padding(top = AppLayout.CompactGap), verticalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                                LegalConsentCard(LegalDoc.License, licenseOk, { licenseOk = it }) { reading = LegalDoc.License }
                                LegalConsentCard(LegalDoc.Terms, termsOk, { termsOk = it }) { reading = LegalDoc.Terms }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                        if (step > 0 && !consentOnly) TextButton({ step-- }, Modifier.heightIn(min = 56.dp), enabled = !departing) { Text("Vissza") }
                        val go = !departing && canLeave
                        Button({ if (last) departing = true else step++ }, Modifier.weight(1f).heightIn(min = 56.dp).testTag("onboarding-next"), border = glassBorder(buttonGlassTone(go)), enabled = go, shape = CircleShape) {
                            Text(when { step == 0 -> "Beszállás!"; last -> "Indulás!"; else -> "Tovább" }, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (step == 0) Text("Tipp: koppints a járművekre!", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
    reading?.let { doc ->
        LegalDocumentDrawer(doc, onDismiss = { reading = null }, onAccept = {
            if (doc == LegalDoc.License) licenseOk = true else termsOk = true
        })
    }
}

/** Line diagram of the ride: stop dots with the rail pictogram travelling between them. */
@Composable private fun RouteStrip(step: Int) {
    val scheme = MaterialTheme.colorScheme
    val n = onboardingStations.size
    val at by animateFloatAsState(step / (n - 1f), tween(1800, easing = FastOutSlowInEasing), label = "Route strip")
    BoxWithConstraints(Modifier.fillMaxWidth().height(32.dp)
        .clearAndSetSemantics { contentDescription = "${step + 1}. állomás az ${n}-ből: ${onboardingStations[step].name}" }) {
        val icon = 28.dp
        val span = maxWidth - icon
        Canvas(Modifier.fillMaxSize()) {
            val r = 5.dp.toPx(); val inset = icon.toPx() / 2; val w = size.width - inset * 2; val cy = size.height / 2
            drawLine(scheme.outlineVariant, Offset(inset, cy), Offset(inset + w, cy), 4.dp.toPx())
            drawLine(scheme.primary, Offset(inset, cy), Offset(inset + w * at, cy), 4.dp.toPx())
            repeat(n) { i ->
                val c = Offset(inset + w * i / (n - 1f), cy)
                val reached = i / (n - 1f) <= at + .001f
                drawCircle(if (reached) scheme.primary else scheme.surface, r, c)
                if (!reached) drawCircle(scheme.outlineVariant, r, c, style = Stroke(2.dp.toPx()))
            }
        }
        Image(painterResource(R.drawable.ic_mode_rail), null, Modifier.align(Alignment.CenterStart).offset(x = span * at).size(icon))
    }
}
