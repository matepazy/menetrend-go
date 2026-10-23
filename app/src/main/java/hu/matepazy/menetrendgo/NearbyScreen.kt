package hu.matepazy.menetrendgo

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.webkit.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.json.*

@Composable fun NearbyScreen(vm: AppState, journey: Journey?, focusedId: String?, onClearRoute: () -> Unit, select: (Vehicle) -> Unit) {
    val context=LocalContext.current
    val bottomInsetPx=with(androidx.compose.ui.platform.LocalDensity.current) {LocalBottomOverlayInset.current.toPx()}
    var web by remember {mutableStateOf<WebView?>(null)}
    var ready by remember {mutableStateOf(false)}
    var center by remember {mutableStateOf<Pair<Double,Double>?>(null)}
    var locationMessage by remember {mutableStateOf<String?>(null)}
    var locating by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val onSelect by rememberUpdatedState(select)
    val currentVehicles by rememberUpdatedState(vm.vehicles)
    fun locate() {
        val manager=context.getSystemService(LocationManager::class.java)
        locating=true;locationMessage=null
        try {
            val providers=manager.getProviders(true)
            val last=providers.mapNotNull {runCatching {manager.getLastKnownLocation(it)}.getOrNull()}.maxByOrNull {it.time}
            if(last!=null && System.currentTimeMillis()-last.time<30*60_000) {center=last.latitude to last.longitude;locating=false}
            else if(android.os.Build.VERSION.SDK_INT>=30 && manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.getCurrentLocation(LocationManager.NETWORK_PROVIDER,null,context.mainExecutor) {loc->
                    locating=false
                    if(loc!=null) center=loc.latitude to loc.longitude else locationMessage="Nem sikerült meghatározni a helyed. Mozgasd a térképet a keresett területre."
                }
                Handler(Looper.getMainLooper()).postDelayed({if(locating){locating=false;locationMessage="A helymeghatározás túl sokáig tart. A térkép kézzel is mozgatható."}},15_000)
            } else {locating=false;locationMessage="Kapcsold be a helymeghatározást, vagy mozgasd a térképet a keresett területre."}
        } catch(e: SecurityException) {locating=false;locationMessage="A helyhozzáférés nincs engedélyezve. A térképet kézzel is használhatod."}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->if(granted) locate() else locationMessage="A helyhozzáférés nélkül kézzel választhatsz területet a térképen."}
    LaunchedEffect(ready,vm.vehicles,journey) {
        if(ready) {
            val array=JSONArray(vm.vehicles.map { vehicle ->
                JSONObject().put("id",vehicle.id).put("name",vehicle.name).put("headsign",vehicle.headsign)
                    .put("lat",vehicle.lat).put("lon",vehicle.lon).put("mode",vehicle.mode)
                    .put("color",trainCategoryColor(vehicle.badgeLeg()) ?: vehicle.routeColor)
                    .put("type",hungarianServiceName(vehicle.category,vehicle.mode))
            })
            val route=JSONArray(journey?.legs.orEmpty().map {leg->JSONObject().put("geometry",leg.geometry).put("from",leg.from.json()).put("to",leg.to.json())})
            web?.evaluateJavascript("window.updateVehicles($array);window.updateRoute($route);",null)
        }
    }
    LaunchedEffect(ready,center) {if(ready && center!=null) web?.evaluateJavascript("window.centerOn(${center!!.first},${center!!.second});",null)}
    LaunchedEffect(ready,focusedId,vm.vehicles) {
        if(ready && focusedId!=null) vm.vehicles.find {it.id==focusedId}?.let {web?.evaluateJavascript("window.centerOn(${it.lat},${it.lon});",null)}
    }
    DisposableEffect(Unit) {onDispose {web?.removeJavascriptInterface("NativeMap");web?.destroy();web=null}}
    Box(Modifier.fillMaxSize()) {
            AndroidView(modifier=Modifier.fillMaxSize(),factory={ctx->
                WebView(ctx).apply {
                    layoutParams=android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled=true
                    settings.allowFileAccess=false;settings.allowContentAccess=false
                    settings.domStorageEnabled=false
                    settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.userAgentString="MenetrendGO/${BuildConfig.VERSION_NAME} (Android; https://github.com/matepazy/menetrend-go)"
                    addJavascriptInterface(object {
                        @JavascriptInterface fun selectVehicle(id: String) {Handler(Looper.getMainLooper()).post {currentVehicles.find {it.id==id}?.let(onSelect)}}
                    },"NativeMap")
                    webViewClient=object:WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            view.evaluateJavascript("window.resizeMap && window.resizeMap();window.setBottomInset("+(bottomInsetPx)+");",null)
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
            FilledTonalIconButton(onClick={
                if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED) locate() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },modifier=Modifier.align(Alignment.BottomEnd).padding(end=14.dp,bottom=LocalBottomOverlayInset.current+52.dp).size(54.dp),enabled=!locating) {
                Icon(Icons.Outlined.MyLocation,if(locating) "Helymeghatározás folyamatban" else "Járművek a közelemben")
            }
    }
}
