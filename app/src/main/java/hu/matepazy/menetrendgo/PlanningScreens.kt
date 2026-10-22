package hu.matepazy.menetrendgo

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable private fun StopField(label: String, value: Station?, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal=18.dp,vertical=16.dp).heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(26.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value?.name ?: "Megálló kiválasztása",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SearchScreen(vm: AppState, onPasses: () -> Unit, onSearch: () -> Unit) {
    var picker by remember { mutableStateOf<String?>(null) }
    var datePicker by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("MenetrendGO",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            FilledTonalIconButton(onClick=onPasses,shape=CircleShape) {Icon(Icons.Outlined.NotificationsNone,"Bérletek és emlékeztetők")}
        } }
        item { Text("Hová indulunk?",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp,bottom=4.dp)) }
        item {
            Glass(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        StopField("Honnan?",vm.from,Icons.Outlined.TripOrigin) { picker="from" }
                        StopField("Hová?",vm.to,Icons.Outlined.LocationOn) { picker="to" }
                    }
                    IconButton(onClick={ val first=vm.from; vm.from=vm.to;vm.to=first },modifier=Modifier.padding(start=4.dp).size(48.dp)) { Icon(Icons.Outlined.SwapVert,"Indulás és érkezés felcserélése",tint=MaterialTheme.colorScheme.primary) }
                }
                Spacer(Modifier.height(14.dp))
                Surface(onClick={datePicker=true},shape=CircleShape,color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp).heightIn(min=24.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CalendarToday,null,tint=MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(if(vm.useNow) "Ma, indulás most" else vm.dateTime.format(DateTimeFormatter.ofPattern("MM. dd. HH:mm")) + if(vm.arriveBy) " · érkezés" else " · indulás",Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                        Icon(Icons.Outlined.ExpandMore,null)
                    }
                }
                Spacer(Modifier.height(16.dp))
                MainButton("Járatok keresése",Icons.Outlined.Search,vm.from!=null && vm.to!=null && vm.from!=vm.to,onSearch)
            }
        }
        item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Legutóbbi keresések",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            if(vm.recents.isNotEmpty()) TextButton(onClick=vm::clearRecents) { Text("Törlés") }
        } }
        if(vm.recents.isEmpty()) item { Text("A kereséseidet itt találod majd. Egy koppintással újratervezheted az utat.",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=4.dp)) }
        items(vm.recents,key={it.from.id+":"+it.to.id}) { recent ->
            Surface(onClick={vm.from=recent.from;vm.to=recent.to;vm.useNow=true;vm.arriveBy=false;onSearch()},shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.History,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)) {
                        Text(recent.from.name,style=MaterialTheme.typography.titleMedium)
                        Text(recent.to.name,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    };Icon(Icons.Outlined.ChevronRight,null)
                }
            }
        }
        item { Text("MÁV-adatokkal · Nem hivatalos alkalmazás",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=12.dp,bottom=16.dp)) }
    }
    if(picker!=null) StationPicker(vm,if(picker=="from") "Indulási megálló" else "Érkezési megálló",onDismiss={picker=null}) {
        if(picker=="from") vm.from=it else vm.to=it;picker=null
    }
    if(datePicker) JourneyDatePicker(vm) {datePicker=false}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun StationPicker(vm: AppState, title: String, onDismiss: () -> Unit, onSelected: (Station) -> Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    var results by remember { mutableStateOf<List<Station>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(query,vm.backend) {
        results=emptyList();error=null
        if(query.trim().length>=2) {
            loading=true;delay(350)
            try {results=vm.stations(query.trim())}
            catch(e: CancellationException){throw e}
            catch(e: Exception){error=e.message}
            finally {loading=false}
        } else loading=false
    }
    ModalBottomSheet(onDismissRequest=onDismiss,shape=RoundedCornerShape(topStart=36.dp,topEnd=36.dp)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal=20.dp).imePadding()) {
            Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,shape=CircleShape,label={Text("Település vagy megálló")},leadingIcon={Icon(Icons.Outlined.Search,null)},trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""}){Icon(Icons.Outlined.Close,"Keresés törlése")}})
            Spacer(Modifier.height(12.dp))
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(error!=null) Text(error!!,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(16.dp))
            if(!loading && query.trim().length>=2 && results.isEmpty() && error==null) Text("Nincs ilyen megálló. Próbálj másik nevet.",Modifier.padding(16.dp))
            if(query.length<2) Text("Írj be legalább két karaktert.",Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(results,key={it.id}) {station ->
                    Surface(onClick={onSelected(station)},shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocationOn,null,tint=MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp));Text(station.name,Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun JourneyDatePicker(vm: AppState, dismiss: () -> Unit) {
    val dateState = rememberDatePickerState(initialSelectedDateMillis=vm.dateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    val timeState = rememberTimePickerState(vm.dateTime.hour,vm.dateTime.minute,is24Hour=true)
    var stage by remember {mutableIntStateOf(0)}
    var arrive by remember {mutableStateOf(vm.arriveBy)}
    if(stage==0) DatePickerDialog(onDismissRequest=dismiss,shape=RoundedCornerShape(32.dp),confirmButton={TextButton(onClick={stage=1}){Text("Időpont")}},dismissButton={TextButton(onClick={vm.useNow=true;vm.arriveBy=false;dismiss()}){Text("Indulás most")}}) {
        DatePicker(dateState,showModeToggle=false,title={Text("Utazás napja",Modifier.padding(24.dp),style=MaterialTheme.typography.titleLarge)})
    } else AlertDialog(onDismissRequest=dismiss,shape=RoundedCornerShape(32.dp),title={Text("Utazás időpontja")},text={
        Column {
            TimeInput(timeState)
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(arrive,{arrive=it});Text("Ekkorra érkezzek meg")}
        }
    },confirmButton={TextButton(onClick={
        val day=Instant.ofEpochMilli(dateState.selectedDateMillis ?: System.currentTimeMillis()).atZone(ZoneOffset.UTC).toLocalDate()
        vm.dateTime=day.atTime(timeState.hour,timeState.minute);vm.useNow=false;vm.arriveBy=arrive;dismiss()
    }){Text("Kész")}},dismissButton={TextButton(onClick={stage=0}){Text("Vissza")}})
}

@Composable fun RouteBadge(leg: Leg) {
    Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(if(leg.walking) Icons.Outlined.DirectionsWalk else if(leg.mode in listOf("COACH","BUS","RAIL_REPLACEMENT_BUS")) Icons.Outlined.DirectionsBus else Icons.Outlined.Train,null,Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp));Text(if(leg.walking) "Gyalog" else leg.route,style=MaterialTheme.typography.labelLarge)
        }
    }
}
@Composable fun ResultsScreen(vm: AppState, back: () -> Unit, select: (Journey) -> Unit) {
    var filter by rememberSaveable {mutableStateOf("Összes")}
    val results=vm.journeys.filter { j -> filter=="Összes" || j.legs.any {if(filter=="Vonat") it.mode in listOf("RAIL","SUBURBAN_RAILWAY","TRAMTRAIN") else it.mode in listOf("BUS","COACH","RAIL_REPLACEMENT_BUS")} }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { PageTitle("Indulások", "${vm.from?.name.orEmpty()} → ${vm.to?.name.orEmpty()}",back) }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Összes","Vonat","Busz").forEach { label -> FilterChip(selected=filter==label,onClick={filter=label},label={Text(label)},shape=CircleShape) } } }
        if(vm.searchError!=null) item {InfoPanel("Nem sikerült a keresés",vm.searchError!!){vm.search(vm.journeys.isNotEmpty())}}
        if(vm.searching && vm.journeys.isEmpty()) item {LoadingRows()}
        if(!vm.searching && vm.searchError==null && results.isEmpty()) item {InfoPanel("Nincs megjeleníthető indulás","Módosítsd az időpontot, a megállókat vagy a járműszűrőt.")}
        items(results,key={it.key}) { journey ->
            Surface(onClick={select(journey)},shape=RoundedCornerShape(32.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=2.dp) {
                Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(timeLabel(journey.start),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward,null,Modifier.padding(horizontal=12.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(timeLabel(journey.end),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null)
                    }
                    Text("${dayLabel(journey.start)} · ${journey.minutes} perc · ${if(journey.changes==0) "Közvetlen" else "${journey.changes} átszállás"}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {journey.legs.forEach {RouteBadge(it)}}
                    val live=journey.legs.any {it.realTime}
                    Text(if(live) "Valós idejű menetadatok" else "Menetrendi időpontok",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(vm.passes.isNotEmpty()) Text(passAdvice(vm.passes,journey).title,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                }
            }
        }
        if(vm.searching && vm.journeys.isNotEmpty()) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(vm.canLoadMore && !vm.searching) item {OutlinedButton(onClick={vm.search(true)},shape=CircleShape,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(if(vm.arriveBy) "Korábbi indulások" else "További indulások")}}
        item {Text("A találatokat a MÁV útvonaltervezője adja. Az újabb keresés további időpontokat tölt be.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=16.dp))}
    }
}

@Composable fun JourneyScreen(vm: AppState, journey: Journey, back: () -> Unit, onTrip: (Leg) -> Unit, onMap: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {PageTitle("Utazás részletei", "${dayLabel(journey.start)} · ${timeLabel(journey.start)}–${timeLabel(journey.end)} · ${journey.minutes} perc",back)}
        item { MainButton("Útvonal a térképen",Icons.Outlined.Map,onClick=onMap) }
        item {val advice=passAdvice(vm.passes,journey);InfoPanel(advice.title,advice.detail)}
        items(journey.legs) {leg ->
            Glass(Modifier.fillMaxWidth()) {
                RouteBadge(leg);Spacer(Modifier.height(18.dp))
                Text("${timeLabel(leg.start)}  ${leg.from.name}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val arrivalDay=if(Instant.ofEpochMilli(leg.end).atZone(Budapest).toLocalDate()!=Instant.ofEpochMilli(journey.start).atZone(Budapest).toLocalDate()) "${dayLabel(leg.end)} · " else ""
                Text("$arrivalDay${timeLabel(leg.end)}  ${leg.to.name}",style=MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Text(if(leg.realTime) "Valós idejű adat · eltérés: ${leg.departureDelay/60} perc" else "Menetrendi adat · élő időpont nem érhető el",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(!leg.walking && leg.tripId.isNotBlank()) TextButton(onClick={onTrip(leg)}) {Text("Teljes megállólista");Icon(Icons.Outlined.ChevronRight,null)}
                if(leg.walking) Text("${(leg.end-leg.start+59_999)/60_000} perc gyaloglás",Modifier.padding(top=8.dp))
            }
        }
    }
}

@Composable fun TripScreen(vm: AppState, id: String, day: String, back: () -> Unit, onMap: () -> Unit) {
    val detail=vm.detail
    val vehicle=vm.vehicles.find {it.id==id}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {PageTitle(detail?.optJSONObject("route")?.optString("shortName")?.ifBlank {"Járat"} ?: "Járat", "$day · ${detail?.optString("tripHeadsign").orEmpty()}",back)}
        if(vm.detailLoading && detail==null) item {LoadingRows()}
        if(vm.detailError!=null) item {InfoPanel("A menetrend nem tölthető be",vm.detailError!!){vm.loadTrip(id,day)}}
        if(detail!=null) {
            item {Text(if(vehicle==null) "Ehhez a járathoz most nincs élő járműpozíció." else "Élő járműpozíció elérhető · 75 mp-es frissítés",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(vehicle!=null) item {OutlinedButton(onClick=onMap,shape=CircleShape,modifier=Modifier.fillMaxWidth()){Text("Jármű a térképen")}}
            itemsIndexed(detail.array("stoptimes")) {index,stop ->
                val s=stop.optJSONObject("stop")
                val scheduled=stop.optInt("scheduledDeparture",stop.optInt("scheduledArrival"))
                val seconds=stop.optInt("realtimeDeparture",scheduled)
                val epoch=if(stop.has("serviceDay")) stop.optLong("serviceDay")*1000L else LocalDate.parse(day).atStartOfDay(Budapest).toInstant().toEpochMilli()
                Surface(shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.Top) {
                        Column(Modifier.width(70.dp)) {
                            Text(timeLabel(epoch+seconds*1000L),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                            val late=stop.optInt("departureDelay")/60
                            if(late!=0) Text("${if(late>0) "+" else ""}$late perc",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(s?.optString("name").orEmpty(),style=MaterialTheme.typography.titleMedium)
                            val platform=s?.optString("platformCode").orEmpty()
                            if(platform.isNotBlank() && platform!="null") Text("Vágány / kocsiállás: $platform",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if(stop.optBoolean("realtime")) "Élő adat · menetrend: ${timeLabel(epoch+scheduled*1000L)}" else "Menetrendi adat",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
