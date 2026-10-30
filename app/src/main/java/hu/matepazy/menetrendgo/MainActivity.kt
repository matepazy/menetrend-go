package hu.matepazy.menetrendgo

import android.os.Bundle
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate

/* THESIS: search and recents first; departure discovery gets its own screen.
 * OWN-WORLD: icy blue ground, ink text, royal-blue actions, fully rounded milky panels.
 * STORY: choose stops, search, scan departures, inspect the journey and its vehicle.
 * FIRST VIEWPORT: title, two station rows and swap, departure date, wide search, recents.
 * FORM: user-approved concept A, revised results flow; seed 1b242c78, brief overrides roll.
 * FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review,
 * the verdict, DESIGN.md, and every shipping raster carrying its provenance.
 */
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val configuration=Configuration(newBase.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags("hu"))
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Locale.setDefault(Locale.forLanguageTag("hu"))
        PassNotifications.saved(this).forEach { PassNotifications.schedule(this,it) }
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.auto(
            android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT))
        setContent { val state: AppState = viewModel(); MenetrendTheme(state.theme) {
            StationFocusOverlay { MenetrendApp(state, intent.getStringExtra("tab")) }
            UpdateHost(state.updates)
        } }
    }
}

private val Light = lightColorScheme(primary=Color(0xFF2864D9), onPrimary=Color.White,
    background=Color(0xFFEDF4FA), onBackground=Color(0xFF112D43), surface=Color(0xFFF8FBFF),
    onSurface=Color(0xFF112D43), surfaceVariant=Color(0xFFE3EDFA), onSurfaceVariant=Color(0xFF4E6581),
    primaryContainer=Color(0xFFDCEAFF), onPrimaryContainer=Color(0xFF174FAF), outline=Color(0xFF71849C),
    secondaryContainer=Color(0xFFDCEAFF),onSecondaryContainer=Color(0xFF174FAF),
    surfaceDim=Color(0xFFD7E3EF),surfaceBright=Color(0xFFF8FBFF),
    surfaceContainerLowest=Color.White,surfaceContainerLow=Color(0xFFF1F7FD),
    surfaceContainer=Color(0xFFEAF2FA),surfaceContainerHigh=Color(0xFFE3EDF7),
    surfaceContainerHighest=Color(0xFFDBE7F3),surfaceTint=Color(0xFF2864D9))
private val Dark = darkColorScheme(primary=Color(0xFFA8C7FF), onPrimary=Color(0xFF092B64),
    background=Color(0xFF101D2A), onBackground=Color(0xFFE6F0FF), surface=Color(0xFF192B3D),
    onSurface=Color(0xFFE6F0FF), surfaceVariant=Color(0xFF253B51), onSurfaceVariant=Color(0xFFB8CAE0),
    primaryContainer=Color(0xFF294C7C), onPrimaryContainer=Color(0xFFD6E6FF), outline=Color(0xFF8CA2BB),
    secondaryContainer=Color(0xFF294C7C),onSecondaryContainer=Color(0xFFD6E6FF),
    surfaceDim=Color(0xFF101D2A),surfaceBright=Color(0xFF30465C),
    surfaceContainerLowest=Color(0xFF0B1723),surfaceContainerLow=Color(0xFF152536),
    surfaceContainer=Color(0xFF192B3D),surfaceContainerHigh=Color(0xFF203449),
    surfaceContainerHighest=Color(0xFF293F55),surfaceTint=Color(0xFFA8C7FF))
