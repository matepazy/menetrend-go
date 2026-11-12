package hu.matepazy.menetrendgo

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Color
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/** Both renderers receive the same data without routing native maps through JavaScript. */
internal interface TransportMapSurface {
    fun call(name: String, vararg args: Any?)
    fun dispose()
}

internal class LeafletMapSurface(private val view: WebView) : TransportMapSurface {
    override fun call(name: String, vararg args: Any?) {
        view.evaluateJavascript("window.$name(${JSONArray(args.toList()).toString().removeSurrounding("[", "]")});",null)
    }
    override fun dispose() { view.removeJavascriptInterface("NativeMap");view.destroy() }
}

@Composable
internal fun GoogleTransportMap(
    modifier: Modifier,
    onReady: (TransportMapSurface) -> Unit,
    onVehicle: (String) -> Unit,
    onStop: (String) -> Unit,
    onViewport: (Double,Double,Int) -> Unit,
    onLocationVisible: (Boolean) -> Unit,
    loadPaths: suspend (List<String>) -> JSONObject
) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val scope=rememberCoroutineScope()
    val ready by rememberUpdatedState(onReady)
    val selectVehicle by rememberUpdatedState(onVehicle)
    val selectStop by rememberUpdatedState(onStop)
    val viewport by rememberUpdatedState(onViewport)
    val locationVisible by rememberUpdatedState(onLocationVisible)
    val paths by rememberUpdatedState(loadPaths)
    val available=remember {GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)==ConnectionResult.SUCCESS}
    if (!BuildConfig.MAPS_API_KEY_CONFIGURED || !available) {
        Box(modifier) {
            Surface(Modifier.align(Alignment.Center).padding(24.dp),shape=MaterialTheme.shapes.large) {
                Text(if (!BuildConfig.MAPS_API_KEY_CONFIGURED) "A Google térkép még nincs beállítva. Add meg az API-kulcsot a fordításhoz, vagy válassz másik térképtípust."
                    else "A Google térképhez Google Play-szolgáltatások szükségesek. Válassz másik térképtípust.",Modifier.padding(20.dp))
            }
        }
        return
    }
    val view=remember {TransportGoogleMapView(context).apply {onCreate(null)}}
    var surface by remember {mutableStateOf<GoogleMapSurface?>(null)}
    DisposableEffect(view,lifecycle) {
        var active=true
        var started=false
        var resumed=false
        fun sync() {
            val state=lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.STARTED) && !started) {view.onStart();started=true}
            if (state.isAtLeast(Lifecycle.State.RESUMED) && !resumed) {view.onResume();resumed=true}
            if (!state.isAtLeast(Lifecycle.State.RESUMED) && resumed) {surface?.finishMotion();view.onPause();resumed=false}
            if (!state.isAtLeast(Lifecycle.State.STARTED) && started) {view.onStop();started=false}
        }
        val observer=LifecycleEventObserver {_,_->sync()}
        lifecycle.addObserver(observer)
        sync()
        val memory=MapMemoryCallbacks(view)
        context.registerComponentCallbacks(memory)
        view.getMapAsync {map->
            if (active && lifecycle.currentState!=Lifecycle.State.DESTROYED) {
                surface=GoogleMapSurface(map,view,
                    {selectVehicle(it)},{selectStop(it)},
                    {lat,lon,radius->viewport(lat,lon,radius)},{locationVisible(it)},
                    {ids->scope.launch {
                        try {surface?.call("updateVehiclePaths",paths(ids))}
                        catch(e:kotlinx.coroutines.CancellationException) {throw e}
                        catch(_:Exception) {surface?.retryPaths(ids)}
                    }})
                view.doOnLayout {if(active)surface?.let {ready(it)}}
            }
        }
        onDispose {
            active=false
            lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(memory)
            surface?.dispose()
            surface=null
            if(resumed)view.onPause()
            if(started)view.onStop()
            view.onDestroy()
        }
    }
    AndroidView(modifier=modifier,factory={view})
}

