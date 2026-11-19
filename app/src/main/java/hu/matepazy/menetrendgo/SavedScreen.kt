package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.Instant

/* THESIS: an agenda of the trips you committed to, then the routes you reuse. A saved trip is a plan on a date, so it is
 * grouped under its day and drawn with the Indulások card language (service pill, large times, endpoints); it stays on
 * scheduled times until opened, where the journey refresh adds live data. Trips that already ran get the dashed outline
 * the results list uses for earlier departures. */
@Composable fun SavedScreen(vm: AppState, onSearch: () -> Unit, onPlan: () -> Unit = {}, onJourney: (Journey) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(60_000L - now % 60_000L) } }
    val journeys = remember(vm.savedJourneys, now) { sortSavedJourneys(vm.savedJourneys, now) }
    val days = remember(journeys) { journeys.groupBy { Instant.ofEpochMilli(it.departure).atZone(Budapest).toLocalDate() }.toList() }
    val routes = vm.savedSearches
    var editingJourney by rememberSaveable { mutableStateOf<String?>(null) }
    var editingRoute by remember { mutableStateOf<SavedSearch?>(null) }
    val swipeHost = remember { SwipeHost() }
    val summary = listOfNotNull(journeys.size.takeIf { it > 0 }?.let { "$it utazás" },
        routes.size.takeIf { it > 0 }?.let { "$it útvonal" }).joinToString(" · ").ifEmpty { null }
    LazyColumn(Modifier.fillMaxSize().swipeHostDismiss(swipeHost), contentPadding = screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
        item(key = "title") { PageTitle("Mentett", summary) }
        if (journeys.isEmpty() && routes.isEmpty()) item(key = "empty") { SavedEmptyState(onPlan) }
        else {
            item(key = "journeys") { SavedSectionTitle("Utazások") }
            if (journeys.isEmpty()) item(key = "journeys-hint") {
                SavedHint(Icons.Outlined.BookmarkAdd, "Egy adott napi járat mentéséhez nyisd meg az utazás részleteit, és koppints a jobb felső könyvjelzőre.")
            }
            days.forEach { (_, items) ->
                itemsIndexed(items, key = { _, it -> "journey:" + it.key }) { index, saved ->
                    // The day label belongs to the first trip of that day, so it sits close to its card.
                    Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                        if (index == 0) Text(savedJourneyDay(saved.departure), Modifier.padding(top = AppLayout.CompactGap)
                            .semantics { heading() }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SavedJourneyCard(saved, saved.past(now), swipeHost, Modifier,
                            onOpen = { onJourney(saved.journey) }, onEdit = { editingJourney = saved.key },
                            onDelete = { vm.removeSavedJourney(saved.key) })
                    }
                }
            }
            item(key = "routes") { SavedSectionTitle("Útvonalak", Modifier.padding(top = AppLayout.SectionGap - AppLayout.ItemGap)) }
            if (routes.isEmpty()) item(key = "routes-hint") {
                SavedHint(Icons.Outlined.Route, "Gyakori útvonalaidat az Indulások jobb felső könyvjelzőjével mentheted, dátum nélkül.")
            }
            items(routes, key = { "route:" + it.key }) { saved ->
                Box(Modifier.animateItem()) { SavedSearchRow(vm, saved, swipeHost, onSearch) { editingRoute = it } }
            }
        }
    }
    editingJourney?.let { key ->
        vm.savedJourneys.firstOrNull { it.key == key }?.let { saved -> SavedJourneyEditor(saved, onSave = { name, note -> vm.updateSavedJourney(key, name, note); editingJourney = null },
            onDismiss = { editingJourney = null }, onDelete = { vm.removeSavedJourney(key); editingJourney = null }) }
    }
    editingRoute?.let { saved ->
        SavedSearchEditor(saved.from, saved.to, saved,
            onSave = { name, icon -> vm.saveSearch(saved.from, saved.to, name, icon); editingRoute = null },
            onDismiss = { editingRoute = null }, onDelete = { vm.removeSavedSearch(saved); editingRoute = null })
    }
}

@Composable private fun SavedSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable private fun SavedHint(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SavedEmptyState(onPlan: () -> Unit) {
    Glass(Modifier.fillMaxWidth()) {
        Text("Tedd félre, amire később is szükséged lesz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(AppLayout.ItemGap))
        Column(verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
            SavedHowTo(Icons.Outlined.BookmarkAdd, "Utazás egy adott napra",
                "Az utazás részleteinél koppints a jobb felső könyvjelzőre. Nevet és megjegyzést is adhatsz neki.")
            SavedHowTo(Icons.Outlined.Route, "Útvonal, dátum nélkül",
                "Az Indulások könyvjelzőjével. Később egy koppintással az aktuális indulásokat kapod.")
        }
        Spacer(Modifier.height(AppLayout.SectionGap))
        OutlinedButton(onClick = onPlan, shape = CircleShape, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)); Spacer(Modifier.width(AppLayout.CompactGap)); Text("Útvonal tervezése")
        }
    }
}

