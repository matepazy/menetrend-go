package hu.matepazy.menetrendgo

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The label owns only its model sheet. Dismissing it cannot clear the selected map vehicle. */
@Composable fun VehicleModelLabel(vehicle: Vehicle, modifier: Modifier = Modifier) {
    var open by rememberSaveable(vehicle.model) { mutableStateOf(false) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val color = MaterialTheme.colorScheme.primary
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap),
        itemVerticalAlignment = Alignment.CenterVertically) {
        if (vehicle.model.isNotBlank()) Text(vehicle.model, style = MaterialTheme.typography.bodyMedium,
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
        if (vehicle.registration.isNotBlank()) Text(vehicle.registration,
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) VehicleModelDrawer(vehicle.model) { open = false }
}

/**
 * THESIS: Turn a reported model name into a small, sourced field guide.
 * OWN-WORLD: Existing icy-blue Material roles, rounded sheets and system typography.
 * STORY: Tap the dotted name, recognise the vehicle, read facts, return to the same journey.
 * FIRST VIEWPORT: Model heading and close control, full-width fleet photo and adjacent credit.
 * FORM: A local extension of the established native drawer; no replacement visual identity.
 * FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review,
 * the verdict, DESIGN.md, and every shipping raster carrying its provenance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun VehicleModelDrawer(model: String, dismiss: () -> Unit) {
    val assets = LocalContext.current.assets
    var attempt by remember { mutableIntStateOf(0) }
    val result by produceState<Result<List<VehicleModelProfile>>?>(null, attempt) {
        value = withContext(Dispatchers.IO) { runCatching { VehicleModelCatalog.load(assets) } }
    }
    val profile = result?.getOrNull()?.let { findVehicleModel(it, model) }
    ModalBottomSheet(onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = AppLayout.SheetPadding),
                verticalAlignment = Alignment.CenterVertically) {
                Text(profile?.title ?: model, Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
                    start = AppLayout.SheetPadding, end = AppLayout.SheetPadding,
                    top = AppLayout.RelatedGap, bottom = AppLayout.SectionGap),
                    verticalArrangement = Arrangement.spacedBy(AppLayout.RelatedGap)) {
                    if (profile.photos.isNotEmpty()) item {
                        val pager = rememberPagerState(pageCount = { profile.photos.size })
                        Column(verticalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                            HorizontalPager(pager, key = { profile.photos[it].asset }) { index ->
                                FleetPhoto(profile.photos[index], profile.title)
                            }
                            if (profile.photos.size > 1) Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Fotók · lapozz a képek között", style = MaterialTheme.typography.labelMedium)
                                Text("${pager.currentPage + 1} / ${profile.photos.size}", style = MaterialTheme.typography.labelMedium)
                            }
                            PhotoCredit(profile.photos[pager.currentPage])
                            Text("A fotó a típus egy hazai példányát mutatja, nem feltétlenül az érkező járművet.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else item {
                        Text("Ehhez a típushoz még nincs szabadon felhasználható, ellenőrzött hazai fotónk.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item { Text(profile.description, style = MaterialTheme.typography.bodyLarge) }
                    item { Text("Jó tudni", Modifier.padding(top = AppLayout.RelatedGap).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    profile.facts.forEach { fact -> item { Text(fact, style = MaterialTheme.typography.bodyMedium) } }
                    item { HorizontalDivider(Modifier.padding(top = AppLayout.RelatedGap)) }
                    item {
                        Column {
                            Text("Az ismertető forrásai", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            profile.sources.forEach { (label, url) -> SourceLink(label, url) }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun FleetPhoto(photo: ModelPhoto, title: String) {
    val assets = LocalContext.current.assets
    val bitmap by produceState<Result<ImageBitmap>?>(null, photo.asset) {
        value = withContext(Dispatchers.IO) {
            runCatching { assets.open("vehicle_models/${photo.asset}").use {
                checkNotNull(BitmapFactory.decodeStream(it)).asImageBitmap()
            } }
        }
    }
    Surface(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(MaterialTheme.shapes.medium),
        color = MaterialTheme.colorScheme.surfaceContainer) {
        bitmap?.getOrNull()?.let { Image(it, "$title · ${photo.caption}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (bitmap == null) CircularProgressIndicator(Modifier.size(24.dp))
                else Text("A fotó nem jeleníthető meg.", Modifier.padding(AppLayout.RelatedGap), style = MaterialTheme.typography.bodyMedium)
            }
    }
}

@Composable private fun PhotoCredit(photo: ModelPhoto) {
    Column {
        Text(photo.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SourceLink("Fotó: ${photo.author} · Wikimedia Commons", photo.source)
        SourceLink("${photo.license} · arányosan megjelenítve", photo.licenseUrl)
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
