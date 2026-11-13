package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Own the request and dismissal independently of the departure board and map details. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun DepartureServiceDrawer(
    departure: StopDeparture,
    dismiss: () -> Unit,
    fetch: (suspend (String, String) -> JSONObject)? = null,
) {
    val client = remember { MavClient() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var detail by remember(departure.key) { mutableStateOf<JSONObject?>(null) }
    var error by remember(departure.key) { mutableStateOf<String?>(null) }
    var loading by remember(departure.key) { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(departure.key, refresh, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                loading = true
                try {
                    detail = fetch?.invoke(departure.tripId, departure.serviceDay)
                        ?: client.trip(departure.tripId, departure.serviceDay, forceRefresh = refresh > 0)
                    error = null
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "A járat adatai nem tölthetők be." }
                finally { loading = false }
                delay(30_000)
            }
        }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); delay(15_000) }
        }
    }
    val stops = remember(detail, departure.serviceDay) {
        detail?.let { vehicleStops(it, departure.serviceDay) }.orEmpty()
    }
    val live = !departure.cancelled && stops.any { it.realtime }
    val vehicle = remember(departure, detail) {
        val position = detail?.array("vehiclePositions")?.singleOrNull()
        Vehicle(departure.tripId, departure.leg.route, departure.headsign, departure.leg.mode, 0.0, 0.0,
            routeColor = departure.leg.routeColor, category = departure.leg.category,
            routeLongName = departure.leg.routeLongName, routeLogo = departure.leg.routeLogo,
            agencyName = departure.leg.agencyName,
            model = position?.optString("vehicleModel")?.takeUnless { it == "null" }.orEmpty(),
            registration = position?.optString("licensePlate")?.takeUnless { it == "null" }.orEmpty(),
            vehicleId = position?.optString("vehicleId")?.takeUnless { it == "null" }.orEmpty(),
            positionUpdatedSeconds = position?.optLong("lastUpdated")?.takeIf { it > 0 })
    }
    if (live) {
        VehicleDrawer(vehicle, detail, departure.serviceDay, now, error, { refresh++ }, dismiss)
        return
    }
    AppBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal = AppLayout.SheetPadding)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { RouteBadge(departure.leg) }
                IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Járat részleteinek bezárása") }
            }
            Text(departure.headsign, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = AppLayout.CompactGap))
            Text(when {
                departure.cancelled -> "Kimaradó járat · tervezett menetrend"
                detail == null -> "Járatadatok betöltése"
                else -> "Tervezett menetrend · ${departure.serviceDay}"
            }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppLayout.CompactGap, bottom = AppLayout.ItemGap))
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = AppLayout.SheetPadding),
                verticalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                error?.let { message -> item {
                    InfoPanel("A járat adatai nem tölthetők be", message) { refresh++ }
                } }
                if (detail != null && !departure.cancelled) item {
                    Text("Ehhez a járathoz most nincs élő megállóadat.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (detail != null && stops.isEmpty()) item {
                    Text("Nincs elérhető megállóadat.")
                }
                itemsIndexed(stops) { _, stop ->
                    Surface(shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(AppLayout.ItemGap),
                            horizontalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
                            Column(Modifier.width(with(LocalDensity.current) { 72.sp.toDp() })) {
                                ScheduledTime(ServiceTime(stop.scheduled, stop.arrival, false),
                                    style = MaterialTheme.typography.titleMedium)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
                                Text(stop.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (stop.platform.isNotBlank() && stop.platform != "null")
                                    Text("Vágány / kocsiállás: ${stop.platform}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
