package hu.matepazy.menetrendgo

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class VehicleMapMotionTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var web: WebView
    private fun js(script: String): String {
        val latch=CountDownLatch(1)
        var result=""
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            web.evaluateJavascript(script) {result=it;latch.countDown()}
        }
        assertTrue(latch.await(5,TimeUnit.SECONDS))
        return result
    }
    @Test fun glideFollowsBendAndSupportsInterruptionAndReducedMotion() {
        val ready=AtomicBoolean(false)
        compose.setContent {
            AndroidView(modifier=Modifier.fillMaxSize(),factory={ctx->WebView(ctx).apply {
                web=this
                settings.javaScriptEnabled=true
                webViewClient=object:WebViewClient(){override fun onPageFinished(view: WebView,url: String){ready.set(true)}}
                val html=ctx.assets.open("map.html").bufferedReader().use {it.readText()}
                    .replace("/* LEAFLET_JS */",ctx.assets.open("leaflet.js").bufferedReader().use {it.readText()})
                    .replace("/* LEAFLET_CSS */",ctx.assets.open("leaflet.css").bufferedReader().use {it.readText()})
                    .replace("/* VEHICLE_ICONS */",ctx.assets.open("vehicle-icons.js").bufferedReader().use {it.readText()})
                loadDataWithBaseURL("https://menetrendgo.local/",html,"text/html","utf-8",null)
            }})
        }
        compose.waitUntil(10000){ready.get()}
        js("""
            window.NativeMap={selectVehicle:()=>{},locationInView:()=>{},stopViewport:()=>{}};
            window.setRouteMode(true);map.setView([47.005,19.005],15);
            window.setVehicleMotionEnabled(true);
            window.fixture=(lat,lon)=>[{id:'motion',lat,lon,mode:'BUS',type:'Busz',name:'1',headsign:'Teszt',heading:90}];
            window.updateVehicles(fixture(47,19));
            window.updateVehiclePaths({motion:[[19,47],[19.01,47],[19.01,47.01]]});
            L.polyline([[47,19],[47,19.01],[47.01,19.01]],{color:'#2864d9',weight:5}).addTo(map);
            window.samples=[];
            window.sample=()=>{samples.push({...vehicles[0]});if(vehicleMoves.size)requestAnimationFrame(sample)};
            window.updateVehicles(fixture(47.006,19.01));requestAnimationFrame(sample);
        """.trimIndent())
        Thread.sleep(300)
        assertEquals("1",js("vehicleMoves.size"))
        // Retarget from the displayed position without a discontinuity.
        assertEquals("true",js("""(()=>{const before={...vehicles[0]};updateVehicles(fixture(47.008,19.01));return Math.hypot(before.lat-vehicles[0].lat,before.lon-vehicles[0].lon)<1e-8})()"""))
        capture("gliding")
        Thread.sleep(1200)
        assertEquals("0",js("vehicleMoves.size"))
        assertEquals("true",js("samples.length>2&&samples.every(p=>Math.abs(p.lat-47)<1e-8||Math.abs(p.lon-19.01)<1e-8)"))
        assertEquals("true",js("Math.abs(vehicles[0].lat-47.008)<1e-8"))
        js("setAppTheme(-15658217,-15065564,-1578513,-6307073)")
        capture("dark")
        js("setVehicleMotionEnabled(false);updateVehicles(fixture(47.009,19.01))")
        assertEquals("0",js("vehicleMoves.size"))
        assertEquals("true",js("vehicles[0].lat===47.009"))
        js("setVehicleMotionEnabled(true);updateVehicles(fixture(47.01,19.01));updateVehicles([])")
        assertEquals("0",js("vehicleMoves.size"))
        assertEquals("0",js("markers.getLayers().length"))
    }
    private fun capture(name: String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val folder=File(instrumentation.targetContext.getExternalFilesDir(null),"vehicle-motion").apply {mkdirs()}
        instrumentation.uiAutomation.takeScreenshot().let {bitmap->
            File(folder,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }
}