@Composable fun MenetrendTheme(choice: String, content: @Composable () -> Unit) {
    val dark = choice == "dark" || (choice == "system" && isSystemInDarkTheme())
    val scheme=if(dark) Dark else Light
    val view=LocalView.current
    SideEffect {
        (view.context as? ComponentActivity)?.window?.let {window ->
            WindowCompat.getInsetsController(window,view).apply {
                isAppearanceLightStatusBars=!dark
                isAppearanceLightNavigationBars=!dark
            }
        }
    }
    MaterialTheme(colorScheme=scheme,
        shapes=Shapes(extraSmall=RoundedCornerShape(16.dp),small=RoundedCornerShape(24.dp),medium=RoundedCornerShape(28.dp),large=RoundedCornerShape(32.dp),extraLarge=RoundedCornerShape(36.dp)),
        content={CompositionLocalProvider(LocalContentColor provides scheme.onBackground,content=content)})
}

// The rounded bar floats above content; list padding keeps final actions reachable.
val LocalBottomOverlayInset = staticCompositionLocalOf { 0.dp }

/** Reuses the page's display list, so the backdrop never composes a second interactive UI. */
@Composable private fun FeatheredContent(enabled: Boolean, bottomHeight: Dp, content: @Composable () -> Unit) {
    val backdrop=rememberGraphicsLayer()
    val blurredBackdrop=rememberGraphicsLayer()
    val ground=MaterialTheme.colorScheme.background
    val topInset=WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val topHeight=if(topInset>0.dp) topInset+24.dp else 0.dp
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()
            .graphicsLayer { compositingStrategy=if(enabled) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
            .drawWithContent {
            if(enabled) {
                backdrop.record { this@drawWithContent.drawContent() }
                drawLayer(backdrop)
                if(android.os.Build.VERSION.SDK_INT>=31) {
                    // Blur the complete page before cropping either edge. Blurring a small
                    // strip with Clamp stretches its boundary pixels into a visible band.
                    blurredBackdrop.renderEffect=BlurEffect(18.dp.toPx(),18.dp.toPx(),TileMode.Decal)
                    blurredBackdrop.record { drawLayer(backdrop) }
                    // Crossfade the sharp and blurred page rather than stacking them.
                    // Otherwise dark badges show twice and leave a shadow-like band.
                    if(topHeight>0.dp) {
                        val height=topHeight.toPx().coerceAtMost(size.height)
                        drawRect(Brush.verticalGradient(0f to Color.Black,.45f to Color.Black,1f to Color.Transparent,endY=height),
                            size=androidx.compose.ui.geometry.Size(size.width,height),blendMode=BlendMode.DstOut)
                    }
                    if(bottomHeight>0.dp) {
                        val height=bottomHeight.toPx().coerceAtMost(size.height)
                        translate(top=size.height-height) {
                            drawRect(Brush.verticalGradient(0f to Color.Transparent,.55f to Color.Black,1f to Color.Black,endY=height),
                                size=androidx.compose.ui.geometry.Size(size.width,height),blendMode=BlendMode.DstOut)
                        }
                    }
                }
            } else drawContent()
        },contentAlignment=Alignment.TopCenter) { content() }
        if(enabled) {
            // Both system-bar edges share one effect, mirrored vertically at the top.
            listOf(true to topHeight,false to bottomHeight).forEach { (top,height) ->
            if(height>0.dp) Box(Modifier.align(if(top) Alignment.TopCenter else Alignment.BottomCenter).fillMaxWidth().height(height)
                .graphicsLayer { compositingStrategy=CompositingStrategy.Offscreen;clip=true }
                .drawWithContent {
                    drawContent()
                    val mask=if(top) Brush.verticalGradient(0f to Color.Black,.45f to Color.Black,1f to Color.Transparent)
                        else Brush.verticalGradient(0f to Color.Transparent,.55f to Color.Black,1f to Color.Black)
                    drawRect(mask,blendMode=BlendMode.DstIn)
                }
                .drawWithContent {
                    if(android.os.Build.VERSION.SDK_INT>=31) translate(top=if(top) 0f else -(backdrop.size.height-size.height)) { drawLayer(blurredBackdrop) }
                    val colors=listOf(ground.copy(alpha=0f),ground.copy(alpha=.82f))
                    drawRect(Brush.verticalGradient(if(top) colors.reversed() else colors))
                })
            }
        }
    }
}

