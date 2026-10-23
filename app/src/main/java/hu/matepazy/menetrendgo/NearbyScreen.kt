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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.json.*

@Composable fun NearbyScreen(vm: AppState, journey: Journey?, focusedId: String?) {
    val context=LocalContext.current
    val bottomInset=LocalBottomOverlayInset.current.value
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
    var selectedVehicle by vm::mapVehicle
    val onSelect by rememberUpdatedState<(Vehicle) -> Unit>({vehicle -> vm.loadTrip(vehicle.id,java.time.LocalDate.now(Budapest).toString());selectedVehicle=vehicle})
    val currentVehicles by rememberUpdatedState(vm.vehicles)
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted ->
        allowed=granted
        if(!granted) locationMessage="A helyhozzáférés nélkül kézzel használhatod a térképet."
    }
    LaunchedEffect(Unit) {if(!allowed) permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)}
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
            if(!allowed) return
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
    LaunchedEffect(ready,vm.vehicles,journey) {
        if(ready) {
            val array=JSONArray(vm.vehicles.map { vehicle ->
                JSONObject().put("id",vehicle.id).put("name",vehicle.name).put("headsign",vehicle.headsign)
                    .put("lat",vehicle.lat).put("lon",vehicle.lon).put("mode",vehicle.mode)
                    .put("color",serviceColorHex(vehicle.badgeLeg()))
                    .put("type",hungarianServiceName(vehicle.category,vehicle.mode))
            })
            val route=JSONArray(journey?.legs.orEmpty().map {leg->JSONObject().put("geometry",leg.geometry).put("from",leg.from.json()).put("to",leg.to.json())})
            web?.evaluateJavascript("window.updateVehicles($array);window.updateRoute($route);",null)
        }
    }
    LaunchedEffect(ready,bottomInset) {if(ready) web?.evaluateJavascript("window.setBottomInset($bottomInset);",null)}
    LaunchedEffect(ready,center,recenter) {
        val position=center
        if(ready && position!=null) {
            val move=recenter || (!hasCentered && journey==null && focusedId==null)
            web?.evaluateJavascript("window.updateLocation(${position.first},${position.second},$move);",null)
            hasCentered=true
            recenter=false
        }
    }
    LaunchedEffect(ready,focusedId,vm.vehicles) {
        if(ready && focusedId!=null) vm.vehicles.find {it.id==focusedId}?.let {web?.evaluateJavascript("window.centerOn(${it.lat},${it.lon});",null)}
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
                            view.evaluateJavascript("window.resizeMap && window.resizeMap();window.setBottomInset($bottomInset);",null)
                            ready=true
                        }
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if(request.url.host=="www.openstreetmap.org") context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,request.url))
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
            if(center==null || !locationVisible) FilledTonalIconButton(onClick={
                if(allowed) {recenter=true;if(center==null) locationMessage="Várakozás a helymeghatározásra. Ellenőrizd, hogy be van-e kapcsolva."}
                else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },modifier=Modifier.align(Alignment.BottomEnd).padding(end=14.dp,bottom=LocalBottomOverlayInset.current+52.dp).size(54.dp)) {
                Icon(Icons.Outlined.MyLocation,"Saját helyzet mutatása")
            }
        selectedVehicle?.let {VehicleDrawer(vm,it) {selectedVehicle=null}}
        SnackbarHost(snackbars,Modifier.align(Alignment.BottomCenter).padding(bottom=LocalBottomOverlayInset.current+12.dp))
    }
}
