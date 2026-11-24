package hu.matepazy.menetrendgo

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/** Page state that outlives the lazy list items: the Transitous list, download sizes and open sheets. */
internal class OfflineCatalogState {
    var feeds by mutableStateOf<List<OfflineFeed>?>(null)
    var loading by mutableStateOf(false)
    var refresh by mutableIntStateOf(0)
    var confirmDelete by mutableStateOf<InstalledOfflineFeed?>(null)
    var confirmDeleteStarter by mutableStateOf(false)
    var forcedWarning by mutableStateOf(false)
    var routesOpen by mutableStateOf(false)
    var townsOpen by mutableStateOf(false)
    var extrasOpen by mutableStateOf(false)
    val sizes = mutableStateMapOf<String, Long>()
    fun feed(id: String) = feeds?.firstOrNull { it.id == id }
}

@Composable internal fun rememberOfflineCatalog(active: Boolean): OfflineCatalogState {
    val state = remember { OfflineCatalogState() }
    LaunchedEffect(active, state.refresh) {
        if (!active) return@LaunchedEffect
        state.loading = true
        try { state.feeds = OfflineSchedules.catalog(forceRefresh = state.refresh > 0).first } finally { state.loading = false }
    }
    state.confirmDelete?.let { feed ->
        AlertDialog(onDismissRequest = { state.confirmDelete = null }, shape = RoundedCornerShape(32.dp),
            title = { Text("Törlöd ezt a menetrendet?") },
            text = { Text("${feed.name} törlődik a készülékről, ${offlineBytesLabel(feed.bytes)} szabadul fel. Később bármikor újra letöltheted.") },
            confirmButton = { TextButton(onClick = { OfflineSchedules.delete(feed.id); state.confirmDelete = null }) { Text("Törlés") } },
            dismissButton = { TextButton(onClick = { state.confirmDelete = null }) { Text("Mégse") } })
    }
    if (state.confirmDeleteStarter) {
        val starter = OfflineFeedCatalog.STARTER.mapNotNull(::installedFeed)
        AlertDialog(onDismissRequest = { state.confirmDeleteStarter = false }, shape = RoundedCornerShape(32.dp),
            title = { Text("Törlöd az alapcsomagot?") },
            text = { Text("A vonatok és a helyközi buszok menetrendje törlődik a készülékről, ${offlineBytesLabel(starter.sumOf { it.bytes })} szabadul fel.") },
            confirmButton = { TextButton(onClick = { starter.forEach { OfflineSchedules.delete(it.id) }; state.confirmDeleteStarter = false }) { Text("Törlés") } },
            dismissButton = { TextButton(onClick = { state.confirmDeleteStarter = false }) { Text("Mégse") } })
    }
    if (state.forcedWarning) {
        val context = LocalContext.current
        ForcedOfflineWarning(onDismiss = { state.forcedWarning = false },
            onConfirm = { OfflineStatus.chooseForced(context, true); state.forcedWarning = false })
    }
    if (state.routesOpen) OfflineRoutesSheet(onDismiss = { state.routesOpen = false })
    if (state.townsOpen) OfflineTownPicker(state, onDismiss = { state.townsOpen = false })
    return state
}

internal fun offlineSummary(): String = OfflineSchedules.installed.let { list ->
    if (list.isEmpty()) "Nincs letöltött menetrend" else "${list.size} menetrend · ${offlineBytesLabel(OfflineSchedules.totalBytes)}"
}

private val OfflineTask?.busy get() = this == OfflineTask.Queued || this is OfflineTask.Downloading || this is OfflineTask.Processing
private fun installedFeed(id: String) = OfflineSchedules.installed.firstOrNull { it.id == id }
private fun isExtra(feed: OfflineFeed) = feed.id !in OfflineFeedCatalog.STARTER && feed.group != OfflineFeedGroup.City

