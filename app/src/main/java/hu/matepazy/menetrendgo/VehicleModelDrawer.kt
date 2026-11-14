package hu.matepazy.menetrendgo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The label owns only its model sheet. Dismissing it cannot clear the selected map vehicle. */
@Composable fun VehicleModelLabel(vehicle: Vehicle, modifier: Modifier = Modifier) {
    VehicleModelLabel(vehicle.model, vehicle.registration, vehicle.mode, vehicle.agencyName, modifier)
}

@Composable internal fun VehicleModelLabel(model: String, registration: String, mode: String, agencyName: String,
    modifier: Modifier = Modifier) {
    var open by rememberSaveable(model) { mutableStateOf(false) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val color = MaterialTheme.colorScheme.primary
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap),
        itemVerticalAlignment = Alignment.CenterVertically) {
        if (model.isNotBlank()) Text(model, style = MaterialTheme.typography.bodyMedium,
            color = color, onTextLayout = { layout = it }, modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = "Járműtípus bemutatása") { open = true }
                .padding(vertical = 12.dp)
                .drawWithContent {
                    drawContent()
                    layout?.let { text ->
                        repeat(text.lineCount) { line ->
                            val y = text.getLineBaseline(line) + 3.dp.toPx()
                            drawLine(color, Offset(text.getLineLeft(line), y), Offset(text.getLineRight(line), y),
                                strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 3.dp.toPx())))
                        }
                    }
                })
        if (registration.isNotBlank()) Text(registration,
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) VehicleModelDrawer(model, mode, agencyName) { open = false }
}

