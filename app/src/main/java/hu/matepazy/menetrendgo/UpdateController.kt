package hu.matepazy.menetrendgo

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Owns the same release/check/download flow as Still, with device-local preferences. */
class UpdateController(private val context: Context, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("menetrendgo_updates", Context.MODE_PRIVATE)
    var enabled by mutableStateOf(prefs.getBoolean("version_check_enabled", true)); private set
    var channel by mutableStateOf(prefs.getString("version_check_channel", "release")!!); private set
    var state by mutableStateOf<UpdateState>(UpdateState.Idle); private set
    var checked by mutableStateOf(false); private set
    var selected by mutableStateOf<UpdateState.UpdateAvailable?>(null); private set
    var versions by mutableStateOf<List<UpdateState.UpdateAvailable>?>(null); private set
    var versionsLoading by mutableStateOf(false); private set
    var versionsError by mutableStateOf<String?>(null); private set
    private var generation = 0
    val busy get() = state is UpdateState.Downloading

    init { if (enabled) check(automatic = true) }

    fun chooseEnabled(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean("version_check_enabled", value).apply()
        if (value) check(automatic = true)
        else if (!busy && state !is UpdateState.Completed) { generation++; state = UpdateState.Idle }
    }

    fun chooseChannel(value: String) {
        if (busy || state is UpdateState.Completed) return
        channel = value
        prefs.edit().putString("version_check_channel", value).apply()
        selected = null; state = UpdateState.Idle; checked = false
        if (enabled) check()
    }

    fun check(automatic: Boolean = false) {
        if (busy || state is UpdateState.Completed) return
        val request = ++generation
        state = UpdateState.Checking
        VersionUpdater.checkForUpdates(BuildConfig.VERSION_NAME, channel == "pre-release") { result ->
            scope.launch {
                if (request != generation) return@launch
                checked = true
                // Background failures stay in Settings and never interrupt journey planning.
                state = result
                if (result is UpdateState.UpdateAvailable) {
                    val deferred = prefs.getString("deferred_version", null) == result.version &&
                        prefs.getLong("remind_after", 0) > System.currentTimeMillis()
                    if (!automatic || !deferred) selected = result
                }
            }
        }
    }

    fun select(update: UpdateState.UpdateAvailable) {
        if (busy) return
        generation++; selected = update; state = update
    }

    fun later() {
        if (busy) return
        val update = selected
        update?.takeUnless { it.isVersionSwitch }?.let { update ->
            val delayMillis = 24 * 60 * 60 * 1000L
            prefs.edit().putString("deferred_version", update.version)
                .putLong("remind_after", System.currentTimeMillis() + delayMillis).apply()
            scope.launch { delay(delayMillis); if (enabled && selected == null) check(automatic = true) }
        }
        selected = null
        if (update != null) state = if (update.isVersionSwitch) UpdateState.Idle else update
    }

    fun loadVersions() {
        if (versionsLoading) return
        versionsLoading = true; versionsError = null
        VersionUpdater.fetchReleases { result -> scope.launch {
            versionsLoading = false
            result.fold(onSuccess = { versions = VersionUpdater.managedVersions(it, BuildConfig.VERSION_NAME) },
                onFailure = { versionsError = it.message ?: "Nem sikerült betölteni a verziókat." })
        } }
    }

    fun download() {
        val update = selected ?: return
        if (busy) return
        generation++; state = UpdateState.Downloading(-1f)
        VersionUpdater.downloadApk(context, update.downloadUrl,
            onProgress = { progress -> scope.launch { state = UpdateState.Downloading(progress.coerceIn(0f, 1f)) } },
            onCompleted = { file -> scope.launch { state = UpdateState.Completed(file) } },
            onError = { message -> scope.launch { state = UpdateState.Error(message) } },
            fileName = "menetrendgo-v${update.version.trimStart('v', 'V')}.apk")
    }

    fun install() {
        val completed = state as? UpdateState.Completed ?: return
        runCatching {
            if (ApkInstaller.canInstallPackages(context)) ApkInstaller.installApk(context, completed.apkFile)
            else ApkInstaller.requestInstallPermission(context)
        }.onFailure { state = UpdateState.Error("Nem sikerült megnyitni a telepítőt. Próbáld újra a letöltést.") }
    }
}
