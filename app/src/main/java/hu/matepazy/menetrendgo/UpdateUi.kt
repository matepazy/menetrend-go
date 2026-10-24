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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable fun UpdateSettings(updates: UpdateController) {
    Glass(Modifier.fillMaxWidth()) {
        Text("MenetrendGO ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("A frissítések a projekt GitHub-kiadásaiból érkeznek. A telepítést mindig te hagyod jóvá az Androidban.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
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
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(Modifier.fillMaxWidth().toggleable(updates.enabled, role = Role.Switch, onValueChange = updates::chooseEnabled).padding(AppLayout.PanelPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Automatikus ellenőrzés", style = MaterialTheme.typography.titleMedium)
                    Text("Új kiadások keresése az alkalmazás indításakor.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = updates.enabled, onCheckedChange = null)
            }
            Text("Frissítési csatorna", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup().padding(vertical = 8.dp)) {
                listOf("release" to "Stabil", "pre-release" to "Stabil és béta").forEach { (value, label) ->
                    Row(Modifier.fillMaxWidth().selectable(updates.channel == value, enabled = !updates.busy && updates.state !is UpdateState.Completed, role = Role.RadioButton, onClick = { updates.chooseChannel(value) }).padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f)); RadioButton(updates.channel == value, onClick = null, enabled = !updates.busy && updates.state !is UpdateState.Completed)
                    }
                }
            }
        }
    }
    Glass(Modifier.fillMaxWidth()) {
        Text("Verziók kezelése", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("A béta kiadások hibákat tartalmazhatnak. Az Android a régebbi verzióra váltást elutasíthatja.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = updates::loadVersions, enabled = !updates.versionsLoading && !updates.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (updates.versionsLoading) "Betöltés…" else "Elérhető verziók") }
        updates.versionsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        updates.versions?.let { versions ->
            if (versions.isEmpty()) Text("Nincs választható APK-kiadás.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            versions.forEach { version ->
                OutlinedButton(onClick = { updates.select(version) }, enabled = !updates.busy && SemanticVersion.compare(version.version, BuildConfig.VERSION_NAME) != 0, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("${version.version} · ${if (version.isBeta) "Béta" else "Stabil"}${if (SemanticVersion.compare(version.version, BuildConfig.VERSION_NAME) == 0) " · Telepítve" else ""}")
                }
            }
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
    ModalBottomSheet(onDismissRequest = updates::later, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !updates.busy }), sheetGesturesEnabled = !updates.busy, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = AppLayout.PanelPadding).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageTitle(if (update.isVersionSwitch) "Verzióváltás" else "Új verzió érhető el")
            Text(update.version, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            if (update.isBeta) Text("Béta kiadás: hibák és váratlan működés előfordulhatnak.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("A kiadás újdonságai", style = MaterialTheme.typography.titleMedium)
                    Text(update.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