/**
 * THESIS: Turn a reported model name into a small, sourced field guide.
 * OWN-WORLD: Existing icy-blue Material roles, rounded sheets and system typography.
 * STORY: Tap the dotted name, recognise the vehicle, read facts, return to the same journey.
 * FIRST VIEWPORT: Model name, familiar-name badge and close control; broad photo and quiet caption.
 * REFERENCE: User's Desiro example guides article spacing and the compact source disclosure.
 * FORM: A local extension of the established native drawer; no replacement visual identity.
 * FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review,
 * the verdict, DESIGN.md, and every shipping raster carrying its provenance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun VehicleModelDrawer(model: String, mode: String = "", agencyName: String = "", dismiss: () -> Unit) {
    val assets = LocalContext.current.assets
    var attempt by remember { mutableIntStateOf(0) }
    val result by produceState<Result<List<VehicleModelProfile>>?>(null, attempt) {
        value = withContext(Dispatchers.IO) { runCatching { VehicleModelCatalog.load(assets) } }
    }
    val profile = result?.getOrNull()?.let { findVehicleModel(it, model, agencyName) }
        ?.let { vehicleModelForContext(it, mode, agencyName) }
    val photo = profile?.photos?.singleOrNull()
    var enlarged by remember(profile?.id) { mutableStateOf<ImageBitmap?>(null) }
    AppBottomSheet(onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(start = AppLayout.ItemGap, end = AppLayout.CompactGap,
                bottom = AppLayout.RelatedGap), verticalAlignment = Alignment.Top) {
                FlowRow(Modifier.weight(1f).padding(top = AppLayout.CompactGap),
                    horizontalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap),
                    verticalArrangement = Arrangement.spacedBy(AppLayout.CompactGap),
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    val title = profile?.title ?: model
                    Text(title.substringBefore(" · "), Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    title.substringAfter(" · ", "").takeIf { it.isNotBlank() }?.let { familiarName ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                            Text(familiarName, Modifier.padding(horizontal = AppLayout.RelatedGap, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Járműtípus bemutatásának bezárása") }
            }
            when {
                result == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(AppLayout.SheetPadding))
                result?.isFailure == true -> Column(Modifier.padding(AppLayout.SheetPadding)) {
                    Text("A járműismertető nem tölthető be.")
                    TextButton(onClick = { attempt++ }) { Text("Újrapróbálás") }
                }
                profile == null -> Column(Modifier.padding(AppLayout.SheetPadding), verticalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
                    Text("Ehhez a típushoz még nincs ellenőrzött ismertetőnk.", style = MaterialTheme.typography.bodyLarge)
                    Text("A szolgáltató által jelzett típus: $model", style = MaterialTheme.typography.bodyMedium)
                }
                else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(
                    start = AppLayout.ItemGap, end = AppLayout.ItemGap,
                    bottom = AppLayout.SectionGap)) {
                    if (photo != null) item {
                        Column(verticalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                            FleetPhoto(photo, profile.title) { enlarged = it }
                            Text(photo.caption,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("A fotó típusillusztráció. Az érkező jármű festése és felszereltsége eltérhet.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else item {
                        Text("Ehhez a típushoz még nincs szabadon felhasználható, ellenőrzött hazai fotónk.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val sheet = vehicleFactSheet(profile.specifications)
                    item { SheetHeading("Adatlap") }
                    if (sheet.figures.isNotEmpty()) item { KeyFigures(sheet.figures) }
                    sheet.specifications.forEach { (label, value) -> item { SpecRow(label, value, mode) } }
                    item { SheetHeading("A járműről") }
                    item { Text(profile.description, style = MaterialTheme.typography.bodyLarge) }
                    if (profile.facts.isNotEmpty()) item { Text(if (profile.facts.size == 1) "Érdekesség" else "Érdekességek",
                        Modifier.padding(top = AppLayout.SectionGap, bottom = AppLayout.RelatedGap).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    profile.facts.forEach { fact -> item {
                        Text(fact, Modifier.padding(bottom = AppLayout.RelatedGap), style = MaterialTheme.typography.bodyLarge)
                    } }
                    item { HorizontalDivider(Modifier.padding(top = AppLayout.SectionGap)) }
                    item {
                        ModelSources(profile, photo)
                    }
                }
            }
        }
    }
    enlarged?.let { bitmap ->
        photo?.let { FleetPhotoViewer(listOf(it), 0, bitmap, onDismiss = { enlarged = null }) }
    }
}

@Composable private fun SheetHeading(text: String) {
    Text(text, Modifier.padding(top = AppLayout.SectionGap, bottom = AppLayout.RelatedGap).semantics { heading() },
        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

/** Capacity and boarding lead as equal tiles; unparsed values keep their full wording instead of a guessed number. */
@Composable private fun KeyFigures(figures: List<KeyFigure>) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = AppLayout.CompactGap),
        horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
        figures.forEach { figure ->
            Surface(Modifier.weight(1f).fillMaxHeight(), shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(AppLayout.RelatedGap).semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(keyFigureIcon(figure), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Text(figure.kind.label, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (figure.figure != null) Text(figure.figure, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    else Text(figure.text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    figure.note?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

private fun keyFigureIcon(figure: KeyFigure): ImageVector = when (figure.kind) {
    KeyFigureKind.Seats -> Icons.Outlined.EventSeat
    KeyFigureKind.Standing -> Icons.Outlined.Man
    KeyFigureKind.Floor -> if ("lépcső" in figure.text.lowercase()) Icons.Outlined.Stairs else Icons.Outlined.DoorSliding
}

@Composable private fun SpecRow(label: String, value: SpecValue, mode: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = AppLayout.CompactGap).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
        Icon(specIcon(label, value.main, mode), null, Modifier.padding(top = 2.dp).size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.main, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            value.note?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun specIcon(label: String, value: String, mode: String): ImageVector {
    val text = value.lowercase()
    return when {
        "gyártó" in label.lowercase() -> Icons.Outlined.Factory
        label == "Járműfajta" -> if (mode == "BUS" || mode == "COACH" || "busz" in text) Icons.Outlined.DirectionsBus
            else if (mode == "TRAM") Icons.Outlined.Tram else Icons.Outlined.Train
        label == "Hajtás" -> when {
            listOf("villamos", "elektrom", "akkumul").any { it in text } -> Icons.Outlined.ElectricBolt
            "dízel" in text || "földgáz" in text -> Icons.Outlined.LocalGasStation
            else -> Icons.Outlined.Settings
        }
        "jel" in label -> Icons.Outlined.Tag
        label.startsWith("Gyártási") -> Icons.Outlined.CalendarMonth
        label.startsWith("Hossz") -> Icons.Outlined.Straighten
        "sebesség" in label -> Icons.Outlined.Speed
        label == "Felhasználás" -> Icons.Outlined.Route
        label == "Összeállítás" -> Icons.Outlined.ViewWeek
        else -> Icons.Outlined.Info
    }
}

@Composable private fun FleetPhoto(photo: ModelPhoto, title: String, onEnlarge: (ImageBitmap) -> Unit) {
    val context = LocalContext.current.applicationContext
    var attempt by remember(photo.url) { mutableIntStateOf(0) }
    val bitmap by produceState<Result<ImageBitmap>?>(null, photo.url, attempt) {
        value = null
        value = try { Result.success(FleetPhotoStore.load(context, photo.url).asImageBitmap()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Result.failure(IllegalStateException("Photo unavailable")) }
    }
    Surface(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(MaterialTheme.shapes.small),
        color = MaterialTheme.colorScheme.surfaceContainer) {
        bitmap?.getOrNull()?.let { loaded ->
            Box(Modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = "Fotó nagyítása") { onEnlarge(loaded) }) {
                Image(loaded, "$title · ${photo.caption}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                FilledTonalIconButton(onClick = { onEnlarge(loaded) }, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                    Icon(Icons.Outlined.OpenInFull, "Fotó nagyítása")
                }
            }
        }
            ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (bitmap == null) Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("Fotó betöltése…", style = MaterialTheme.typography.bodySmall)
                }
                else Column(Modifier.padding(AppLayout.RelatedGap), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("A webes fotó nem tölthető be. Ellenőrizd az internetkapcsolatot.",
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { attempt++ }) { Text("Fotó újratöltése") }
                }
            }
    }
}

@Composable private fun ModelSources(profile: VehicleModelProfile, currentPhoto: ModelPhoto?) {
    var expanded by rememberSaveable(profile.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else -90f,
        tween(AppMotion.Reveal, easing = AppMotion.State), label = "Vehicle sources")
    Column {
        Row(Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Források elrejtése" else "Források megjelenítése") { expanded = !expanded }
            .semantics { stateDescription = if (expanded) "Kibontva" else "Összecsukva" }
            .padding(vertical = AppLayout.ItemGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
            Surface(Modifier.size(44.dp), shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Link, null, Modifier.size(22.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
                Text("Fotó és források", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(currentPhoto?.let { "${it.author} · Wikimedia Commons" } ?: "Az ismertető adatforrásai",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                currentPhoto?.let { photo ->
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                        Text(photo.license, Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(24.dp).graphicsLayer { rotationZ = rotation },
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(expanded,
            enter = expandVertically(tween(AppMotion.Reveal, easing = AppMotion.Arrival)) + fadeIn(tween(AppMotion.Feedback)),
            exit = shrinkVertically(tween(AppMotion.Dismiss, easing = AppMotion.State)) + fadeOut(tween(AppMotion.Dismiss))) {
            Column(Modifier.padding(bottom = AppLayout.CompactGap), verticalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
                currentPhoto?.let { PhotoCredit(it) }
                Text("Az ismertető forrásai", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                profile.sources.forEach { (label, url) -> SourceLink(label, url) }
            }
        }
    }
}

@Composable private fun PhotoCredit(photo: ModelPhoto) {
    Column {
        Text(photo.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SourceLink("Fotó: ${photo.author} · Wikimedia Commons", photo.source)
        SourceLink("${photo.license} · átméretezett fotó", photo.licenseUrl)
    }
}

@Composable private fun SourceLink(label: String, url: String) {
    val uri = LocalUriHandler.current
    var failed by remember(url) { mutableStateOf(false) }
    TextButton(onClick = { failed = runCatching { uri.openUri(url) }.isFailure },
        contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.heightIn(min = 48.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
    if (failed) Text("A hivatkozás nem nyitható meg ezen az eszközön.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
