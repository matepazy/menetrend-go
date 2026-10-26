package hu.matepazy.menetrendgo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MapTypeDrawer(vm: AppState, dismiss: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf(vm.customMapUrl) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val valid = validMapTileUrl(draft.trim())
    fun apply() {
        attempted = true
        if (valid) { vm.applyCustomMapUrl(draft); dismiss() }
    }
    AppBottomSheet(onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = AppLayout.SheetPadding).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Térképtípus", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Térképtípus bezárása") }
            }
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MapType.entries.filter { it != MapType.Custom || (vm.customMapEnabled && validMapTileUrl(vm.customMapUrl)) }.forEach { type ->
                    val selected = vm.effectiveMapType == type
                    Surface(shape = RoundedCornerShape(24.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton,
                            onClick = { vm.chooseMapType(type); dismiss() }).heightIn(min = 72.dp).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(when (type) {
                                MapType.Standard -> Icons.Outlined.Map
                                MapType.Satellite -> Icons.Outlined.SatelliteAlt
                                MapType.Hybrid -> Icons.Outlined.Layers
                                MapType.Rail -> Icons.Outlined.Train
                                MapType.Custom -> Icons.Outlined.Link
                            }, null, tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(type.title, style = MaterialTheme.typography.titleMedium)
                                Text(type.summary(vm.unofficialGoogleMapEnabled), style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(selected = selected, onClick = null)
                        }
                    }
                }
            }
            if (vm.customMapEnabled) {
                Text("Saját térkép URL", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = draft, onValueChange = { draft = it.take(4096); attempted = false },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Csempe URL") },
                    placeholder = { Text("https://…/{z}/{x}/{y}.png") },
                    isError = attempted && !valid, singleLine = true, shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { apply() }),
                    supportingText = { Text(if (attempted && !valid) "HTTPS URL szükséges {z}, {x} és {y} helyőrzőkkel."
                        else "HTTPS XYZ csempék. A {s} és {r} helyőrző is használható.") })
                Text("A csempéket közvetlenül a megadott szolgáltatótól töltjük be. Az URL-t ezen az eszközön mentjük.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { apply() }, modifier = Modifier.fillMaxWidth()) { Text("Saját térkép használata") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MapFilterDrawer(vm: AppState, dismiss: () -> Unit) {
    AppBottomSheet(onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = AppLayout.SheetPadding).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Járműszűrők", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Járműszűrők bezárása") }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MapVehicleFilter.entries.forEach { filter ->
                    val checked = filter in vm.vehicleFilters
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch,
                            onValueChange = { vm.chooseVehicleFilter(filter, it) }).heightIn(min = 72.dp).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Keep the mode identity visible in both switch states. The native
                            // switch, rather than a change of category color, communicates selection.
                            Image(painterResource(when (filter) {
                                MapVehicleFilter.Rail -> R.drawable.ic_mode_rail
                                MapVehicleFilter.Coach -> R.drawable.ic_mode_coach
                                MapVehicleFilter.LocalBus -> R.drawable.ic_mode_local_bus
                                MapVehicleFilter.SuburbanRailway -> R.drawable.ic_mode_hev
                                MapVehicleFilter.TramTrain -> R.drawable.ic_mode_tramtrain
                                MapVehicleFilter.ReplacementBus -> R.drawable.ic_mode_replacement_bus
                            }), contentDescription = null, modifier = Modifier.size(40.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(filter.title, style = MaterialTheme.typography.titleMedium)
                                Text(filter.summary, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = checked, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }
}
