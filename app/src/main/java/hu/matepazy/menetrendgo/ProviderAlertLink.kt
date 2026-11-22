package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Browser-powered reading above the alert, with native close/Back returning to the same sheet. */
@Composable
internal fun ProviderAlertLink(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val toolbar = MaterialTheme.colorScheme.surface
    val dark = LocalAppDarkTheme.current
    var unavailable by remember(url) { mutableStateOf(false) }
    GlassButton(onClick = { unavailable = !openInAppBrowser(context, url, dark, toolbar) },
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("Részletek a szolgáltatónál")
        Spacer(Modifier.width(AppLayout.CompactGap))
        Icon(Icons.Outlined.Language, null, Modifier.size(18.dp))
    }
    if (unavailable) BrowserUnavailableDialog("A szolgáltató oldalának megnyitásához engedélyezz vagy telepíts egy böngészőt.") { unavailable = false }
}

@Composable internal fun BrowserUnavailableDialog(text: String, onDismiss: () -> Unit) = AlertDialog(
    modifier = Modifier.glassHighlight(AlertDialogDefaults.shape, glassTone(GlassLevel.Floating)),
    onDismissRequest = onDismiss,
    title = { Text("A hivatkozás nem nyitható meg") },
    text = { Text(text) },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Rendben") } },
)
