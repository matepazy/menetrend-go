package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Commute
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Update
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
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.LocalDate

data class VehicleStop(val name: String, val scheduled: Long, val arrival: Long, val departure: Long,
    val realtime: Boolean, val platform: String, val id: String = "") {
    val changed get() = realtime && arrival / 60_000 != scheduled / 60_000
}

fun vehicleStops(detail: JSONObject, day: String): List<VehicleStop> = detail.array("stoptimes").mapNotNull { stop ->
    fun seconds(key: String): Long? = if(stop.isNull(key)) null else stop.optLong(key,-1).takeIf {it>=0}
    val scheduled=seconds("scheduledArrival") ?: seconds("scheduledDeparture") ?: return@mapNotNull null
    val serviceDate=seconds("serviceDay")?.let {
        java.time.Instant.ofEpochSecond(it).atZone(Budapest).toLocalDate()
    } ?: LocalDate.parse(day)
    val realtime=stop.optBoolean("realtime")
    val arrival=if(realtime) seconds("realtimeArrival") ?: scheduled else scheduled
    val departure=if(realtime) seconds("realtimeDeparture") ?: seconds("scheduledDeparture") ?: arrival
        else seconds("scheduledDeparture") ?: scheduled
    val station=stop.optJSONObject("stop")
    VehicleStop(station?.optString("name").orEmpty(),vehicleStopTime(serviceDate,scheduled),
        vehicleStopTime(serviceDate,arrival),vehicleStopTime(serviceDate,departure),
        realtime,station?.optString("platformCode").orEmpty(),station?.optString("gtfsId").orEmpty())
}

/** OTP returns local timetable seconds, not elapsed seconds across a clock change.
 * Keep values over 24 hours on the following calendar day. */
internal fun vehicleStopTime(day: LocalDate, seconds: Long): Long =
    day.atStartOfDay().plusSeconds(seconds).atZone(Budapest).toInstant().toEpochMilli()

/** Keep provider stop order, including dwell at the current stop; discard departed stops. */
fun upcomingVehicleStops(stops: List<VehicleStop>, now: Long): List<VehicleStop> =
    stops.dropWhile {maxOf(it.arrival,it.departure)<now}

/** Prefer the selected physical vehicle's live stop relationship over clock estimates.
 * Exact stop IDs distinguish platforms and repeated station names. If a stop occurs
 * more than once, the provider's arrival time identifies the visit. */
