package hu.matepazy.menetrendgo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Searching previews matches. Only selecting a row changes the map. */
@Composable internal fun MapSearch(
    query: String, onQuery: (String) -> Unit, refreshing: Boolean, onRefresh: () -> Unit,
    modifier: Modifier = Modifier, focused: Boolean = false, onFocusChange: (Boolean) -> Unit = {},
    vehicles: List<Vehicle> = emptyList(), vehicleError: String? = null,
    cachedStops: suspend (String) -> List<Station> = { emptyList() },
    searchStops: suspend (String) -> List<Station> = { emptyList() },
    selectedVehicleName: String? = null, onClearSelection: () -> Unit = {},
    onVehicle: (Vehicle) -> Unit = {}, onStop: (Station) -> Unit = {}
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val spotlight = LocalStationFocus.current
    fun finishEditing() {
        onFocusChange(false)
        focus.clearFocus()
        keyboard?.hide()
    }
    fun close() { finishEditing(); expanded = false; onQuery("") }
    BackHandler(expanded) { close() }
    val dismiss by rememberUpdatedState<() -> Unit>({ close() })
    if (expanded) DisposableEffect(Unit) {
        spotlight.dismiss = { dismiss() }
        onDispose { spotlight.dismiss = null; spotlight.bounds = null }
    }
    LaunchedEffect(expanded, focused) {
        if (expanded && focused) {
            withFrameNanos { }
            requester.requestFocus()
            keyboard?.show()
        }
    }
    val term = query.trim()
    val search = remember(vehicles) { VehicleSearch(vehicles) }
    var matches by remember(term, vehicles) { mutableStateOf<List<Vehicle>>(emptyList()) }
    var matching by remember(term, vehicles) { mutableStateOf(term.isNotEmpty()) }
    LaunchedEffect(term, search) {
        if (term.isNotEmpty()) matches = withContext(Dispatchers.Default) { search.filter(term) }
        matching = false
    }
    var retry by remember { mutableIntStateOf(0) }
    var stops by remember(term, retry) { mutableStateOf<List<Station>>(emptyList()) }
    var loading by remember(term, retry) { mutableStateOf(term.length >= 2) }
    var failed by remember(term, retry) { mutableStateOf(false) }
    var allMatches by remember(term) { mutableStateOf(false) }
    val listState = key(term) { rememberLazyListState() }
    val currentCached by rememberUpdatedState(cachedStops)
    val currentSearch by rememberUpdatedState(searchStops)
    LaunchedEffect(term, retry, expanded) {
        if (!expanded || term.length < 2) return@LaunchedEffect
        try {
            stops = mapSearchStops(currentCached(term))
            delay(300)
            stops = mapSearchStops(currentSearch(term))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    BoxWithConstraints(modifier.padding(10.dp)) {
        val availableHeight = maxHeight
        Column(Modifier.align(Alignment.TopEnd).widthIn(max = 560.dp)
            .fillMaxWidth(),
            horizontalAlignment = Alignment.End) {
            AnimatedContent(targetState = expanded, contentAlignment = Alignment.TopEnd,
                transitionSpec = {
                    fadeIn(tween(AppMotion.Reveal)) togetherWith fadeOut(tween(AppMotion.Dismiss)) using
                        SizeTransform(clip = true, sizeAnimationSpec = { _, _ ->
                            tween(if (targetState) AppMotion.Reveal else AppMotion.Dismiss, easing = AppMotion.Arrival)
                        })
                }, label = "Map search expansion") { open ->
            if (!open) {
                if (selectedVehicleName != null) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(start = 56.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).heightIn(min = 48.dp)
                                .clickable(onClickLabel = "Keresés a térképen") { expanded = true; onFocusChange(true) }
                                .padding(horizontal = AppLayout.RelatedGap, vertical = AppLayout.CompactGap),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
                                Icon(Icons.Outlined.Search, null, Modifier.size(24.dp))
                                Text(selectedVehicleName, style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            IconButton(onClick = { onQuery(""); onClearSelection() }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Outlined.Close, "Keresés törlése", Modifier.size(20.dp))
                            }
                        }
                    }
                } else {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                    IconButton(onClick = { expanded = true; onFocusChange(true) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Search, "Keresés a térképen")
                    }
                }
                }
            } else Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.heightIn(max = availableHeight)
                    .onGloballyPositioned { spotlight.bounds = it.boundsInWindow() }
                    .semantics { paneTitle = "Térkép keresése"; dismiss { close(); true } }) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = AppLayout.RelatedGap)
                        .heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Search, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(AppLayout.RelatedGap))
                        Column(Modifier.weight(1f)) {
                        BasicTextField(query, { onQuery(it.take(100)) },
                            Modifier.fillMaxWidth().focusRequester(requester)
                                .onFocusChanged { if (it.isFocused) onFocusChange(true) }
                                .semantics { contentDescription = "Jármű vagy megálló keresése" },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { finishEditing() }),
                            decorationBox = { editor -> Box {
                                if (query.isEmpty()) Text("Jármű vagy megálló…", maxLines = 1,
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface)
                                editor()
                            } })
                        }
                        if (query.isNotEmpty() || selectedVehicleName != null) IconButton(onClick = {
                            onQuery(""); onClearSelection(); onFocusChange(true)
                        },
                            modifier = Modifier.padding(start = AppLayout.CompactGap).size(48.dp)) {
                            Icon(Icons.Outlined.Close, "Keresés törlése", Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { close() }, modifier = Modifier.padding(start = AppLayout.CompactGap).size(48.dp)) {
                            Icon(Icons.Outlined.KeyboardArrowUp, "Keresés bezárása")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LazyColumn(Modifier.weight(1f, fill = false), state = listState,
                        contentPadding = PaddingValues(bottom = AppLayout.RelatedGap)) {
                        if (term.isEmpty()) item {
                            SearchMessage("Járatszám, célállomás vagy megállónév")
                        } else {
                            if (matches.isNotEmpty() || matching || refreshing || vehicleError != null) item(key = "vehicles-heading") {
                                SearchHeading("Járművek", if (matching) null else matches.size)
                            }
                            if (refreshing || matching) item {
                                SearchProgress(if (refreshing) "Járműpozíciók frissítése…" else "Járművek keresése…")
                            }
                            if (vehicleError != null) item {
                                SearchMessage(if (vehicles.isEmpty()) "A járműpozíciók nem tölthetők be. Ellenőrizd az internetkapcsolatot."
                                    else "A járműpozíciók frissítése nem sikerült. A korábbi adatok láthatók.", error = true)
                                TextButton(onClick = onRefresh, enabled = !refreshing,
                                    modifier = Modifier.padding(horizontal = 12.dp)) { Text("Újrapróbálás") }
                            }
                            items(if (allMatches) matches else matches.take(3), key = { "vehicle:${it.id}" }) { vehicle ->
                                SearchResult(vehicle.name,
                                    listOf(vehicle.headsign, vehicle.modelAndRegistration).filter { it.isNotBlank() }.joinToString(" · "),
                                    modifier = Modifier.animateItem(fadeInSpec = tween(AppMotion.Feedback),
                                        placementSpec = tween(AppMotion.Reveal, easing = AppMotion.Arrival), fadeOutSpec = tween(AppMotion.Dismiss)),
                                    icon = { StationSearchIcon(Station(vehicle.id, vehicle.name, vehicle.lat, vehicle.lon, modes = listOf(vehicle.mode)), decorative = true) },
                                    select = { close(); onVehicle(vehicle) })
                            }
                            if (matches.size > 3) item {
                                TextButton(onClick = { allMatches = !allMatches }, modifier = Modifier.padding(horizontal = 12.dp)) {
                                    Text(if (allMatches) "Kevesebb jármű" else "Mind a ${matches.size} jármű")
                                }
                            }
                            if (stops.isNotEmpty() || loading || failed) item(key = "stops-heading") {
                                SearchHeading("Megállók és állomások", if (loading || failed) null else stops.size)
                            }
                            if (term.length < 2) item { SearchMessage("A megállók kereséséhez írj be legalább 2 karaktert.") }
                            if (loading) item { SearchProgress("Megállók keresése…") }
                            if (failed) item {
                                SearchMessage(if (stops.isEmpty()) "A megállók nem tölthetők be. Ellenőrizd az internetkapcsolatot."
                                    else "A megállók frissítése nem sikerült. A mentett találatok láthatók.", error = true)
                                TextButton(onClick = { retry++ }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Újrapróbálás") }
                            }
                            if (term.length >= 2 && !loading && !matching && !refreshing && !failed &&
                                vehicleError == null && stops.isEmpty() && matches.isEmpty()) item {
                                SearchMessage("Nincs találat. Próbálj másik nevet vagy járatszámot.")
                            }
                            items(stops, key = { "stop:${it.id}" }) { station ->
                                SearchResult(station.name,
                                    modifier = Modifier.animateItem(fadeInSpec = tween(AppMotion.Feedback),
                                        placementSpec = tween(AppMotion.Reveal, easing = AppMotion.Arrival), fadeOutSpec = tween(AppMotion.Dismiss)),
                                    icon = { StationSearchIcon(station) },
                                    select = { close(); onStop(station) })
                            }
                        }
                    }
                }
            }
            }
            AnimatedVisibility(!expanded, enter = fadeIn(tween(AppMotion.Feedback)), exit = fadeOut(tween(AppMotion.Dismiss))) {
            Surface(modifier = Modifier.padding(top = AppLayout.CompactGap),
                shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                IconButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.size(48.dp)) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(24.dp)
                        .semantics { contentDescription = "Járműpozíciók frissítése folyamatban" }, strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, "Járműpozíciók frissítése")
                }
            }
            }
        }
    }
}

