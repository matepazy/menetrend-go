package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Commute
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
internal fun upcomingVehicleStops(stops: List<VehicleStop>, now: Long, detail: JSONObject?, vehicle: Vehicle): List<VehicleStop> =
    vehicleStopProgress(stops, detail, vehicle)?.let { stops.drop(it.index) } ?: upcomingVehicleStops(stops,now)

internal data class VehicleStopProgress(val index: Int, val atStop: Boolean)

/** A timetable estimate alone is not evidence of a physical vehicle position. */
internal fun vehicleStopProgress(stops: List<VehicleStop>, detail: JSONObject?, vehicle: Vehicle): VehicleStopProgress? {
    val positions=detail?.array("vehiclePositions").orEmpty()
    val position=positions.firstOrNull {vehicle.vehicleId.isNotBlank() && it.optString("vehicleId")==vehicle.vehicleId}
        ?: positions.singleOrNull()?.takeIf {vehicle.vehicleId.isBlank()}
    val relationship=position?.optJSONObject("stopRelationship") ?: return null
    val status=relationship.optString("status")
    if(status !in listOf("IN_TRANSIT_TO","INCOMING_AT","STOPPED_AT")) return null
    val id=relationship.optJSONObject("stop")?.optString("gtfsId").orEmpty()
    val candidates=stops.indices.filter {id.isNotBlank() && stops[it].id==id}
    val rawTimes=detail?.array("stoptimes").orEmpty()
    val arrival=relationship.optLong("arrivalTime",-1)
    val index=if(candidates.size>1 && arrival>=0 && rawTimes.size==stops.size)
        candidates.minByOrNull {kotlin.math.abs(rawTimes[it].optLong("realtimeArrival",rawTimes[it].optLong("scheduledArrival"))-arrival)}
        else candidates.singleOrNull()
    return index?.let {VehicleStopProgress(it,status=="STOPPED_AT")}
}

/** Delay at the next stop, compared at minute resolution like the struck-through stop times. */
internal fun vehicleDelayMinutes(stop: VehicleStop): Long? =
    if(stop.realtime) stop.arrival/60_000-stop.scheduled/60_000 else null

internal fun vehicleDelayTime(stop: VehicleStop): ServiceTime =
    ServiceTime(stop.scheduled/60_000*60_000,stop.arrival/60_000*60_000,stop.realtime)

internal fun vehicleDelaySummary(stop: VehicleStop): String {
    val minutes=vehicleDelayMinutes(stop) ?: return "Menetrend szerinti adat"
    return when {
        minutes>0 -> "$minutes perc késés"
        minutes<0 -> "${-minutes} perccel korábban"
        else -> "Időben közlekedik"
    }
}

internal fun vehicleStopPlatform(platform: String, mode: String): String = when(mode) {
    "RAIL","SUBURBAN_RAILWAY" -> "$platform. vágány"
    "BUS","COACH" -> "$platform. kocsiállás"
    else -> "Megállóhely: $platform"
}

@Composable private fun RefreshFailure(retry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom=AppLayout.CompactGap),verticalAlignment=Alignment.CenterVertically) {
        Text("A frissítés nem sikerült, korábbi adatok láthatók.",Modifier.weight(1f),
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
        TextButton(onClick=retry) {Text("Újra")}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun VehicleDrawer(vm: AppState, vehicle: Vehicle, dismiss: () -> Unit) {
    var day by remember(vehicle.id) {mutableStateOf(LocalDate.now(Budapest).toString())}
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vehicle.id,lifecycle,vm.mapRefreshSeconds) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) {
                day=LocalDate.now(Budapest).toString()
                vm.loadTrip(vehicle.id,day)
                delay(vm.mapRefreshSeconds * 1_000L)
            }
        }
    }
    LaunchedEffect(Unit) {while(true) {now=System.currentTimeMillis();delay(15_000)}}
    val detail=vm.detail?.takeIf {it.optString("gtfsId")==vehicle.id}
    VehicleDrawer(vehicle, detail, day, now, vm.detailError, {vm.loadTrip(vehicle.id,day)}, dismiss)
}

