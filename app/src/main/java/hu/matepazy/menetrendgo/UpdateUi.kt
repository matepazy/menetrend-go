package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun UpdateSettings(updates: UpdateController, installedVersion: String = BuildConfig.VERSION_NAME) {
    var showChannel by rememberSaveable { mutableStateOf(false) }
    var showBetaConsent by rememberSaveable { mutableStateOf(false) }
    var showVersions by rememberSaveable { mutableStateOf(false) }
    val installedBeta = installedVersion.substringBefore('+').contains('-')
    val channelEnabled = !updates.busy && updates.state !is UpdateState.Completed
    Glass(Modifier.fillMaxWidth()) {
        Text("MenetrendGO $installedVersion", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        val status = when (val state = updates.state) {
            UpdateState.Checking -> "Frissítések keresése…"
            is UpdateState.UpdateAvailable -> "Elérhető: ${state.version}"
            is UpdateState.Downloading -> "Frissítés letöltése…"
            is UpdateState.Completed -> "A frissítés telepítésre kész."
            is UpdateState.Error -> state.message
            UpdateState.Idle -> if (updates.checked) "Nincs újabb kiadás a választott csatornán." else "Még nem kerestünk frissítést."
        }
        Text(status, color = if (updates.state is UpdateState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        if (updates.state == UpdateState.Checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = { updates.check() }, enabled = !updates.busy && updates.state != UpdateState.Checking && updates.state !is UpdateState.Completed, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Frissítések keresése") }
        (updates.state as? UpdateState.UpdateAvailable)?.let { update ->
            TextButton(onClick = { updates.select(update) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Kiadás megtekintése") }
        }
    }
    SettingsGroup {
        Row(Modifier.fillMaxWidth().toggleable(updates.enabled, role = Role.Switch, onValueChange = updates::chooseEnabled).padding(AppLayout.PanelPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Automatikus ellenőrzés", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = updates.enabled, onCheckedChange = null)
        }
        SettingsRow("Frissítési csatorna", if (updates.channel == "pre-release") "Béta" else "Stabil", enabled = channelEnabled, onClick = { showChannel = true })
        if (installedBeta) SettingsRow("Elérhető verziók", onClick = {
            showVersions = !showVersions
            if (showVersions && updates.versions == null && !updates.versionsLoading) updates.loadVersions()
        })
    }
    if (installedBeta && showVersions) Column(Modifier.padding(horizontal = AppLayout.PanelPadding), verticalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
        if (updates.versionsLoading) {
            Text("Verziók betöltése…", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        updates.versionsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (updates.versionsError != null) TextButton(onClick = updates::loadVersions, enabled = !updates.versionsLoading) { Text("Újrapróbálás") }
        updates.versions?.let { versions ->
            if (versions.isEmpty()) Text("Nincs választható APK-kiadás.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            versions.forEach { version ->
                OutlinedButton(onClick = { updates.select(version) }, enabled = !updates.busy && SemanticVersion.compare(version.version, installedVersion) != 0, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("${version.version} · ${if (version.isBeta) "Béta" else "Stabil"}${if (SemanticVersion.compare(version.version, installedVersion) == 0) " · Telepítve" else ""}")
                }
            }
        }
    }
    if (showChannel) {
        var choice by rememberSaveable { mutableStateOf(updates.channel) }
        AppBottomSheet(onDismissRequest = { showChannel = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(AppLayout.SheetPadding), verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
                PageTitle("Frissítési csatorna")
                Column(Modifier.selectableGroup()) {
                    listOf("release" to "Stabil", "pre-release" to "Béta").forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth().selectable(choice == value, enabled = channelEnabled, role = Role.RadioButton, onClick = {
                            if (value == "pre-release" && updates.channel != value) {
                                showChannel = false
                                showBetaConsent = true
                            } else choice = value
                        }).padding(vertical = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f))
                            RadioButton(choice == value, onClick = null, enabled = channelEnabled)
                        }
                    }
                }
                Button(onClick = {
                    showChannel = false
                    if (choice != updates.channel) updates.chooseChannel(choice)
                }, enabled = channelEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Kész") }
            }
        }
    }
    if (showBetaConsent) BetaChannelConsent(
        enabled = channelEnabled,
        onDismiss = { showBetaConsent = false; showChannel = true },
        onConfirm = {
            showBetaConsent = false
            updates.chooseChannel("pre-release")
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BetaChannelConsent(enabled: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("beta-consent-drawer"), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalWindowInfo.current.containerDpSize.height * .85f).padding(horizontal = AppLayout.SheetPadding).padding(bottom = AppLayout.SheetPadding), verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
                PageTitle("Béta kiadások kipróbálása")
                Text("Korábban megkapod az új funkciókat, de ezek a kiadások még tesztelés alatt állnak. A stabil frissítéseket továbbra is megkapod.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Hibákra is számíts", style = MaterialTheme.typography.titleMedium)
                Text("Előfordulhat összeomlás, hibás megjelenítés vagy nem működő funkció. Fontos utazás előtt ellenőrizd az adatokat a hivatalos MÁV-felületeken is.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Visszatérés stabil verzióra", style = MaterialTheme.typography.titleMedium)
                Text("A Stabil csatornára váltás önmagában nem cseréli le a telepített bétát. Az Elérhető verziók alatt választhatod ki a legutóbbi stabil kiadást, de az Android a régebbi verzió telepítését elutasíthatja. Az alkalmazás eltávolítása törli a helyben mentett bérleteket és beállításokat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onConfirm, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Megértettem, engedélyezem a bétát") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Mégse") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun UpdateHost(updates: UpdateController) {
    val update = updates.selected ?: return
    var betaConfirmed by rememberSaveable(update.version) { mutableStateOf(false) }
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var canInstall by remember { mutableStateOf(ApkInstaller.canInstallPackages(context)) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canInstall = ApkInstaller.canInstallPackages(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(updates.state) {
        if (updates.state is UpdateState.Completed && ApkInstaller.canInstallPackages(context)) updates.install()
    }
    AppBottomSheet(onDismissRequest = updates::later,
        modifier = Modifier.testTag("update-drawer"),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !updates.busy }), sheetGesturesEnabled = !updates.busy, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalWindowInfo.current.containerDpSize.height * .85f).padding(horizontal = AppLayout.PanelPadding).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("update-notes"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PageTitle(if (update.isVersionSwitch) "Verzióváltás" else "Új verzió érhető el")
                Text(update.version, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (update.isBeta) Text("Béta kiadás: hibák és váratlan működés előfordulhatnak.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (update.isVersionSwitch && SemanticVersion.compare(update.version, BuildConfig.VERSION_NAME) < 0) Text("Az Android a régebbi verzióra váltást elutasíthatja.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (updates.state !is UpdateState.Downloading && updates.state !is UpdateState.Completed) {
                    Text("A kiadás újdonságai", style = MaterialTheme.typography.titleMedium)
                    ReleaseNotes(update.notes)
                }
            }
            when (val state = updates.state) {
                is UpdateState.Downloading -> {
                    Text(if (state.progress < 0) "Letöltés…" else "Letöltés · ${(state.progress * 100).toInt()}%")
                    if (state.progress < 0) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.Completed -> {
                    Text("A letöltés kész. Az Android telepítőjében erősítsd meg a frissítést. Ha telepítési engedélyt kér, engedélyezd, majd térj vissza ide.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = updates::install, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (canInstall) "Telepítés" else "Telepítési engedély") }
                }
                else -> {
                    if (state is UpdateState.Error) Text(state.message, color = MaterialTheme.colorScheme.error)
                    if (update.isBeta && !betaConfirmed) {
                        OutlinedButton(onClick = { betaConfirmed = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Megértettem, a bétát választom") }
                    } else Button(onClick = updates::download, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (state is UpdateState.Error) "Letöltés újra" else "Letöltés és frissítés") }
                }
            }
            if (!updates.busy) TextButton(onClick = updates::later, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (update.isVersionSwitch) "Mégse" else "Emlékeztess holnap") }
        }
    }
}
