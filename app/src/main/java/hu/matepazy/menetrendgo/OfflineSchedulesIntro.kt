package hu.matepazy.menetrendgo

import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Keeps the previous destination mounted while the release notice or onboarding replay is open. */
@Composable internal fun IntroWindow(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val lightSystemBars = MaterialTheme.colorScheme.background.luminance() > .5f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
    )) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setWindowAnimations(0)
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightSystemBars
                    isAppearanceLightNavigationBars = lightSystemBars
                }
            }
        }
        content()
    }
}

@Composable internal fun OfflineSchedulesIntro(onDismiss: () -> Unit) {
    IntroWindow(onDismiss) {
        val scheme = MaterialTheme.colorScheme
        BoxWithConstraints(Modifier.fillMaxSize().background(scheme.background).safeDrawingPadding(),
            contentAlignment = Alignment.Center) {
            val wide = maxWidth >= 640.dp && maxWidth > maxHeight
            val artworkWidth = (maxHeight * .44f).coerceIn(180.dp, 340.dp)
            Column(Modifier.widthIn(max = if (wide) 820.dp else 520.dp).fillMaxWidth().fillMaxHeight()
                .padding(horizontal = AppLayout.SheetPadding), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(vertical = AppLayout.SheetPadding),
                    verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap, Alignment.CenterVertically)) {
                    if (wide) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(36.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            OfflinePreviewPhones(Modifier.width(artworkWidth))
                            OfflineIntroCopy(Modifier.weight(1f))
                        }
                    } else {
                        OfflinePreviewPhones(Modifier.width(artworkWidth).align(Alignment.CenterHorizontally))
                        OfflineIntroCopy()
                    }
                }
                MainButton("Rendben", Icons.Outlined.Check, onClick = onDismiss)
                Spacer(Modifier.height(AppLayout.ItemGap))
            }
        }
    }
}

@Composable private fun OfflineIntroCopy(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
        Text("Offline menetrendek", Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Töltsd le előre a menetrendeket a Beállításokban. Utána internet nélkül is tervezhetsz utat és megnézheted az indulásokat.",
            style = MaterialTheme.typography.bodyLarge)
        Text("Az offline idők menetrend szerintiek. Az élő késésekhez és a járművek térképes követéséhez internet kell.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun OfflinePreviewPhones(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val width = maxWidth
        Box(Modifier.fillMaxWidth().height(width * 1.24f)) {
            val flirt = FooterVehicle.Flirt
            val desiro = FooterVehicle.Desiro
            Image(painterResource(flirt.art), contentDescription = null,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = width * -.015f, y = width * .118f)
                    .width(width * .78f).aspectRatio(flirt.aspect).rotate(-10f))
            Image(painterResource(desiro.art), contentDescription = null,
                modifier = Modifier.offset(x = width * -.005f, y = width * .91f)
                    .width(width * .66f).aspectRatio(desiro.aspect).rotate(8f))
            OfflinePhone(R.drawable.offline_intro_downloads, "Letöltött országos és budapesti menetrendek a Beállításokban",
                Modifier.align(Alignment.TopStart).offset(x = width * .065f, y = width * .04f)
                    .width(width * .49f).rotate(-8f))
            OfflinePhone(R.drawable.offline_intro_results, "Menetrend szerinti indulások Szolnok felé, Offline mód sávval",
                Modifier.align(Alignment.TopEnd).offset(x = width * -.065f, y = width * .105f)
                    .width(width * .49f).rotate(8f))
        }
    }
}

@Composable private fun OfflinePhone(@DrawableRes image: Int, description: String, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val width = maxWidth
        val body = RoundedCornerShape(14)
        val screen = RoundedCornerShape(11)
        Column(Modifier.fillMaxWidth().background(Color(0xFF101923), body)
            .border(1.dp, Color(0xFF536478), body).padding(width * .032f).clip(screen)
            .background(Color(0xFFEDF4FA))) {
            Box(Modifier.fillMaxWidth().height(width * .087f), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width * .031f).background(Color(0xFF101923), CircleShape))
            }
            Image(painterResource(image), description, Modifier.fillMaxWidth().aspectRatio(720f / 1533f),
                contentScale = ContentScale.FillBounds)
        }
    }
}
