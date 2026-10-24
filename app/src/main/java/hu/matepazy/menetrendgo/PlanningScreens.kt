package hu.matepazy.menetrendgo

import androidx.compose.foundation.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable private fun StopField(label: String, value: Station?, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal=4.dp,vertical=12.dp).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
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
    var timePicker by remember { mutableStateOf(false) }
    var swaps by remember { mutableIntStateOf(0) }
    val swapRotation by animateFloatAsState(swaps*180f,
        tween(AppMotion.Reveal,easing=AppMotion.State),label="Swap stations")
    val focus=LocalFocusManager.current
    fun closePicker() { focus.clearFocus();picker=null }
    BackHandler(picker!=null) { closePicker() }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=screenContentPadding(),verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
        item { Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Hová indulunk?",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            FilledTonalIconButton(onClick=onPasses,shape=CircleShape) {Icon(Icons.Outlined.NotificationsNone,"Bérletek és emlékeztetők")}
        } }
        item {
            Glass(Modifier.fillMaxWidth()) {
                if(picker=="from") StationAutocomplete(vm,"Honnan?",vm.from,Icons.Outlined.TripOrigin,::closePicker) {
                    vm.from=it;vm.selectStation(it);closePicker()
                } else StopField("Honnan?",vm.from,Icons.Outlined.TripOrigin) { picker="from" }
                Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f),color=MaterialTheme.colorScheme.outlineVariant)
                    IconButton(onClick={ closePicker();val first=vm.from; vm.from=vm.to;vm.to=first;swaps++ }) {
                        Icon(Icons.Outlined.SwapVert,"Indulás és érkezés felcserélése",modifier=Modifier.graphicsLayer {rotationZ=swapRotation},tint=MaterialTheme.colorScheme.primary)
                    }
                }
                if(picker=="to") StationAutocomplete(vm,"Hová?",vm.to,Icons.Outlined.LocationOn,::closePicker) {
                    vm.to=it;vm.selectStation(it);closePicker()
                } else StopField("Hová?",vm.to,Icons.Outlined.LocationOn) { picker="to" }
                Spacer(Modifier.height(AppLayout.ItemGap))
                val today=LocalDate.now(Budapest)
                val selectedDate=if(vm.useNow) today else vm.dateTime.toLocalDate()
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    JourneyPickerControl("Dátum",when(selectedDate) {
                        today -> "Ma"
                        today.plusDays(1) -> "Holnap"
                        else -> selectedDate.format(DateTimeFormatter.ofPattern("MM. dd."))
                    },Icons.Outlined.CalendarToday,Modifier.weight(1f)) {closePicker();datePicker=true}
                    JourneyPickerControl(if(vm.arriveBy) "Érkezés" else "Indulás",
                        if(vm.useNow) "Most" else vm.dateTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        Icons.Outlined.Schedule,Modifier.weight(1f)) {closePicker();timePicker=true}
                }
                Spacer(Modifier.height(16.dp))
                MainButton("Járatok keresése",Icons.Outlined.Search,vm.from!=null && vm.to!=null && vm.from!=vm.to,onSearch)
            }
        }
        item { Column(verticalArrangement=Arrangement.spacedBy(AppLayout.CompactGap)) {
            Row(Modifier.fillMaxWidth().padding(top=AppLayout.SectionGap-AppLayout.ItemGap),verticalAlignment=Alignment.CenterVertically) {
                Text("Legutóbbi keresések",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                if(vm.recents.isNotEmpty()) TextButton(onClick=vm::clearRecents) { Text("Törlés") }
            }
            if(vm.recents.isEmpty()) Text("A kereséseidet itt találod majd. Egy koppintással újratervezheted az utat.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        items(vm.recents,key={it.from.id+":"+it.to.id}) { recent ->
            Surface(onClick={vm.from=recent.from;vm.to=recent.to;vm.useNow=true;vm.arriveBy=false;onSearch()},shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(AppLayout.PanelPadding),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.History,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)) {
                        Text(recent.from.name,style=MaterialTheme.typography.titleMedium)
                        Text(recent.to.name,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    };Icon(Icons.Outlined.ChevronRight,null)
                }
            }
        }
    }
    if(datePicker) JourneyDatePicker(vm) {datePicker=false}
    if(timePicker) JourneyTimePicker(vm) {timePicker=false}
}

