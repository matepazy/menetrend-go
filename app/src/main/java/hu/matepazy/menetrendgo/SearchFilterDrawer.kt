package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SearchFilterDrawer(vm: AppState, dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = AppLayout.SheetPadding), verticalAlignment = Alignment.CenterVertically) {
                Text("Keresési feltételek", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Keresési feltételek bezárása") }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(horizontal = AppLayout.SheetPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Csak a kiválasztott feltételeknek megfelelő találatok jelennek meg. A hiányzó vagy nem igazolható szolgáltatásadat nem számít egyezésnek.",
                    Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SearchFilter.entries.forEach { filter ->
                    val checked = filter in vm.searchFilters
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch,
                            onValueChange = { vm.chooseSearchFilter(filter, it) }).heightIn(min = 72.dp).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(filter.title, style = MaterialTheme.typography.titleMedium)
                                Text(filter.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = checked, onCheckedChange = null)
                        }
                    }
                }
                Text("Az országbérlet és az 1. osztály jelölése két külön feltétel. A szűrés nem igazolja, hogy az országbérlet az 1. osztályon is érvényes.",
                    Modifier.padding(top = 8.dp, bottom = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(AppLayout.SheetPadding), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::clearSearchFilters, enabled = vm.searchFilters.isNotEmpty()) { Text("Alaphelyzet") }
                Button(onClick = dismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Kész") }
            }
        }
    }
}
