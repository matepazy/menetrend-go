package hu.matepazy.menetrendgo

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Bundled pictograms at MNR2007 code points: direct carriage, climate control, first class,
// country pass, bicycle transport and wheelchair access. These are artwork,
// so their size stays fixed while the adjacent accessible labels scale.
@Composable private fun SearchFilterIcon(filter: SearchFilter) {
    val code = when (filter) {
        SearchFilter.Direct -> 211
        SearchFilter.AirConditioning -> 319
        SearchFilter.FirstClass -> 221
        SearchFilter.PremiumClass -> 190
        SearchFilter.SecondClass -> 222
        SearchFilter.CountryPass -> 557
        SearchFilter.Bicycle -> 213
        SearchFilter.Accessible -> 223
        SearchFilter.Dining -> 194
        SearchFilter.Sleeper -> 203
    }
    val context = LocalContext.current
    val outline = remember(context, code) {
        val glyph = String(Character.toChars(code))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.transit_pictograms)
            textSize = 1000f
        }
        val path = Path().apply { paint.getTextPath(glyph, 0, glyph.length, 0f, 0f, this) }
        path to RectF().also { path.computeBounds(it, true) }
    }
    val color = MaterialTheme.colorScheme.onSurface
    val paint = remember(color) { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() } }
    Box(Modifier.size(24.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(20.dp)) {
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

@Composable fun SearchFilterDrawer(vm: AppState, dismiss: () -> Unit) {
    SearchFilterDrawer(vm.searchFilters, vm::chooseSearchFilter, vm::clearSearchFilters, dismiss,
        vm.searchVehicles, vm::chooseSearchVehicle, { vm.chooseSearchVehicles(emptySet()) })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable internal fun SearchFilterDrawer(selected: Set<SearchFilter>,
    choose: (SearchFilter, Boolean) -> Unit, reset: () -> Unit, dismiss: () -> Unit,
    vehicles: Set<SearchVehicle> = emptySet(), toggleVehicle: (SearchVehicle) -> Unit = {},
    allVehicles: () -> Unit = {}) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var vehiclesOpen by rememberSaveable { mutableStateOf(false) }
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
                val count = selected.size + if (vehicles.isEmpty()) 0 else 1
                Text(if (count == 0) "Több feltételt is választhatsz." else "$count feltétel kiválasztva",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val enabledVehicles = SearchVehicle.entries.filter { vehicles.isEmpty() || it in vehicles }
                Surface(onClick = { vehiclesOpen = true }, shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).semantics {
                        stateDescription = "Kiválasztott járműtípusok: ${enabledVehicles.joinToString { it.title }}"
                    }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Járművek", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                enabledVehicles.forEach { vehicle ->
                                    Image(painterResource(vehicle.searchPictogram()), contentDescription = null,
                                        modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                listOf(
                    "Utazás" to listOf(SearchFilter.Direct, SearchFilter.CountryPass),
                    "Kocsiosztály" to listOf(SearchFilter.SecondClass, SearchFilter.FirstClass, SearchFilter.PremiumClass),
                    "Szolgáltatások" to listOf(SearchFilter.AirConditioning, SearchFilter.Bicycle,
                        SearchFilter.Accessible, SearchFilter.Dining, SearchFilter.Sleeper)
                ).forEach { (title, filters) ->
                    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filters.forEach { filter ->
                                val checked = filter in selected
                                FilterChip(selected = checked,
                                    onClick = { choose(filter, !checked) },
                                    label = { Text(filter.title) },
                                    leadingIcon = { SearchFilterIcon(filter) },
                                    modifier = Modifier.heightIn(min = 48.dp))
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
                        Text("A klímát a MÁV keresési feltétele alapján szűrjük. A többi szolgáltatáshoz minden utazási szakaszon szolgáltatói adat szükséges. A hiányzó adat nem számít egyezésnek.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Országbérletnél helyjegy, kerékpárnál foglalás, akadálymentes utazásnál előzetes bejelentés szükséges lehet.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Az országbérlet és az 1. osztály külön feltétel. Együttes kiválasztásuk nem igazolja a bérlet 1. osztályú érvényességét.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(AppLayout.SheetPadding), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = reset, enabled = selected.isNotEmpty() || vehicles.isNotEmpty()) { Text("Alaphelyzet") }
                Button(onClick = dismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Kész") }
            }
        }
    }
    if (vehiclesOpen) {
        SearchVehicleDrawer(vehicles, toggleVehicle, allVehicles) { vehiclesOpen = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SearchVehicleDrawer(vehicles: Set<SearchVehicle>, toggleVehicle: (SearchVehicle) -> Unit,
    allVehicles: () -> Unit, dismiss: () -> Unit) {
    AppBottomSheet(onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = AppLayout.SheetPadding),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = dismiss) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Vissza a keresési feltételekhez")
                }
                Text("Járművek", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(horizontal = AppLayout.SheetPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Legalább egy járműtípust hagyj bekapcsolva. A gyalogos szakaszok megmaradnak.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SearchVehicle.entries.forEach { vehicle ->
                    val checked = vehicles.isEmpty() || vehicle in vehicles
                    val enabled = !checked || vehicles.size != 1
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch,
                            onValueChange = { toggleVehicle(vehicle) }).heightIn(min = 56.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Image(painterResource(vehicle.searchPictogram()), contentDescription = null,
                                modifier = Modifier.size(28.dp))
                            Text(vehicle.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(AppLayout.SheetPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = allVehicles, enabled = vehicles.isNotEmpty()) { Text("Mindegyik") }
                Button(onClick = dismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Kész") }
            }
        }
    }
}

private fun SearchVehicle.searchPictogram(): Int = when (this) {
    SearchVehicle.Train -> R.drawable.ic_mode_rail
    SearchVehicle.Coach -> R.drawable.ic_mode_coach
    SearchVehicle.LocalBus -> R.drawable.ic_mode_local_bus
    SearchVehicle.Hev -> R.drawable.ic_mode_hev
    SearchVehicle.TramTrain -> R.drawable.ic_mode_tramtrain
}