private class TransportGoogleMapView(context:android.content.Context):MapView(context) {
    var lastTap:android.graphics.Point?=null
    var lastTapAt=0L
    override fun dispatchTouchEvent(event:android.view.MotionEvent):Boolean {
        if(event.actionMasked==android.view.MotionEvent.ACTION_UP) {
            lastTap=android.graphics.Point(event.x.toInt(),event.y.toInt())
            lastTapAt=android.os.SystemClock.elapsedRealtime()
        }
        return super.dispatchTouchEvent(event)
    }
}

private class MapMemoryCallbacks(private val view:MapView):android.content.ComponentCallbacks {
    @Deprecated("Legacy memory callback")
    override fun onLowMemory() {view.onLowMemory()}
    override fun onConfigurationChanged(config:android.content.res.Configuration) {}
}

private class GoogleMapSurface(
    private val map:GoogleMap,
    private val view:TransportGoogleMapView,
    private val selectVehicle:(String)->Unit,
    private val selectStop:(String)->Unit,
    private val viewport:(Double,Double,Int)->Unit,
    private val locationVisible:(Boolean)->Unit,
    private val requestPaths:(List<String>)->Unit
):TransportMapSurface {
    private val density=view.resources.displayMetrics.density
    private val artwork=NativeMapArtwork(view.context)
    private var ink=0xff112d43.toInt()
    private var surfaceColor=0xfff8fbff.toInt()
    private var dark=false
    private var vehicles=JSONArray()
    private var stops=JSONArray()
    private var stopsEnabled=false
    private var routeMode=false
    private var motionEnabled=true
    private var top=0
    private var bottom=0
    private var location:Marker?=null
    private var routeKey=""
    private val routes=mutableListOf<Polyline>()
    private val routeStops=mutableListOf<Marker>()
    private val alerts=mutableListOf<Circle>()
    private val vehicleMarkers=mutableMapOf<String,Marker>()
    private val stopMarkers=mutableListOf<Marker>()
    private val animations=mutableMapOf<String,ValueAnimator>()
    private val targets=mutableMapOf<String,LatLng>()
    private val pathCache=linkedMapOf<String,List<GeoPoint>>()
    private val requestedPaths=linkedSetOf<String>()
    private val stopEnclosures=mutableListOf<Polygon>()
    private val stopLabels=mutableListOf<GroundOverlay>()
    private var displayedStops=emptyList<Station>()
    private var destroyed=false

    init {
        map.uiSettings.apply {isZoomControlsEnabled=false;isMapToolbarEnabled=false;isMyLocationButtonEnabled=false;isCompassEnabled=false;isRotateGesturesEnabled=false;isTiltGesturesEnabled=false}
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(47.17,19.5),7f))
        map.setOnCameraIdleListener {
            drawVehicles(false)
            drawStops()
            reportViewport()
        }
        map.setOnMarkerClickListener {marker->
            val tag=marker.tag as? Pair<*,*>
            when(tag?.first) {
                "vehicle"->selectVehicle(tag.second as String)
                "stop"->{
                    val tap=view.lastTap?.takeIf {android.os.SystemClock.elapsedRealtime()-view.lastTapAt<500}
                    val nearest=if(tap!=null)displayedStops.minByOrNull {stop->
                        val point=map.projection.toScreenLocation(LatLng(stop.lat,stop.lon))
                        hypot((point.x-tap.x).toDouble(),(point.y-tap.y).toDouble())
                    }?.id else null
                    selectStop(nearest ?: tag.second as String)
                }
                "cluster"->{
                    @Suppress("UNCHECKED_CAST") val points=tag.second as List<LatLng>
                    val bounds=LatLngBounds.builder().apply {points.forEach(::include)}.build()
                    val screen=points.map(map.projection::toScreenLocation)
                    val spanX=(screen.maxOf {it.x}-screen.minOf {it.x}).coerceAtLeast(1)
                    val spanY=(screen.maxOf {it.y}-screen.minOf {it.y}).coerceAtLeast(1)
                    val factor=min((view.width-120*density).coerceAtLeast(1f)/spanX,(view.height-top-bottom-120*density).coerceAtLeast(1f)/spanY)
                    val zoom=min(19.0,min(map.cameraPosition.zoom+3.0,map.cameraPosition.zoom+ln(factor.toDouble())/ln(2.0)))
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(bounds.center,zoom.toFloat()))
                }
            }
            true
        }
    }
    override fun call(name:String,vararg args:Any?) {
        if(destroyed)return
        when(name) {
            "setMapType"->{
                map.mapType=when(args[0]) {"satellite"->GoogleMap.MAP_TYPE_SATELLITE;"hybrid"->GoogleMap.MAP_TYPE_HYBRID;else->GoogleMap.MAP_TYPE_NORMAL}
                applyBasemapStyle()
            }
            "setAppTheme"->{
                val color=(args[0] as Number).toInt()
                dark=Color.red(color)*.299+Color.green(color)*.587+Color.blue(color)*.114<128
                surfaceColor=(args[1] as Number).toInt()
                ink=(args[2] as Number).toInt()
                applyBasemapStyle()
                drawStops()
            }
            "setBottomInset"->{bottom=((args[0] as Number).toFloat()*density).toInt();padding()}
            "setTopInset"->{top=((args[0] as Number).toFloat()*density).toInt();padding()}
            "setRouteMode"->routeMode=args[0] as Boolean
            "setVehicleMotionEnabled"->{motionEnabled=args[0] as Boolean;if(!motionEnabled)finishMotion()}
            "setStopsEnabled"->{stopsEnabled=args[0] as Boolean;drawStops();reportViewport()}
            "updateStops"->{stops=args[0] as JSONArray;drawStops()}
            "updateVehicles"->{requestedPaths.clear();vehicles=args[0] as JSONArray;drawVehicles(true)}
            "updateVehiclePaths"->{
                val items=args[0] as JSONObject
                items.keys().forEach {id->
                    val coordinates=items.optJSONArray(id) ?: return@forEach
                    val points=(0 until coordinates.length()).mapNotNull {i->coordinates.optJSONArray(i)?.let {GeoPoint(it.optDouble(1),it.optDouble(0))}}
                    if(points.size>=2 && points.all {it.lat.isFinite() && it.lon.isFinite()})pathCache[id]=points
                }
                while(pathCache.size>256)pathCache.remove(pathCache.keys.first())
            }
            "updateLocation"->{
                val point=LatLng((args[0] as Number).toDouble(),(args[1] as Number).toDouble())
                if(location==null)location=map.addMarker(MarkerOptions().position(point).icon(artwork.dot(8f,0xff2864d9.toInt(),Color.WHITE,3f)).anchor(.5f,.5f).title("Jelenlegi helyzet").zIndex(5f))
                location?.position=point
                if(args[2] as Boolean)map.animateCamera(CameraUpdateFactory.newLatLngZoom(point,max(14f,map.cameraPosition.zoom)))
                reportViewport()
            }
            "centerOn"->map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng((args[0] as Number).toDouble(),(args[1] as Number).toDouble()),max(14f,map.cameraPosition.zoom)))
            "updateRoute"->drawRoute(args[0] as JSONArray)
            "updateDestinationAlert"->{
                alerts.forEach {it.remove()};alerts.clear()
                (args[0] as? JSONObject)?.let {area->
                    val color=color(area)
                    fun circle(item:JSONObject,guard:Boolean) {
                        map.addCircle(CircleOptions().center(item.point()).radius(item.getDouble("radius"))
                            .strokeColor(color).strokeWidth(2*density).fillColor((color and 0xffffff) or 0x20000000)
                            .apply {if(guard)strokePattern(listOf(Dash(4*density),Gap(5*density)))})?.let(alerts::add)
                    }
                    circle(area,false);area.optJSONObject("guard")?.let {circle(it,true)}
                }
            }
        }
    }
    private fun padding() {
        // The SDK adds a 5dp left logo margin. Align its visible edge with the floating bar.
        val left=((AppLayout.PageGutter.value-5)*density).roundToInt()
        // The overlay includes a 6dp top margin, and the SDK adds its own 6dp below the logo.
        // Subtract 4dp to leave an 8dp visible gap above the bar, following its measured height.
        val logoBottom=if(bottom>0) (bottom-4*density).roundToInt().coerceAtLeast(0) else (12*density).roundToInt()
        map.setPadding(left,top+(12*density).toInt(),(12*density).toInt(),logoBottom)
    }
    private fun JSONObject.point()=LatLng(getDouble("lat"),getDouble("lon"))
    private fun color(item:JSONObject)=runCatching {Color.parseColor(item.optString("color"))}.getOrDefault(Color.rgb(40,100,217))
    private fun applyBasemapStyle() {
        val rules=JSONArray()
        if(dark) {
            rules.put(JSONObject("""{"elementType":"geometry","stylers":[{"color":"#242424"}]}"""))
            rules.put(JSONObject("""{"elementType":"labels.text.fill","stylers":[{"color":"#bdbdbd"}]}"""))
            rules.put(JSONObject("""{"elementType":"labels.text.stroke","stylers":[{"color":"#212121"}]}"""))
            rules.put(JSONObject("""{"featureType":"road","elementType":"geometry","stylers":[{"color":"#383838"}]}"""))
            rules.put(JSONObject("""{"featureType":"water","elementType":"geometry","stylers":[{"color":"#111820"}]}"""))
        }
        // Google remains the basemap. The app's own live transport layers own the foreground.
        rules.put(JSONObject("""{"featureType":"poi","elementType":"labels","stylers":[{"visibility":"off"}]}"""))
        rules.put(JSONObject("""{"featureType":"transit","elementType":"labels.icon","stylers":[{"visibility":"off"}]}"""))
        map.setMapStyle(MapStyleOptions(rules.toString()))
    }
    private fun reportViewport() {
        val point=location?.position?.let(map.projection::toScreenLocation)
        locationVisible(point!=null && point.x>=8*density && point.x<=view.width-8*density && point.y>=top+8*density && point.y<=view.height-bottom-32*density)
        if(!stopsEnabled)return
        if(map.cameraPosition.zoom<16f) {viewport(0.0,0.0,0);return}
        val center=map.cameraPosition.target
        val bounds=map.projection.visibleRegion.latLngBounds
        val result=FloatArray(1)
        android.location.Location.distanceBetween(center.latitude,center.longitude,bounds.northeast.latitude,bounds.northeast.longitude,result)
        viewport(center.latitude,center.longitude,(ceil(result[0]*1.1).toInt()+300).coerceIn(100,15000))
    }
    private fun drawStops() {
        stopMarkers.forEach {it.remove()};stopMarkers.clear()
        stopEnclosures.forEach {it.remove()};stopEnclosures.clear()
        stopLabels.forEach {it.remove()};stopLabels.clear()
        displayedStops=emptyList()
        if(!stopsEnabled || map.cameraPosition.zoom<16f)return
        val all=stops.objects().mapNotNull {runCatching {Station.parse(it)}.getOrNull()}
        val selected=selectNativeMapStops(all,map.cameraPosition.zoom.toDouble())
        val groups=groupNativeMapStops(all).filter {group->
            group.any {it.id in selected} && group.any {
                val p=map.projection.toScreenLocation(LatLng(it.lat,it.lon))
                p.x in (-view.width/10)..(view.width*11/10) && p.y in (-view.height/10)..(view.height*11/10)
            }
        }
        displayedStops=groups.flatten()
        groups.forEach {group->
            if(group.size>1) {
                val points=group.map {val p=map.projection.toScreenLocation(LatLng(it.lat,it.lon));MapPixel(p.x.toDouble(),p.y.toDouble())}
                val outline=nativeMapStopOutline(points,18.0*density).map {map.projection.fromScreenLocation(android.graphics.Point(it.x.toInt(),it.y.toInt()))}
                map.addPolygon(PolygonOptions().addAll(outline).strokeColor(0x8c737b80.toInt()).strokeWidth(1.5f*density)
                    .fillColor(0x1f737b80).zIndex(.5f).clickable(false))?.let(stopEnclosures::add)
                val x=points.map {it.x}.average()
                var y=points.map {it.y}.average()
                if(points.any {abs(it.x-x)<70*density && abs(it.y-y)<22*density})y=points.maxOf {it.y}+30*density
                val sw=map.projection.fromScreenLocation(android.graphics.Point((x-70*density).toInt(),(y+56*density).toInt()))
                val ne=map.projection.fromScreenLocation(android.graphics.Point((x+70*density).toInt(),y.toInt()))
                map.addGroundOverlay(GroundOverlayOptions().image(artwork.label(group.first().name,ink,surfaceColor))
                    .positionFromBounds(LatLngBounds(sw,ne)).zIndex(1.5f).clickable(false))?.let(stopLabels::add)
            }
        }
        displayedStops.forEach {stop->
            map.addMarker(MarkerOptions().position(LatLng(stop.lat,stop.lon)).title(stop.name+" · indulási tábla")
                .icon(artwork.stop(stop.modes)).anchor(.5f,.5f).zIndex(1f))?.let {it.tag="stop" to stop.id;stopMarkers.add(it)}
        }
    }
    private fun drawVehicles(animate:Boolean) {
        val visible=vehicles.objects().filter {
            val point=map.projection.toScreenLocation(it.point())
            point.x in (-view.width/5)..(view.width*6/5) && point.y in (-view.height/5)..(view.height*6/5)
        }
        val cells=visible.groupBy {v->
            val point=map.projection.toScreenLocation(v.point())
            if(routeMode) v.getString("id") else "${floor(point.x/(64*density))},${floor(point.y/(64*density))}"
        }
        val keep=mutableSetOf<String>()
        val paths=mutableListOf<String>()
        cells.forEach {(_,items)->
            val v=items.first()
            val cluster=items.size>1
            val id=if(cluster) "cluster:${items.map {it.getString("id")}.sorted().joinToString("|")}" else v.getString("id")
            keep.add(id)
            val point=if(cluster)LatLng(items.map {it.getDouble("lat")}.average(),items.map {it.getDouble("lon")}.average()) else v.point()
            val marker=vehicleMarkers[id] ?: map.addMarker(MarkerOptions().position(point).anchor(.5f,.5f).zIndex(2f))?.also {vehicleMarkers[id]=it} ?: return@forEach
            marker.title=if(cluster) "${items.size} jármű · nagyítás" else "${v.optString("type")} ${v.optString("name")} ${v.optString("headsign")}"
            marker.tag=if(cluster) "cluster" to items.map {it.point()} else "vehicle" to id
            val heading=v.optDouble("heading").takeIf {it.isFinite() && it in 0.0..360.0}
            marker.setIcon(if(cluster) artwork.cluster(items.size) else artwork.vehicle(v.optString("mode"),heading))
            if(!cluster && !pathCache.containsKey(id) && id !in requestedPaths && paths.size<24)paths.add(id)
            if(animate) {
                animations.remove(id)?.cancel()
                targets.remove(id)
                val movement=if(motionEnabled && ValueAnimator.areAnimatorsEnabled()) pathCache[id]?.let {googleVehicleMovement(it,marker.position,point)} else null
                if(movement!=null) {
                    targets[id]=point
                    animations[id]=ValueAnimator.ofFloat(0f,1f).apply {
                        duration=1000
                        interpolator=android.view.animation.DecelerateInterpolator(1.5f)
                        addUpdateListener {marker.position=movement(it.animatedFraction)}
                        addListener(object:AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation:Animator) {
                                animations.remove(id)
                                targets.remove(id)
                            }
                        })
                        start()
                    }
                } else marker.position=point
            } else if(id !in animations)marker.position=point
        }
        (vehicleMarkers.keys-keep).forEach {id->animations.remove(id)?.cancel();targets.remove(id);vehicleMarkers.remove(id)?.remove()}
        if(paths.isNotEmpty()) {
            requestedPaths.addAll(paths)
            while(requestedPaths.size>512)requestedPaths.remove(requestedPaths.first())
            requestPaths(paths)
        }
    }
    fun retryPaths(ids:List<String>) {requestedPaths.removeAll(ids.toSet())}
    private fun drawRoute(legs:JSONArray) {
        val key=legs.toString()
        if(routeKey==key)return
        routeKey=key
        routes.forEach {it.remove()};routes.clear();routeStops.forEach {it.remove()};routeStops.clear()
        val points=mutableListOf<LatLng>()
        for(i in 0 until legs.length()) {
            val leg=legs.getJSONObject(i)
            val color=color(leg)
            val line=runCatching {decodeRoute(leg.optString("geometry")).map {LatLng(it.lat,it.lon)}}.getOrDefault(emptyList())
                .ifEmpty {listOf(leg.getJSONObject("from").point(),leg.getJSONObject("to").point())}
            points.addAll(line)
            map.addPolyline(PolylineOptions().addAll(line).color(color).width(5*density).apply {
                if(leg.optBoolean("walking"))pattern(listOf(Dash(5*density),Gap(9*density)))
            })?.let(routes::add)
            listOf("from","to").forEach {end->map.addMarker(MarkerOptions().position(leg.getJSONObject(end).point()).icon(artwork.dot(6f,Color.WHITE,color,3f)).anchor(.5f,.5f).zIndex(1f))?.let(routeStops::add)}
        }
        if(points.isNotEmpty()) {
            if(routeMode)for(i in 0 until vehicles.length())points.add(vehicles.getJSONObject(i).point())
            val bounds=LatLngBounds.builder().apply {points.forEach(::include)}.build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,(60*density).toInt()))
        }
    }
    fun finishMotion() {
        val finalPositions=targets.toMap()
        animations.values.toList().forEach {it.cancel()};animations.clear()
        finalPositions.forEach {(id,point)->vehicleMarkers[id]?.position=point};targets.clear()
    }
    override fun dispose() {
        destroyed=true
        finishMotion()
        map.setOnCameraIdleListener(null);map.setOnMarkerClickListener(null)
    }
}