@Composable private fun SavedHowTo(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
            Icon(icon, null, Modifier.padding(10.dp).size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun savedJourneyWhen(saved: SavedJourney) =
    "${savedJourneyDay(saved.departure)} · ${timeLabel(saved.departure)}–${timeLabel(saved.arrival)}"

@Composable private fun SavedJourneyCard(saved: SavedJourney, past: Boolean, swipeHost: SwipeHost, modifier: Modifier,
    onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val journey = saved.journey
    val outline = MaterialTheme.colorScheme.outline
    SwipeActionsRow(listOf(
        SwipeAction(Icons.Outlined.Edit, "${saved.title} szerkesztése", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, onEdit),
        SwipeAction(Icons.Outlined.Delete, "${saved.title} törlése", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError, onDelete)),
        swipeHost, modifier) { open, close ->
        GlassSurface(onClick = { if (open) close() else onOpen() }, shape = RoundedCornerShape(32.dp),
            modifier = Modifier.semantics { if (past) stateDescription = "Már lezajlott" }.drawWithContent {
                drawContent()
                if (past) {
                    val width = 1.5.dp.toPx()
                    drawRoundRect(outline, topLeft = Offset(width / 2, width / 2), size = Size(size.width - width, size.height - width),
                        cornerRadius = CornerRadius(32.dp.toPx() - width / 2),
                        style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
                }
            }) {
            Column(Modifier.fillMaxWidth().padding(start = AppLayout.PanelPadding, end = AppLayout.PanelPadding, bottom = AppLayout.PanelPadding,
                top = AppLayout.RelatedGap),
                verticalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
                val services = journey.legs.filterNot { it.walking }.distinctBy { it.mode to it.route }.ifEmpty { journey.legs.take(1) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (saved.name.isNotBlank()) Text(saved.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    else ServicePill(services, Modifier.weight(1f))
                    // The edit control keeps its 48dp target but its icon lines up with the card's padding edge.
                    if (!open) IconButton(onClick = onEdit, modifier = Modifier.offset(x = 12.dp)) {
                        Icon(Icons.Outlined.Edit, "${saved.title}: név és megjegyzés szerkesztése")
                    } else Spacer(Modifier.size(48.dp))
                }
                if (saved.name.isNotBlank()) ServicePill(services)
                JourneyEndpoints(journey)
                Text(listOf(if (past) "Lezajlott" else "", "${journey.serviceMinutes} perc",
                    if (journey.changes == 0) "Közvetlen" else "${journey.changes} átszállás", "menetrend szerint")
                    .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (saved.note.isNotBlank()) Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = AppLayout.RelatedGap, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                        Icon(Icons.AutoMirrored.Outlined.Notes, "Megjegyzés", Modifier.padding(top = 2.dp).size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(saved.note, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SavedJourneyEditor(saved: SavedJourney, onSave: (String, String) -> Unit, onDismiss: () -> Unit, onDelete: () -> Unit) {
    var name by rememberSaveable(saved.key) { mutableStateOf(saved.name) }
    var note by rememberSaveable(saved.key) { mutableStateOf(saved.note) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = AppLayout.SheetPadding).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Mentett utazás", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${saved.origin} → ${saved.destination}", style = MaterialTheme.typography.bodyLarge)
                Text("${savedJourneyWhen(saved)}, menetrend szerint", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(value = name, onValueChange = { name = it.take(SavedJourney.MAX_NAME_LENGTH) },
                label = { Text("Név (nem kötelező)") }, placeholder = { Text("Például: Nagyi születésnapja") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                supportingText = { Text("${name.length}/${SavedJourney.MAX_NAME_LENGTH}") })
            OutlinedTextField(value = note, onValueChange = { note = it.take(SavedJourney.MAX_NOTE_LENGTH) },
                label = { Text("Megjegyzés") }, placeholder = { Text("Például: 3. kocsi, a jegy az e-mailben") },
                minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                supportingText = { Text("${note.length}/${SavedJourney.MAX_NOTE_LENGTH}") })
            MainButton("Mentés", Icons.Outlined.Check) { onSave(name, note) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onDismiss) { Text("Mégse") }
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Eltávolítás", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Eltávolítod a mentett utazást?") },
        text = { Text(if (saved.note.isBlank()) saved.title else "${saved.title}\nA megjegyzés is törlődik.") },
        confirmButton = { TextButton(onClick = onDelete) { Text("Eltávolítás") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Mégse") } })
}
