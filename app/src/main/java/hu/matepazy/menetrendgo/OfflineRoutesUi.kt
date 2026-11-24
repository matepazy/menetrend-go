package hu.matepazy.menetrendgo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Classic line timetables from the downloaded data: pick a line, a direction, a stop and a day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun OfflineRoutesSheet(onDismiss: () -> Unit) {
    var network by remember { mutableStateOf<OfflineNetwork?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { network = OfflineSchedules.network(); loaded = true }
    var selected by remember { mutableStateOf<OfflineNetwork.RouteSummary?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    AppBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalWindowInfo.current.containerDpSize.height * .9f)
            .padding(horizontal = AppLayout.SheetPadding)) {
            val net = network
            when {
                !loaded -> VehicleProgress(Modifier.fillMaxWidth())
                net == null -> Text("Nincs letöltött menetrend.", Modifier.padding(bottom = AppLayout.SheetPadding), style = MaterialTheme.typography.bodyLarge)
                selected == null -> RouteList(net, query, { query = it }, onDismiss) { selected = it }
                else -> {
                    BackHandlerCompat { selected = null }
                    RouteTimetable(net, selected!!, back = { selected = null })
                }
            }
        }
    }
}

@Composable private fun BackHandlerCompat(onBack: () -> Unit) = androidx.activity.compose.BackHandler(onBack = onBack)

private fun summaryLeg(route: OfflineNetwork.RouteSummary, headsign: String): Leg {
    val point = Station("", "", 0.0, 0.0)
    return Leg(route.mode, 0, 0, point, point, route.label, "", false, 0, emptyList(), "", route.color, "", headsign,
        route.longName, route.agency, routeShortName = route.label)
}

@Composable private fun ColumnScope.RouteList(network: OfflineNetwork, query: String, onQuery: (String) -> Unit, close: () -> Unit,
                                          select: (OfflineNetwork.RouteSummary) -> Unit) {
    var routes by remember { mutableStateOf<List<OfflineNetwork.RouteSummary>?>(null) }
    LaunchedEffect(network, query) { routes = withContext(Dispatchers.Default) { network.routes(query, limit = 80) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Vonalak menetrendje", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        IconButton(onClick = close) { Icon(Icons.Outlined.Close, "Bezárás") }
    }
    Text("Offline menetrend · csak menetrend szerinti idők", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(AppLayout.RelatedGap))
    OutlinedTextField(query, { onQuery(it.take(40)) }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(24.dp),
        label = { Text("Vonal száma vagy neve") }, leadingIcon = { Icon(Icons.Outlined.Search, null) })
    Spacer(Modifier.height(AppLayout.RelatedGap))
    val list = routes
    LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = AppLayout.SheetPadding),
        verticalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
        if (list == null) item { VehicleProgress(Modifier.fillMaxWidth()) }
        else if (list.isEmpty()) item { Text("Nincs ilyen vonal a letöltött menetrendekben.", style = MaterialTheme.typography.bodyLarge) }
        else items(list, key = { "${it.ref.feed}:${it.ref.route}" }) { route ->
            GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, onClick = { select(route) }) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
                        ServiceHeading(summaryLeg(route, route.longName.ifBlank { route.feedName }), route.longName.ifBlank { route.feedName })
                        Text(if (route.agency.isBlank() || route.feedName.contains(route.agency, ignoreCase = true)) route.feedName else "${route.agency} · ${route.feedName}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun ColumnScope.RouteTimetable(network: OfflineNetwork, route: OfflineNetwork.RouteSummary, back: () -> Unit) {
    val directions = remember(route) { network.directions(route.ref) }
    var direction by remember(route) { mutableIntStateOf(0) }
    val current = directions.getOrNull(direction)
    var stopIndex by remember(route, direction) { mutableIntStateOf(0) }
    val today = remember { LocalDate.now(Budapest) }
    val lastDay = remember(route) { network.feeds[route.ref.feed].lastServiceDay ?: today }
    val days = remember(today, lastDay) { (0L..6L).map { today.plusDays(it) }.filter { !it.isAfter(lastDay) }.ifEmpty { listOf(today) } }
    var day by remember(route) { mutableStateOf(today) }
    val stop = current?.stops?.getOrNull(stopIndex)
    var departures by remember { mutableStateOf<List<OfflineNetwork.RouteDeparture>?>(null) }
    LaunchedEffect(route, current, stop, day) {
        departures = null
        if (current != null && stop != null) departures = withContext(Dispatchers.Default) { network.routeDepartures(route.ref, current.headsign, stop.id, day) }
    }
    var opened by remember { mutableStateOf<OfflineNetwork.RouteDeparture?>(null) }
    var stopMenu by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Vissza a vonalakhoz") }
        Box(Modifier.weight(1f)) { ServiceHeading(summaryLeg(route, current?.headsign.orEmpty()), current?.headsign.orEmpty()) }
    }
    Text("Offline menetrend · ${route.feedName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(AppLayout.RelatedGap))
    LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(bottom = AppLayout.SheetPadding),
        verticalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
        if (directions.size > 1) item {
            Text("Irány", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                directions.forEachIndexed { index, d ->
                    GlassFilterChip(selected = index == direction, onClick = { direction = index }, label = { Text(d.headsign) },
                        modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
        item {
            ExposedDropdownMenuBox(expanded = stopMenu, onExpandedChange = { stopMenu = it }) {
                OutlinedTextField(stop?.name.orEmpty(), {}, readOnly = true, singleLine = true, label = { Text("Megálló") },
                    shape = RoundedCornerShape(24.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(stopMenu) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
                ExposedDropdownMenu(expanded = stopMenu, onDismissRequest = { stopMenu = false }) {
                    current?.stops?.forEachIndexed { index, s ->
                        if (index < current.stops.lastIndex) DropdownMenuItem(text = { Text(s.name) }, onClick = { stopIndex = index; stopMenu = false })
                    }
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEach { d ->
                    GlassFilterChip(selected = d == day, onClick = { day = d }, modifier = Modifier.heightIn(min = 48.dp), label = {
                        Text(when (d) { today -> "Ma"; today.plusDays(1) -> "Holnap"
                            else -> "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("hu"))} ${d.dayOfMonth}." })
                    })
                }
            }
        }
        val list = departures
        when {
            list == null -> item { VehicleProgress(Modifier.fillMaxWidth()) }
            list.isEmpty() -> item { Text("Ezen a napon nem indul járat erről a megállóról ebbe az irányba.", style = MaterialTheme.typography.bodyLarge) }
            else -> items(list.groupBy { timeLabel(it.time).substringBefore(':') }.entries.toList(), key = { it.key }) { (hour, rows) ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
                    Text(hour, Modifier.width(40.dp).padding(top = 12.dp).semantics { contentDescription = "$hour óra" },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rows.forEach { row ->
                            val minute = timeLabel(row.time).substringAfter(':')
                            Box(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
                                .clickable(role = Role.Button, onClickLabel = "Járat megállói") { opened = row }
                                .semantics { contentDescription = "${timeLabel(row.time)}, ${row.headsign}" },
                                contentAlignment = Alignment.Center) {
                                Text(minute, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
    opened?.let { row ->
        val departure = StopDeparture(row.tripId, row.headsign, ServiceTime(row.time, row.time, false), stop?.platform.orEmpty(), false,
            row.leg, row.day.toString(), offline = true)
        DepartureServiceDrawer(departure, dismiss = { opened = null },
            fetch = { id, serviceDay -> network.trip(id, serviceDay) ?: throw OfflineNotCovered() })
    }
}