/** Reject off-route fixes, backward jitter and jumps. Choose a short forward passage at crossings. */
internal fun googleVehicleMovement(points:List<GeoPoint>,from:LatLng,to:LatLng):((Float)->LatLng)? {
    if(points.size<2)return null
    val scale=cos(points.first().lat*PI/180)*111195
    fun distance(a:GeoPoint,b:GeoPoint)=hypot((a.lon-b.lon)*scale,(a.lat-b.lat)*111195)
    val lengths=DoubleArray(points.size)
    for(i in 1 until points.size)lengths[i]=lengths[i-1]+distance(points[i-1],points[i])
    fun project(p:LatLng):List<Pair<Double,Double>> = (1 until points.size).mapNotNull {i->
        val a=points[i-1];val b=points[i];val length=lengths[i]-lengths[i-1]
        if(length==0.0)return@mapNotNull null
        val dx=(b.lon-a.lon)*scale;val dy=(b.lat-a.lat)*111195
        val x=(p.longitude-a.lon)*scale;val y=(p.latitude-a.lat)*111195
        val t=((x*dx+y*dy)/(length*length)).coerceIn(0.0,1.0)
        val error=hypot(x-dx*t,y-dy*t)
        if(error<=60) (lengths[i-1]+length*t) to error else null
    }
    val direct=distance(GeoPoint(from.latitude,from.longitude),GeoPoint(to.latitude,to.longitude))
    if(direct<1 || direct>10000)return null
    val candidates=project(from).flatMap {start->project(to).mapNotNull {end->
        val length=end.first-start.first
        if(length<1 || length>max(1500.0,direct*4))null else Triple(start.first,end.first,start.second+end.second+length*.01)
    }}
    val best=candidates.minByOrNull {it.third} ?: return null
    return {fraction->
        val d=best.first+(best.second-best.first)*fraction
        val i=(1 until points.size).firstOrNull {lengths[it]>=d} ?: points.lastIndex
        val length=lengths[i]-lengths[i-1]
        val t=if(length>0) ((d-lengths[i-1])/length).coerceIn(0.0,1.0) else 0.0
        LatLng(points[i-1].lat+(points[i].lat-points[i-1].lat)*t,points[i-1].lon+(points[i].lon-points[i-1].lon)*t)
    }
}
