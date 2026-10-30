package hu.matepazy.menetrendgo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/* Operate: the timetable leads. Alerts belong to the stop they concern.
 * Inherit the timeline's typography and plain surface; expose one quiet bell at arrival.
 * Setup/management uses the incumbent AppBottomSheet, never a panel above the schedule.
 */
@Composable
internal fun JourneyTrackingStop(journey: Journey, legIndex: Int, onOpen: () -> Unit) {
    val tracking by JourneyTracking.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val leg = journey.legs[legIndex]
    val active = tracking.session?.takeIf { it.journey.key == journey.key && it.legIndex == legIndex }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
            Box(Modifier.weight(1f)) { StationLabel(leg.to) }
            IconButton(onClick = onOpen) {
                Icon(if (active != null) Icons.Outlined.NotificationsActive else Icons.Outlined.AddAlert,
                    "Leszállási jelzés: ${leg.to.name}",
                    tint = if (active != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (active != null) {
            Text(if (!tracking.running) "A leszállási jelzés szünetel" else
                if (active.awaitingBoarding) "Átszállás · ${active.nextLegIndex?.let { active.journey.legs[it].route }.orEmpty()}" else tracking.message,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (active.awaitingBoarding && tracking.running) {
                TextButton(onClick = {
                    context.startService(Intent(context, JourneyTrackingService::class.java).setAction("board"))
                }, contentPadding = PaddingValues(horizontal = AppLayout.RelatedGap), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Felszálltam")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JourneyTrackingSheet(journey: Journey, selectedLegIndex: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val tracking by JourneyTracking.state.collectAsStateWithLifecycle()
    val active = tracking.session?.takeIf { it.journey.key == journey.key && it.legIndex == selectedLegIndex }
    val leg = journey.legs[selectedLegIndex]
    var warning by remember(journey.key, selectedLegIndex) { mutableIntStateOf(2) }
    var error by remember { mutableStateOf<String?>(null) }
    var recovery by remember { mutableStateOf<Intent?>(null) }
    var starting by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)

    fun start() {
        error = null; recovery = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            error = "A jelzéshez pontos helyhozzáférés szükséges. Engedélyezd az alkalmazás beállításaiban."
            recovery = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            return
        }
        if (!JourneyTracking.notificationsEnabled(context)) {
            error = "Engedélyezd az értesítéseket és a leszállási figyelmeztetéseket."
            recovery = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            return
        }
        if (!LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))) {
            error = "Kapcsold be a telefon helymeghatározását, majd próbáld újra."
            recovery = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            return
        }
        val session = active ?: TrackingSession(journey, selectedLegIndex, warning)
        if (session.leg.start > System.currentTimeMillis() + 30 * 60_000L) {
            error = "A jelzést felszálláskor indítsd el, legkorábban indulás előtt 30 perccel."
            return
        }
        if (session.leg.end < System.currentTimeMillis() - 6 * 60 * 60_000L) {
            error = "Ez az utazás már véget ért. Válassz egy aktuális járatot."
            return
        }
        try {
            starting = true
            JourneyTracking.start(context, session)
        } catch (_: Exception) {
            starting = false
            error = "A jelzés nem indult el. Nyisd meg újra az alkalmazást, majd próbáld újra."
        }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { start() }
    fun requestStart() {
        val required = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) required += Manifest.permission.POST_NOTIFICATIONS
        if (required.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) permissions.launch(required.toTypedArray())
        else start()
    }
    LaunchedEffect(starting, tracking.running) {
        if (starting && tracking.running && active != null) { starting = false; dismiss() }
        else if (starting) {
            kotlinx.coroutines.delay(5_000)
            starting = false
            if (!JourneyTracking.state.value.running) error = "A jelzés nem indult el. Ellenőrizd az engedélyeket, majd próbáld újra."
        }
    }

    AppBottomSheet(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = AppLayout.SheetPadding).padding(bottom = AppLayout.SheetPadding),
            verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Leszállási jelzés", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Leszállási jelzés bezárása") }
            }
            Column(verticalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                RouteBadge(leg)
                Text(leg.to.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Érkezés: ${timeLabel(leg.arrivalTime.estimate)}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (active != null) {
                Text(tracking.message, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                tracking.estimate?.takeUnless { active.awaitingBoarding }?.secondsRemaining?.let {
                    Text("Kb. ${maxOf(1, (it + 59) / 60)} perc · helyzet alapján", style = MaterialTheme.typography.bodyMedium)
                }
                if (active.awaitingBoarding && tracking.running) {
                    active.nextLegIndex?.let { next ->
                        Text("Következő: ${active.journey.legs[next].route} → ${active.journey.legs[next].to.name}", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = {
                            context.startService(Intent(context, JourneyTrackingService::class.java).setAction("board")); onDismiss()
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Felszálltam a következő járatra") }
                    }
                }
                if (!tracking.running) Button(onClick = { requestStart() }, enabled = !starting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (starting) "Indítás…" else "Jelzés folytatása") }
                OutlinedButton(onClick = { JourneyTracking.stop(context); onDismiss() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Jelzés leállítása")
                }
            } else if (tracking.session != null) {
                val existing = tracking.session!!.leg
                Text("Már aktív a jelzés: ${existing.route} → ${existing.to.name}. Új jelzés előtt állítsd le a jelenlegit.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { JourneyTracking.stop(context) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Aktív jelzés leállítása") }
            } else {
                Text("Figyelmeztetés érkezés előtt", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    (1..3).forEach { minutes ->
                        SegmentedButton(selected = warning == minutes, onClick = { warning = minutes },
                            shape = SegmentedButtonDefaults.itemShape(minutes - 1, 3)) { Text("$minutes perc") }
                    }
                }
                Text("Felszállás után indítsd el. Lezárt képernyőnél is jelzünk. Gyenge helyjelnél a figyelmeztetés pontatlan lehet.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { requestStart() }, enabled = !starting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (starting) "Indítás…" else "Jelzés indítása") }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                recovery?.let { destination ->
                    TextButton(onClick = { context.startActivity(destination) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (destination.action == Settings.ACTION_LOCATION_SOURCE_SETTINGS) "Helymeghatározás beállításai" else "Alkalmazás beállításai")
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActiveJourneyTracking(onOpen: (Journey) -> Unit) {
    val tracking by JourneyTracking.state.collectAsStateWithLifecycle()
    tracking.session?.let { session ->
        Surface(onClick = { onOpen(session.journey) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = AppLayout.PanelPadding, vertical = AppLayout.RelatedGap),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
                Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
                    Text("${session.leg.route} → ${session.leg.to.name}", style = MaterialTheme.typography.bodyMedium)
                    Text(if (tracking.running) "Leszállási jelzés aktív" else "Leszállási jelzés szünetel",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
