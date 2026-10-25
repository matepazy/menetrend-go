package hu.matepazy.menetrendgo

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Service descriptions and their original validity sections are kept together.
 * A missing entry is unknown, never evidence that a facility is unavailable.
 */
data class MavService(val name: String, val fontCode: Int?, val fontCharSet: String,
    val fromIndex: Int?, val tillIndex: Int?, val from: String, val till: String) {
    val ticketRule: Boolean get() = ticketRulePattern.containsMatchIn(name)
    val section: String get() = when {
        from.isNotBlank() && till.isNotBlank() -> if(from==till) from else "$from – $till"
        from.isNotBlank() -> "Ettől: $from"
        till.isNotBlank() -> "Eddig: $till"
        else -> ""
    }
    // MÁV uses the numeric fontCode directly as a Unicode code point.
    val glyph: String? get() = fontCode?.takeIf {
        fontCharSet.equals("MNR2007",ignoreCase=true) && it in 33..0x10ffff && it !in 0xd800..0xdfff
    }?.let {String(Character.toChars(it))}

    companion object {
        private val ticketRulePattern=Regex("bérlet|helyjegy|helyfoglal|foglalása|pótjegy|nemzetközi jegy",RegexOption.IGNORE_CASE)
        fun parseAll(parent: JSONObject): List<MavService> = parent.array("infoServices").mapNotNull {o ->
            val name=o.optString("name").trim().takeUnless {it.isBlank() || it=="null"}
            if(name==null || !o.optBoolean("displayable",true)) null else {
                fun number(key: String) = if(o.isNull(key)) null else o.optInt(key).takeIf {it>=0}
                fun stop(key: String) = o.optJSONObject(key)?.optString("name")?.takeUnless {it=="null"}.orEmpty()
                MavService(name,number("fontCode"),o.optString("fontCharSet"),number("fromStopIndex"),
                    number("tillStopIndex"),stop("fromStop"),stop("tillStop"))
            }
        }.distinct()
    }
}

private val mavServiceFont = FontFamily(Font(R.font.mav_services))

/** Declaration order is the departure-card priority. Unclassified notes stay in details. */
enum class AmenityPriority(val pattern: Regex) {
    RequiredReservation(Regex("^(?=.*(?:helyjegy|helyfoglal|pótjegy|nemzetközi jegy))(?=.*kötelező)(?!.*kerékpár)")),
    CountryPass(Regex("országbérlet|ország.?bérlet|országnapijegy|magyarország24")),
    Catering(Regex("büfé|büfékocsi|bisztró|étkezőkocsi|étterem|étkezési|vendéglát|italautomata")),
    Accessibility(Regex("kerekesszék|mozgáskorlátozott|akadályment|emelő|alacsonypadló")),
    BicycleReservation(Regex("(?=.*kerékpár)(?=.*(?:foglal|helyjegy|kötelező))")),
    Bicycle(Regex("kerékpár|bicikli")),
    Sleeper(Regex("hálókocsi|fekvőhely|hálóhely|kusett")),
    Wifi(Regex("wi.?fi|wlan|internet")),
    Power(Regex("konnektor|csatlakozóaljzat|dugalj|töltő|usb|230\\s*v")),
    AirConditioning(Regex("légkondicion|klimatiz|klím")),
    Family(Regex("családi|gyermek|játszó|pelenkáz|babakocsi")),
    Quiet(Regex("csendes|csendkocsi")),
    Class(Regex("1\\.\\s*(?:kocsi)?osztály|első osztály|prémium|business")),
    OtherPass(Regex("bérlet|napijegy")),
    OptionalReservation(Regex("helyjegy|helyfoglal|foglalása|pótjegy|nemzetközi jegy")),
    OtherFacility(Regex("poggyász|csomag|mosdó|wc|állatszáll|kutya"));

    companion object {
        fun of(service: MavService): AmenityPriority? {
            if(service.fontCode in setOf(168,339,406)) return null // Provider notes, including the boxed [i] glyph (406).
            val name=service.name.lowercase(java.util.Locale.ROOT)
            return entries.firstOrNull {it.pattern.containsMatchIn(name)}
        }
    }
}

internal fun departureAmenities(services: List<MavService>): List<MavService> = services
    .filter {it.glyph!=null && AmenityPriority.of(it)!=null}
    .sortedBy {AmenityPriority.of(it)!!.ordinal}
    .distinctBy {Triple(AmenityPriority.of(it),it.fontCharSet,it.fontCode)}
    .take(6)

