package hu.matepazy.menetrendgo

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Network-wide provider notices, filterable by operator; each card expands in place to the full text. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NetworkAlertsDrawer(vm: AppState, dismiss: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadNetworkAlerts() }
    val alerts = vm.networkAlerts
    var operator by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    val now = vm.networkAlertsUpdated.takeIf { it > 0 } ?: System.currentTimeMillis()
    val today = remember(now) { Instant.ofEpochMilli(now).atZone(Budapest).toLocalDate() }
    val operators = remember(alerts) { networkAlertOperators(alerts.orEmpty()) }
    if (operator != null && operators.none { it.first == operator }) operator = null
    val shown = alerts.orEmpty().filter { operator == null || it.operator == operator }
    val (upcoming, current) = shown.partition { it.upcoming(now) }

    AppBottomSheet(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = AppLayout.SheetPadding, end = AppLayout.SheetPadding, bottom = AppLayout.SheetPadding),
            verticalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
            item(key = "header") {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = AppLayout.CompactGap), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
                        Text("Forgalmi hírek", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() })
                        Text(when {
                            vm.networkAlertsUpdated > 0 -> "MÁV, BKK és Volánbusz · frissítve ${clock.format(Instant.ofEpochMilli(vm.networkAlertsUpdated).atZone(Budapest))}"
                            else -> "MÁV, BKK és Volánbusz közleményei"
                        }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.loadNetworkAlerts(force = true) }, enabled = !vm.networkAlertsLoading) {
                        Icon(Icons.Outlined.Refresh, "Közlemények frissítése")
                    }
                    IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Forgalmi hírek bezárása") }
                }
            }
            if (operators.size > 1) item(key = "filters") {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                    OperatorChip("Mind", alerts.orEmpty().size, operator == null) { operator = null }
                    operators.forEach { (op, count) ->
                        OperatorChip(NetworkAlert.operatorName(op), count, operator == op) { operator = if (operator == op) null else op }
                    }
                }
            }
            if (vm.networkAlertsLoading) item(key = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape)) }
            vm.networkAlertsError?.let { message -> item(key = "error") {
                if (alerts == null) InfoPanel("A közlemények nem tölthetők be", message) { vm.loadNetworkAlerts(force = true) }
                else Text("Korábbi adatok · $message", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            } }
            if (alerts == null && vm.networkAlertsError == null) item(key = "skeleton") {
                Column(verticalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
                    repeat(3) { Surface(Modifier.fillMaxWidth().height(132.dp), shape = cardShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {} }
                }
            }
            if (alerts != null && shown.isEmpty()) item(key = "empty") {
                InfoPanel("Nincs érvényes közlemény", "A szolgáltatók most nem jeleznek vonal- vagy állomásszintű forgalmi változást.")
            }
            if (current.isNotEmpty()) {
                item(key = "current") { SectionLabel("Most érvényes", current.size) }
                items(current, key = { "c" + it.id }) { alert ->
                    NetworkAlertCard(alert, today, alert.id in expanded) { expanded = expanded.toggle(alert.id) }
                }
            }
            if (upcoming.isNotEmpty()) {
                item(key = "upcoming") { SectionLabel("Tervezett", upcoming.size, Modifier.padding(top = AppLayout.CompactGap)) }
                items(upcoming, key = { "u" + it.id }) { alert ->
                    NetworkAlertCard(alert, today, alert.id in expanded) { expanded = expanded.toggle(alert.id) }
                }
            }
            if (alerts != null && shown.isNotEmpty()) item(key = "footnote") {
                Text("A vonatonkénti késésokokat az utazás részleteinél látod.", Modifier.padding(top = AppLayout.CompactGap),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val clock = DateTimeFormatter.ofPattern("HH:mm")
private val cardShape = RoundedCornerShape(28.dp)
private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

@Composable
private fun OperatorChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, shape = CircleShape,
        label = { Text("$label  $count", style = MaterialTheme.typography.labelLarge) },
        modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = "$label, $count közlemény" })
}

@Composable
private fun SectionLabel(text: String, count: Int, modifier: Modifier = Modifier) {
    Text("$text · $count", modifier.padding(top = AppLayout.CompactGap).semantics { heading() },
        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NetworkAlertCard(item: NetworkAlert, today: LocalDate, open: Boolean, toggle: () -> Unit) {
    val alert = item.alert
    val colors = MaterialTheme.colorScheme
    val links = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline))
    val title = remember(alert.title) { plainAlertText(alert.title) }
    val body = remember(alert.body, colors.primary) { alert.body.takeIf(String::isNotBlank)?.let { alertMarkup(it, links) } }
    val fullTitle = remember(alert.title, colors.primary) { alertMarkup(alert.title, links) }
    val chevron by animateFloatAsState(if (open) 180f else 0f, tween(AppMotion.Feedback), label = "Alert expand")
    val meta = listOfNotNull(item.operatorName, item.effectLabel).joinToString(" · ")
    Surface(shape = cardShape, color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(role = Role.Button, onClickLabel = if (open) "Közlemény összecsukása" else "Közlemény kibontása", onClick = toggle)
            .animateContentSize(tween(AppMotion.Reveal, easing = AppMotion.State)).padding(AppLayout.PanelPadding),
            verticalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                Icon(alert.icon, alert.kind, Modifier.size(20.dp), tint = alert.tint())
                Text(meta, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = if (alert.warning) colors.error else colors.onSurfaceVariant)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(22.dp).rotate(chevron), tint = colors.onSurfaceVariant)
            }
            if (open) Text(fullTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            else Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (item.lines.isNotEmpty()) {
                val visible = if (open) item.lines else item.lines.take(8)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Érintett vonalak: " + item.lines.joinToString { it.name } }) {
                    visible.forEach { LinePill(it) }
                    if (visible.size < item.lines.size) Text("+${item.lines.size - visible.size}", Modifier.align(Alignment.CenterVertically).padding(start = 2.dp),
                        style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                }
            }
            if (item.stations.isNotEmpty() && (item.lines.isEmpty() || open)) {
                val names = if (open) item.stations else item.stations.take(3)
                val more = item.stations.size - names.size
                Text("Érintett ${if (item.lines.isEmpty()) "állomások" else "megállók"}: ${names.joinToString(", ")}${if (more > 0) " és még $more" else ""}",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            }
            alertValidity(alert, today)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            if (open) {
                body?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (alert.url.isNotBlank()) ProviderAlertLink(alert.url)
            }
        }
    }
}

/** Provider line colours where given; otherwise a quiet tonal pill. */
@Composable
private fun LinePill(line: AlertLine) {
    val provided = line.color.takeIf { it.isNotBlank() }?.let { Color(0xFF000000 or it.toLong(16)) }
    val background = provided ?: MaterialTheme.colorScheme.primaryContainer
    val foreground = line.textColor.takeIf { it.isNotBlank() && provided != null }?.let { Color(0xFF000000 or it.toLong(16)) }
        ?: if (provided == null) MaterialTheme.colorScheme.onPrimaryContainer else if (background.luminance() > .179f) Color.Black else Color.White
    Surface(shape = CircleShape, color = background, contentColor = foreground) {
        Text(line.name, Modifier.padding(horizontal = 10.dp, vertical = 4.dp).widthIn(min = 16.dp),
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
