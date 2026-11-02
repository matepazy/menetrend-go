package hu.matepazy.menetrendgo

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class ProviderAlertLinkTest {
    @get:Rule val compose = createComposeRule()

    @Test fun opensBrowserTabInLightTheme() = verifyTab(false)
    @Test fun opensBrowserTabInDarkTheme() = verifyTab(true)

    private fun verifyTab(dark: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val launched = AtomicReference<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW) return null
                launched.set(intent)
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            val url = "https://bkk.hu/forgalmi-hirek/"
            compose.setContent { MenetrendTheme(if (dark) "dark" else "light") { ProviderAlertLink(url) } }
            compose.onNodeWithText("Részletek a szolgáltatónál").performClick()
            compose.runOnIdle {
                val intent = requireNotNull(launched.get()) { "The provider link did not launch a browser" }
                assertEquals(url, intent.dataString)
                assertTrue("Must request a Custom Tab", intent.hasExtra(CustomTabsIntent.EXTRA_SESSION))
                assertEquals(if (dark) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT,
                    intent.getIntExtra(CustomTabsIntent.EXTRA_COLOR_SCHEME, -1))
                assertEquals("Must stay in the app task", 0, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            compose.onNodeWithText("Részletek a szolgáltatónál").assertIsDisplayed()
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
