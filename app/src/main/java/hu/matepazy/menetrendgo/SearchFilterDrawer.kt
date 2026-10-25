package hu.matepazy.menetrendgo

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Original MNR2007 pictograms: direct carriage, climate control, first class,
// country pass, bicycle transport and wheelchair access. These are artwork,
// so their size stays fixed while the adjacent accessible labels scale.
@Composable private fun SearchFilterIcon(filter: SearchFilter) {
    val code = when (filter) {
        SearchFilter.Direct -> 211
        SearchFilter.AirConditioning -> 319
        SearchFilter.FirstClass -> 221
        SearchFilter.SecondClass -> 222
        SearchFilter.CountryPass -> 557
        SearchFilter.Bicycle -> 213
        SearchFilter.Accessible -> 223
        SearchFilter.Wifi -> 333
        SearchFilter.Power -> 318
        SearchFilter.Dining -> 194
        SearchFilter.Sleeper -> 203
    }
    val context = LocalContext.current
    val outline = remember(context, code) {
        val glyph = String(Character.toChars(code))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.mav_services)
            textSize = 1000f
        }
        val path = Path().apply { paint.getTextPath(glyph, 0, glyph.length, 0f, 0f, this) }
        path to RectF().also { path.computeBounds(it, true) }
    }
    val color = MaterialTheme.colorScheme.onSurface
    val paint = remember(color) { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() } }
    Box(Modifier.size(32.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            val (path, bounds) = outline
            if (!bounds.isEmpty) drawIntoCanvas { canvas ->
                val scale = minOf(size.width / bounds.width(), size.height / bounds.height())
                canvas.nativeCanvas.apply {
                    save()
                    translate((size.width - bounds.width() * scale) / 2,
                        (size.height - bounds.height() * scale) / 2)
                    scale(scale, scale)
                    translate(-bounds.left, -bounds.top)
                    drawPath(path, paint)
                    restore()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SearchFilterDrawer(vm: AppState, dismiss: () -> Unit) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    AppBottomSheet(onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = AppLayout.SheetPadding), verticalAlignment = Alignment.CenterVertically) {
                Text("Keresési feltételek", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Keresési feltételek bezárása") }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(horizontal = AppLayout.SheetPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Column {
                        SearchFilter.entries.forEach { filter ->
                            val checked = filter in vm.searchFilters
                            Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch,
                                onValueChange = { vm.chooseSearchFilter(filter, it) }).heightIn(min = 64.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                SearchFilterIcon(filter)
                                Text(filter.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Switch(checked = checked, onCheckedChange = null)
                            }
                        }
                    }
                }
                TextButton(onClick = { showDetails = !showDetails }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("A szűrésről")
                    Spacer(Modifier.width(8.dp))
                    Icon(if (showDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        if (showDetails) "Tudnivalók elrejtése" else "Tudnivalók megjelenítése")
                }
                if (showDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        Text("A szolgáltatásoknak minden utazási szakaszon igazolhatónak kell lenniük. A hiányzó adat nem számít egyezésnek.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Országbérletnél helyjegy, kerékpárnál foglalás, akadálymentes utazásnál előzetes bejelentés szükséges lehet.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Az országbérlet és az 1. osztály külön feltétel. Együttes kiválasztásuk nem igazolja a bérlet 1. osztályú érvényességét.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(AppLayout.SheetPadding), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::clearSearchFilters, enabled = vm.searchFilters.isNotEmpty()) { Text("Alaphelyzet") }
                Button(onClick = dismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Kész") }
            }
        }
    }
}
