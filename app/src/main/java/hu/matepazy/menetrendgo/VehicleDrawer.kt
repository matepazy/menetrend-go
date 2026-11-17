package hu.matepazy.menetrendgo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
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

/** Live-map selection content: loads and refreshes the trip while the map sheet shows it. */
@Composable fun ColumnScope.MapVehicleSheetContent(vm: AppState, vehicle: Vehicle, dismiss: () -> Unit) {
    var day by remember(vehicle.id) {mutableStateOf(LocalDate.now(Budapest).toString())}
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vehicle.id,lifecycle,vm.mapRefreshSeconds) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(vehicle.hasMavTrip) {
                day=LocalDate.now(Budapest).toString()
                vm.loadTrip(vehicle.id,day)
                delay(vm.mapRefreshSeconds * 1_000L)
            }
        }
    }
    LaunchedEffect(Unit) {while(true) {now=System.currentTimeMillis();delay(15_000)}}
    val detail=vm.detail?.takeIf {it.optString("gtfsId")==vehicle.id}
    VehicleDrawerContent(vehicle, detail, day, now, vm.detailError, {vm.loadTrip(vehicle.id,day)}, dismiss)
}

/** Modal presentation for independently loaded stop-board departures. */
@Composable internal fun VehicleDrawer(
    vehicle: Vehicle,
    detail: JSONObject?,
    day: String,
    now: Long,
    error: String?,
    retry: () -> Unit,
    dismiss: () -> Unit,
) {
    AppExpandableBottomSheet(onDismissRequest=dismiss,initialHeightFraction=.78f) {
        VehicleDrawerContent(vehicle, detail, day, now, error, retry, dismiss)
    }
}

/** Shared drawer body: the next stop leads; passed stops unfold above it on request. */
@Composable internal fun ColumnScope.VehicleDrawerContent(
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
    val nextIndex=stops.size-upcoming.size
    // Passed stops unfold in place under an explicit toggle, independent of the sheet height,
    // so a drag never swaps the list. Each vehicle opens on its next stop.
    var showPassed by rememberSaveable(vehicle.id) {mutableStateOf(false)}
    val empty=if(showPassed) stops.isEmpty() else upcoming.isEmpty()
    val listState=rememberLazyListState()
    val showUicCode=vehicle.mode!="RAIL" && vehicle.uicCode.isNotBlank()
    val track=TimelineTrack(serviceColor(vehicle.badgeLeg()))
    val passedTrack=track.copy(color=track.color.copy(alpha=.6f))
    val context=LocalContext.current
    val dark=LocalAppDarkTheme.current
    val colorContext=remember(context,dark) {departureThemeContext(context,dark)}
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal=AppLayout.SheetPadding)) {
            // The summary stays visible when the map sheet collapses to a peek.
            val reportSummary=LocalMapSheetSummaryHeight.current
            Column(if(reportSummary!=null) Modifier.onSizeChanged {reportSummary(it.height)} else Modifier) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(AppLayout.CompactGap),
                verticalAlignment=Alignment.CenterVertically) {
                RouteBadge(vehicle.badgeLeg(),contentPadding=if(vehicle.routeLogo!=null) PaddingValues(0.dp) else PaddingValues(horizontal=14.dp,vertical=6.dp),
                    showModeIcon=false)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward,"Irány",Modifier.size(20.dp),
                    tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(stops.isNotEmpty()) stops.last().name else vehicle.headsign,Modifier.weight(1f),
                    style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,
                    maxLines=2,overflow=TextOverflow.Ellipsis)
                IconButton(onClick=dismiss) {Icon(Icons.Outlined.Close,"Jármű részleteinek bezárása")}
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
            }
            // The toggle row's 48dp target already carries whitespace above its label.
            val leadsWithToggle=vehicle.hasMavTrip && detail!=null && !empty && nextIndex>0
            Spacer(Modifier.height(if(leadsWithToggle) AppLayout.LabelGap else AppLayout.ItemGap))
            // A failed refresh keeps the stops already shown; only a first load without data shows the full error.
            when {
                !vehicle.hasMavTrip -> Text("Ehhez a járműhöz csak élő pozíció érhető el, menetrend nem.",
                    modifier=Modifier.padding(bottom=24.dp),style=MaterialTheme.typography.bodyMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                detail==null && error!=null -> InfoPanel("A megállók nem tölthetők be",error,retry)
                detail==null -> VehicleProgress(Modifier.fillMaxWidth())
                else -> {
                    error?.let {RefreshFailure(retry)}
                    @Composable fun StopRow(position: Int, stop: VehicleStop) {
                        val next=position==nextIndex
                        TimetableStopRow(stop.name,ServiceTime(stop.scheduled,stop.arrival,stop.realtime),
                            stop.platform,vehicle.mode,track,
                            first=position==0,last=position==stops.lastIndex,
                            prominent=next || position==0,next=next,
                            // Passed rows dim as a whole; the next stop's incoming leg matches them.
                            incomingTrack=if(next) passedTrack else track)
                    }
                    if(empty) Text(if(stops.isEmpty()) "Nincs elérhető megállóadat." else "Nincs további várható megálló a menetrendben.",
                        modifier=Modifier.padding(bottom=24.dp))
                    else LazyColumn(Modifier.weight(1f),state=listState,contentPadding=PaddingValues(bottom=24.dp)) {
                        if(nextIndex>0) {
                            item(key="passed") {
                                PassedStopsToggle(nextIndex,showPassed,passedTrack,Modifier) {showPassed=!showPassed}
                            }
                            // One block grows open from the toggle and pushes the next stop down with it,
                            // instead of rows popping in at full height (same motion as köztes megálló).
                            item(key="passed-stops") {
                                AnimatedVisibility(showPassed,
                                    enter=expandVertically(tween(AppMotion.Reveal,easing=AppMotion.Arrival),expandFrom=Alignment.Top)+fadeIn(tween(AppMotion.Reveal)),
                                    exit=shrinkVertically(tween(AppMotion.Reveal,easing=AppMotion.State),shrinkTowards=Alignment.Top)+fadeOut(tween(AppMotion.Dismiss))) {
                                    Column(Modifier.alpha(.6f)) {
                                        stops.take(nextIndex).forEachIndexed {position,stop ->
                                            Box(Modifier.semantics(mergeDescendants=true) {stateDescription="Elhagyott megálló"}) {StopRow(position,stop)}
                                        }
                                    }
                                }
                            }
                        }
                        itemsIndexed(upcoming,key={index,_ -> nextIndex+index}) {index,stop ->
                            // No placement animation: the rows follow the growing block frame by frame
                            // rather than lagging behind it and overlapping the unfolding stops.
                            Box(Modifier.animateItem(fadeInSpec=tween(AppMotion.Feedback),
                                placementSpec=null,fadeOutSpec=tween(AppMotion.Dismiss))) {
                                StopRow(nextIndex+index,stop)
                            }
                        }
                    }
                }
            }
        }
}

