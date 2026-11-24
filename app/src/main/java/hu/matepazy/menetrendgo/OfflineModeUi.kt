package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** The calm pill above the navigation bar while the app works from downloaded timetables (or cannot). */
@Composable internal fun OfflineModeBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, level = GlassLevel.Floating, onClick = onClick) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp).heightIn(min = 24.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
            Icon(Icons.Outlined.CloudOff, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Offline mód", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** What still works right now, what does not, and the way to the downloads. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun OfflineModeDrawer(onDismiss: () -> Unit, openRoutes: () -> Unit, openDownloads: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val installed = OfflineSchedules.installed
    val today = LocalDate.now(Budapest)
    AppBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalWindowInfo.current.containerDpSize.height * .9f)
            .padding(horizontal = AppLayout.SheetPadding).padding(bottom = AppLayout.SheetPadding)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Offline mód", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Bezárás") }
                }
                Text(when {
                    OfflineStatus.forced -> "Bekapcsoltad az Offline módot, az alkalmazás nem használ internetet."
                    OfflineStatus.deviceOffline -> "Nincs internetkapcsolat."
                    else -> "A MÁV most nem érhető el."
                }, style = MaterialTheme.typography.bodyLarge)
                if (installed.isNotEmpty()) {
                    Section("Ami működik") {
                        Capability(Icons.Outlined.Route, "Útvonaltervezés")
                        Capability(Icons.Outlined.DepartureBoard, "Indulási táblák és widget")
                        Capability(Icons.Outlined.Schedule, "Járatok megállói")
                        Capability(Icons.Outlined.Search, "Megállókeresés")
                    }
                    Section("Ami most nem") {
                        Capability(Icons.Outlined.Timer, "Késések és kimaradások", muted = true)
                        Capability(Icons.Outlined.Map, "Járművek a térképen", muted = true)
                        Capability(Icons.Outlined.Campaign, "Közlemények és szolgáltatások", muted = true)
                    }
                    val (validity, warn) = offlineValidity(installed.minOf { it.lastServiceDay }, today)
                    if (warn) Text(validity, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Nincs letöltött menetrend. Tölts le egyet, amikor van internet.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(AppLayout.ItemGap))
            if (installed.isNotEmpty()) {
                Button(onClick = openRoutes, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), border = glassBorder(buttonGlassTone())) {
                    Icon(Icons.Outlined.TableRows, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
                    Text("Vonalak menetrendje", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(AppLayout.CompactGap))
                if (OfflineStatus.forced) TextButton(onClick = { OfflineStatus.chooseForced(context, false); onDismiss() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Offline mód kikapcsolása") }
                else TextButton(onClick = openDownloads, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Offline menetrendek kezelése") }
            } else {
                Button(onClick = openDownloads, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), border = glassBorder(buttonGlassTone())) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
                    Text("Offline menetrendek", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable private fun Capability(icon: ImageVector, title: String, muted: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.ItemGap), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
}

/** Stands in for every map while Offline mód is on; route maps keep their Back action. */
@Composable internal fun OfflineMapPlaceholder(routeOnly: Boolean, back: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize().padding(screenContentPadding())) {
        if (routeOnly) IconButton(onClick = back, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Vissza")
        }
        Column(Modifier.align(Alignment.Center).widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
            Icon(Icons.Outlined.CloudOff, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Offline módban nincs térkép", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("A térkép és az élő járművek internetet használnak.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(AppLayout.CompactGap))
            GlassButton(onClick = { OfflineStatus.chooseForced(context, false) }, container = MaterialTheme.colorScheme.surface) {
                Text("Offline mód kikapcsolása")
            }
        }
    }
}

/** The one place Offline mód is switched on: what stops working is said before it happens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ForcedOfflineWarning(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val installed = OfflineSchedules.installed
    val today = LocalDate.now(Budapest)
    AppBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalWindowInfo.current.containerDpSize.height * .9f)
            .padding(horizontal = AppLayout.SheetPadding).padding(bottom = AppLayout.SheetPadding)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
                Text("Bekapcsolod az Offline módot?", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Amíg be van kapcsolva, az alkalmazás semmit nem tölt le és nem küld el, akkor sem, ha van internet.",
                    style = MaterialTheme.typography.bodyLarge)
                if (installed.isEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
                        Text("Nincs letöltött menetrend, így tervezni és indulásokat nézni sem tudsz majd.",
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                    }
                }
                Section("Ami így lesz") {
                    Consequence(Icons.AutoMirrored.Outlined.EventNote, "Csak menetrend szerinti idők", "Késést, kimaradást és pótlóbuszt nem látsz. Indulás előtt nézz rá az állomáson.")
                    Consequence(Icons.Outlined.Map, "Nincs térkép és élő jármű", null)
                    Consequence(Icons.Outlined.Campaign, "Nincs közlemény és szolgáltatás", null)
                    Consequence(Icons.Outlined.SyncDisabled, "Semmi nem frissül", if (installed.isEmpty()) "Sem a menetrendek, sem az alkalmazás."
                        else "Sem a menetrendek, sem az alkalmazás. A letöltött menetrend ${offlineValidity(installed.minOf { it.lastServiceDay }, today).first.replaceFirstChar { it.lowercase() }}.")
                }
                Text("Bármikor kikapcsolhatod itt, a Térképen vagy az Offline mód sávban.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(AppLayout.ItemGap))
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), border = glassBorder(buttonGlassTone())) {
                Icon(Icons.Outlined.CloudOff, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp))
                Text(if (installed.isEmpty()) "Bekapcsolás mégis" else "Bekapcsolás", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(AppLayout.CompactGap))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Mégse") }
        }
    }
}

@Composable private fun Consequence(icon: ImageVector, title: String, detail: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.ItemGap), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