@Composable private fun MavServiceIcon(service: MavService, modifier: Modifier = Modifier) {
    service.glyph?.let {glyph ->
        Text(glyph,modifier=modifier.widthIn(min=28.dp).clearAndSetSemantics {},fontFamily=mavServiceFont,
            fontSize=24.sp,lineHeight=30.sp,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurface)
    }
}

/** A single accessible target keeps the row usable even when its pictograms shrink. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun JourneyAmenityIcons(journey: Journey) {
    val rules=journey.legs.filterNot {it.walking}.flatMap {leg ->
        leg.services.map {leg.route to it}
    }
    val amenities=departureAmenities(rules.map {it.second})
    if(amenities.isEmpty()) return
    var selected by remember(journey.key) {mutableStateOf<List<Pair<String,MavService>>?>(null)}
    Column {
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top=AppLayout.CompactGap)
            .heightIn(min=48.dp).clickable(onClickLabel="Szolgáltatások részletei",onClick={
                selected=amenities.flatMap {service -> rules.filter {
                    it.second.fontCharSet==service.fontCharSet && it.second.fontCode==service.fontCode &&
                        AmenityPriority.of(it.second)==AmenityPriority.of(service)
                }}
            }).semantics(mergeDescendants=true) {
                contentDescription=amenities.joinToString(". ") {it.name}
            },contentAlignment=Alignment.CenterStart) {
            // Symbols behave like vector icons, independently of the system text scale.
            val slotWidth=minOf(56.dp,maxWidth/amenities.size)
            val iconSize=minOf(24f,slotWidth.value/2)
            val fontScale=LocalDensity.current.fontScale
            Row(verticalAlignment=Alignment.CenterVertically) {
                amenities.forEach {service ->
                    Box(Modifier.width(slotWidth).height(48.dp),contentAlignment=Alignment.Center) {
                        Text(service.glyph.orEmpty(),fontFamily=mavServiceFont,
                            fontSize=(iconSize/fontScale).sp,lineHeight=(iconSize*1.25f/fontScale).sp,
                            maxLines=1,softWrap=false,modifier=Modifier.clearAndSetSemantics {},
                            color=MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
    selected?.let {entries ->
        ModalBottomSheet(onDismissRequest={selected=null},
            sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            Column(Modifier.fillMaxWidth().heightIn(max=600.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal=AppLayout.SheetPadding).padding(bottom=AppLayout.SheetPadding),
                verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Szolgáltatások",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    IconButton(onClick={selected=null}) {Icon(Icons.Outlined.Close,"Bezárás")}
                }
                entries.distinct().forEach {(route,service)->
                    Column(verticalArrangement=Arrangement.spacedBy(AppLayout.LabelGap)) {
                        Text(route,style=MaterialTheme.typography.titleSmall)
                        ServiceDescription(service)
                    }
                }
            }
        }
    }
}

@Composable private fun ServiceDescription(service: MavService) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(AppLayout.RelatedGap),verticalAlignment=Alignment.Top) {
        Box(Modifier.width(32.dp),contentAlignment=Alignment.TopCenter) {MavServiceIcon(service)}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(AppLayout.LabelGap)) {
            Text(service.name,style=MaterialTheme.typography.bodyMedium)
            if(service.section.isNotBlank()) Text(service.section,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun MavServices(services: List<MavService>, modifier: Modifier = Modifier) {
    if(services.isEmpty()) return
    var expanded by rememberSaveable(services) {mutableStateOf(false)}
    val rules=services.filter {it.ticketRule}
    val notes=services.filter { !it.ticketRule && it.fontCode in setOf(168,339) }
    val facilities=services.filterNot {it in rules || it in notes}
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
        listOf("A fedélzeten" to facilities,"Jegyek és bérletek" to rules).filter {it.second.isNotEmpty()}.forEachIndexed {index,(title,rows) ->
            if(index>0) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Text(title,style=MaterialTheme.typography.titleMedium)
            rows.forEach {ServiceDescription(it)}
        }
        if(notes.isNotEmpty()) {
        TextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(0.dp),
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics {
                stateDescription=if(expanded) "Kibontva" else "Összecsukva"
            }) {
            Text("További tudnivalók",Modifier.weight(1f),textAlign=TextAlign.Start)
            Icon(if(expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,if(expanded) "Részletek elrejtése" else "Részletek megjelenítése")
        }
        if(expanded) notes.forEach {ServiceDescription(it)}
        }
    }
}
