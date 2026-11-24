package hu.matepazy.menetrendgo

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** The Transitous list as shown on the page; [live] is false while it comes from a saved or bundled copy. */
internal class OfflineCatalogState {
    var feeds by mutableStateOf<List<OfflineFeed>?>(null)
    var live by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var refresh by mutableIntStateOf(0)
    var query by mutableStateOf("")
    var confirmDelete by mutableStateOf<InstalledOfflineFeed?>(null)
    var routesOpen by mutableStateOf(false)
    val sizes = mutableStateMapOf<String, Long>()
}

@Composable internal fun rememberOfflineCatalog(active: Boolean): OfflineCatalogState {
    val state = remember { OfflineCatalogState() }
    LaunchedEffect(active, state.refresh) {
        if (!active) return@LaunchedEffect
        state.loading = true
        try {
            val (feeds, live) = OfflineSchedules.catalog(forceRefresh = state.refresh > 0)
            state.feeds = feeds; state.live = live
        } finally { state.loading = false }
    }
    state.confirmDelete?.let { feed ->
        AlertDialog(onDismissRequest = { state.confirmDelete = null }, shape = RoundedCornerShape(32.dp),
            title = { Text("Törlöd: ${feed.name}?") },
            text = { Text("A menetrend törlődik a készülékről, ${offlineBytesLabel(feed.bytes)} szabadul fel. Később újra letöltheted.") },
            confirmButton = { TextButton(onClick = { OfflineSchedules.delete(feed.id); state.confirmDelete = null }) { Text("Törlés") } },
            dismissButton = { TextButton(onClick = { state.confirmDelete = null }) { Text("Mégse") } })
    }
    if (state.routesOpen) OfflineRoutesSheet(onDismiss = { state.routesOpen = false })
    return state
}

internal fun offlineSummary(): String = OfflineSchedules.installed.let { list ->
    if (list.isEmpty()) "Nincs letöltött menetrend" else "${list.size} menetrend · ${offlineBytesLabel(OfflineSchedules.totalBytes)}"
}