@Composable fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier=modifier,shape=MaterialTheme.shapes.medium,color=colors.surface) {
        Column(Modifier.padding(AppLayout.PanelPadding),content=content)
    }
}
@Composable fun PageTitle(title: String, subtitle: String? = null, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(AppLayout.CompactGap)) {
        if(back != null) IconButton(onClick=back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Vissza") }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(AppLayout.LabelGap)) {
            Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            if(subtitle != null) Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

@Composable fun MainButton(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick=onClick,enabled=enabled,shape=CircleShape,modifier=Modifier.fillMaxWidth().heightIn(min=58.dp)) {
        Icon(icon,null,Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Text(label,style=MaterialTheme.typography.titleMedium)
    }
}
@Composable fun InfoPanel(title: String, message: String, retry: (() -> Unit)? = null) {
    Glass(Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Info,null,tint=MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp)); Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        Spacer(Modifier.height(AppLayout.CompactGap)); Text(message,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(retry != null) TextButton(onClick=retry) { Text("Újrapróbálás") }
    }
}
@Composable fun LoadingRows() {
    Column(verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
        LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
        repeat(3) { Surface(Modifier.fillMaxWidth().height(100.dp),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.5f)) {} }
    }
}

@Composable fun MenetrendApp(vm: AppState, initialTab: String?) {
    var tab by rememberSaveable { mutableIntStateOf(if(initialTab == "passes") 2 else 0) }
    var page by rememberSaveable { mutableStateOf("search") }
    var selected by vm::selectedJourney
    var returning by remember { mutableStateOf(false) }
    // The live destination and predictive preview share the retained scroll anchor.
    val resultsNavigation = key(vm.searchGeneration) {
        val listState = rememberLazyListState()
        remember(listState) { ResultsNavigationState(listState) }
    }
    LaunchedEffect(page,selected) {
        if(page=="routeMap" && selected==null) page="search"
    }
    var tripId by rememberSaveable { mutableStateOf("") }
    var tripDay by rememberSaveable { mutableStateOf(LocalDate.now(Budapest).toString()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(tab, page, tripId, tripDay, vm.mapRefreshSeconds) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if(tab == 1 || page == "trip" || page == "journey" || page == "routeMap" || page == "vehicleMap") {
                while(true) {
                    vm.refreshVehicles()
                    if(page == "trip" && tripId.isNotEmpty()) vm.loadTrip(tripId,tripDay)
                    delay(vm.mapRefreshSeconds * 1_000L)
                }
            }
        }
    }
    fun back() {
        returning=true
        when {
            page == "vehicleMap" -> page="trip"
            page == "routeMap" -> page="journey"
            page == "trip" -> page = if(selected != null) "journey" else "search"
            page == "journey" -> { selected=null; page="results" }
            page == "results" -> page="search"
            tab != 0 -> { tab=0; page="search" }
        }
    }
    val mapScreen=page=="routeMap" || page=="vehicleMap"
    val destinations = listOf("Tervezés","Térkép","Bérletek","Beállítások")
    val icons = listOf(Icons.Outlined.Search,Icons.Outlined.Map,Icons.Outlined.CreditCard,Icons.Outlined.Settings)
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 700.dp
        val largeFont=LocalDensity.current.fontScale>1.15f
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(if(wide || mapScreen) WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom else WindowInsetsSides.Horizontal))) {
            if(wide && !mapScreen) NavigationRail(modifier=Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),containerColor=MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.height(30.dp))
                destinations.forEachIndexed { index,label -> NavigationRailItem(selected=tab==index,onClick={returning=false;tab=index;page="search";selected=null},icon={Icon(icons[index],null)},label={Text(label)}) }
            }
            Scaffold(containerColor=Color.Transparent,contentWindowInsets=WindowInsets(0),
                bottomBar={ if(!wide && !mapScreen) {
                    Surface(Modifier.navigationBarsPadding().padding(horizontal=AppLayout.PageGutter,vertical=6.dp),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(6.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            destinations.forEachIndexed { index,label ->
                                val active=tab==index
                                val tint by animateColorAsState(
                                    if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    tween(AppMotion.Feedback),label="Navigation content")
                                val selection by animateColorAsState(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha=if(active) .65f else 0f),
                                    tween(AppMotion.Feedback),label="Navigation selection")
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(26.dp))
                                    .background(selection)
                                    .selectable(selected=active,role=Role.Tab,onClick={returning=false;tab=index;page="search";selected=null})
                                    .padding(horizontal=2.dp,vertical=8.dp).heightIn(min=if(largeFont) 64.dp else 48.dp),
                                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                    Icon(icons[index],null,Modifier.size(24.dp),tint=tint)
                                    Spacer(Modifier.height(5.dp))
                                    Text(label,style=MaterialTheme.typography.labelMedium,textAlign=TextAlign.Center,maxLines=2,color=tint)
                                }
                            }
                        }
                    }
                } }
            ) { inset ->
                Box(Modifier.fillMaxSize().padding(top=inset.calculateTopPadding()).imePadding(),contentAlignment=Alignment.TopCenter) {
                    CompositionLocalProvider(LocalBottomOverlayInset provides inset.calculateBottomPadding()) {
                    // The bar has a 6dp outer top margin; keep the feather below its surface edge.
                    FeatheredContent(enabled=tab!=1 && !mapScreen,bottomHeight=if(wide) 0.dp else (inset.calculateBottomPadding()-6.dp).coerceAtLeast(0.dp)) {
                    Box(Modifier.widthIn(max=if(mapScreen) Dp.Infinity else if(tab==1) 1600.dp else 760.dp).fillMaxSize()) {
                        PredictiveBackPane(enabled=(page!="search" || tab!=0) && vm.mapVehicle==null,onBack=::back,preview={
                            when {
                                page=="vehicleMap" -> TripScreen(vm,tripId,tripDay,{},{})
                                page=="routeMap" && selected!=null -> JourneyScreen(vm,selected!!,{},onTrip={},onMap={})
                                page=="trip" && selected!=null -> JourneyScreen(vm,selected!!,{},onTrip={},onMap={})
                                page=="trip" && tab==1 -> NearbyScreen(vm)
                                page=="journey" -> ResultsScreen(vm,resultsNavigation,{}) {}
                                else -> SearchScreen(vm,onPasses={}) {}
                            }
                        }) {
                        ScreenArrival("$tab:$page",animate=!returning) {
                        when {
                            page=="vehicleMap" -> VehicleMapScreen(vm,tripId,::back)
                            page=="routeMap" && selected!=null -> JourneyMapScreen(vm,selected!!,::back)
                            page == "trip" -> TripScreen(vm, tripId, tripDay, ::back) { returning=false;vm.mapVehicle=null;page="vehicleMap" }
                            tab==0 && page=="results" -> ResultsScreen(vm,resultsNavigation,::back) { returning=false;selected=it; page="journey" }
                            tab==0 && page=="journey" && selected!=null -> JourneyScreen(vm,selected!!,::back,
                                onTrip={ leg -> returning=false;tripId=leg.tripId; tripDay=Instant.ofEpochMilli(leg.start).atZone(Budapest).toLocalDate().toString(); vm.loadTrip(tripId,tripDay); page="trip" },
                                onMap={ returning=false;vm.mapVehicle=null;page="routeMap" })
                            tab==0 -> SearchScreen(vm,onPasses={returning=false;tab=2}) { returning=false;vm.search();page="results" }
                            tab==1 -> NearbyScreen(vm)
                            tab==2 -> PassesScreen(vm)
                            else -> SettingsScreen(vm)
                        }
                        }
                        }
                    }
                    }
                    }
                }
            }
        }
    }
}