internal fun LazyListScope.offlineScheduleItems(state: OfflineCatalogState, context: Context, openSources: () -> Unit) {
    item { OfflineIntro() }
    item { SectionHeading("Országos alapcsomag") }
    item { StarterPack(state, context) }
    item { SectionHeading("Városok") }
    item { Towns(state, context) }
    item { SectionHeading("Továbbiak") }
    item { Extras(state, context) }
    item {
        SettingsGroup {
            Row(
                Modifier.fillMaxWidth().toggleable(OfflineSchedules.autoRefresh, role = Role.Switch,
                    onValueChange = { OfflineSchedules.chooseAutoRefresh(context, it) }).padding(AppLayout.PanelPadding),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Automatikus frissítés", style = MaterialTheme.typography.titleMedium)
                    Text("Hetente, Wi-Fi-n, töltés közben", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = OfflineSchedules.autoRefresh, onCheckedChange = null)
            }
            if (OfflineSchedules.installed.isNotEmpty()) {
                Divider()
                SettingsRow("Vonalak menetrendje", onClick = { state.routesOpen = true })
            }
        }
    }
    item { SettingsGroup { SettingsRow("Források és licencek", "Transitous", external = true, onClick = openSources) } }
    item {
        SettingsGroup {
            // Switching on asks first; switching off is immediate.
            Row(
                Modifier.fillMaxWidth().toggleable(OfflineStatus.forced, role = Role.Switch,
                    onValueChange = { on -> if (on) state.forcedWarning = true else OfflineStatus.chooseForced(context, false) })
                    .padding(AppLayout.PanelPadding),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Offline mód", style = MaterialTheme.typography.titleMedium)
                    Text(if (OfflineStatus.forced) "Bekapcsolva: nincs internethasználat" else "Az alkalmazás nem használ internetet",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = OfflineStatus.forced, onCheckedChange = null)
            }
        }
    }
}

