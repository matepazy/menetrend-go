package hu.matepazy.menetrendgo

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Opens [url] in a themed Custom Tab above the app; native close/Back return to the same screen. False when no browser can open it. */
internal fun openInAppBrowser(context: Context, url: String, dark: Boolean, toolbar: Color): Boolean {
    val tab = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setColorScheme(if (dark) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
        .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbar.toArgb()).build())
        .build()
    // Respect the preferred browser when supported, otherwise use an available Custom Tabs provider.
    // Pinning the package also keeps app links inside the browser reading flow.
    tab.intent.setPackage(CustomTabsClient.getPackageName(context, emptyList()))
    return try {
        tab.launchUrl(context, Uri.parse(url)); true
    } catch (_: ActivityNotFoundException) { false }
}
