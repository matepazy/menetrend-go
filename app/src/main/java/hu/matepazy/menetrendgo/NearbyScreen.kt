package hu.matepazy.menetrendgo

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.os.Handler
import android.os.Looper
import android.webkit.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun NearbyScreen(vm: AppState) =
    TransportMap(vm,null,null,routeOnly=false)

@Composable fun JourneyMapScreen(vm: AppState, journey: Journey, back: () -> Unit) =
    TransportMap(vm,journey,null,routeOnly=true,back=back)

@Composable fun VehicleMapScreen(vm: AppState, tripId: String, back: () -> Unit) =
    TransportMap(vm,null,tripId,routeOnly=true,back=back)

/** Shared map renderer. A route screen never requests the user's location or loads unrelated markers. */
@Composable private fun TransportMap(vm: AppState, journey: Journey?, focusedId: String?, routeOnly: Boolean, back: () -> Unit={}) {
    val context=LocalContext.current
    val bottomInset=LocalBottomOverlayInset.current.value
    val topInset=WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val featherColor=MaterialTheme.colorScheme.background.toArgb()
    var web by remember {mutableStateOf<WebView?>(null)}
    var ready by remember {mutableStateOf(false)}
    var center by remember {mutableStateOf<Pair<Double,Double>?>(null)}
    var locationMessage by remember {mutableStateOf<String?>(null)}
    var locationVisible by remember {mutableStateOf(false)}
    var recenter by remember {mutableStateOf(false)}
    var hasCentered by remember {mutableStateOf(false)}
    var allowed by remember {mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)}
    val manager=remember {context.getSystemService(LocationManager::class.java)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val snackbars=remember {SnackbarHostState()}
    val scope=rememberCoroutineScope()
    var selectedVehicle by vm::mapVehicle
    val onSelect by rememberUpdatedState<(Vehicle) -> Unit>({vehicle -> vm.loadTrip(vehicle.id,java.time.LocalDate.now(Budapest).toString());selectedVehicle=vehicle})
    var mapTypesOpen by rememberSaveable {mutableStateOf(false)}
    var filtersOpen by rememberSaveable {mutableStateOf(false)}
    var searchQuery by rememberSaveable {mutableStateOf("")}
    var searchFocused by rememberSaveable {mutableStateOf(false)}
    val allVehicles=remember(vm.vehicles,routeOnly,vm.vehicleFilters) {
        if(routeOnly) vm.vehicles else mapLayerVehicles(vm.vehicles,vm.vehicleFilters)
    }
    val search=remember(allVehicles) {VehicleSearch(allVehicles)}
    val filteredVehicles by produceState(
        initialValue=if(searchQuery.isBlank()) allVehicles else emptyList(),
        allVehicles,searchQuery,routeOnly
    ) {
        value=if(routeOnly || searchQuery.isBlank()) allVehicles
            else withContext(Dispatchers.Default) {search.filter(searchQuery)}
    }
    val visibleVehicles=remember(routeOnly,journey,focusedId,allVehicles,vm.vehicleUpdated,searchQuery,filteredVehicles) {
        when {
            routeOnly && focusedId!=null -> vehicleForTrip(vm.vehicles,focusedId)
            routeOnly && journey!=null -> routeVehicles(journey,vm.vehicles,System.currentTimeMillis())
            routeOnly -> emptyList()
            else -> if(searchQuery.isBlank()) allVehicles else filteredVehicles
        }
    }
    val currentVehicles by rememberUpdatedState(visibleVehicles)
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted ->
        allowed=granted
        if(!granted) locationMessage="A helyhozzáférés nélkül kézzel használhatod a térképet."
    }
    LaunchedEffect(Unit) {if(!routeOnly && !allowed) permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)}
    LaunchedEffect(locationMessage,vm.vehicleError) {
        (locationMessage ?: vm.vehicleError)?.let {snackbars.showSnackbar(it)}
    }
    DisposableEffect(allowed,lifecycle) {
        val listener=object:LocationListener {
            override fun onLocationChanged(location: Location) {center=location.latitude to location.longitude}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {locationMessage="Kapcsold be a helymeghatározást a saját helyzetedhez."}
            @Deprecated("Legacy Android callback")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        fun start() {
            if(routeOnly || !allowed) return
            try {
                val providers=manager.getProviders(true).filter {it!=LocationManager.PASSIVE_PROVIDER}
                providers.mapNotNull {runCatching {manager.getLastKnownLocation(it)}.getOrNull()}
                    .filter {System.currentTimeMillis()-it.time<120_000}.maxByOrNull {it.time}
                    ?.let {listener.onLocationChanged(it)}
                if(providers.isEmpty()) locationMessage="Kapcsold be a helymeghatározást a saját helyzetedhez."
                providers.forEach {provider ->
                    runCatching {manager.requestLocationUpdates(provider,15_000L,20f,listener,Looper.getMainLooper())}
                }
            } catch(e: SecurityException) {locationMessage="Engedélyezd a helyhozzáférést a Beállításokban."}
        }
        val observer=LifecycleEventObserver {_,event ->
            if(event==Lifecycle.Event.ON_START) {
                allowed=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
                start()
            }
            if(event==Lifecycle.Event.ON_STOP) manager.removeUpdates(listener)
        }
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        onDispose {lifecycle.removeObserver(observer);manager.removeUpdates(listener)}
    }
    LaunchedEffect(ready,visibleVehicles,journey) {
        if(ready) {
            val array=withContext(Dispatchers.Default) { JSONArray(visibleVehicles.map { vehicle ->
                JSONObject().put("id",vehicle.id).put("name",vehicle.name).put("headsign",vehicle.headsign)
                    .put("lat",vehicle.lat).put("lon",vehicle.lon).put("mode",vehicle.mode)
                    .put("heading",vehicle.heading ?: JSONObject.NULL)
                    .put("color",serviceColorHex(vehicle.badgeLeg()))
                    .put("type",hungarianServiceName(vehicle.category,vehicle.mode))
            }).toString() }
            val route=JSONArray(journey?.legs.orEmpty().map {leg->JSONObject().put("geometry",leg.geometry).put("from",leg.from.json()).put("to",leg.to.json()).put("walking",leg.walking).put("color",serviceColorHex(leg))})
            web?.evaluateJavascript("window.setRouteMode($routeOnly);window.updateVehicles($array);window.updateRoute($route);",null)
        }
    }
    LaunchedEffect(ready,vm.effectiveMapType,vm.customMapUrl,vm.unofficialGoogleMapEnabled) {
        if(ready) web?.evaluateJavascript("window.setMapType(${JSONObject.quote(vm.effectiveMapType.id)},${JSONObject.quote(vm.customMapUrl)},${vm.unofficialGoogleMapEnabled});",null)
    }
    LaunchedEffect(ready,bottomInset) {if(ready) web?.evaluateJavascript("window.setBottomInset($bottomInset);",null)}
    LaunchedEffect(ready,topInset,featherColor) {
        if(ready) web?.evaluateJavascript("window.setTopInset(${topInset.value},$featherColor,${android.os.Build.VERSION.SDK_INT>=31});",null)
    }
    LaunchedEffect(ready,center,recenter) {
        val position=center
        if(ready && position!=null) {
            val move=recenter || (!hasCentered && journey==null && focusedId==null)
            web?.evaluateJavascript("window.updateLocation(${position.first},${position.second},$move);",null)
            hasCentered=true
            recenter=false
        }
    }
    var focusedOnce by remember(focusedId) {mutableStateOf(false)}
    LaunchedEffect(ready,focusedId,visibleVehicles) {
        if(ready && focusedId!=null && !focusedOnce) visibleVehicles.firstOrNull()?.let {
            web?.evaluateJavascript("window.centerOn(${it.lat},${it.lon});",null)
            focusedOnce=true
        }
    }
    DisposableEffect(Unit) {onDispose {web?.removeJavascriptInterface("NativeMap");web?.destroy();web=null}}
    Box(Modifier.fillMaxSize()) {
            AndroidView(modifier=Modifier.fillMaxSize(),factory={ctx->
                WebView(ctx).apply {
                    if(BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
                    layoutParams=android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled=true
                    settings.allowFileAccess=false;settings.allowContentAccess=false
                    settings.domStorageEnabled=false
                    settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.userAgentString="MenetrendGO/${BuildConfig.VERSION_NAME} (Android; https://github.com/matepazy/menetrend-go)"
                    addJavascriptInterface(object {
                        @JavascriptInterface fun locationInView(visible: Boolean) {Handler(Looper.getMainLooper()).post {locationVisible=visible}}
                        @JavascriptInterface fun selectVehicle(id: String) {Handler(Looper.getMainLooper()).post {currentVehicles.find {it.id==id}?.let(onSelect)}}
                    },"NativeMap")
                    webViewClient=object:WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            view.evaluateJavascript("window.resizeMap && window.resizeMap();window.setBottomInset($bottomInset);window.setTopInset(${topInset.value},$featherColor,${android.os.Build.VERSION.SDK_INT>=31});",null)
                            ready=true
                        }
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if(request.url.scheme=="https" && request.url.host in setOf("www.openstreetmap.org","www.openrailwaymap.org","creativecommons.org","www.esri.com")) context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,request.url))
                            return true
                        }
                    }
                    val html=ctx.assets.open("map.html").bufferedReader().use {it.readText()}
                        .replace("/* LEAFLET_JS */",ctx.assets.open("leaflet.js").bufferedReader().use {it.readText()})
                        .replace("/* LEAFLET_CSS */",ctx.assets.open("leaflet.css").bufferedReader().use {it.readText()})
                    loadDataWithBaseURL("https://menetrendgo.local/",html,"text/html","utf-8",null)
                    web=this
                }
            })
        if(!routeOnly) {
            AnimatedVisibility(!searchFocused,modifier=Modifier.align(Alignment.TopStart),
                enter=fadeIn(tween(AppMotion.Feedback)),exit=fadeOut(tween(AppMotion.Feedback))) {
            Column(modifier=Modifier.padding(top=topInset+10.dp,start=10.dp),
                verticalArrangement=Arrangement.spacedBy(AppLayout.CompactGap)) {
                Surface(shape=androidx.compose.foundation.shape.CircleShape,color=MaterialTheme.colorScheme.surface) {
                    IconButton(onClick={mapTypesOpen=true},enabled=!searchFocused,modifier=Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Layers,"Térképtípus")
                    }
                }
                Surface(shape=androidx.compose.foundation.shape.CircleShape,color=MaterialTheme.colorScheme.surface) {
                    IconButton(onClick={filtersOpen=true},enabled=!searchFocused,modifier=Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Tune,"Járműszűrők")
                    }
                }
            }
            }
            MapSearch(searchQuery,{searchQuery=it},refreshing=vm.vehiclesLoading,
                onRefresh={scope.launch {vm.refreshVehicles(forceRefresh=true)}},
                modifier=Modifier.align(Alignment.TopEnd).padding(top=topInset).fillMaxWidth(),
                focused=searchFocused,onFocusChange={searchFocused=it})
        }
            if(!routeOnly && (center==null || !locationVisible)) FilledTonalIconButton(onClick={
                if(allowed) {recenter=true;if(center==null) locationMessage="Várakozás a helymeghatározásra. Ellenőrizd, hogy be van-e kapcsolva."}
                else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },modifier=Modifier.align(Alignment.BottomEnd).padding(end=14.dp,bottom=LocalBottomOverlayInset.current+52.dp).size(54.dp)) {
                Icon(Icons.Outlined.MyLocation,"Saját helyzet mutatása")
            }
        if(routeOnly) FilledTonalIconButton(onClick=back,modifier=Modifier.align(Alignment.TopStart).padding(top=topInset).padding(12.dp).size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack,if(focusedId!=null) "Vissza a járat menetrendjéhez" else "Vissza az utazás részleteihez")
        }
        if(routeOnly && focusedId!=null && visibleVehicles.isEmpty()) Surface(
            modifier=Modifier.align(Alignment.BottomCenter).padding(16.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(vm.vehiclesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if(vm.vehiclesLoading) "Járműpozíció betöltése…" else "Ehhez a járathoz most nincs élő járműpozíció.",style=MaterialTheme.typography.bodyMedium)
            }
        }
        if(mapTypesOpen) MapTypeDrawer(vm) {mapTypesOpen=false}
        if(filtersOpen) MapFilterDrawer(vm) {filtersOpen=false}
        selectedVehicle?.let {selected -> VehicleDrawer(vm,currentVehicles.firstOrNull {it.id==selected.id} ?: selected) {selectedVehicle=null}}
        SnackbarHost(snackbars,Modifier.align(Alignment.BottomCenter).padding(bottom=LocalBottomOverlayInset.current+12.dp))
    }
}
