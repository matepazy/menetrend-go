package hu.matepazy.menetrendgo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** An explicit, one-shot fix. Leaving the picker cancels every outstanding request. */
@SuppressLint("MissingPermission") // Both precise and approximate access are checked by the caller.
internal suspend fun currentDepartureLocation(context: Context): Location? {
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = manager.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }
    if (providers.isEmpty()) return null
    return withTimeoutOrNull(20_000L) {
        suspendCancellableCoroutine { continuation ->
            val signals = providers.map { CancellationSignal() }
            var remaining = providers.size
            fun finish(location: Location?) {
                remaining--
                if (continuation.isActive && (location != null || remaining == 0)) {
                    continuation.resume(location)
                    signals.forEach { it.cancel() }
                }
            }
            continuation.invokeOnCancellation { signals.forEach { it.cancel() } }
            providers.zip(signals).forEach { (provider, signal) ->
                if (continuation.isActive) {
                    try {
                        LocationManagerCompat.getCurrentLocation(manager, provider, signal,
                            ContextCompat.getMainExecutor(context)) { finish(it) }
                    } catch (_: IllegalArgumentException) { finish(null) }
                    catch (_: SecurityException) { finish(null) }
                }
            }
        }
    }
}

@Composable
internal fun CurrentLocationOption(onSelected: (Station) -> Unit, onBusyChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selected by rememberUpdatedState(onSelected)
    val busyChanged by rememberUpdatedState(onBusyChanged)
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun setBusy(value: Boolean) { busy = value; busyChanged(value) }
    fun locate() {
        scope.launch {
            try {
                val manager = context.getSystemService(LocationManager::class.java)
                if (manager == null || !LocationManagerCompat.isLocationEnabled(manager)) {
                    error = "Kapcsold be a helymeghatározást, majd próbáld újra."
                } else {
                    val location = currentDepartureLocation(context)
                    if (location == null) error = "Nem sikerült meghatározni a helyzeted. Próbáld újra."
                    else selected(Station("", "Jelenlegi helyzet", location.latitude, location.longitude))
                }
            } catch (e: CancellationException) { throw e }
            catch (_: SecurityException) { error = "Engedélyezd a helyhozzáférést a Beállításokban, majd próbáld újra." }
            catch (_: Exception) { error = "Nem sikerült meghatározni a helyzeted. Próbáld újra." }
            finally { setBusy(false) }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) { setBusy(true); locate() }
        else {
            error = "Helyhozzáférés nélkül válassz megállót, vagy engedélyezd a Beállításokban."
            setBusy(false)
        }
    }
    Surface(onClick = {
        error = null
        setBusy(true)
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) locate()
        else permission.launch(permissions)
    }, enabled = !busy, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = AppLayout.RelatedGap),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.MyLocation, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
            Text(if (busy) "Helymeghatározás…" else "Jelenlegi helyzet",
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    error?.let {
        Text(it, Modifier.padding(start = 46.dp, end = 4.dp, top = 8.dp, bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