@Composable private fun OfflineIntro() {
    val installed = OfflineSchedules.installed
    val today = LocalDate.now(Budapest)
    val expiring = installed.filter { offlineValidity(it.lastServiceDay, today).second }
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
        Text(if (installed.isEmpty()) "Internet nélkül is tervezhetsz a letöltött menetrendekkel."
            else "${installed.size} menetrend · ${offlineBytesLabel(OfflineSchedules.totalBytes)}",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (expiring.isNotEmpty()) Text("${expiring.size} menetrend hamarosan lejár vagy lejárt. Frissítsd, amikor van internet.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

@Composable private fun SectionHeading(text: String) {
    Text(text, Modifier.padding(start = 4.dp, top = AppLayout.CompactGap).semantics { heading() },
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable private fun Divider() = HorizontalDivider(Modifier.padding(horizontal = AppLayout.PanelPadding), color = MaterialTheme.colorScheme.outlineVariant)

/** Download size of a catalog feed, fetched once per page visit. */
@Composable private fun rememberSize(state: OfflineCatalogState, feed: OfflineFeed?) {
    LaunchedEffect(feed?.id) {
        if (feed != null && feed.id !in state.sizes) OfflineSchedules.downloadSize(feed)?.let { state.sizes[feed.id] = it }
    }
}

@Composable private fun StarterPack(state: OfflineCatalogState, context: Context) {
    val members = OfflineFeedCatalog.STARTER.mapNotNull { state.feed(it) }
    members.forEach { rememberSize(state, it) }
    val missing = members.filter { installedFeed(it.id) == null }
    val tasks = OfflineFeedCatalog.STARTER.mapNotNull { id -> OfflineSchedules.tasks[id]?.let { id to it } }
    val busy = tasks.firstOrNull { it.second.busy }
    val failed = tasks.firstOrNull { it.second is OfflineTask.Failed }
    val installed = OfflineFeedCatalog.STARTER.mapNotNull(::installedFeed)
    SettingsGroup {
        Column(Modifier.fillMaxWidth().padding(AppLayout.PanelPadding), verticalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
            Text("MÁV és GYSEV vonatok, Volánbusz helyközi buszok", style = MaterialTheme.typography.titleMedium)
            when {
                members.isEmpty() -> VehicleProgress(Modifier.fillMaxWidth())
                busy != null -> {
                    val done = OfflineFeedCatalog.STARTER.count { id -> installedFeed(id) != null && OfflineSchedules.tasks[id] == null }
                    TaskStatus(busy.second, prefix = "${(done + 1).coerceAtMost(members.size)}/${members.size} · ${state.feed(busy.first)?.name.orEmpty()}")
                    TextButton(onClick = { tasks.filter { it.second.busy }.forEach { OfflineSchedules.cancel(it.first) } }) { Text("Megszakítás") }
                }
                failed != null && missing.isNotEmpty() -> {
                    Text((failed.second as OfflineTask.Failed).message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    PrimaryAction("Újrapróbálás", Icons.Outlined.Refresh) { missing.forEach { OfflineSchedules.enqueue(context, it) } }
                }
                missing.isEmpty() -> {
                    val last = installed.minOf { it.lastServiceDay }
                    val (validity, warn) = offlineValidity(last, LocalDate.now(Budapest))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                        Icon(if (warn) Icons.Outlined.WarningAmber else Icons.Outlined.CheckCircle, null, Modifier.size(20.dp),
                            tint = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text("Letöltve · $validity · ${offlineBytesLabel(installed.sumOf { it.bytes })}", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium, color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                        GlassButton(onClick = { members.forEach { OfflineSchedules.enqueue(context, it) } }) {
                            Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(AppLayout.CompactGap)); Text("Frissítés")
                        }
                        TextButton(onClick = { state.confirmDeleteStarter = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Törlés") }
                    }
                }
                else -> {
                    if (installed.isNotEmpty()) Text("${installed.size}/${members.size} letöltve. Hiányzik: ${missing.joinToString(", ") { it.name }}.",
                        style = MaterialTheme.typography.bodyMedium)
                    val size = missing.mapNotNull { state.sizes[it.id] }.takeIf { it.size == missing.size }?.sum()
                    val label = (if (installed.isEmpty()) "Letöltés" else "A hiányzó rész letöltése") + (size?.let { " · ${offlineBytesLabel(it)}" } ?: "")
                    PrimaryAction(label, Icons.Outlined.Download) { missing.forEach { OfflineSchedules.enqueue(context, it) } }
                }
            }
        }
    }
}

@Composable private fun PrimaryAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), border = glassBorder(buttonGlassTone())) {
        Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable private fun TaskStatus(task: OfflineTask, prefix: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
        val text = when (task) {
            OfflineTask.Queued -> "Várakozik"
            is OfflineTask.Downloading -> if (task.total > 0) "Letöltés · ${offlineBytesLabel(task.read)} / ${offlineBytesLabel(task.total)}" else "Letöltés · ${offlineBytesLabel(task.read)}"
            is OfflineTask.Processing -> "Tömörítés a telefonon · ${(task.fraction * 100).toInt()}%"
            is OfflineTask.Failed -> task.message
        }
        if (prefix != null) Text(prefix, style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium,
            color = if (task is OfflineTask.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        when (task) {
            is OfflineTask.Downloading -> VehicleProgress(Modifier.fillMaxWidth(), progress = if (task.total > 0) (task.read.toFloat() / task.total).coerceIn(0f, 1f) else null)
            is OfflineTask.Processing -> VehicleProgress(Modifier.fillMaxWidth(), progress = task.fraction)
            else -> {}
        }
    }
}

/** One downloaded or downloading feed: title, its state, and the one action that fits that state. */
@Composable private fun FeedRow(title: String, subtitle: String, id: String, state: OfflineCatalogState, context: Context) {
    val task = OfflineSchedules.tasks[id]
    val installed = installedFeed(id)
    val today = LocalDate.now(Budapest)
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            when {
                task != null -> TaskStatus(task)
                installed != null -> {
                    val (validity, warn) = offlineValidity(installed.lastServiceDay, today)
                    Text(listOf(subtitle, validity, offlineBytesLabel(installed.bytes)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Text(listOfNotNull(subtitle.takeIf { it.isNotBlank() }, state.sizes[id]?.let(::offlineBytesLabel)).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            task.busy -> IconButton(onClick = { OfflineSchedules.cancel(id) }) { Icon(Icons.Outlined.Close, "$title letöltésének megszakítása") }
            task is OfflineTask.Failed -> IconButton(onClick = { state.feed(id)?.let { OfflineSchedules.enqueue(context, it) } }) {
                Icon(Icons.Outlined.Refresh, "$title letöltésének újrapróbálása")
            }
            installed != null -> IconButton(onClick = { state.confirmDelete = installed }) { Icon(Icons.Outlined.DeleteOutline, "$title törlése") }
            else -> IconButton(onClick = { state.feed(id)?.let { OfflineSchedules.enqueue(context, it) } }) { Icon(Icons.Outlined.Download, "$title letöltése") }
        }
    }
}

@Composable private fun Towns(state: OfflineCatalogState, context: Context) {
    val catalog = state.feeds.orEmpty()
    // Towns on the phone or on their way to it; everything else is one tap away in the picker.
    val shown = catalog.filter { it.group == OfflineFeedGroup.City && (installedFeed(it.id) != null || OfflineSchedules.tasks[it.id] != null) }
        .sortedWith(compareBy(OfflineFeedCatalog.collator) { it.town })
    val orphans = OfflineSchedules.installed.filter { i -> i.group == OfflineFeedGroup.City && catalog.none { it.id == i.id } }
    SettingsGroup {
        shown.forEach { feed -> FeedRow(feed.town, feed.operator, feed.id, state, context); Divider() }
        orphans.forEach { feed -> FeedRow(feed.name, "", feed.id, state, context); Divider() }
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { state.townsOpen = true }.padding(horizontal = 20.dp, vertical = 16.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.AddCircleOutline, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
                Text("Város hozzáadása", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable private fun Extras(state: OfflineCatalogState, context: Context) {
    val extras = state.feeds.orEmpty().filter(::isExtra)
    val downloaded = extras.count { installedFeed(it.id) != null }
    val rotation by animateFloatAsState(if (state.extrasOpen) 180f else 0f, tween(AppMotion.Reveal), label = "extras")
    SettingsGroup {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (state.extrasOpen) "Becsukás" else "Kinyitás") { state.extrasOpen = !state.extrasOpen }
            .semantics { stateDescription = if (state.extrasOpen) "Kinyitva" else "Becsukva" }
            .padding(horizontal = 20.dp, vertical = 16.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
                Text("Hajók, kisvasutak, sikló és libegő", style = MaterialTheme.typography.titleMedium)
                if (downloaded > 0) Text("$downloaded letöltve", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ExpandMore, null, Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(state.extrasOpen, enter = expandVertically(tween(AppMotion.Reveal)) + fadeIn(tween(AppMotion.Reveal)),
            exit = shrinkVertically(tween(AppMotion.Dismiss)) + fadeOut(tween(AppMotion.Dismiss))) {
            Column {
                extras.forEach { feed ->
                    rememberSize(state, feed)
                    Divider()
                    FeedRow(feed.name, "", feed.id, state, context)
                }
            }
        }
    }
}

/** Searchable A–Z list of local transport, one tap to download; the sheet stays open so several towns can be added. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun OfflineTownPicker(state: OfflineCatalogState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val towns = remember(state.feeds) {
        state.feeds.orEmpty().filter { it.group == OfflineFeedGroup.City }.sortedWith(compareBy(OfflineFeedCatalog.collator) { it.town })
    }
    val wanted = OfflineNetwork.normalizedName(query)
    val shown = towns.filter { wanted.isEmpty() || OfflineNetwork.normalizedName("${it.town} ${it.operator}").contains(wanted) }
    AppBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalWindowInfo.current.containerDpSize.height * .9f).padding(horizontal = AppLayout.SheetPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Város hozzáadása", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Bezárás") }
            }
            Spacer(Modifier.height(AppLayout.CompactGap))
            OutlinedTextField(query, { query = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(24.dp),
                label = { Text("Város keresése") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (query.isEmpty()) null else ({ IconButton({ query = "" }) { Icon(Icons.Outlined.Close, "Keresés törlése") } }))
            Spacer(Modifier.height(AppLayout.CompactGap))
            LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = AppLayout.SheetPadding)) {
                if (towns.isEmpty()) item { VehicleProgress(Modifier.fillMaxWidth()) }
                else if (shown.isEmpty()) item { Text("Ehhez a városhoz nincs offline menetrend a Transitousban.", Modifier.padding(vertical = AppLayout.ItemGap), style = MaterialTheme.typography.bodyLarge) }
                items(shown, key = { it.id }) { feed ->
                    rememberSize(state, feed)
                    val task = OfflineSchedules.tasks[feed.id]
                    val installed = installedFeed(feed.id) != null
                    val busy = task.busy
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .clickable(enabled = !installed && !busy, role = Role.Button, onClickLabel = "Letöltés") { OfflineSchedules.enqueue(context, feed) }
                        .semantics { stateDescription = when { installed -> "Letöltve"; busy -> "Letöltés folyamatban"; else -> "Nincs letöltve" } }
                        .padding(horizontal = 12.dp, vertical = 12.dp).heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
                            Text(feed.town, style = MaterialTheme.typography.titleMedium)
                            Text(when {
                                task is OfflineTask.Failed -> task.message
                                installed -> listOf(feed.operator, "Letöltve").filter { it.isNotBlank() }.joinToString(" · ")
                                else -> listOfNotNull(feed.operator.takeIf { it.isNotBlank() }, state.sizes[feed.id]?.let(::offlineBytesLabel)).joinToString(" · ")
                            }, style = MaterialTheme.typography.bodyMedium,
                                color = if (task is OfflineTask.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when {
                            installed -> Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            busy -> {
                                val fraction = when (task) {
                                    is OfflineTask.Processing -> task.fraction
                                    is OfflineTask.Downloading -> if (task.total > 0) task.read.toFloat() / task.total else 0f
                                    else -> 0f
                                }
                                CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                            }
                            else -> Icon(Icons.Outlined.Download, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