/** Folded, the dimmed track enters as a fading dot trail standing in for the hidden stops and turns
 * solid at the label, instead of being cut off. Unfolded, the route origin below takes over and the row has no track. */
@Composable private fun PassedStopsToggle(count: Int, open: Boolean, track: TimelineTrack, modifier: Modifier, toggle: () -> Unit) {
    val rotation by animateFloatAsState(if(open) 180f else 0f,tween(AppMotion.Reveal,easing=AppMotion.State),label="Passed stops")
    val timeGutter=56.dp*LocalDensity.current.fontScale
    // The trail recedes downward and fades in step with the stops unfolding, and grows back when they fold.
    val trail by animateFloatAsState(if(open) 0f else 1f,
        tween(if(open) AppMotion.Dismiss else AppMotion.Reveal,easing=if(open) AppMotion.State else AppMotion.Arrival),label="Passed stops trail")
    Box(modifier.drawBehind {
        if(trail<=0f) return@drawBehind
        val x=(timeGutter+12.dp).toPx()
        val drop=(1-trail)*size.height/2
        val center=size.height/2+drop
        val pitch=7.dp.toPx()
        fun color(alpha: Float)=track.color.copy(alpha=track.color.alpha*alpha*trail)
        listOf(.3f,.55f,.8f).forEachIndexed {index,alpha ->
            drawCircle(color(alpha),2.dp.toPx(),Offset(x,center-pitch*(3-index)))
        }
        drawLine(color(1f),Offset(x,minOf(center-2.dp.toPx(),size.height)),Offset(x,size.height),strokeWidth=4.dp.toPx())
    }) {
        TimelineRow(null,track,incoming=null,outgoing=null,marker={},bottomPadding=0.dp) {
            TextButton(onClick=toggle,shape=MaterialTheme.shapes.extraSmall,
                contentPadding=PaddingValues(vertical=AppLayout.CompactGap),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    .semantics {stateDescription=if(open) "Kibontva" else "Összecsukva"}) {
                Text("$count elhagyott megálló",Modifier.weight(1f))
                Icon(Icons.Outlined.ExpandMore,if(open) "Elhagyott megállók elrejtése" else "Elhagyott megállók megjelenítése",
                    Modifier.size(20.dp).graphicsLayer {rotationZ=rotation})
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
