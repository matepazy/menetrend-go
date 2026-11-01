package hu.matepazy.menetrendgo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Provider notices for one ride: quiet tappable titles in the timeline; details open in a drawer. */
@Composable
internal fun JourneyLegAlerts(leg: Leg, modifier: Modifier = Modifier) {
    if (leg.alerts.isEmpty()) return
    var openId by rememberSaveable(leg.tripId, leg.start) { mutableStateOf<String?>(null) }
    // Rows carry their own 48dp touch height, so they stack without extra gaps.
    Column(modifier.fillMaxWidth()) {
        leg.alerts.forEach { alert -> JourneyAlertTitle(alert) { openId = alert.id } }
    }
    leg.alerts.firstOrNull { it.id == openId }?.let { alert ->
        JourneyAlertDrawer(alert, leg) { openId = null }
    }
}

private val MavAlert.icon: ImageVector get() = if (warning) Icons.Outlined.WarningAmber else Icons.Outlined.Info
private val MavAlert.kind: String get() = if (warning) "Figyelmeztetés" else "Tájékoztatás"
@Composable private fun MavAlert.tint(): Color =
    if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

@Composable
private fun JourneyAlertTitle(alert: MavAlert, open: () -> Unit) {
    val title = remember(alert.title) { plainAlertText(alert.title) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
        .clickable(onClickLabel = "Közlemény megnyitása", role = Role.Button, onClick = open)
        .padding(vertical = AppLayout.CompactGap),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
        Icon(alert.icon, alert.kind, Modifier.size(20.dp), tint = alert.tint())
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JourneyAlertDrawer(alert: MavAlert, leg: Leg, dismiss: () -> Unit) {
    val linkColor = MaterialTheme.colorScheme.primary
    val links = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    val title = remember(alert.title, linkColor) { alertMarkup(alert.title, links) }
    val body = remember(alert.body, linkColor) { alert.body.takeIf(String::isNotBlank)?.let { alertMarkup(it, links) } }
    val travelDay = remember(leg.start) { java.time.Instant.ofEpochMilli(leg.start).atZone(Budapest).toLocalDate() }
    val uriHandler = LocalUriHandler.current
    AppBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = AppLayout.SheetPadding, end = AppLayout.SheetPadding, bottom = AppLayout.SheetPadding)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { RouteBadge(leg) }
                IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Közlemény bezárása") }
            }
            Text("${leg.from.name} → ${leg.to.name}", Modifier.padding(top = AppLayout.CompactGap),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = AppLayout.SheetPadding), horizontalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
                // Optically centre the icon on the headline's first 32sp line.
                Icon(alert.icon, alert.kind, Modifier.padding(top = 2.dp).size(28.dp), tint = alert.tint())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    alertValidity(alert, travelDay)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            body?.let { Text(it, Modifier.padding(top = AppLayout.SheetPadding), style = MaterialTheme.typography.bodyLarge) }
            if (alert.url.isNotBlank()) OutlinedButton(onClick = { runCatching { uriHandler.openUri(alert.url) } }, shape = CircleShape,
                modifier = Modifier.padding(top = AppLayout.SheetPadding).fillMaxWidth().heightIn(min = 52.dp)) {
                Text("Részletek a szolgáltatónál")
                Spacer(Modifier.width(AppLayout.CompactGap))
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, "külső hivatkozás", Modifier.size(18.dp))
            }
        }
    }
}

/** Provider HTML keeps emphasis, lists and links; inline colours and fonts would fight the theme. */
private fun alertMarkup(html: String, links: TextLinkStyles): AnnotatedString {
    val cleaned = html
        .replace(Regex("\\sstyle\\s*=\\s*(\"[^\"]*\"|'[^']*')", RegexOption.IGNORE_CASE), "")
        .replace(Regex("<p>\\s*(<br\\s*/?>|&nbsp;)?\\s*</p>", RegexOption.IGNORE_CASE), "")
        .replace("\n", " ")
    val text = AnnotatedString.fromHtml(cleaned, links)
    val end = text.text.trimEnd().length
    val start = text.text.length - text.text.trimStart().length
    return if (start >= end) AnnotatedString(plainAlertText(html)) else text.subSequence(start, end)
}