internal fun LazyListScope.offlineScheduleItems(state: OfflineCatalogState, context: Context, openSources: () -> Unit) {
    item {
        Text("Ha a MÁV nem érhető el, az indulási táblák, a járatok és az útvonaltervezés a letöltött menetrendekből működnek tovább. Ezek csak menetrend szerinti időket tartalmaznak, késést és kimaradást nem.",
            Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    item {
        SettingsGroup {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
                Text("Tárhely", style = MaterialTheme.typography.titleMedium)
                Text(offlineSummary(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(horizontal = AppLayout.PanelPadding), color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().toggleable(OfflineSchedules.autoRefresh, role = Role.Switch,
                    onValueChange = { OfflineSchedules.chooseAutoRefresh(context, it) }).padding(AppLayout.PanelPadding),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Automatikus frissítés", style = MaterialTheme.typography.titleMedium)
                    Text("Hetente egyszer, csak Wi-Fi-n és töltés közben. Változatlan menetrendet nem tölt le újra.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = OfflineSchedules.autoRefresh, onCheckedChange = null)
            }
            HorizontalDivider(Modifier.padding(horizontal = AppLayout.PanelPadding), color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow("Vonalak menetrendje", if (OfflineSchedules.installed.isEmpty()) "Előbb tölts le egy menetrendet." else "Indulások megállónként, egy kiválasztott napra",
                enabled = OfflineSchedules.installed.isNotEmpty(), onClick = { state.routesOpen = true })
        }
    }
    val installed = OfflineSchedules.installed.sortedWith(compareBy({ it.group.ordinal }, { it.name }))
    if (installed.isNotEmpty()) {
        item { SectionHeading("Letöltve") }
        item {
            SettingsGroup {
                installed.forEachIndexed { index, feed ->
                    if (index > 0) HorizontalDivider(Modifier.padding(horizontal = AppLayout.PanelPadding), color = MaterialTheme.colorScheme.outlineVariant)
                    InstalledFeedRow(feed, state)
                }
            }
        }
    }
    item { SectionHeading("Letölthető menetrendek") }
    item {
        OutlinedTextField(state.query, { state.query = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(24.dp), label = { Text("Keresés város vagy szolgáltató szerint") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (state.query.isEmpty()) null else ({ IconButton({ state.query = "" }) { Icon(Icons.Outlined.Close, "Keresés törlése") } }))
    }
    val feeds = state.feeds
    if (feeds == null) {
        item { if (state.loading) VehicleProgress(Modifier.fillMaxWidth()) }
    } else {
        val wanted = OfflineNetwork.normalizedName(state.query)
        val matching = feeds.filter { wanted.isEmpty() || OfflineNetwork.normalizedName(it.name).contains(wanted) || OfflineNetwork.normalizedName(it.id).contains(wanted) }
        if (matching.isEmpty()) item {
            Text("Nincs ilyen nevű menetrend a Transitous magyar forrásai között.", Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodyLarge)
        }
        OfflineFeedGroup.entries.forEach { group ->
            val inGroup = matching.filter { it.group == group }
            if (inGroup.isEmpty()) return@forEach
            item(key = "group-${group.name}") {
                Text(group.title, Modifier.padding(horizontal = 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item(key = "feeds-${group.name}") {
                SettingsGroup {
                    inGroup.forEachIndexed { index, feed ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = AppLayout.PanelPadding), color = MaterialTheme.colorScheme.outlineVariant)
                        CatalogFeedRow(feed, state, context)
                    }
                }
            }
        }
        item {
            Text(if (state.live) "A lista a Transitous aktuális forrásjegyzéke." else "A Transitous friss listája most nem érhető el, a legutóbb betöltött változat látszik.",
                Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!state.live) TextButton(onClick = { state.refresh++ }, enabled = !state.loading) { Text("Lista frissítése") }
        }
    }
    item { SettingsGroup { SettingsRow("Források a Transitous oldalán", "transitous.org/sources", external = true, onClick = openSources) } }
    item {
        Text("A menetrendeket a Transitous gyűjti és ellenőrzi; a licencük a kiadóké. Letöltés után a készülék tömöríti őket: csak a következő ${GtfsCompactor.WINDOW_DAYS} nap járatai, megállói és vonalai maradnak meg, az eredeti fájl törlődik.",
            Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SectionHeading(text: String) {
    Text(text, Modifier.padding(start = 4.dp, top = AppLayout.CompactGap).semantics { heading() },
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable private fun TaskLine(task: OfflineTask?) {
    when (task) {
        OfflineTask.Queued -> Text("Sorban áll…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is OfflineTask.Downloading -> {
            Text(if (task.total > 0) "Letöltés · ${offlineBytesLabel(task.read)} / ${offlineBytesLabel(task.total)}" else "Letöltés · ${offlineBytesLabel(task.read)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VehicleProgress(Modifier.fillMaxWidth(), progress = if (task.total > 0) (task.read.toFloat() / task.total).coerceIn(0f, 1f) else null)
        }
        is OfflineTask.Processing -> {
            Text("Tömörítés · ${(task.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VehicleProgress(Modifier.fillMaxWidth(), progress = task.fraction)
        }
        is OfflineTask.Failed -> Text(task.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        null -> {}
    }
}

private val OfflineTask?.busy get() = this == OfflineTask.Queued || this is OfflineTask.Downloading || this is OfflineTask.Processing

@Composable private fun InstalledFeedRow(feed: InstalledOfflineFeed, state: OfflineCatalogState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val task = OfflineSchedules.tasks[feed.id]
    val (validity, warn) = offlineValidity(feed.lastServiceDay, LocalDate.now(Budapest))
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
            Text(feed.name, style = MaterialTheme.typography.titleMedium)
            if (task == null) {
                Text(validity, style = MaterialTheme.typography.bodyMedium, color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${offlineBytesLabel(feed.bytes)} · ${feed.trips} járat · frissítve ${updateAge(feed.builtAt, System.currentTimeMillis())}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else TaskLine(task)
        }
        if (task.busy) IconButton(onClick = { OfflineSchedules.cancel(feed.id) }) { Icon(Icons.Outlined.Close, "${feed.name} frissítésének megszakítása") }
        else {
            IconButton(onClick = {
                val source = state.feeds?.firstOrNull { it.id == feed.id } ?: OfflineSchedules.installedAsFeeds(emptyList()).first { it.id == feed.id }
                OfflineSchedules.enqueue(context, source)
            }) { Icon(Icons.Outlined.Refresh, "${feed.name} frissítése") }
            IconButton(onClick = { state.confirmDelete = feed }) { Icon(Icons.Outlined.DeleteOutline, "${feed.name} törlése") }
        }
    }
}

@Composable private fun CatalogFeedRow(feed: OfflineFeed, state: OfflineCatalogState, context: Context) {
    val task = OfflineSchedules.tasks[feed.id]
    val installed = OfflineSchedules.installed.any { it.id == feed.id }
    LaunchedEffect(feed.id) {
        if (feed.id !in state.sizes) OfflineSchedules.downloadSize(feed)?.let { state.sizes[feed.id] = it }
    }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
            Text(feed.name, style = MaterialTheme.typography.titleMedium)
            if (task != null) TaskLine(task)
            else Text(listOfNotNull(
                if (installed) "Letöltve" else state.sizes[feed.id]?.let { "${offlineBytesLabel(it)} letöltés" },
                feed.license?.let { "Licenc: $it" } ?: "A licencet a kiadó nem adta meg"
            ).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            task.busy -> IconButton(onClick = { OfflineSchedules.cancel(feed.id) }) { Icon(Icons.Outlined.Close, "${feed.name} letöltésének megszakítása") }
            task is OfflineTask.Failed -> IconButton(onClick = { OfflineSchedules.enqueue(context, feed) }) { Icon(Icons.Outlined.Refresh, "${feed.name} letöltésének újrapróbálása") }
            installed -> Icon(Icons.Outlined.CheckCircle, "Letöltve", Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
            else -> IconButton(onClick = { OfflineSchedules.enqueue(context, feed) }) { Icon(Icons.Outlined.Download, "${feed.name} letöltése") }
        }
    }
}
