package hu.matepazy.menetrendgo

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Browser-powered reading above the alert, with native close/Back returning to the same sheet. */
@Composable
internal fun ProviderAlertLink(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val dark = LocalAppDarkTheme.current
    var unavailable by remember(url) { mutableStateOf(false) }
    OutlinedButton(onClick = {
        val tab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setColorScheme(if (dark) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder()
                .setToolbarColor(colors.surface.toArgb())
                .build())
            .build()
        // Respect the preferred browser when supported, otherwise use an available Custom Tabs provider.
        // Pinning the package also keeps provider app links inside the browser reading flow.
        tab.intent.setPackage(CustomTabsClient.getPackageName(context, emptyList()))
        try {
            tab.launchUrl(context, Uri.parse(url))
        } catch (_: ActivityNotFoundException) {
            unavailable = true
        }
    }, shape = CircleShape, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("Részletek a szolgáltatónál")
        Spacer(Modifier.width(AppLayout.CompactGap))
        Icon(Icons.Outlined.Language, null, Modifier.size(18.dp))
    }
    if (unavailable) AlertDialog(
        onDismissRequest = { unavailable = false },
        title = { Text("A hivatkozás nem nyitható meg") },
        text = { Text("A szolgáltató oldalának megnyitásához engedélyezz vagy telepíts egy böngészőt.") },
        confirmButton = { TextButton(onClick = { unavailable = false }) { Text("Rendben") } },
    )
}
