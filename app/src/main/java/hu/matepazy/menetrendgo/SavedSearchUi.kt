package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

val SavedSearchIcon.vector: ImageVector get() = when (this) {
    SavedSearchIcon.Bookmark -> Icons.Outlined.BookmarkBorder
    SavedSearchIcon.Home -> Icons.Outlined.Home
    SavedSearchIcon.Work -> Icons.Outlined.WorkOutline
    SavedSearchIcon.School -> Icons.Outlined.School
    SavedSearchIcon.Favorite -> Icons.Outlined.FavoriteBorder
    SavedSearchIcon.Train -> Icons.Outlined.Train
    SavedSearchIcon.Shopping -> Icons.Outlined.ShoppingBag
    SavedSearchIcon.Explore -> Icons.Outlined.Explore
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SavedSearchEditor(from: Station, to: Station, existing: SavedSearch?,
    onSave: (String, SavedSearchIcon) -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null) {
    var name by rememberSaveable(from.id, to.id) { mutableStateOf(existing?.name.orEmpty()) }
    var icon by rememberSaveable(from.id, to.id) { mutableStateOf(existing?.icon ?: SavedSearchIcon.Bookmark) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = AppLayout.SheetPadding).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (existing == null) "Keresés mentése" else "Mentett keresés szerkesztése",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${from.name} → ${to.name}", style = MaterialTheme.typography.bodyLarge)
                Text("Csak az útvonalat mentjük. Új kereséskor az aktuális időpontot használjuk.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(value = name, onValueChange = { name = it.take(SavedSearch.MAX_NAME_LENGTH) },
                label = { Text("Név") }, placeholder = { Text("Például: Munkába") }, singleLine = true,
                leadingIcon = { Icon(icon.vector, null) }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                supportingText = { Text("${name.length}/${SavedSearch.MAX_NAME_LENGTH}") })
            Text("Ikon", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SavedSearchIcon.entries.forEach { option ->
                    FilledTonalIconButton(onClick = { icon = option }, modifier = Modifier.size(48.dp)
                        .semantics { selected = icon == option; role = Role.RadioButton },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (icon == option) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = if (icon == option) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)) {
                        Icon(option.vector, option.title)
                    }
                }
            }
            MainButton("Mentés", Icons.Outlined.BookmarkAdd, name.isNotBlank()) { onSave(name.trim(), icon) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onDismiss) { Text("Mégse") }
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) {
                    Text("Keresés törlése", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Törlöd a mentett keresést?") }, text = { Text(existing?.name.orEmpty()) },
        confirmButton = { TextButton(onClick = { onDelete?.invoke() }) { Text("Törlés") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Mégse") } })
}