@Composable private fun JourneyPickerControl(label: String, value: String, icon: ImageVector,
    modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,modifier=modifier) {
        Row(Modifier.padding(12.dp).heightIn(min=40.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value,style=MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun StationAutocomplete(vm: AppState, label: String, value: Station?, icon: ImageVector,
    onDismiss: () -> Unit, onSelected: (Station) -> Unit) {
    var input by rememberSaveable(label,stateSaver=TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value?.name.orEmpty(),TextRange(0,value?.name?.length ?: 0)))
    }
    val query=input.text.trim()
    val requester=remember {FocusRequester()}
    var revealSuggestions by remember {mutableStateOf(false)}
    val labelColor by animateColorAsState(if(revealSuggestions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(AppMotion.Feedback),label="Station focus label")
    val spotlight=LocalStationFocus.current
    val focusGutter=with(LocalDensity.current) {20.dp.toPx()}
    val dismiss by rememberUpdatedState(onDismiss)
    val keyboardVisible=WindowInsets.isImeVisible
    var keyboardWasVisible by remember {mutableStateOf(false)}
    LaunchedEffect(keyboardVisible) {
        if(keyboardVisible) keyboardWasVisible=true
        else if(keyboardWasVisible) dismiss()
    }
    DisposableEffect(label) {
        spotlight.dismiss={dismiss()}
        onDispose {spotlight.dismiss=null;spotlight.bounds=null}
    }
    var results by remember { mutableStateOf<List<Station>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember {mutableStateOf<String?>(null)}
    val listState=key(query) {rememberLazyListState()}
    LaunchedEffect(Unit) {requester.requestFocus();revealSuggestions=true}
    LaunchedEffect(query) {
        results=emptyList();error=null
        if(query.length>=2) {
            loading=true
            try {
                results=vm.cachedStations(query)
                delay(300)
                results=vm.stations(query)
            }
            catch(e: CancellationException){throw e}
            catch(e: Exception){error=if(results.isEmpty()) e.message ?: "A megállók nem tölthetők be. Próbáld újra." else "Mentett találatok · a frissítés nem sikerült."}
            finally {loading=false}
        } else loading=false
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface)
        .onGloballyPositioned {spotlight.bounds=it.boundsInWindow().inflate(focusGutter)}
        .semantics {paneTitle="Megállókeresés";dismiss {dismiss();true}}) {
        // Match StopField's geometry exactly: focus changes editing, never the text anchor.
        Row(Modifier.padding(horizontal=4.dp,vertical=12.dp).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(26.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label,style=MaterialTheme.typography.bodyMedium,color=labelColor)
                BasicTextField(input,{input=it},Modifier.fillMaxWidth().focusRequester(requester)
                    .semantics {contentDescription=label},singleLine=true,
                    textStyle=MaterialTheme.typography.titleMedium.copy(color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.SemiBold),
                    cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox={editor -> Box {
                        if(input.text.isEmpty()) Text("Megálló kiválasztása",style=MaterialTheme.typography.titleMedium,
                            fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface)
                        editor()
                    }},
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={onDismiss()}))
            }
            IconButton(onClick=onDismiss) {Icon(Icons.Outlined.Close,"Megállókeresés bezárása")}
        }
        AnimatedVisibility(revealSuggestions,
            enter=expandVertically(tween(AppMotion.Reveal,easing=AppMotion.Arrival),expandFrom=Alignment.Top)+fadeIn(tween(AppMotion.Feedback))) {
        Column(Modifier.fillMaxWidth().padding(bottom=12.dp)) {
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=8.dp))
        if(error!=null) Text(error!!,color=if(results.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=8.dp))
        if(!loading && query.length>=2 && results.isEmpty() && error==null) Text("Nincs ilyen megálló. Próbálj másik nevet.",Modifier.padding(vertical=12.dp))
        if(query.length<2) Text("Írj be legalább két karaktert.",Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(results.isNotEmpty()) {
            val rowHeight=with(LocalDensity.current) {MaterialTheme.typography.bodyLarge.lineHeight.toDp()*2+16.dp}.coerceAtLeast(56.dp)
            LazyColumn(Modifier.fillMaxWidth().height(rowHeight*minOf(4,results.size)),state=listState) {
                items(results,key={it.id}) {station ->
                    Surface(onClick={onSelected(station)},color=MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().height(rowHeight).padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                            val stationIcon=when {
                                station.name.endsWith('*') -> Icons.Outlined.LocationCity
                                "RAIL" in station.modes -> Icons.Outlined.Train
                                "COACH" in station.modes || "BUS" in station.modes -> Icons.Outlined.DirectionsBus
                                else -> Icons.Outlined.LocationOn
                            }
                            Icon(stationIcon,null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(station.name,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,
                                fontWeight=if(station.name.endsWith('*')) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines=2,overflow=TextOverflow.Ellipsis)
                            Icon(Icons.Outlined.ChevronRight,null,Modifier.padding(start=8.dp).size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun JourneyDatePicker(vm: AppState, dismiss: () -> Unit) {
    val initial=remember {if(vm.useNow) LocalDateTime.now(Budapest).withSecond(0).withNano(0) else vm.dateTime}
    val dateState = rememberDatePickerState(initialSelectedDateMillis=initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        shape=RoundedCornerShape(topStart=36.dp,topEnd=36.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom=24.dp)) {
            DatePicker(dateState,showModeToggle=false,
                colors=DatePickerDefaults.colors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow),
                title={Text("Utazás napja",Modifier.padding(24.dp),style=MaterialTheme.typography.titleLarge)})
            Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.End)) {
                TextButton(onClick=dismiss) {Text("Mégse")}
                Button(onClick={
                    dateState.selectedDateMillis?.let {millis ->
                        val day=Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.dateTime=day.atTime(initial.toLocalTime())
                        vm.useNow=vm.useNow && day==LocalDate.now(Budapest)
                        dismiss()
                    }
                },enabled=dateState.selectedDateMillis!=null) {Text("Kész")}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun JourneyTimePicker(vm: AppState, dismiss: () -> Unit) {
    val initial=remember {if(vm.useNow) LocalDateTime.now(Budapest).withSecond(0).withNano(0) else vm.dateTime}
    val timeState = rememberTimePickerState(initial.hour,initial.minute,is24Hour=true)
    var arrive by remember {mutableStateOf(vm.arriveBy)}
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        shape=RoundedCornerShape(topStart=36.dp,topEnd=36.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal=24.dp).padding(bottom=24.dp),
            verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
            Text("Utazás időpontja",style=MaterialTheme.typography.titleLarge)
            TimeInput(timeState,modifier=Modifier.align(Alignment.CenterHorizontally))
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(arrive,{arrive=it});Text("Ekkorra érkezzek meg")}
            TextButton(onClick={vm.useNow=true;vm.arriveBy=false;dismiss()}) {Text("Indulás most")}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.End)) {
                TextButton(onClick=dismiss) {Text("Mégse")}
                Button(onClick={
                    vm.dateTime=initial.toLocalDate().atTime(timeState.hour,timeState.minute)
                    vm.useNow=false;vm.arriveBy=arrive;dismiss()
                }) {Text("Kész")}
            }
        }
    }
}

/** Rail uses MÁV category colors; other services retain provider route colors. */
@Composable private fun serviceColor(leg: Leg): Color =
    serviceColorHex(leg).removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }
        ?.toLongOrNull(16)?.let { Color((0xFF000000L or it).toInt()) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant

private fun serviceIcon(leg: Leg): ImageVector = when(leg.mode) {
    "WALK" -> Icons.AutoMirrored.Outlined.DirectionsWalk
    "BUS", "COACH", "RAIL_REPLACEMENT_BUS" -> Icons.Outlined.DirectionsBus
    "TRAM", "TRAMTRAIN" -> Icons.Outlined.Tram
    "SUBURBAN_RAILWAY" -> Icons.Outlined.DirectionsTransit
    else -> Icons.Outlined.Train
}

private fun serviceType(leg: Leg): String = hungarianServiceName(leg.category, leg.mode)

@Composable fun RouteBadge(leg: Leg) {
    val color=serviceColor(leg)
    val foreground=if(color.luminance()>.179f) Color.Black else Color.White
    Surface(shape=CircleShape,color=color,contentColor=foreground,
        modifier=Modifier.semantics(mergeDescendants=true) { contentDescription="${serviceType(leg)} ${if(leg.walking) "" else leg.route}" }) {
        Row(Modifier.padding(start=6.dp,end=12.dp,top=5.dp,bottom=5.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(serviceIcon(leg),null,Modifier.padding(5.dp).size(18.dp),tint=foreground)
            Spacer(Modifier.width(7.dp))
            Text(if(leg.walking) "Gyalog" else leg.route,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
        }
    }
}

@Composable private fun JourneyEndpoints(journey: Journey) {
    val departure=journey.boardingLeg
    val arrival=journey.alightingLeg
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top) {
        Column(Modifier.weight(1f)) {
            ScheduledTime(departure.departureTime,style=MaterialTheme.typography.headlineMedium)
            Text(departure.from.name,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward,null,Modifier.padding(horizontal=12.dp,vertical=6.dp).size(24.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f),horizontalAlignment=Alignment.End) {
            ScheduledTime(arrival.arrivalTime,style=MaterialTheme.typography.headlineMedium,alignment=Alignment.End)
            Text(arrival.to.name,style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.End,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(dayLabel(arrival.end)!=dayLabel(departure.start)) Text(dayLabel(arrival.end),style=MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable fun ResultsScreen(vm: AppState, back: () -> Unit, select: (Journey) -> Unit) {
    var filter by rememberSaveable {mutableStateOf("Összes")}
    val results=vm.journeys.filter { j -> filter=="Összes" || j.legs.any {if(filter=="Vonat") it.mode in listOf("RAIL","SUBURBAN_RAILWAY","TRAMTRAIN") else it.mode in listOf("BUS","COACH","RAIL_REPLACEMENT_BUS")} }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=screenContentPadding(),verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
        item { PageTitle("Indulások", "${vm.from?.name.orEmpty()} → ${vm.to?.name.orEmpty()}",back) }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(AppLayout.CompactGap),verticalArrangement=Arrangement.spacedBy(AppLayout.CompactGap)) { listOf("Összes","Vonat","Busz").forEach { label -> FilterChip(selected=filter==label,onClick={filter=label},label={Text(label)},shape=CircleShape,modifier=Modifier.heightIn(min=48.dp)) } } }
        if(vm.searchError!=null) item {InfoPanel("Nem sikerült a keresés",vm.searchError!!){vm.search(vm.journeys.isNotEmpty())}}
        if(vm.searching && vm.journeys.isEmpty()) item {LoadingRows()}
        if(!vm.searching && vm.searchError==null && results.isEmpty()) item {InfoPanel("Nincs megjeleníthető indulás","Módosítsd az időpontot, a megállókat vagy a járműszűrőt.")}
        items(results,key={it.key}) { journey ->
            Surface(onClick={select(journey)},shape=RoundedCornerShape(32.dp),color=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
                Column(Modifier.fillMaxWidth().padding(AppLayout.PanelPadding),verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        val services=journey.legs.filterNot {it.walking}.distinctBy {it.mode to it.route}
                        FlowRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            (services.ifEmpty {journey.legs.take(1)}).forEach {RouteBadge(it)}
                        }
                        Icon(Icons.Outlined.ChevronRight,null,Modifier.padding(start=8.dp))
                    }
                    JourneyEndpoints(journey)
                    Text(listOf(dayLabel(journey.boardingLeg.start),"${journey.serviceMinutes} perc",if(journey.changes==0) "Közvetlen" else "${journey.changes} átszállás").filter {it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(vm.passes.isNotEmpty()) Text(passAdvice(vm.passes,journey,vm.passGeography).title,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                }
            }
        }
        if(vm.searching && vm.journeys.isNotEmpty()) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(vm.canLoadMore && !vm.searching) item {OutlinedButton(onClick={vm.search(true)},shape=CircleShape,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(if(vm.arriveBy) "Korábbi indulások" else "További indulások")}}
        item {Text("A találatokat a MÁV útvonaltervezője adja. Az újabb keresés további időpontokat tölt be.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

/** A continuous stop/time column with solid transit lines and dotted walking connections. */
@Composable private fun TimelineRow(time: ServiceTime?, color: Color, stop: Boolean=false, first: Boolean=false, last: Boolean=false,
    walking: Boolean=false, content: @Composable ColumnScope.() -> Unit) {
    val surface=MaterialTheme.colorScheme.surface
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(with(LocalDensity.current) { 64.sp.toDp() }).padding(top=2.dp,end=8.dp)) {
            time?.let {ScheduledTime(it,style=MaterialTheme.typography.labelLarge)}
        }
        Canvas(Modifier.width(24.dp).fillMaxHeight()) {
            val x=size.width/2
            val nodeY=12.dp.toPx()
            drawLine(color,Offset(x,if(first) nodeY else 0f),Offset(x,if(last) nodeY else size.height),
                strokeWidth=if(walking) 3.dp.toPx() else 4.dp.toPx(),cap=StrokeCap.Round,
                pathEffect=if(walking) PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(),6.dp.toPx())) else null)
            if(stop) {
                drawCircle(color,6.dp.toPx(),Offset(x,nodeY))
                drawCircle(surface,3.dp.toPx(),Offset(x,nodeY))
            }
        }
        Column(Modifier.weight(1f).padding(start=12.dp,bottom=24.dp),content=content)
    }
}

@Composable fun JourneyScreen(vm: AppState, journey: Journey, back: () -> Unit, onTrip: (Leg) -> Unit, onMap: () -> Unit) {
    var menuOpen by remember(journey.key) { mutableStateOf(false) }
    var rawOpen by remember(journey.key) { mutableStateOf(false) }
    val journeyVehicles=routeVehicles(journey,vm.vehicles,System.currentTimeMillis()).associateBy {it.id}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=screenContentPadding(),verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
        item {PageTitle("Utazás részletei", listOf(dayLabel(journey.start),"${journey.minutes} perc").filter {it.isNotBlank()}.joinToString(" · "),back) {
            if(vm.showRawGraphQl) Box {
                IconButton(onClick={menuOpen=true}) {Icon(Icons.Outlined.MoreVert,"További lehetőségek")}
                DropdownMenu(expanded=menuOpen,onDismissRequest={menuOpen=false}) {
                    DropdownMenuItem(text={Text("Nyers adatok")},onClick={menuOpen=false;rawOpen=true})
                }
            }
        }}
        item { MainButton("Útvonal a térképen",Icons.Outlined.Map,onClick=onMap) }
        item {
            Surface(shape=RoundedCornerShape(32.dp),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(AppLayout.PanelPadding)) {
                    journey.legs.forEachIndexed {index,leg ->
                        val color=serviceColor(leg)
                        val walking=leg.walking
                        TimelineRow(leg.departureTime,color,stop=true,first=index==0,walking=walking) {
                            Text(leg.from.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                            if(index>0 && dayLabel(leg.start)!=dayLabel(journey.start)) Text(dayLabel(leg.start),style=MaterialTheme.typography.labelMedium)
                        }
                        TimelineRow(null,color,walking=walking) {
                            if(walking) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Outlined.DirectionsWalk,null,Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("${(leg.end-leg.start+59_999)/60_000} perc gyaloglás",style=MaterialTheme.typography.bodyMedium)
                                }
                            } else {
                                RouteBadge(leg)
                                Text("${serviceType(leg)} · ${((leg.end-leg.start+59_999)/60_000)} perc",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium)
                                journeyVehicles[leg.tripId]?.takeIf {it.modelAndRegistration.isNotBlank()}?.let {vehicle ->
                                    VehicleModelLabel(vehicle,Modifier.padding(top=AppLayout.LabelGap))
                                }
                                if(leg.headsign.isNotBlank()) Text("Irány: ${leg.headsign}",Modifier.padding(top=4.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                if(!leg.realTime) Text("Menetrend szerint · élő adat nem érhető el",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                var expanded by remember(journey.key,index) { mutableStateOf(false) }
                                if(leg.intermediate.isNotEmpty()) {
                                    val disclosureRotation by animateFloatAsState(if(expanded) 180f else 0f,
                                        tween(AppMotion.Reveal,easing=AppMotion.State),label="Intermediate stops")
                                    TextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(horizontal=0.dp),modifier=Modifier.heightIn(min=48.dp)
                                        .semantics {stateDescription=if(expanded) "Kibontva" else "Összecsukva"}) {
                                        Text("${leg.intermediate.size} köztes megálló",Modifier.weight(1f))
                                        Icon(Icons.Outlined.ExpandMore,if(expanded) "Megállók elrejtése" else "Megállók megjelenítése",
                                            Modifier.graphicsLayer {rotationZ=disclosureRotation})
                                    }
                                    AnimatedVisibility(expanded,
                                        enter=expandVertically(tween(AppMotion.Reveal,easing=AppMotion.Arrival),expandFrom=Alignment.Top)+fadeIn(tween(AppMotion.Feedback)),
                                        exit=shrinkVertically(tween(AppMotion.Dismiss,easing=AppMotion.State),shrinkTowards=Alignment.Top)+fadeOut(tween(AppMotion.Dismiss))) {
                                    Column { leg.intermediate.forEach {stop ->
                                        Text(stop.name,Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    } }
                                    }
                                }
                                if(leg.tripId.isNotBlank()) TextButton(onClick={onTrip(leg)},contentPadding=PaddingValues(horizontal=0.dp),modifier=Modifier.heightIn(min=48.dp)) {
                                    Text("Teljes járatmenetrend",Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null)
                                }
                            }
                        }
                        val next=journey.legs.getOrNull(index+1)
                        // A shared stop at the same time is one interchange node, not two repeated rows.
                        val sharedStop=next!=null && next.from.name==leg.to.name && next.start==leg.end && next.departureTime==leg.arrivalTime
                        if(!sharedStop) TimelineRow(leg.arrivalTime,color,stop=true,last=next==null,walking=next?.walking ?: walking) {
                            Text(leg.to.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                            if(dayLabel(leg.end)!=dayLabel(journey.start)) Text(dayLabel(leg.end),style=MaterialTheme.typography.labelMedium)
                        }
                        if(next!=null) {
                            val wait=(next.start-leg.end)/60_000
                            if(wait>=1) TimelineRow(null,MaterialTheme.colorScheme.onSurfaceVariant,walking=true) {
                                Text("$wait perc várakozás",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {val advice=passAdvice(vm.passes,journey,vm.passGeography);InfoPanel(advice.title,advice.detail)}
    }
    if(vm.showRawGraphQl && rawOpen) RawGraphQlSheet(journey.rawGraphQl) {rawOpen=false}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RawGraphQlSheet(raw: String?, dismiss: () -> Unit) {
    val context=LocalContext.current
    var copied by remember(raw) {mutableStateOf(false)}
    var lines by remember(raw) {mutableStateOf<List<String>?>(null)}
    LaunchedEffect(raw) {
        lines=raw?.let {response -> withContext(Dispatchers.Default) {
            // Only presentation is formatted. Copy always uses the exact received body.
            runCatching {JSONObject(response).toString(2)}.getOrDefault(response).lines()
        }}
    }
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("Nyers adatok",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                TextButton(enabled=raw!=null,onClick={
                    context.getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(android.content.ClipData.newPlainText("GraphQL-válasz",raw))
                    copied=true
                }) {Text(if(copied) "Másolva" else "Másolás")}
                IconButton(onClick=dismiss) {Icon(Icons.Outlined.Close,"Nyers adatok bezárása")}
            }
            Text("A kiválasztott utazást tartalmazó teljes GraphQL-válasz. A behúzás az olvashatóságot segíti; másoláskor az eredeti választ kapod.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                raw==null -> InfoPanel("Nincs eltárolt nyers válasz","Indíts új keresést, majd nyisd meg újra az utazás részleteit.")
                lines==null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> SelectionContainer(Modifier.weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
                        items(lines.orEmpty()) {line ->
                            Text(line,style=MaterialTheme.typography.bodySmall,fontFamily=FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable fun TripScreen(vm: AppState, id: String, day: String, back: () -> Unit, onMap: () -> Unit) {
    val detail=vm.detail
    val vehicle=vm.vehicles.find {it.id==id}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=screenContentPadding(),verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
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
                Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(AppLayout.PanelPadding),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(AppLayout.RelatedGap)) {
                        Column(Modifier.width(with(LocalDensity.current) { 64.sp.toDp() })) {
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
