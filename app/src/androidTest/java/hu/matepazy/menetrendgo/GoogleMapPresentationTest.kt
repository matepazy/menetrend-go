package hu.matepazy.menetrendgo

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class GoogleMapPresentationTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun capture(name:String) {
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"map-restoration").apply {mkdirs()}
        File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
    @Test fun journeyPathsRetainServiceColorsInBothThemes() {
        assumeTrue(BuildConfig.MAPS_API_KEY_CONFIGURED)
        var api:TransportMapSurface?=null
        var theme by mutableStateOf("light")
        val loaded=AtomicBoolean(false)
        compose.setContent {
            MenetrendTheme(theme) {
                val colors=MaterialTheme.colorScheme
                SideEffect {api?.call("setAppTheme",colors.background.toArgb(),colors.surface.toArgb(),colors.onSurface.toArgb(),colors.primary.toArgb())}
                GoogleTransportMap(Modifier.fillMaxSize(),onReady={api=it},onVehicle={},onStop={},
                    onViewport={_,_,_->},onLocationVisible={},loadPaths={JSONObject()})
            }
        }
        compose.waitUntil(15000) {api!=null}
        compose.runOnIdle {
            val field=api!!.javaClass.getDeclaredField("map").apply {isAccessible=true}
            (field.get(api) as GoogleMap).setOnMapLoadedCallback {loaded.set(true)}
        }
        val serviceColors=listOf("29803C","FECC00","#00A0E3")
        compose.runOnIdle {
            api!!.call("setRouteMode",true)
            api!!.call("updateRoute",JSONArray(serviceColors.mapIndexed {index,color->
                JSONObject().put("color",color).put("walking",false)
                    .put("from",Station("$index","Teszt",47.50+index*.01,19.04+index*.01).json())
                    .put("to",Station("${index+1}","Teszt",47.51+index*.01,19.05+index*.01).json())
            }))
        }
        compose.waitUntil(20000) {loaded.get()}
        for(mode in listOf("light","dark")) {
            compose.runOnIdle {theme=mode}
            compose.waitForIdle()
            compose.runOnIdle {
                val field=api!!.javaClass.getDeclaredField("routes").apply {isAccessible=true}
                @Suppress("UNCHECKED_CAST")
                val routes=field.get(api) as List<com.google.android.gms.maps.model.Polyline>
                assertEquals(serviceColors.map {android.graphics.Color.parseColor("#"+it.removePrefix("#"))},routes.map {it.color})
            }
            // Let the SDK camera animation finish before the visual capture.
            instrumentation.waitForIdleSync()
            capture("journey-service-colors-$mode")
        }
    }
    @Test fun officialPictogramsKeepCircularSilhouetteAndDirection() {
        val artwork=NativeMapArtwork(instrumentation.targetContext)
        val density=instrumentation.targetContext.resources.displayMetrics.density
        for(mode in NativeMapArtwork.modeColors.keys) {
            val bitmap=artwork.vehicleBitmap(mode,null)
            // A square background leaked into this corner in the first native implementation.
            assertTrue(android.graphics.Color.alpha(bitmap.getPixel((11*density).toInt(),(11*density).toInt()))<100)
            assertEquals(NativeMapArtwork.modeColors.getValue(mode),bitmap.getPixel((12*density).toInt(),(24*density).toInt()))
            bitmap.recycle()
        }
        val north=artwork.vehicleBitmap("RAIL",0.0)
        val east=artwork.vehicleBitmap("RAIL",90.0)
        assertTrue(android.graphics.Color.alpha(north.getPixel((24*density).toInt(),(4*density).toInt()))>200)
        assertTrue(android.graphics.Color.alpha(east.getPixel((24*density).toInt(),(4*density).toInt()))<100)
        north.recycle();east.recycle()
    }
    @Test fun groupedStopsRetainExactIdentityAndGoogleMapRendersInBothThemes() {
        assumeTrue(BuildConfig.MAPS_API_KEY_CONFIGURED)
        var api:TransportMapSurface?=null
        var google:GoogleMap?=null
        var selected=""
        var theme by mutableStateOf("light")
        val loaded=AtomicBoolean(false)
        compose.setContent {
            MenetrendTheme(theme) {
                val colors=MaterialTheme.colorScheme
                SideEffect {api?.call("setAppTheme",colors.background.toArgb(),colors.surface.toArgb(),colors.onSurface.toArgb(),colors.primary.toArgb())}
                GoogleTransportMap(Modifier.fillMaxSize(),onReady={api=it},onVehicle={selected=it},onStop={selected=it},
                    onViewport={_,_,_->},onLocationVisible={},loadPaths={JSONObject()})
            }
        }
        compose.waitUntil(15000) {api!=null}
        compose.runOnIdle {
            fun find(view:View):MapView? {
                if(view is MapView)return view
                if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i))?.let {return it}
                return null
            }
            val activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
            find(activity.window.decorView)!!.getMapAsync {map->
                google=map
                map.setOnMapLoadedCallback {loaded.set(true)}
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(47.507,19.025),16.7f))
                api!!.call("setAppTheme",0xffedf4fa.toInt(),0xfff8fbff.toInt(),0xff112d43.toInt(),0xff2864d9.toInt())
                api!!.call("setMapType","standard","")
                api!!.call("setRouteMode",true)
                api!!.call("setStopsEnabled",true)
                val stops=listOf(Station("platform:a","Széll Kálmán tér",47.507,19.025,modes=listOf("BUS")),
                    Station("platform:b","Széll Kálmán tér",47.507,19.02512,modes=listOf("BUS","RAIL")),
                    Station("platform:c","Széll Kálmán tér",47.50735,19.025,modes=listOf("RAIL")))
                api!!.call("updateStops",JSONArray(stops.map {it.json()}))
                api!!.call("updateVehicles",JSONArray(NativeMapArtwork.modeColors.keys.mapIndexed {i,mode->
                    JSONObject().put("id","vehicle:$i").put("mode",mode).put("lat",47.509+i*.0001).put("lon",19.021+i*.001)
                        .put("name","Teszt").put("type",mode).put("headsign","Szemléltető tesztadat").put("heading",i*60)
                }))
            }
        }
        compose.waitUntil(20000) {loaded.get()}
        compose.waitForIdle()
        capture("fixture-light")
        // A camera idle and an overlapping viewport response must retain the actual SDK
        // objects. Recreating them leaves a visible gap while their textures upload.
        fun layer(name:String):Map<*,*> = api!!.javaClass.getDeclaredField(name).let {
            it.isAccessible=true
            (it.get(api) as Map<*,*>).toMap()
        }
        lateinit var layers:Map<String,Map<*,*>>
        compose.runOnIdle {
            layers=listOf("stopMarkers","stopEnclosures","stopLabels").associateWith(::layer)
            assertEquals(3,layers.getValue("stopMarkers").size)
            assertEquals(1,layers.getValue("stopLabels").size)
        }
        repeat(3) {index->
            compose.onRoot().performTouchInput {
                swipe(Offset(center.x,center.y),Offset(center.x+if(index%2==0)60f else -60f,center.y),300)
            }
            compose.waitForIdle()
            compose.runOnIdle {
                // Refreshing the same provider data also used to rebuild the whole layer.
                val field=api!!.javaClass.getDeclaredField("stops").apply {isAccessible=true}
                api!!.call("updateStops",JSONArray(field.get(api).toString()))
                for((name,previous) in layers) {
                    val current=layer(name)
                    assertEquals(previous.keys,current.keys)
                    previous.forEach {(id,item)->assertSame("$name $id was recreated",item,current[id])}
                }
            }
        }
        capture("fixture-after-pan")
        lateinit var point:android.graphics.Point
        compose.runOnIdle {point=google!!.projection.toScreenLocation(LatLng(47.507,19.02512))}
        compose.onRoot().performTouchInput {click(Offset(point.x.toFloat(),point.y.toFloat()))}
        compose.waitUntil(5000) {selected.isNotEmpty()}
        assertEquals("platform:b",selected)
        compose.runOnIdle {theme="dark"}
        compose.waitForIdle()
        loaded.set(false)
        compose.runOnIdle {google!!.setOnMapLoadedCallback {loaded.set(true)}}
        compose.waitUntil(20000) {loaded.get()}
        // Map callbacks can precede the next compositor frame that contains its markers.
        instrumentation.waitForIdleSync()
        capture("fixture-dark")
    }
}
