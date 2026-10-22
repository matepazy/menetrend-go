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
    var web by remember {mutableStateOf<WebView?>(null)}
    var ready by remember {mutableStateOf(false)}
    var center by remember {mutableStateOf<Pair<Double,Double>?>(null)}
    var locationMessage by remember {mutableStateOf<String?>(null)}
    var locating by remember {mutableStateOf(false)}
    var filter by remember {mutableStateOf("Összes")}
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
    LaunchedEffect(ready,vm.vehicles,journey,filter) {
        if(ready) {
            val vehicles=vm.vehicles.filter {filter=="Összes" || if(filter=="Vonat") it.mode in listOf("RAIL","SUBURBAN_RAILWAY","TRAMTRAIN") else it.mode in listOf("COACH","BUS","RAIL_REPLACEMENT_BUS")}
            val array=JSONArray(vehicles.map {JSONObject().put("id",it.id).put("name",it.name).put("headsign",it.headsign).put("lat",it.lat).put("lon",it.lon).put("mode",it.mode)})
            val route=JSONArray(journey?.legs.orEmpty().map {leg->JSONObject().put("geometry",leg.geometry).put("from",leg.from.json()).put("to",leg.to.json())})
            web?.evaluateJavascript("window.updateVehicles($array);window.updateRoute($route);",null)
        }
    }
    LaunchedEffect(ready,center) {if(ready && center!=null) web?.evaluateJavascript("window.centerOn(${center!!.first},${center!!.second});",null)}
    LaunchedEffect(ready,focusedId,vm.vehicles) {
        if(ready && focusedId!=null) vm.vehicles.find {it.id==focusedId}?.let {web?.evaluateJavascript("window.centerOn(${it.lat},${it.lon});",null)}
    }
    DisposableEffect(Unit) {onDispose {web?.removeJavascriptInterface("NativeMap");web?.destroy();web=null}}
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp)) {
        PageTitle(if(journey==null) "Térkép" else "Az utad a térképen", "Koppints egy járműre a megállókért")
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Összes","Vonat","Busz").forEach {label->FilterChip(filter==label,{filter=label},label={Text(label)},shape=CircleShape)}
            Spacer(Modifier.weight(1f))
            IconButton(onClick={scope.launch {vm.refreshVehicles()}},enabled=!vm.vehiclesLoading) {Icon(Icons.Outlined.Refresh,"Járműadatok frissítése")}
        }
        val age=System.currentTimeMillis()/1000-vm.vehicleUpdated
        if(vm.vehicleUpdated>0) Text(if(age>180) "Régi adat · utolsó frissítés: ${timeLabel(vm.vehicleUpdated*1000)}" else "Frissítve: ${timeLabel(vm.vehicleUpdated*1000)} · 75 mp-enként",style=MaterialTheme.typography.bodySmall,color=if(age>180) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=10.dp))
        if(vm.vehicleError!=null) Text(vm.vehicleError!!,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(bottom=8.dp))
        if(!vm.vehiclesLoading && vm.vehicleError==null && vm.vehicleUpdated>0 && vm.vehicles.isEmpty()) Text("Most nincs megjeleníthető járműadat.",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(bottom=8.dp))
        if(locationMessage!=null) Text(locationMessage!!,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=8.dp))
        if(vm.vehiclesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(32.dp))) {
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
                            view.evaluateJavascript("window.resizeMap && window.resizeMap();",null)
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
            },modifier=Modifier.align(Alignment.BottomEnd).padding(end=14.dp,bottom=34.dp).size(54.dp),enabled=!locating) {
                Icon(Icons.Outlined.MyLocation,if(locating) "Helymeghatározás folyamatban" else "Járművek a közelemben")
            }
        }
        if(journey!=null || focusedId!=null) TextButton(onClick=onClearRoute){Text("Követés / útvonal elrejtése")}
        Spacer(Modifier.height(8.dp))
    }
}