internal fun upcomingVehicleStops(stops: List<VehicleStop>, now: Long, detail: JSONObject?, vehicle: Vehicle): List<VehicleStop> {
    val positions=detail?.array("vehiclePositions").orEmpty()
    val position=positions.firstOrNull {vehicle.vehicleId.isNotBlank() && it.optString("vehicleId")==vehicle.vehicleId}
        ?: positions.singleOrNull()?.takeIf {vehicle.vehicleId.isBlank()}
    val relationship=position?.optJSONObject("stopRelationship")
    if(relationship?.optString("status") in listOf("IN_TRANSIT_TO","INCOMING_AT","STOPPED_AT")) {
        val id=relationship?.optJSONObject("stop")?.optString("gtfsId").orEmpty()
        val candidates=stops.indices.filter {id.isNotBlank() && stops[it].id==id}
        val rawTimes=detail?.array("stoptimes").orEmpty()
        val arrival=relationship?.optLong("arrivalTime",-1) ?: -1
        val index=if(candidates.size>1 && arrival>=0 && rawTimes.size==stops.size)
            candidates.minByOrNull {kotlin.math.abs(rawTimes[it].optLong("realtimeArrival",rawTimes[it].optLong("scheduledArrival"))-arrival)}
            else candidates.singleOrNull()
        if(index!=null) return stops.drop(index)
    }
    return upcomingVehicleStops(stops,now)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun VehicleDrawer(vm: AppState, vehicle: Vehicle, dismiss: () -> Unit) {
    var day by remember(vehicle.id) {mutableStateOf(LocalDate.now(Budapest).toString())}
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vehicle.id,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) {
                day=LocalDate.now(Budapest).toString()
                vm.loadTrip(vehicle.id,day)
                delay(75_000)
            }
        }
    }
    LaunchedEffect(Unit) {while(true) {now=System.currentTimeMillis();delay(15_000)}}
    val detail=vm.detail?.takeIf {it.optString("gtfsId")==vehicle.id}
    val stops=remember(detail,day) {detail?.let {vehicleStops(it,day)}.orEmpty()}
    val upcoming=upcomingVehicleStops(stops,now,detail,vehicle)
    AppBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),shape=MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.78f).padding(horizontal=AppLayout.SheetPadding)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {RouteBadge(vehicle.badgeLeg())}
                IconButton(onClick=dismiss) {Icon(Icons.Outlined.Close,"Jármű részleteinek bezárása")}
            }
            if(vehicle.routeLogo==null) Text(hungarianServiceName(vehicle.category,vehicle.mode),style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=AppLayout.CompactGap))
            Row(Modifier.padding(top=AppLayout.CompactGap),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(AppLayout.CompactGap)) {
                Icon(Icons.Outlined.Update,contentDescription=null,modifier=Modifier.size(16.dp),
                    tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(vehiclePositionAge(vehicle.positionUpdatedSeconds,now/1000),
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(vehicle.model.isNotBlank() || vehicle.registration.isNotBlank() || vehicle.uicCode.isNotBlank()) {
                Column(Modifier.padding(top=AppLayout.RelatedGap),verticalArrangement=Arrangement.spacedBy(AppLayout.CompactGap)) {
                    if(vehicle.modelAndRegistration.isNotBlank()) Row(verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(AppLayout.RelatedGap)) {
                        Icon(Icons.Outlined.Commute,"Jármű",Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        VehicleModelLabel(vehicle,Modifier.weight(1f))
                    }
                    if(vehicle.uicCode.isNotBlank()) VehicleMetadataRow(Icons.Outlined.Badge,"Pályaszám",vehicle.uicCode)
                }
            }
            Text(if(stops.isNotEmpty()) "${stops.first().name} → ${stops.last().name}"
                else vehicle.headsign,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,
                modifier=Modifier.padding(top=AppLayout.RelatedGap,bottom=AppLayout.ItemGap))
            when {
                vm.detailLoading && detail==null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                vm.detailError!=null -> InfoPanel("A megállók nem tölthetők be",vm.detailError!!) {vm.loadTrip(vehicle.id,day)}
                detail!=null && upcoming.isEmpty() -> Text(if(stops.isEmpty()) "Nincs elérhető megállóadat." else "Nincs további várható megálló a menetrendben.",
                    modifier=Modifier.padding(bottom=24.dp))
                else -> LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(upcoming) {index,stop ->
                        Surface(shape=RoundedCornerShape(24.dp),color=if(index==0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().padding(AppLayout.ItemGap),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(AppLayout.RelatedGap)) {
                                Column(Modifier.width(with(LocalDensity.current) {72.sp.toDp()})) {
                                    ScheduledTime(ServiceTime(stop.scheduled,stop.arrival,stop.realtime),style=MaterialTheme.typography.titleMedium)
                                }
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    if(index==0) Text("Következő megálló",style=MaterialTheme.typography.labelMedium)
                                    Text(stop.name,style=MaterialTheme.typography.titleMedium,fontWeight=if(index==0) FontWeight.Bold else FontWeight.Medium)
                                    if(stop.platform.isNotBlank() && stop.platform!="null") Text("Vágány / kocsiállás: ${stop.platform}",style=MaterialTheme.typography.bodySmall)
                                    if(!stop.realtime) Text("Menetrendi érkezés",style=MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun VehicleMetadataRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(AppLayout.RelatedGap)) {
        Icon(icon,contentDescription=label,modifier=Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,style=MaterialTheme.typography.bodyMedium)
    }
}
