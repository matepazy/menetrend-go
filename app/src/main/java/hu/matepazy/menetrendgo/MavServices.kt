package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable private fun MavServiceIcon(service: MavService, modifier: Modifier = Modifier) {
    service.glyph?.let {glyph ->
        Text(glyph,modifier=modifier.widthIn(min=28.dp).clearAndSetSemantics {},fontFamily=mavServiceFont,
            fontSize=24.sp,lineHeight=30.sp,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurface)
    }
}

@Composable fun MavServices(services: List<MavService>, modifier: Modifier = Modifier) {
    if(services.isEmpty()) return
    var expanded by rememberSaveable(services) {mutableStateOf(false)}
    Column(modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        TextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(0.dp),
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics {
                stateDescription=if(expanded) "Kibontva" else "Összecsukva"
            }) {
            Text("Szolgáltatások és tudnivalók",Modifier.weight(1f),textAlign=TextAlign.Start)
            Icon(Icons.Outlined.ExpandMore,if(expanded) "Részletek elrejtése" else "Részletek megjelenítése")
        }
        if(!expanded) FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            services.filter {it.glyph!=null}.distinctBy {it.fontCharSet to it.fontCode}.forEach {service ->
                // The label remains readable to TalkBack without announcing the font character.
                Box(Modifier.semantics {contentDescription=services.filter {
                    it.fontCharSet==service.fontCharSet && it.fontCode==service.fontCode
                }.joinToString(". ") {listOf(it.name,it.section).filter(String::isNotBlank).joinToString(" · ")}}) {
                    MavServiceIcon(service)
                }
            }
        }
        if(expanded) services.forEach {service ->
            Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),
                verticalAlignment=Alignment.Top) {
                MavServiceIcon(service)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(service.name,style=MaterialTheme.typography.bodyMedium)
                    if(service.section.isNotBlank()) Text(service.section,style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
