package hu.matepazy.menetrendgo

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class GoogleMapSetupTest {
    @get:Rule val compose=createComposeRule()
    private fun railwayMapIsRunning(): Boolean {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val result=java.util.concurrent.atomic.AtomicReference<String?>()
        fun findWeb(view:android.view.View):android.webkit.WebView? {
            if(view is android.webkit.WebView)return view
            if(view is android.view.ViewGroup)for(i in 0 until view.childCount) {
                findWeb(view.getChildAt(i))?.let {return it}
            }
            return null
        }
        instrumentation.runOnMainSync {
            val activity=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first()
            val web=findWeb(activity.window.decorView)
            if(web==null)result.set("false") else web.evaluateJavascript(
                "typeof map !== 'undefined' && activeMapType === 'rail' && mapLayers.length === 2 && mapLayers.every(layer => map.hasLayer(layer)) && map.getSize().x > 0 && map.getSize().y > 0"
            ) {result.set(it)}
        }
        compose.waitUntil(2_000) {result.get()!=null}
        return result.get()=="true"
    }
    @Test fun googleIsDefaultAndSwitchingToLeafletAndBackWorks() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        lateinit var vm:AppState
        lateinit var originalType:MapType
        compose.runOnUiThread {vm=AppState(app);originalType=vm.mapType;vm.chooseMapType(MapType.Standard)}
        try {
            compose.setContent {MenetrendTheme("light") {VehicleMapScreen(vm,"test:vehicle") {}}}
            compose.onNodeWithTag("google-transport-map").assertIsDisplayed()
            if(!BuildConfig.MAPS_API_KEY_CONFIGURED)compose.onNodeWithText("A Google térkép még nincs beállítva.",substring=true).assertIsDisplayed()
            compose.runOnUiThread {vm.chooseMapType(MapType.Rail)}
            compose.onNodeWithTag("google-transport-map").assertDoesNotExist()
            compose.waitUntil(15_000) {railwayMapIsRunning()}
            compose.runOnUiThread {vm.chooseMapType(MapType.Standard)}
            compose.onNodeWithTag("google-transport-map").assertIsDisplayed()
            if(!BuildConfig.MAPS_API_KEY_CONFIGURED)compose.onNodeWithText("A Google térkép még nincs beállítva.",substring=true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Vissza a járat menetrendjéhez").assertIsDisplayed()
            // Recreating the railway renderer must work on subsequent switches too.
            compose.runOnUiThread {vm.chooseMapType(MapType.Rail)}
            compose.waitUntil(15_000) {railwayMapIsRunning()}
        } finally {compose.runOnUiThread {vm.chooseMapType(originalType)}}
    }
}