/** A missing selected position must not reveal unrelated vehicles during a feed refresh. */
internal fun mapSearchVehicles(vehicles: List<Vehicle>, selectedId: String?): List<Vehicle> =
    if (selectedId == null) vehicles else vehicles.filter { it.id == selectedId }

/** Town aggregates have no departure board. Keep exact provider identities for real stops. */
internal fun mapSearchStops(stations: List<Station>): List<Station> = stations.filter {
    it.type != "MetaStation" && !it.name.trim().endsWith('*') && it.id.isNotBlank() &&
        it.lat.isFinite() && it.lon.isFinite() && it.lat in -90.0..90.0 && it.lon in -180.0..180.0
}.distinctBy { it.id }

@Composable private fun SearchHeading(title: String, count: Int?) {
    Text(if (count == null || count == 0) title else "$title · $count",
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun SearchMessage(message: String, error: Boolean = false) {
    Text(message, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun SearchProgress(message: String) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SearchResult(title: String, subtitle: String = "", modifier: Modifier = Modifier,
    icon: @Composable () -> Unit, select: () -> Unit) {
    Surface(onClick = select, modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .padding(horizontal = AppLayout.ItemGap, vertical = AppLayout.RelatedGap),
            verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(AppLayout.RelatedGap))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.padding(start = AppLayout.CompactGap).size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
