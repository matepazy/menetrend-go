package hu.matepazy.menetrendgo

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.util.lerp
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
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import org.json.JSONObject
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable private fun StopField(label: String, value: Station?, icon: ImageVector, modifier: Modifier = Modifier,
    onClear: () -> Unit, onClick: () -> Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface,modifier=modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal=4.dp,vertical=AppLayout.RelatedGap).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(26.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value?.name ?: "Megálló kiválasztása",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            }
            if(value!=null) StationClearButton(label,onClear)
        }
    }
}

@Composable private fun StationClearButton(label: String, onClick: () -> Unit) {
    IconButton(onClick=onClick,modifier=Modifier.padding(start=AppLayout.CompactGap).size(48.dp)) {
        Icon(Icons.Outlined.Close,"$label mező törlése",modifier=Modifier.size(20.dp),
            tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun JourneyStopField(vm: AppState, label: String, value: Station?, icon: ImageVector,
    active: Boolean, onOpen: () -> Unit, onDismiss: () -> Unit, onClear: () -> Unit,
    allowCurrentLocation: Boolean = false, onSelected: (Station) -> Unit) {
    val spotlight=LocalStationFocus.current
    val focusGutter=with(LocalDensity.current) {AppLayout.PanelPadding.toPx()}
    Box(Modifier.fillMaxWidth()
        // Track the visible, animated bounds so the scrim follows the editor's reveal.
        .onGloballyPositioned {if(active) spotlight.bounds=it.boundsInWindow().inflate(focusGutter)}
        .animateContentSize(tween(AppMotion.StationFocus,easing=AppMotion.Arrival),alignment=Alignment.TopStart)) {
        if(active) StationAutocomplete(vm,label,value,icon,onDismiss,onSelected,onClear,allowCurrentLocation)
        else StopField(label,value,icon,Modifier.padding(end=48.dp+AppLayout.CompactGap),onClear,onOpen)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SearchScreen(vm: AppState, onSearch: () -> Unit) =
    SearchScreen(vm, {}, onSearch)

@Composable fun SearchScreen(vm: AppState, onTracking: (Journey) -> Unit, onSearch: () -> Unit) {
    var showAlerts by rememberSaveable { mutableStateOf(false) }
    if(showAlerts) NetworkAlertsDrawer(vm) { showAlerts=false }
    val tracking by JourneyTracking.state.collectAsState()
    val trackingContext = LocalContext.current
    LaunchedEffect(Unit) { JourneyTracking.load(trackingContext) }
    var picker by remember { mutableStateOf<String?>(null) }
    var datePicker by remember { mutableStateOf(false) }
    var timePicker by remember { mutableStateOf(false) }
    var searchOptions by rememberSaveable { mutableStateOf(false) }
    var editingSaved by remember { mutableStateOf<SavedSearch?>(null) }
    var swaps by remember { mutableIntStateOf(0) }
    val swapRotation by animateFloatAsState(swaps*180f,
        tween(AppMotion.Reveal,easing=AppMotion.State),label="Swap stations")
    val focus=LocalFocusManager.current
    fun closePicker() { focus.clearFocus();picker=null }
    BackHandler(picker!=null) { closePicker() }
    var confirmClearRecents by remember { mutableStateOf(false) }
    val swipeHost = remember { SwipeHost() }
    // 0 = Mentett, 1 = Legutóbbi; a finger drives it live, taps and releases settle it with a spring.
    val collectionScope = rememberCoroutineScope()
    val collection = remember { CollectionPager(if(vm.showSavedSearches) 0f else 1f,collectionScope) }
    LaunchedEffect(vm.showSavedSearches) { collection.settle(if(vm.showSavedSearches) 0f else 1f) }
    LazyColumn(Modifier.fillMaxSize().swipeHostDismiss(swipeHost).swipeBetweenTabs(swipeHost,collection) { vm.chooseSearchCollection(it) },
        // Keep suggestion drags and flings from scrolling the page, including at the list edges.
        userScrollEnabled=picker==null,
        contentPadding=screenContentPadding(),verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
        item { Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("Hová indulunk?",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            FilledTonalIconButton(onClick={showAlerts=true},shape=CircleShape) {Icon(Icons.Outlined.Campaign,"Forgalmi hírek")}
        } }
        item {
            Glass(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        JourneyStopField(vm,"Honnan?",vm.from,Icons.Outlined.TripOrigin,picker=="from",{picker="from"},::closePicker,{vm.from=null},allowCurrentLocation=true) {
                            vm.from=it;vm.selectStation(it);closePicker()
                        }
                        HorizontalDivider(Modifier.padding(start=46.dp,end=48.dp+AppLayout.ItemGap,top=AppLayout.LabelGap,bottom=AppLayout.LabelGap),
                            color=if(picker==null) MaterialTheme.colorScheme.outlineVariant else Color.Transparent)
                        JourneyStopField(vm,"Hová?",vm.to,Icons.Outlined.LocationOn,picker=="to",{picker="to"},::closePicker,{vm.to=null}) {
                            vm.to=it;vm.selectStation(it);closePicker()
                        }
                    }
                    if(picker==null) IconButton(modifier=Modifier.align(Alignment.CenterEnd),onClick={ closePicker();val first=vm.from; vm.from=vm.to;vm.to=first;swaps++ }) {
                        Icon(Icons.Outlined.SwapVert,"Indulás és érkezés felcserélése",modifier=Modifier.graphicsLayer {rotationZ=swapRotation},tint=MaterialTheme.colorScheme.primary)
                    }
                }
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick={closePicker();searchOptions=true},shape=CircleShape,
                        modifier=Modifier.size(58.dp).semantics {
                            stateDescription=if(vm.searchConditionCount == 0) "Nincs aktív feltétel" else "${vm.searchConditionCount} aktív feltétel"
                        }) {
                        BadgedBox(badge={if(vm.searchConditionCount > 0) Badge {Text(vm.searchConditionCount.toString())}}) {
                            Icon(Icons.Outlined.Settings,"Keresési feltételek")
                        }
                    }
                    Box(Modifier.weight(1f)) {MainButton("Járatok keresése",Icons.Outlined.Search,vm.from!=null && vm.to!=null && vm.from!=vm.to,onSearch)}
                }
                if(vm.searchConditionCount > 0) Text(vm.searchConditionSummary,
                    Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (vm.journeyTrackingBetaEnabled && tracking.session != null) item { ActiveJourneyTracking(onTracking) }
        item { Column(verticalArrangement=Arrangement.spacedBy(AppLayout.RelatedGap)) {
            SearchCollectionTabs(collection,Modifier.padding(top=AppLayout.SectionGap-AppLayout.ItemGap).onGloballyPositioned { swipeHost.tabTop=it.positionInWindow().y }) { vm.chooseSearchCollection(it) }
            // Saved sits on the left, recents on the right; both panes follow the same progress value, so a finger can drag them.
            CollectionPanes(collection,vm.showSavedSearches) { saved ->
                Column(Modifier.fillMaxWidth().animateContentSize(),verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
                    if(saved) {
                        if(vm.savedSearches.isEmpty()) Column(verticalArrangement=Arrangement.spacedBy(AppLayout.CompactGap)) {
                            Text("A gyakori útjaid helye",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                            Text("Az Indulások jobb felső sarkában lévő könyvjelzővel menthetsz útvonalat. Adj neki nevet és ikont, hogy könnyen megtaláld.",
                                style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        vm.savedSearches.forEach { s -> key("saved:"+s.key) { SavedSearchRow(vm,s,swipeHost,onSearch) {editingSaved=it} } }
                    } else {
                        if(vm.recents.isEmpty()) Text("A kereséseidet itt találod majd. Egy koppintással újratervezheted az utat.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        vm.recents.forEach { r -> key("recent:"+r.from.id+":"+r.to.id) { RecentSearchRow(vm,r,swipeHost,onSearch) } }
                        if(vm.recents.isNotEmpty()) TextButton(onClick={confirmClearRecents=true},modifier=Modifier.fillMaxWidth()) { Text("Előzmények törlése") }
                    }
                }
            }
        } }
    }
    if(confirmClearRecents) ClearRecentsDrawer({vm.clearRecents();confirmClearRecents=false}) {confirmClearRecents=false}
    if(datePicker) JourneyDatePicker(vm) {datePicker=false}
    if(timePicker) JourneyTimePicker(vm) {timePicker=false}
    if(searchOptions) SearchFilterDrawer(vm) {searchOptions=false}
    editingSaved?.let { saved ->
        SavedSearchEditor(saved.from,saved.to,saved,
            onSave={name,icon -> vm.saveSearch(saved.from,saved.to,name,icon);editingSaved=null},
            onDismiss={editingSaved=null},onDelete={vm.removeSavedSearch(saved);editingSaved=null})
    }
}

/** Right-to-left swipe parks the row on a red delete action; a tap on the trash icon deletes, a tap on the row closes it. */
private class SwipeHost {
    var closeOpen: (() -> Unit)? = null
    var deleteBounds: androidx.compose.ui.geometry.Rect? = null
    var origin = Offset.Zero
    var tabTop = Float.NEGATIVE_INFINITY
}

@Composable private fun SavedSearchRow(vm: AppState, saved: SavedSearch, swipeHost: SwipeHost, onSearch: () -> Unit, onEdit: (SavedSearch) -> Unit) {
    SwipeActionsRow(listOf(
        SwipeAction(Icons.Outlined.Edit,"${saved.name} szerkesztése",MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.onPrimary) {onEdit(saved)},
        SwipeAction(Icons.Outlined.Delete,"${saved.name} törlése",MaterialTheme.colorScheme.error,MaterialTheme.colorScheme.onError) {vm.removeSavedSearch(saved)}),
        swipeHost) { open,close ->
        Surface(onClick={if(open) close() else {vm.useSavedSearch(saved);onSearch()}},shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(start=AppLayout.PanelPadding,top=12.dp,bottom=12.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(saved.icon.vector,null,tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(saved.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    Text("${saved.from.name} → ${saved.to.name}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(!open) IconButton(onClick={onEdit(saved)}) {Icon(Icons.Outlined.Edit,"${saved.name} szerkesztése")}
                else Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable private fun RecentSearchRow(vm: AppState, recent: Recent, swipeHost: SwipeHost, onSearch: () -> Unit) {
    SwipeToDeleteRow("${recent.from.name} – ${recent.to.name}",{vm.removeRecent(recent)},swipeHost) { open,close ->
        Surface(onClick={if(open) close() else {vm.from=recent.from;vm.to=recent.to;vm.useNow=true;vm.arriveBy=false;onSearch()}},shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface) {
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

/** Any touch that starts outside the open row's delete button springs the open row back; the touch itself still reaches its target. */
private fun Modifier.swipeHostDismiss(host: SwipeHost) = onGloballyPositioned { host.origin = it.positionInWindow() }.pointerInput(host) {
    awaitPointerEventScope {
        while(true) {
            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            val down = event.changes.firstOrNull { it.changedToDown() } ?: continue
            if(host.deleteBounds?.contains(down.position + host.origin) != true) host.closeOpen?.invoke()
        }
    }
}

/**
 * Position of the saved/recent panes: 0 = Mentett, 1 = Legutóbbi. Drags write it synchronously, so no finger
 * movement is dropped; a release or a tab tap hands it to one spring that keeps the fling velocity.
 */
private class CollectionPager(initial: Float, private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(initial)
        private set
    /** One page step in pixels: a pane plus the gap that separates the two panes. */
    var pageStep = 1f
    private var target = initial
    private var job: Job? = null
    fun drag(dx: Float) { job?.cancel();job = null;progress = (progress - dx / pageStep).coerceIn(0f,1f) }
    fun settle(to: Float, velocity: Float = 0f) {
        if(to == target && job?.isActive == true) return
        target = to;job?.cancel()
        job = scope.launch { animate(progress,to,velocity,spring(Spring.DampingRatioNoBouncy,Spring.StiffnessMediumLow)) { v, _ -> progress = v } }
    }
}

/** A horizontal drag no row claimed, started at or below the tab selector, moves the panes under the finger and settles on the nearer one (or the flung one) on release. */
private fun Modifier.swipeBetweenTabs(host: SwipeHost, pager: CollectionPager, choose: (Boolean) -> Unit) = pointerInput(host,pager) {
    var armed = false
    val tracker = VelocityTracker()
    val flingVelocity = 600.dp.toPx()
    fun release(velocityX: Float) {
        if(!armed) return
        armed = false
        val saved = if(kotlin.math.abs(velocityX) > flingVelocity) velocityX > 0f else pager.progress < 0.5f
        pager.settle(if(saved) 0f else 1f,-velocityX / pager.pageStep)
        choose(saved)
    }
    detectHorizontalDragGestures(
        onDragStart={ armed = it.y + host.origin.y >= host.tabTop;tracker.resetTracking() },
        onDragEnd={ release(tracker.calculateVelocity().x) },
        onDragCancel={ release(0f) }) { change, dx ->
        if(!armed) return@detectHorizontalDragGestures
        tracker.addPosition(change.uptimeMillis,change.position);change.consume()
        pager.drag(dx)
    }
}

/** Both panes sit side by side, a full gutter pair apart like separate pages; the height eases between theirs as they move. */
@Composable private fun CollectionPanes(pager: CollectionPager, showSaved: Boolean, pane: @Composable (saved: Boolean) -> Unit) {
    val gap = with(LocalDensity.current) { (AppLayout.PageGutter * 2).roundToPx() }
    Layout({
        Box(if(showSaved) Modifier else Modifier.clearAndSetSemantics {}) { pane(true) }
        Box(if(showSaved) Modifier.clearAndSetSemantics {} else Modifier) { pane(false) }
    },Modifier.fillMaxWidth()) { measurables, c ->
        val w = c.maxWidth;val step = w + gap;pager.pageStep = step.toFloat()
        val placeables = measurables.map { it.measure(androidx.compose.ui.unit.Constraints.fixedWidth(w)) }
        val p = pager.progress
        layout(w,lerp(placeables[0].height.toFloat(),placeables[1].height.toFloat(),p).toInt()) {
            placeables[0].placeRelative((-p * step).toInt(),0)
            placeables[1].placeRelative(((1f - p) * step).toInt(),0)
        }
    }
}

/**
 * The bottom navigation's anatomy at list scale: a milky rounded bar holding two separated pill tabs, with a
 * pale-blue selection pill that travels between them in step with the panes and tints whichever tab it covers.
 */
@Composable private fun SearchCollectionTabs(pager: CollectionPager, modifier: Modifier = Modifier, onSelect: (saved: Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tabs = listOf("Mentett" to Icons.Outlined.BookmarkBorder,"Legutóbbi" to Icons.Outlined.History)
    val gap = 6.dp
    // Same squash as the bottom navigation: the pressed tab dips to 94% and springs back.
    val presses = remember { List(2) { MutableInteractionSource() } }
    val scales = presses.mapIndexed { index, press ->
        val pressed by press.collectIsPressedAsState()
        animateFloatAsState(if(pressed) .94f else 1f,spring(Spring.DampingRatioMediumBouncy,Spring.StiffnessMedium),label="Collection tab press $index")
    }
    Surface(modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,color=colors.surface) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(6.dp)) {
            val tabWidth = (maxWidth - gap) / 2
            Box(Modifier.offset { androidx.compose.ui.unit.IntOffset((pager.progress * (tabWidth + gap).toPx()).toInt(),0) }
                .width(tabWidth).height(44.dp)
                // The pill squashes with whichever tab it covers, so a press on the selected tab moves as one piece.
                .graphicsLayer { val p=pager.progress.coerceIn(0f,1f); val s=scales[0].value*(1f-p)+scales[1].value*p; scaleX=s;scaleY=s }
                .clip(RoundedCornerShape(26.dp)).background(colors.primaryContainer.copy(alpha=.65f)))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(gap)) {
                tabs.forEachIndexed { index, (label, icon) ->
                    val on = (if(index == 0) 1f - pager.progress else pager.progress).coerceIn(0f,1f)
                    val tint = androidx.compose.ui.graphics.lerp(colors.onSurfaceVariant,colors.primary,on)
                    Row(Modifier.weight(1f).height(44.dp).graphicsLayer { scaleX=scales[index].value;scaleY=scales[index].value }.clip(RoundedCornerShape(26.dp))
                        // The travelling pill is the press feedback; a grey ripple on top of it would muddy the selection.
                        .selectable(selected=on > .5f,interactionSource=presses[index],indication=null,role=Role.Tab,onClick={ onSelect(index == 0) }),
                        horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                        Icon(icon,null,Modifier.size(20.dp),tint=tint)
                        Spacer(Modifier.width(AppLayout.CompactGap))
                        Text(label,style=MaterialTheme.typography.labelLarge,color=tint,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private class SwipeAction(val icon: ImageVector, val description: String, val container: Color, val content: Color, val onClick: () -> Unit)

@Composable private fun SwipeToDeleteRow(label: String, onDelete: () -> Unit, host: SwipeHost, modifier: Modifier = Modifier,
    content: @Composable (open: Boolean, close: () -> Unit) -> Unit) = SwipeActionsRow(
    listOf(SwipeAction(Icons.Outlined.Delete,"$label törlése",MaterialTheme.colorScheme.error,MaterialTheme.colorScheme.onError,onDelete)),host,modifier,content)

/** Actions are listed left to right once revealed; the last one sits at the trailing edge. */
@Composable private fun SwipeActionsRow(actions: List<SwipeAction>, host: SwipeHost, modifier: Modifier = Modifier,
    content: @Composable (open: Boolean, close: () -> Unit) -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val reveal = with(LocalDensity.current) { 72.dp.toPx() * actions.size }
    val offset = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    // Stays "open" for a beat after closing so the tap that dismissed the row cannot also start its search.
    val close: () -> Unit = { scope.launch { closing = true;offset.animateTo(0f,tween(150));delay(250);closing = false } }
    val open = offset.value < 0f
    LaunchedEffect(open) {
        if(open) host.closeOpen?.takeIf { it !== close }?.invoke()
        if(open) host.closeOpen = close else if(host.closeOpen === close) { host.closeOpen = null;host.deleteBounds = null }
    }
    DisposableEffect(Unit) { onDispose { if(host.closeOpen === close) { host.closeOpen = null;host.deleteBounds = null } } }
    Box(modifier.fillMaxWidth().clip(shape)) {
        if(offset.value < 0f) Row(Modifier.matchParentSize().onGloballyPositioned {
            val b = it.boundsInWindow();host.deleteBounds = androidx.compose.ui.geometry.Rect(b.right - reveal,b.top,b.right,b.bottom)
        }.background(actions.first().container),horizontalArrangement=Arrangement.End) {
            // The first action's colour fills the whole row so it shows behind the card's rounded corners.
            actions.forEach { a ->
                Box(Modifier.width(72.dp).fillMaxHeight().background(a.container),contentAlignment=Alignment.Center) {
                    IconButton(onClick={ close();a.onClick() },Modifier.fillMaxSize()) { Icon(a.icon,a.description,tint=a.content) }
                }
            }
        }
        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(offset.value.toInt(),0) }
            .draggable(rememberDraggableState { d -> scope.launch { offset.snapTo((offset.value + d).coerceIn(-reveal,0f)) } },Orientation.Horizontal,
                onDragStopped={ v -> scope.launch { offset.animateTo(if(offset.value < -reveal / 2 || v < -800f) -reveal else 0f,tween(150)) } })) {
            content(open || closing,close)
        }
    }
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

@Composable private fun StationSearchIcon(station: Station) {
    val town = station.type == "MetaStation" || station.name.endsWith('*')
    // Reuse the original, full-color MÁVPlusz artwork bundled for the mode filters.
    // A town groups several stops; absent mode data must not imply a vehicle type.
    val drawable = when {
        town -> null
        "TRAMTRAIN" in station.modes -> R.drawable.ic_mode_tramtrain
        "SUBURBAN_RAILWAY" in station.modes -> R.drawable.ic_mode_hev
        "RAIL" in station.modes -> R.drawable.ic_mode_rail
        "RAIL_REPLACEMENT_BUS" in station.modes -> R.drawable.ic_mode_replacement_bus
        "COACH" in station.modes -> R.drawable.ic_mode_coach
        "BUS" in station.modes -> R.drawable.ic_mode_local_bus
        else -> null
    }
    if (drawable != null) {
        Image(painterResource(drawable), contentDescription = when (drawable) {
            R.drawable.ic_mode_tramtrain -> "TramTrain-megálló"
            R.drawable.ic_mode_hev -> "HÉV-megálló"
            R.drawable.ic_mode_rail -> "Vasútállomás"
            R.drawable.ic_mode_replacement_bus -> "Vonatpótló autóbusz-megálló"
            R.drawable.ic_mode_coach -> "Helyközi autóbusz-megálló"
            else -> "Helyi autóbusz-megálló"
        }, modifier = Modifier.size(28.dp))
    } else {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Icon(if (town) Icons.Outlined.LocationCity else Icons.Outlined.LocationOn,
                if (town) "Település összes megállója" else null,
                Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun StationAutocomplete(vm: AppState, label: String, value: Station?, icon: ImageVector,
    onDismiss: () -> Unit, onSelected: (Station) -> Unit, onClear: () -> Unit, allowCurrentLocation: Boolean = false) {
    var input by rememberSaveable(label,stateSaver=TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value?.name.orEmpty(),TextRange(0,value?.name?.length ?: 0)))
    }
    val query=input.text.trim()
    val requester=remember {FocusRequester()}
    var revealSuggestions by remember {mutableStateOf(false)}
    val labelColor by animateColorAsState(if(revealSuggestions) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(AppMotion.Feedback),label="Station focus label")
    val spotlight=LocalStationFocus.current
    val dismiss by rememberUpdatedState(onDismiss)
    val keyboardVisible=WindowInsets.isImeVisible
    var keyboardWasVisible by remember {mutableStateOf(false)}
    var locating by remember {mutableStateOf(false)}
    LaunchedEffect(keyboardVisible,locating) {
        if(keyboardVisible) keyboardWasVisible=true
        else if(keyboardWasVisible && !locating) dismiss()
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
        .semantics {paneTitle="Megállókeresés";dismiss {dismiss();true}}) {
        // Match StopField's geometry exactly: focus changes editing, never the text anchor.
        Row(Modifier.padding(horizontal=4.dp,vertical=AppLayout.RelatedGap).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
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
            if(input.text.isNotEmpty() || value!=null) StationClearButton(label) {
                input=TextFieldValue("")
                onClear()
                requester.requestFocus()
            }
            IconButton(onClick=onDismiss,modifier=Modifier.padding(start=AppLayout.CompactGap).size(48.dp)) {
                Icon(Icons.Outlined.KeyboardArrowUp,"Megállókeresés bezárása")
            }
        }
        AnimatedVisibility(revealSuggestions,
            enter=fadeIn(tween(AppMotion.Feedback))) {
        Column(Modifier.fillMaxWidth().padding(bottom=12.dp)) {
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        if(allowCurrentLocation) CurrentLocationOption(onSelected=onSelected,onBusyChanged={
            locating=it
            // The system permission prompt may hide the IME; keep the picker alive.
            keyboardWasVisible=false
        })
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
                            StationSearchIcon(station)
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
@Composable private fun ClearRecentsDrawer(onConfirm: () -> Unit, dismiss: () -> Unit) {
    AppBottomSheet(onDismissRequest=dismiss,shape=RoundedCornerShape(topStart=36.dp,topEnd=36.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
            Text("Törlöd az előzményeket?",style=MaterialTheme.typography.titleLarge)
            Text("Az összes legutóbbi keresés törlődik. A mentett kereséseid megmaradnak.",
                style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.End)) {
                TextButton(onClick=dismiss) {Text("Mégse")}
                Button(onClick=onConfirm,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError)) {Text("Törlés")}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun JourneyDatePicker(vm: AppState, dismiss: () -> Unit) {
    val initial=remember {if(vm.useNow) LocalDateTime.now(Budapest).withSecond(0).withNano(0) else vm.dateTime}
    val dateState = rememberDatePickerState(initialSelectedDateMillis=initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    AppBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
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
                        vm.dateTime=day.atStartOfDay()
                        vm.useNow=false
                        dismiss()
                    }
                },enabled=dateState.selectedDateMillis!=null) {Text("Kész")}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun JourneyTimePicker(vm: AppState, dismiss: () -> Unit) {
    val initial=remember {if(vm.useNow) LocalDateTime.now(Budapest).withSecond(0).withNano(0) else vm.dateTime}
    val timeState = rememberTimePickerState(initial.hour,initial.minute,is24Hour=true)
    var arrive by rememberSaveable {mutableStateOf(vm.arriveBy)}
    AppBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        shape=RoundedCornerShape(topStart=36.dp,topEnd=36.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal=24.dp).padding(bottom=24.dp),
            verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
            Text("Utazás időpontja",style=MaterialTheme.typography.titleLarge)
            JourneyTimeModeSelector(arrive,onSelect={arrive=it})
            TimePicker(timeState,modifier=Modifier.align(Alignment.CenterHorizontally),
                layoutType=TimePickerLayoutType.Vertical)
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.End),
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={vm.useNow=true;vm.arriveBy=false;dismiss()}) {Text("Indulás most")}
                Spacer(Modifier.weight(1f))
                TextButton(onClick=dismiss) {Text("Mégse")}
                Button(onClick={
                    vm.dateTime=initial.toLocalDate().atTime(timeState.hour,timeState.minute)
                    vm.useNow=false;vm.arriveBy=arrive;dismiss()
                }) {Text("Kész")}
            }
        }
    }
}

/** Matches the collection tabs' travelling pill, label tint and spring press feedback. */
@Composable private fun JourneyTimeModeSelector(arrive: Boolean, onSelect: (Boolean) -> Unit) {
    val colors=MaterialTheme.colorScheme
    val gap=6.dp
    val progress by animateFloatAsState(if(arrive) 1f else 0f,
        spring(Spring.DampingRatioNoBouncy,Spring.StiffnessMediumLow),label="Journey time mode")
    val presses=remember { List(2) { MutableInteractionSource() } }
    val scales=presses.mapIndexed { index, press ->
        val pressed by press.collectIsPressedAsState()
        animateFloatAsState(if(pressed) .94f else 1f,
            spring(Spring.DampingRatioMediumBouncy,Spring.StiffnessMedium),label="Journey time mode press $index")
    }
    Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,color=colors.surface) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(6.dp)) {
            val tabWidth=(maxWidth-gap)/2
            Box(Modifier.offset { androidx.compose.ui.unit.IntOffset((progress*(tabWidth+gap).toPx()).toInt(),0) }
                .width(tabWidth).height(48.dp)
                .graphicsLayer {
                    val p=progress.coerceIn(0f,1f)
                    val scale=scales[0].value*(1f-p)+scales[1].value*p
                    scaleX=scale;scaleY=scale
                }
                .clip(RoundedCornerShape(26.dp)).background(colors.primaryContainer.copy(alpha=.65f)))
            Row(Modifier.fillMaxWidth().selectableGroup(),horizontalArrangement=Arrangement.spacedBy(gap)) {
                listOf("Indulás", "Érkezés").forEachIndexed { index, label ->
                    val on=(if(index==0) 1f-progress else progress).coerceIn(0f,1f)
                    val tint=androidx.compose.ui.graphics.lerp(colors.onSurfaceVariant,colors.primary,on)
                    Box(Modifier.weight(1f).height(48.dp)
                        .graphicsLayer { scaleX=scales[index].value;scaleY=scales[index].value }
                        .clip(RoundedCornerShape(26.dp))
                        .selectable(selected=arrive==(index==1),interactionSource=presses[index],
                            indication=null,role=Role.RadioButton,onClick={onSelect(index==1)}),
                        contentAlignment=Alignment.Center) {
                        Text(label,style=MaterialTheme.typography.labelLarge,color=tint)
                    }
                }
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

@Composable fun RouteBadge(leg: Leg, contentPadding: PaddingValues = PaddingValues(start=6.dp,end=12.dp,top=5.dp,bottom=5.dp)) {
    val logo=leg.routeLogo
    val color=if(logo!=null) Color.Transparent else serviceColor(leg)
    val foreground=if(logo!=null) MaterialTheme.colorScheme.onSurface else if(color.luminance()>.179f) Color.Black else Color.White
    Surface(shape=if(logo!=null) androidx.compose.ui.graphics.RectangleShape else CircleShape,color=color,contentColor=foreground,
        modifier=Modifier.semantics(mergeDescendants=true) { contentDescription="${serviceType(leg)} ${if(leg.walking) "" else leg.route}" }) {
        Row(Modifier.padding(contentPadding),verticalAlignment=Alignment.CenterVertically) {
            if(logo!=null) {
                RouteLogoIcon(logo)
            } else Icon(serviceIcon(leg),null,Modifier.padding(5.dp).size(18.dp),tint=foreground)
            Spacer(Modifier.width(7.dp))
            Text(if(leg.walking) "Gyalog" else leg.route,modifier=Modifier.weight(1f,fill=false),
                style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.Bold)
        }
    }
}

@Composable internal fun StationLabel(station: Station, rightAligned: Boolean = false, inlineName: Boolean = false,
    wrapStopName: Boolean = false) {
    Column(horizontalAlignment=if(rightAligned) Alignment.End else Alignment.Start) {
        if(inlineName) Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onSurface)) {
                append(station.townName)
                if(station.stopName.isNotBlank()) append(", ")
            }
            if(station.stopName.isNotBlank()) withStyle(SpanStyle(fontWeight=FontWeight.Normal,
                color=MaterialTheme.colorScheme.onSurfaceVariant)) {append(station.stopName)}
        },style=MaterialTheme.typography.titleMedium,
            textAlign=if(rightAligned) TextAlign.End else TextAlign.Start)
        else Text(station.townName,style=MaterialTheme.typography.titleMedium.copy(lineHeight=20.sp),fontWeight=FontWeight.Bold,
            textAlign=if(rightAligned) TextAlign.End else TextAlign.Start)
        if((!inlineName && station.stopName.isNotBlank()) || station.platform.isNotBlank()) {
            Row(horizontalArrangement=Arrangement.spacedBy(AppLayout.LabelGap),verticalAlignment=Alignment.CenterVertically) {
                if(!inlineName && station.stopName.isNotBlank()) Text(station.stopName,Modifier.weight(1f,fill=false),
                    style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines=if(wrapStopName) Int.MAX_VALUE else 1,softWrap=wrapStopName,overflow=TextOverflow.Ellipsis,
                    textAlign=if(rightAligned) TextAlign.End else TextAlign.Start)
                if(station.platform.isNotBlank()) Surface(shape=CircleShape,
                    color=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,
                    modifier=Modifier.clearAndSetSemantics {contentDescription="Vágány / kocsiállás: ${station.platform}"}) {
                    Box(Modifier.defaultMinSize(minWidth=24.dp,minHeight=24.dp).padding(horizontal=5.dp,vertical=2.dp),contentAlignment=Alignment.Center) {
                        Text(station.platform,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable private fun JourneyEndpoints(journey: Journey, departingNow: Boolean=false) {
    val departure=journey.boardingLeg
    val arrival=journey.alightingLeg
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top) {
        Column(Modifier.weight(1f)) {
            ScheduledTime(departure.departureTime,style=MaterialTheme.typography.headlineMedium,pulse=departingNow)
            StationLabel(departure.from)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward,null,Modifier.padding(horizontal=12.dp,vertical=6.dp).size(24.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f),horizontalAlignment=Alignment.End) {
            ScheduledTime(arrival.arrivalTime,style=MaterialTheme.typography.headlineMedium,alignment=Alignment.End)
            StationLabel(arrival.to,rightAligned=true)
            if(dayLabel(arrival.end)!=dayLabel(departure.start)) Text(dayLabel(arrival.end),style=MaterialTheme.typography.labelMedium)
        }
    }
}

class ResultsNavigationState(val listState: LazyListState) {
    var earlierAnchor by mutableStateOf<Pair<String,Int>?>(null)
}

internal const val DeparturePaginationKey = "later-departures"

@Composable internal fun DepartureAutoLoading(listState: LazyListState, enabled: Boolean, loadMore: () -> Unit) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentLoadMore by rememberUpdatedState(loadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            currentEnabled && listState.layoutInfo.visibleItemsInfo.any { it.key == DeparturePaginationKey }
        }.collect { visible -> if (visible) currentLoadMore() }
    }
}

@Composable fun ResultsScreen(vm: AppState, navigation: ResultsNavigationState, back: () -> Unit, select: (Journey) -> Unit) {
    var saveSearch by rememberSaveable {mutableStateOf(false)}
    val from=vm.resultFrom
    val to=vm.resultTo
    val saved=vm.savedSearches.firstOrNull {it.from.id==from?.id && it.to.id==to?.id}
    if(saveSearch && from!=null && to!=null) SavedSearchEditor(from,to,saved,
        onSave={name,icon -> vm.saveSearch(from,to,name,icon);saveSearch=false},
        onDismiss={saveSearch=false},
        onDelete=if(saved==null) null else {{vm.removeSavedSearch(saved);saveSearch=false}})
    val listState=navigation.listState
    DepartureAutoLoading(listState, enabled=vm.canLoadMore && !vm.searching && vm.searchError==null) {vm.search(true)}
    var earlierAnchor by navigation::earlierAnchor
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit) {
        while (true) {
            now=System.currentTimeMillis()
            delay(minOf(15_000L,60_000L-now%60_000L))
        }
    }
    val results=vm.filteredJourneys.sortedBy {it.boardingLeg.start}
    val currentMinute=now/60_000L
    val firstUpcoming=results.indexOfFirst {it.boardingLeg.start/60_000L>=currentMinute}.let {if(it<0) results.size else it}
    val outline=MaterialTheme.colorScheme.outline
    // Anchor a service rather than the header, which stays at index zero when pages are prepended.
    fun rememberEarlierAnchor() {
        val keys=results.mapTo(mutableSetOf()) {it.key}
        earlierAnchor=listState.layoutInfo.visibleItemsInfo.firstOrNull {it.key in keys}?.let {it.key as String to -it.offset}
    }
    LaunchedEffect(vm.loadingEarlier,vm.journeys) {
        if(!vm.loadingEarlier) earlierAnchor?.let { (key,offset) ->
            val resultIndex=results.indexOfFirst {it.key==key}
            if(resultIndex>=0) {
                val headerCount=1+(if(vm.canLoadEarlier) 1 else 0)+(if(vm.searchError!=null) 1 else 0)
                val dividerCount=if(vm.earlierRequested && firstUpcoming<=resultIndex) 1 else 0
                listState.requestScrollToItem(headerCount+resultIndex+dividerCount,offset)
            }
            earlierAnchor=null
        }
    }
    LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=screenContentPadding(),verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
        item { PageTitle("Indulások", "${from?.name.orEmpty()} → ${to?.name.orEmpty()}",back) {
            FilledTonalIconButton(onClick={saveSearch=true},enabled=canSaveRoute(from,to)) {
                Icon(if(saved==null) Icons.Outlined.BookmarkAdd else Icons.Outlined.BookmarkAdded,
                    if(saved==null) "Keresés mentése" else "Mentett keresés szerkesztése")
            }
        } }
        if(vm.canLoadEarlier || vm.loadingEarlier) item(key="earlier") {
            TextButton(onClick={rememberEarlierAnchor();vm.loadEarlier()},enabled=!vm.searching,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                if(vm.loadingEarlier) {
                    CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
                    Spacer(Modifier.width(AppLayout.CompactGap))
                }
                Text(if(vm.loadingEarlier) "Korábbiak betöltése…" else "Korábbiak mutatása")
            }
        }
        if(vm.searchError!=null) item {InfoPanel("Nem sikerült a keresés",vm.searchError!!){rememberEarlierAnchor();vm.retrySearch()}}
        if(vm.searching && vm.journeys.isEmpty()) item {LoadingRows()}
        if(!vm.searching && vm.searchError==null && results.isEmpty()) item {InfoPanel("Nincs megjeleníthető indulás",
            if(vm.activeSearchFilters.isEmpty() && vm.activeSearchVehicles.isEmpty()) "Módosítsd az időpontot vagy a megállókat."
            else "A betöltött járatok között nincs minden feltételnek megfelelő találat. Tölts be további indulásokat, vagy lépj vissza a keresési feltételek módosításához.")}
        results.forEachIndexed { index, journey ->
            if(vm.earlierRequested && index==firstUpcoming) item(key="now") { NowDivider() }
            item(key=journey.key) {
            val departureMinute=journey.boardingLeg.start/60_000L
            val earlier=departureMinute<currentMinute
            val departingNow=departureMinute==currentMinute
            Surface(onClick={select(journey)},shape=RoundedCornerShape(32.dp),color=MaterialTheme.colorScheme.surface,tonalElevation=0.dp,
                modifier=Modifier.semantics {if(earlier) stateDescription="Korábbi indulás"}.drawWithContent {
                    drawContent()
                    if(earlier || departingNow) {
                        val width=1.5.dp.toPx()
                        drawRoundRect(outline,topLeft=Offset(width/2,width/2),size=Size(size.width-width,size.height-width),
                            cornerRadius=CornerRadius(32.dp.toPx()-width/2),
                            style=Stroke(width=width,pathEffect=if(earlier)
                                PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(),4.dp.toPx())) else null))
                    }
                }) {
                Column(Modifier.fillMaxWidth().padding(AppLayout.PanelPadding),verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        val services=journey.legs.filterNot {it.walking}.distinctBy {it.mode to it.route}
                        FlowRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            (services.ifEmpty {journey.legs.take(1)}).forEach {RouteBadge(it)}
                        }
                        Icon(Icons.Outlined.ChevronRight,null,Modifier.padding(start=8.dp))
                    }
                    JourneyEndpoints(journey,departingNow=departingNow)
                    Text(listOf(dayLabel(journey.boardingLeg.start),"${journey.serviceMinutes} perc",if(journey.changes==0) "Közvetlen" else "${journey.changes} átszállás").filter {it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    JourneyAmenityIcons(journey)
                }
            }
            }
        }
        if(vm.earlierRequested && results.isNotEmpty() && firstUpcoming==results.size) item(key="now") { NowDivider() }
        if(vm.canLoadMore || (vm.searching && !vm.loadingEarlier && vm.journeys.isNotEmpty())) item(key=DeparturePaginationKey) {
            if(vm.searching && !vm.loadingEarlier) {
                Row(Modifier.fillMaxWidth().heightIn(min=52.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
                    Spacer(Modifier.width(AppLayout.CompactGap))
                    Text("További indulások betöltése…",style=MaterialTheme.typography.bodyMedium)
                }
            } else OutlinedButton(onClick={vm.search(true)},enabled=!vm.searching,shape=CircleShape,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("További indulások")}
        }
        item {Text(listOf("A találatokat a MÁV útvonaltervezője adja. Az újabb keresés további időpontokat tölt be.",
            searchVehicleSummary(vm.activeSearchVehicles), vm.activeSearchFilters.sortedBy {it.ordinal}.joinToString(" · ") {it.title}).filter {it.isNotBlank()}.joinToString("\n"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

@Composable private fun NowDivider() {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants=true) {},verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(AppLayout.CompactGap)) {
        HorizontalDivider(Modifier.weight(1f),thickness=2.dp,color=MaterialTheme.colorScheme.error)
        Text("Most",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.error)
        HorizontalDivider(Modifier.weight(1f),thickness=2.dp,color=MaterialTheme.colorScheme.error)
    }
}

private data class TimelineTrack(val color: Color, val walking: Boolean=false)

/** Walking endpoints may repeat a vehicle stop with different planner timestamps. */
internal fun sameJourneyStop(first: Station, second: Station): Boolean =
    first.platform == second.platform && if (first.id.isNotBlank() && second.id.isNotBlank()) first.id == second.id
    else first.name == second.name && kotlin.math.abs(first.lat-second.lat) < .0001 && kotlin.math.abs(first.lon-second.lon) < .0001

@Composable internal fun JourneyStationLabel(station: Station, mode: String) = Column {
    StationLabel(station.copy(platform=""), wrapStopName=true)
    val platform = station.platform.takeIf { it.isNotBlank() }?.let {
        when (mode) {
            "RAIL", "SUBURBAN_RAILWAY" -> "$it. vágány"
            "BUS", "COACH" -> "$it. kocsiállás"
            else -> "Megállóhely: $it"
        }
    }
    if (platform != null) Text(platform,
        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Each stop joins its incoming and outgoing tracks at the marker, never at a row boundary. */
@Composable private fun TimelineRow(time: ServiceTime?, track: TimelineTrack, stop: Boolean=false,
    incoming: TimelineTrack?=track, outgoing: TimelineTrack?=track,
    content: @Composable ColumnScope.() -> Unit) {
    val surface=MaterialTheme.colorScheme.surface
    val nodeOffset=with(LocalDensity.current) { MaterialTheme.typography.titleMedium.lineHeight.toDp()/2 }
    var rowTop by remember { mutableFloatStateOf(0f) }
    val timeGutter=64.dp * LocalDensity.current.fontScale
    // Intrinsic height sees AnimatedVisibility's full content before its reveal finishes.
    // Measure the content normally and draw the track at that same animated height.
    Box(Modifier.fillMaxWidth().onGloballyPositioned {
        rowTop=it.positionInParent().y
    }) {
        Canvas(Modifier.matchParentSize()) {
            val x=timeGutter.toPx()+12.dp.toPx()
            val nodeY=nodeOffset.toPx()
            fun segment(style: TimelineTrack?, top: Float, bottom: Float) {
                if(style==null || bottom<=top) return
                // Clip rounded dots at row edges; solid tracks meet flush without protruding caps.
                clipRect(top=top,bottom=bottom) {
                    drawLine(style.color,Offset(x,top),Offset(x,bottom),
                        strokeWidth=if(style.walking) 3.dp.toPx() else 4.dp.toPx(),
                        cap=if(style.walking) StrokeCap.Round else StrokeCap.Butt,
                        pathEffect=if(style.walking) PathEffect.dashPathEffect(
                            floatArrayOf(2.dp.toPx(),6.dp.toPx()),(rowTop+top)%8.dp.toPx()) else null)
                }
            }
            if(stop) {
                segment(incoming,0f,nodeY)
                segment(outgoing,nodeY,size.height)
            } else segment(track,0f,size.height)
            if(stop) {
                drawCircle(track.color,6.dp.toPx(),Offset(x,nodeY))
                drawCircle(surface,3.dp.toPx(),Offset(x,nodeY))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            // Large sp values use Android's nonlinear scaling, so scale this gutter explicitly.
            Box(Modifier.width(timeGutter).padding(top=2.dp,end=8.dp)) {
                time?.let {ScheduledTime(it,style=MaterialTheme.typography.labelLarge)}
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f).padding(start=12.dp,bottom=24.dp),content=content)
        }
    }
}

@Composable fun JourneyScreen(vm: AppState, journey: Journey, back: () -> Unit, onTrip: (Leg) -> Unit, onMap: () -> Unit,
    refreshTransfers: Boolean = true) {
    val transfers = remember(journey) { journeyTransfers(journey).associateBy { it.incomingIndex } }
    var legTimes by remember(journey) { mutableStateOf<Map<Int, TransferTimes>>(emptyMap()) }
    var transferTimes by remember(journey) { mutableStateOf(transfers.mapValues { initialTransferTimes(journey, it.value) }) }
    var transferNow by remember(journey) { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(journey, refreshTransfers, lifecycle, vm.journeyRefreshSeconds) {
        if (refreshTransfers) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            launch { while (true) { transferNow = System.currentTimeMillis(); delay(15_000) } }
            while (true) {
                legTimes = vm.refreshJourneyTimes(journey, legTimes)
                transferTimes = transfers.mapValues { (_, connection) ->
                    TransferTimes(legTimes.getValue(connection.incomingIndex).arrival,
                        legTimes.getValue(connection.outgoingIndex).departure)
                }
                transferTimes = vm.refreshTransferAlternatives(journey, transferTimes)
                delay(vm.journeyRefreshSeconds * 1_000L)
            }
        }
    }
    var trackingLeg by remember(journey.key) { mutableStateOf<Int?>(null) }
    val trackingContext = LocalContext.current
    LaunchedEffect(Unit) { JourneyTracking.load(trackingContext) }
    LaunchedEffect(vm.journeyTrackingBetaEnabled) { if (!vm.journeyTrackingBetaEnabled) trackingLeg = null }
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
                    val connection=TimelineTrack(MaterialTheme.colorScheme.onSurfaceVariant,walking=true)
                    journey.legs.forEachIndexed {index,leg ->
                        val color=serviceColor(leg)
                        val walking=leg.walking
                        val track=TimelineTrack(color,walking)
                        val previous=journey.legs.getOrNull(index-1)
                        val next=journey.legs.getOrNull(index+1)
                        val repeatedWalkStart=walking && previous!=null && sameJourneyStop(previous.to,leg.from)
                        val repeatedWalkEnd=walking && next!=null && sameJourneyStop(leg.to,next.from)
                        val sharedDeparture=previous!=null && index-1 !in transfers && previous.to.name==leg.from.name && previous.to.platform==leg.from.platform && previous.end==leg.start && previous.arrivalTime==leg.departureTime
                        val incoming=when {
                            previous==null -> null
                            sharedDeparture -> TimelineTrack(serviceColor(previous),previous.walking)
                            else -> connection
                        }
                        val transferDeparture = legTimes[index]?.departure?.time ?: leg.departureTime
                        // A transfer walk has a duration, not an independently refreshed absolute time.
                        // Keeping the original timestamps could place it before a newly delayed arrival.
                        val transferWalk = walking && transfers.values.any { index > it.incomingIndex && index < it.outgoingIndex }
                        if(!repeatedWalkStart) TimelineRow(if (transferWalk) null else transferDeparture,track,stop=true,incoming=incoming) {
                            if (vm.journeyTrackingBetaEnabled && sharedDeparture && !previous.walking) {
                                JourneyTrackingStop(journey, index - 1) { trackingLeg = index - 1 }
                            } else if(walking) StationLabel(leg.from,wrapStopName=true)
                            else JourneyStationLabel(leg.from,leg.mode)
                            if(index>0 && dayLabel(leg.start)!=dayLabel(journey.start)) Text(dayLabel(leg.start),style=MaterialTheme.typography.labelMedium)
                        }
                        TimelineRow(null,track) {
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
                                JourneyLegAlerts(leg,Modifier.padding(top=AppLayout.LabelGap))
                                if(leg.headsign.isNotBlank()) Text("Irány: ${leg.headsign}",Modifier.padding(top=4.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                if(!transferDeparture.realtime && !(legTimes[index]?.arrival?.time ?: leg.arrivalTime).realtime)
                                    Text("Menetrend szerint · élő adat nem érhető el",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
                                        Box(Modifier.padding(vertical=AppLayout.CompactGap)) {StationLabel(stop,inlineName=true)}
                                    } }
                                    }
                                }
                                if(leg.tripId.isNotBlank()) TextButton(onClick={onTrip(leg)},contentPadding=PaddingValues(horizontal=0.dp),modifier=Modifier.heightIn(min=48.dp)) {
                                    Text("Teljes járatmenetrend",Modifier.weight(1f));Icon(Icons.Outlined.ChevronRight,null)
                                }
                            }
                        }
                        // A shared stop at the same time is one interchange node, not two repeated rows.
                        val sharedStop=next!=null && !next.walking && index !in transfers && sameJourneyStop(next.from,leg.to) && next.start==leg.end && next.departureTime==leg.arrivalTime
                        val transferArrival = legTimes[index]?.arrival?.time ?: leg.arrivalTime
                        if(!sharedStop && !repeatedWalkEnd) TimelineRow(if (transferWalk) null else transferArrival,track,stop=true,outgoing=if(next==null) null else connection) {
                            if (vm.journeyTrackingBetaEnabled && !walking) JourneyTrackingStop(journey, index) { trackingLeg = index }
                            else if(walking) StationLabel(leg.to,wrapStopName=true)
                            else JourneyStationLabel(leg.to,leg.mode)
                            if(dayLabel(leg.end)!=dayLabel(journey.start)) Text(dayLabel(leg.end),style=MaterialTheme.typography.labelMedium)
                        }
                        transfers[index]?.takeIf { transferWarning(it,transferTimes.getValue(index),transferNow) != null }?.let { transfer ->
                            val times = transferTimes.getValue(index)
                            TimelineRow(null,connection) {
                                JourneyTransferNotice(transfer,times,transferNow)
                            }
                        }
                        if(next!=null && transfers.values.none { index in it.incomingIndex until it.outgoingIndex }) {
                            val wait=(next.start-leg.end)/60_000
                            if(wait>=1) TimelineRow(null,connection) {
                                Text("$wait perc várakozás",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        journey.legs.filter { !it.walking && it.services.isNotEmpty() }.forEach {leg ->
            item {
                Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(AppLayout.PanelPadding),verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
                        RouteBadge(leg)
                        Text("${leg.from.name} → ${leg.to.name}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        MavServices(leg.services)
                    }
                }
            }
        }
        item {val advice=passAdvice(vm.passes,journey,vm.passGeography);InfoPanel(advice.title,advice.detail)}
    }
    if (vm.journeyTrackingBetaEnabled) trackingLeg?.let { index -> JourneyTrackingSheet(journey, index) { trackingLeg = null } }
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
    AppBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
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
            item {Text(if(vehicle==null) "Ehhez a járathoz most nincs élő járműpozíció." else "Élő járműpozíció elérhető · ${vm.mapRefreshSeconds} mp-es frissítés",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(vehicle!=null) item {OutlinedButton(onClick=onMap,shape=CircleShape,modifier=Modifier.fillMaxWidth()){Text("Jármű a térképen")}}
            itemsIndexed(detail.array("stoptimes")) {index,stop ->
                val s=stop.optJSONObject("stop")
                val time=tripStopTime(stop,day)
                Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(AppLayout.PanelPadding),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(AppLayout.RelatedGap)) {
                        Column(Modifier.width(with(LocalDensity.current) { 64.sp.toDp() })) {
                            if(time!=null) ScheduledTime(time,style=MaterialTheme.typography.titleMedium)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(s?.optString("name").orEmpty(),style=MaterialTheme.typography.titleMedium)
                            val platform=s?.optString("platformCode").orEmpty()
                            if(platform.isNotBlank() && platform!="null") Text("Vágány / kocsiállás: $platform",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
