package hu.matepazy.menetrendgo

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
    val view=remember {MapView(context).apply {onCreate(null)}}
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
                surface=GoogleMapSurface(map,view.resources.displayMetrics.density,
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

private class MapMemoryCallbacks(private val view:MapView):android.content.ComponentCallbacks {
    @Deprecated("Legacy memory callback")
    override fun onLowMemory() {view.onLowMemory()}
    override fun onConfigurationChanged(config:android.content.res.Configuration) {}
}

private class GoogleMapSurface(
    private val map:GoogleMap,
    private val density:Float,
    private val selectVehicle:(String)->Unit,
    private val selectStop:(String)->Unit,
    private val viewport:(Double,Double,Int)->Unit,
    private val locationVisible:(Boolean)->Unit,
    private val requestPaths:(List<String>)->Unit
):TransportMapSurface {
    private var vehicles=JSONArray()
    private var stops=JSONArray()
    private var stopsEnabled=false
    private var routeMode=false
    private var motionEnabled=true
    private var top=0
    private var bottom=0
    private var location:Circle?=null
    private var routeKey=""
    private val routes=mutableListOf<Polyline>()
    private val routeStops=mutableListOf<Circle>()
    private val alerts=mutableListOf<Circle>()
    private val vehicleMarkers=mutableMapOf<String,Marker>()
    private val stopMarkers=mutableListOf<Marker>()
    private val animations=mutableMapOf<String,ValueAnimator>()
    private val targets=mutableMapOf<String,LatLng>()
    private val pathCache=linkedMapOf<String,List<GeoPoint>>()
    private val requestedPaths=linkedSetOf<String>()
    private val icons=linkedMapOf<String,BitmapDescriptor>()
    private var destroyed=false

    init {
        map.uiSettings.apply {isZoomControlsEnabled=false;isMapToolbarEnabled=false;isMyLocationButtonEnabled=false;isCompassEnabled=false}
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
                "stop"->selectStop(tag.second as String)
                "cluster"->map.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position,map.cameraPosition.zoom+2f))
            }
            true
        }
    }
    override fun call(name:String,vararg args:Any?) {
        if(destroyed)return
        when(name) {
            "setMapType"->map.mapType=when(args[0]) {"satellite"->GoogleMap.MAP_TYPE_SATELLITE;"hybrid"->GoogleMap.MAP_TYPE_HYBRID;else->GoogleMap.MAP_TYPE_NORMAL}
            "setAppTheme"->{
                val color=(args[0] as Number).toInt()
                val dark=Color.red(color)*.299+Color.green(color)*.587+Color.blue(color)*.114<128
                map.setMapStyle(if(dark) MapStyleOptions("""[{"elementType":"geometry","stylers":[{"color":"#242424"}]},{"elementType":"labels.text.fill","stylers":[{"color":"#bdbdbd"}]},{"elementType":"labels.text.stroke","stylers":[{"color":"#212121"}]},{"featureType":"road","elementType":"geometry","stylers":[{"color":"#383838"}]},{"featureType":"water","elementType":"geometry","stylers":[{"color":"#111820"}]}]""") else null)
            }
            "setBottomInset"->{bottom=((args[0] as Number).toFloat()*density).toInt();padding()}
            "setTopInset"->{top=((args[0] as Number).toFloat()*density).toInt();padding()}
            "setRouteMode"->routeMode=args[0] as Boolean
            "setVehicleMotionEnabled"->{motionEnabled=args[0] as Boolean;if(!motionEnabled)finishMotion()}
            "setStopsEnabled"->{stopsEnabled=args[0] as Boolean;drawStops();reportViewport()}
            "updateStops"->{stops=args[0] as JSONArray;drawStops()}
            "updateVehicles"->{vehicles=args[0] as JSONArray;drawVehicles(true)}
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
                if(location==null)location=map.addCircle(CircleOptions().center(point).radius(8.0).fillColor(Color.rgb(40,100,217)).strokeColor(Color.WHITE).strokeWidth(3*density).zIndex(5f))
                location?.center=point
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
    private fun padding() {map.setPadding((12*density).toInt(),top+(12*density).toInt(),(12*density).toInt(),bottom+(12*density).toInt())}
    private fun JSONObject.point()=LatLng(getDouble("lat"),getDouble("lon"))
    private fun color(item:JSONObject)=runCatching {Color.parseColor(item.optString("color"))}.getOrDefault(Color.rgb(40,100,217))
    private fun reportViewport() {
        val bounds=map.projection.visibleRegion.latLngBounds
        locationVisible(location?.center?.let(bounds::contains) ?: false)
        location?.radius=8*156543.03392*cos(location!!.center.latitude*PI/180)/2.0.pow(map.cameraPosition.zoom.toDouble())*density
        if(!stopsEnabled || map.cameraPosition.zoom<16f)return
        val center=map.cameraPosition.target
        val result=FloatArray(1)
        android.location.Location.distanceBetween(center.latitude,center.longitude,bounds.northeast.latitude,bounds.northeast.longitude,result)
        viewport(center.latitude,center.longitude,result[0].toInt().coerceIn(100,15000))
    }
    private fun drawStops() {
        stopMarkers.forEach {it.remove()};stopMarkers.clear()
        if(!stopsEnabled || map.cameraPosition.zoom<16f)return
        for(i in 0 until stops.length()) {
            val stop=stops.getJSONObject(i)
            map.addMarker(MarkerOptions().position(stop.point()).title(stop.optString("name"))
                .icon(icon("•",Color.rgb(78,101,129))).anchor(.5f,.5f).zIndex(1f))?.let {it.tag="stop" to stop.getString("id");stopMarkers.add(it)}
        }
    }
    private fun drawVehicles(animate:Boolean) {
        val bounds=map.projection.visibleRegion.latLngBounds
        val visible=(0 until vehicles.length()).map {vehicles.getJSONObject(it)}.filter {bounds.contains(it.point())}
        val cells=visible.groupBy {v->
            val point=map.projection.toScreenLocation(v.point())
            if(map.cameraPosition.zoom>=14f) v.getString("id") else "${floor(point.x/(64*density))},${floor(point.y/(64*density))}"
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
            marker.title=if(cluster) "${items.size} jármű" else "${v.optString("name")} · ${v.optString("headsign")}" 
            marker.tag=(if(cluster) "cluster" else "vehicle") to id
            marker.setIcon(icon(if(cluster) items.size.toString() else v.optString("name").ifBlank {v.optString("type","Jármű")},if(cluster)Color.rgb(36,76,134) else color(v)))
            if(!cluster && !pathCache.containsKey(id) && id !in requestedPaths && paths.size<24)paths.add(id)
            if(animate) {
                animations.remove(id)?.cancel()
                targets.remove(id)
                val movement=if(motionEnabled && ValueAnimator.areAnimatorsEnabled()) pathCache[id]?.let {googleVehicleMovement(it,marker.position,point)} else null
                if(movement!=null) {
                    targets[id]=point
                    animations[id]=ValueAnimator.ofFloat(0f,1f).apply {
                        duration=1000
                        interpolator=android.view.animation.LinearInterpolator()
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
    private fun icon(label:String,color:Int):BitmapDescriptor {
        val text=label.take(24)
        val key="$text:$color"
        return icons.getOrPut(key) {
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {textSize=13*density;typeface=android.graphics.Typeface.DEFAULT_BOLD}
            val width=max(32*density,paint.measureText(text)+20*density)
            val height=34*density
            val bitmap=Bitmap.createBitmap(width.toInt(),height.toInt(),Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap)
            paint.color=Color.WHITE
            canvas.drawRoundRect(0f,0f,width,height,height/2,height/2,paint)
            paint.color=color
            canvas.drawRoundRect(2*density,2*density,width-2*density,height-2*density,height/2,height/2,paint)
            paint.color=Color.WHITE
            canvas.drawText(text,(width-paint.measureText(text))/2,(height-paint.ascent()-paint.descent())/2,paint)
            BitmapDescriptorFactory.fromBitmap(bitmap).also {bitmap.recycle()}
        }.also {while(icons.size>256)icons.remove(icons.keys.first())}
    }
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
            listOf("from","to").forEach {end->map.addCircle(CircleOptions().center(leg.getJSONObject(end).point()).radius(15.0).strokeColor(color).fillColor(Color.WHITE).strokeWidth(3*density))?.let(routeStops::add)}
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
