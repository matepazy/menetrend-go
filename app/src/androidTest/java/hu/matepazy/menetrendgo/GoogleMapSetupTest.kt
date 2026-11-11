package hu.matepazy.menetrendgo

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class GoogleMapSetupTest {
    @get:Rule val compose=createComposeRule()
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
            // Let the fallback WebView finish loading before destroying it again.
            compose.waitForIdle()
            compose.runOnUiThread {vm.chooseMapType(MapType.Standard)}
            compose.onNodeWithTag("google-transport-map").assertIsDisplayed()
            if(!BuildConfig.MAPS_API_KEY_CONFIGURED)compose.onNodeWithText("A Google térkép még nincs beállítva.",substring=true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Vissza a járat menetrendjéhez").assertIsDisplayed()
        } finally {compose.runOnUiThread {vm.chooseMapType(originalType)}}
    }
}
