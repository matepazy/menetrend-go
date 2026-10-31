package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StopBoardDrawer(station: Station, dismiss: () -> Unit,
    fetch: (suspend (String) -> List<StopDeparture>)? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val client = remember { MavClient() }
    var departures by remember(station.id) { mutableStateOf<List<StopDeparture>>(emptyList()) }
    var updated by remember(station.id) { mutableLongStateOf(0) }
    var error by remember(station.id) { mutableStateOf<String?>(null) }
    var loading by remember(station.id) { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var pinMessage by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); delay(1000) }
        }
    }
    LaunchedEffect(station.id, refresh, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                loading = true
                try {
                    departures = fetch?.invoke(station.id) ?: client.stopDepartures(station.id)
                    updated = System.currentTimeMillis()
                    error = null
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "Az indulások nem tölthetők be." }
                finally { loading = false }
                delay(30_000)
            }
        }
    }
    AppBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(horizontal = AppLayout.SheetPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.Top) {
                    Text(station.name, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Indulási tábla bezárása") }
                }
                if (station.platform.isNotBlank()) Text("Vágány / kocsiállás: ${station.platform}", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Indulási tábla", style = MaterialTheme.typography.titleMedium)
                        Text(if (updated == 0L) "Indulások betöltése" else "Frissítve ${updateAge(updated, now)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { refresh++ }, enabled = !loading) { Icon(Icons.Outlined.Refresh, "Indulások frissítése") }
                }
                FilledTonalButton(onClick = { pinMessage = StopBoardWidget.requestPin(context, station) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Widgets, null); Spacer(Modifier.width(8.dp)); Text("Widget a kezdőképernyőre")
                }
                pinMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message -> item {
                Text(if (updated > 0) "Korábbi adatok · $message" else message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { refresh++ }) { Text("Újrapróbálás") }
            } }
            if (!loading && error == null && departures.isEmpty()) item {
                Text("A következő 24 órában nincs felszállásra alkalmas indulás ennél a megállónál.", style = MaterialTheme.typography.bodyLarge)
            }
            items(departures.filter { it.time.estimate >= now - 30_000 }, key = { it.key }) { departure ->
                Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RouteBadge(departure.leg, contentPadding = if (departure.leg.routeLogo != null) PaddingValues(0.dp)
                                else PaddingValues(horizontal = 8.dp, vertical = 5.dp))
                            Text(departure.headsign, style = MaterialTheme.typography.titleMedium)
                            if (departure.platform.isNotBlank()) Text("Vágány / kocsiállás: ${departure.platform}", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (departure.cancelled) "Kimarad" else departureCountdown(departure.time.estimate, now),
                                modifier = Modifier.semantics { stateDescription = departureStateDescription(departure) },
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                color = colorResource(departureColorResource(departure.time, departure.cancelled)))
                            departureClockTime(departure.time.estimate, now, departure.cancelled)?.let { clock ->
                                Text(clock, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
