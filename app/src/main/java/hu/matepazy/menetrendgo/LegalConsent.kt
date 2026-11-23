package hu.matepazy.menetrendgo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Terms every user accepts before using the app. Bump [Version] when either text changes to ask everyone again. */
internal object LegalTerms {
    const val Version = 1
    const val LicenseUrl = "https://creativecommons.org/licenses/by-nc-sa/4.0/legalcode"
}

internal enum class LegalDoc(val title: String, val summary: String) {
    License("Licencszerződés", "CC BY-NC-SA 4.0, szabadon módosíthatod és megoszthatod, de nem kereskedelmi célra."),
    Terms("Felhasználási feltételek", "Független alkalmazás a MÁV és az utas.hu nyilvános adataival."),
}

private class Clause(val heading: String, val body: String)

/** GitHub-style license summary: what the license allows, what it asks for, and what it does not cover. */
private enum class LicenseGroup(val title: String, val icon: ImageVector, val items: List<String>) {
    Permissions("Engedélyek", Icons.Outlined.Check, listOf("Módosítás", "Terjesztés", "Magáncélú használat")),
    Conditions("Feltételek", Icons.Outlined.Info, listOf("A szerző és a licenc feltüntetése", "A változtatások jelölése", "Továbbadás ugyanezzel a licenccel")),
    Limitations("Korlátozások", Icons.Outlined.Block, listOf("Kereskedelmi felhasználás", "Felelősségvállalás", "Garancia", "Védjegyhasználat", "Szabadalmi jogok")),
}

private val termsClauses = listOf(
    Clause("Független alkalmazás", "A MenetrendGO független fejlesztés. Nem a MÁV-csoport, a Volánbusz vagy az utas.hu terméke, és nem áll kapcsolatban velük."),
    Clause("Az adatok forrása", "A menetrendet és az élő adatokat a MÁV (MÁVPlusz) és bekapcsolva az utas.hu nyilvános szolgáltatásai adják. Üzemeltetőik ezt a hozzáférést bármikor módosíthatják vagy megszüntethetik. Ilyenkor az eszközödre már letöltött adatok továbbra is használhatók maradnak."),
    // When offline timetables ship, name them here and bump LegalTerms.Version.
    Clause("A hivatalos menetrend", "A menetrend hivatalos forrása a MÁV. Az indulásokat a MÁV felületein is megnézheted."),
)

/**
 * The repository LICENSE is hard-wrapped at ~70 columns. Joins wrapped lines into paragraphs so the text
 * reflows at any width; blank lines, list items and section titles keep their own lines, divider rules are dropped.
 */
internal fun reflowLegalText(raw: String): String {
    val item = Regex("""^(\(?[a-z0-9]{1,4}[.)]\s|Section \d)""")
    val rule = Regex("^[=-]{3,}$")
    val out = StringBuilder()
    var open = false
    raw.replace("\r\n", "\n").lines().forEach { line ->
        val text = line.trim()
        // Plain-text divider rules would wrap on a phone; paragraph spacing already separates the parts.
        if (rule.matches(text)) return@forEach
        if (text.isEmpty()) { if (open) out.append("\n\n"); open = false; return@forEach }
        if (open && !item.containsMatchIn(text)) out.append(' ').append(text)
        else { if (open) out.append('\n'); out.append(text); open = true }
    }
    return out.toString().replace(Regex("\n{3,}"), "\n\n").trim()
}

/** One document to accept: the checkbox toggles, the rest of the card opens the document. */
@Composable internal fun LegalConsentCard(doc: LegalDoc, checked: Boolean, onCheckedChange: (Boolean) -> Unit, onOpen: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth().testTag("legal-card-${doc.name}"),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, onClick = onOpen) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = AppLayout.ItemGap, top = AppLayout.CompactGap, bottom = AppLayout.CompactGap),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
            Checkbox(checked, onCheckedChange, Modifier.testTag("legal-check-${doc.name}").semantics { contentDescription = "${doc.title} elfogadása" })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
                Text(doc.title, style = MaterialTheme.typography.titleMedium)
                Text(doc.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Reads [doc] in a drawer. With [onAccept] the drawer ends in "Elfogadom"; without it, it only closes (Settings). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun LegalDocumentDrawer(doc: LegalDoc, onDismiss: () -> Unit, onAccept: (() -> Unit)? = null) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("legal-drawer-${doc.name}"), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalWindowInfo.current.containerDpSize.height * .85f)
            .padding(horizontal = AppLayout.SheetPadding).padding(bottom = AppLayout.SheetPadding), verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
                PageTitle(doc.title)
                if (doc == LegalDoc.License) {
                    Text("A MenetrendGO a Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International (CC BY-NC-SA 4.0) licenc alatt érhető el.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LicenseGroup.entries.forEach { LicenseGroupBlock(it) }
                    Text("Ez az összefoglaló csak eligazít, nem helyettesíti a licencet. Kötelező erejű a teljes, angol nyelvű jogi szöveg.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FullLicense()
                } else {
                    termsClauses.forEach { ClauseBlock(it) }
                }
            }
            if (onAccept != null) {
                Button(onClick = { onAccept(); onDismiss() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("legal-accept"),
                    border = glassBorder(buttonGlassTone(true))) { Text("Elfogadom") }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Bezárás") }
            } else {
                FilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Bezárás") }
            }
        }
    }
}

@Composable private fun LicenseGroupBlock(group: LicenseGroup) {
    val tint = when (group) {
        LicenseGroup.Permissions -> MaterialTheme.colorScheme.primary
        LicenseGroup.Conditions -> MaterialTheme.colorScheme.onSurfaceVariant
        LicenseGroup.Limitations -> MaterialTheme.colorScheme.error
    }
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
        Text(group.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        group.items.forEach { item ->
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                Icon(group.icon, null, Modifier.size(20.dp), tint = tint)
                Text(item, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable private fun ClauseBlock(clause: Clause) {
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
        Text(clause.heading, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text(clause.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The bundled legal code, loaded only when expanded, plus the official page. */
@Composable private fun FullLicense() {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val dark = LocalAppDarkTheme.current
    val toolbar = MaterialTheme.colorScheme.surface
    Column(verticalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
            OutlinedButton({ open = !open }, Modifier.weight(1f).heightIn(min = 48.dp).testTag("legal-full-text")) {
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(AppLayout.LabelGap))
                Text(if (open) "Teljes szöveg elrejtése" else "Teljes jogi szöveg")
            }
            IconButton({ openInAppBrowser(context, LegalTerms.LicenseUrl, dark, toolbar) }, Modifier.size(48.dp)) {
                Icon(Icons.Outlined.OpenInNew, "Licenc megnyitása a creativecommons.org oldalon")
            }
        }
        AnimatedVisibility(open) { LegalAssetText("LICENSE.txt") }
    }
}

/** A bundled license file, reflowed for the screen width and selectable; loaded off the main thread when first shown. */
@Composable internal fun LegalAssetText(asset: String) {
    val context = LocalContext.current
    val text by produceState<String?>(null, asset) {
        value = withContext(Dispatchers.IO) {
            runCatching { reflowLegalText(context.assets.open(asset).bufferedReader().use { it.readText() }) }.getOrDefault("")
        }
    }
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        when (val t = text) {
            null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(AppLayout.ItemGap))
            "" -> Text("A licenc szövege nem tölthető be. A projekt oldalán is elolvashatod.",
                Modifier.padding(AppLayout.ItemGap), style = MaterialTheme.typography.bodyMedium)
            else -> SelectionContainer {
                Text(t, Modifier.padding(AppLayout.ItemGap), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