/** Shared presentation for map selections and independently loaded stop-board departures. */
@Composable internal fun VehicleDrawer(
    vehicle: Vehicle,
    detail: JSONObject?,
    day: String,
    now: Long,
    error: String?,
    retry: () -> Unit,
    dismiss: () -> Unit,
) {
    val stops=remember(detail,day) {detail?.let {vehicleStops(it,day)}.orEmpty()}
    val upcoming=upcomingVehicleStops(stops,now,detail,vehicle)
    val showUicCode=vehicle.mode!="RAIL" && vehicle.uicCode.isNotBlank()
    val track=TimelineTrack(serviceColor(vehicle.badgeLeg()))
    val context=LocalContext.current
    val dark=LocalAppDarkTheme.current
    val colorContext=remember(context,dark) {departureThemeContext(context,dark)}
    AppExpandableBottomSheet(onDismissRequest=dismiss,initialHeightFraction=.78f) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal=AppLayout.SheetPadding)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {RouteBadge(vehicle.badgeLeg(),contentPadding=if(vehicle.routeLogo!=null) PaddingValues(0.dp) else PaddingValues(horizontal=14.dp,vertical=6.dp),
                    showModeIcon=false)}
                IconButton(onClick=dismiss) {Icon(Icons.Outlined.Close,"Jármű részleteinek bezárása")}
            }
            Column(Modifier.fillMaxWidth().padding(top=AppLayout.RelatedGap),
                verticalArrangement=Arrangement.spacedBy(AppLayout.LabelGap)) {
                if(stops.isNotEmpty()) Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(AppLayout.CompactGap),
                    verticalAlignment=Alignment.CenterVertically) {
                    Text(stops.first().name,Modifier.weight(1f,fill=false),
                        style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Normal,
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward,"Irány",Modifier.size(20.dp),
                        tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if(stops.isNotEmpty()) stops.last().name else vehicle.headsign,
                    style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            }
            upcoming.firstOrNull()?.let {next ->
                val time=vehicleDelayTime(next)
                Text(vehicleDelaySummary(next),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold,
                    color=Color(colorContext.getColor(departureColorResource(time))),
                    modifier=Modifier.padding(top=AppLayout.CompactGap).semantics {
                        stateDescription=if(next.realtime) "Élő adat" else "Menetrend szerinti adat"})
            }
            VehicleTelemetry(vehicle,Modifier.padding(top=AppLayout.CompactGap))
            if(vehicle.modelAndRegistration.isNotBlank() || showUicCode) {
                Column(Modifier.padding(top=AppLayout.RelatedGap),verticalArrangement=Arrangement.spacedBy(AppLayout.CompactGap)) {
                    if(vehicle.modelAndRegistration.isNotBlank()) Row(verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(AppLayout.RelatedGap)) {
                        Icon(Icons.Outlined.Commute,"Jármű",Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        VehicleModelLabel(vehicle,Modifier.weight(1f))
                    }
                    if(showUicCode) VehicleMetadataRow(Icons.Outlined.Badge,"Pályaszám",vehicle.uicCode)
                }
            }
            Spacer(Modifier.height(AppLayout.SheetPadding))
            // A failed refresh keeps the stops already shown; only a first load without data shows the full error.
            when {
                detail==null && error!=null -> InfoPanel("A megállók nem tölthetők be",error,retry)
                detail==null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> {
                    error?.let {RefreshFailure(retry)}
                    if(upcoming.isEmpty()) Text(if(stops.isEmpty()) "Nincs elérhető megállóadat." else "Nincs további várható megálló a menetrendben.",
                        modifier=Modifier.padding(bottom=24.dp))
                    else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp)) {
                        itemsIndexed(upcoming) {index,stop ->
                            val next=index==0
                            TimetableStopRow(stop.name,ServiceTime(stop.scheduled,stop.arrival,stop.realtime),
                                stop.platform,vehicle.mode,track,first=next,last=index==upcoming.lastIndex,
                                prominent=next,next=next)
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun VehicleTelemetry(vehicle: Vehicle, modifier: Modifier = Modifier) {
    var now by remember {mutableLongStateOf(System.currentTimeMillis()/1000)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) {
                now=System.currentTimeMillis()/1000
                delay(1000)
            }
        }
    }
    FlowRow(modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(AppLayout.RelatedGap),
        verticalArrangement=Arrangement.spacedBy(AppLayout.CompactGap)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(AppLayout.CompactGap)) {
            Icon(Icons.Outlined.Update,contentDescription=null,modifier=Modifier.size(16.dp),
                tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(vehiclePositionAge(vehicle.positionUpdatedSeconds,now),
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        vehicleSpeedLabel(vehicle.speedMetersPerSecond)?.let {speed ->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(AppLayout.CompactGap)) {
                Icon(Icons.Outlined.Speed,contentDescription="Sebesség",modifier=Modifier.size(16.dp),
                    tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(speed,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
