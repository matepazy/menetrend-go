package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val PassDateFormat=DateTimeFormatter.ofPattern("yyyy. MMM d.",Locale.forLanguageTag("hu-HU"))
internal fun passDate(date: LocalDate): String = date.format(PassDateFormat)

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun PassEditor(pass: Pass?, onDismiss: () -> Unit, save: (Pass) -> Unit) {
    var type by rememberSaveable {mutableStateOf(if(pass?.localCity!=null) "local" else if(pass?.county!=null) "county" else "country")}
    var county by rememberSaveable {mutableStateOf(pass?.county ?: "Fejér")}
    var city by rememberSaveable {mutableStateOf(pass?.localCity ?: "")}
    var countyOpen by remember {mutableStateOf(false)}
    var startDay by rememberSaveable {mutableLongStateOf((pass?.starts ?: LocalDate.now(Budapest)).toEpochDay())}
    var endDay by rememberSaveable {mutableLongStateOf((pass?.expires ?: LocalDate.now(Budapest).plusDays(29)).toEpochDay())}
    var thirtyDays by rememberSaveable {mutableStateOf(pass?.thirtyDays ?: true)}
    var calendar by rememberSaveable {mutableStateOf<String?>(null)}
    val starts=LocalDate.ofEpochDay(startDay)
    val expires=LocalDate.ofEpochDay(endDay)
    val valid=!expires.isBefore(starts) && (type!="local" || city.isNotBlank())

    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),shape=MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start=AppLayout.SheetPadding,end=AppLayout.SheetPadding,bottom=AppLayout.SheetPadding),verticalArrangement=Arrangement.spacedBy(AppLayout.SectionGap)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(if(pass==null) "Új bérlet" else "Bérlet szerkesztése",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                IconButton(onClick=onDismiss) {Icon(Icons.Outlined.Close,"Bezárás")}
            }
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Bérlet típusa",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(AppLayout.CompactGap),verticalArrangement=Arrangement.spacedBy(AppLayout.CompactGap)) {
                    listOf("country" to "Ország", "county" to "Vármegye", "local" to "Helyi").forEach {(value,label)->
                        FilterChip(type==value,{type=value},label={Text(label)},leadingIcon=if(type==value) ({Icon(Icons.Outlined.Check,null,Modifier.size(18.dp))}) else null,shape=CircleShape,modifier=Modifier.heightIn(min=48.dp))
                    }
                }
                if(type=="county") Box {
                    OutlinedButton(onClick={countyOpen=true},shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                        Text("$county vármegye",Modifier.weight(1f));Icon(Icons.Outlined.ExpandMore,"Vármegye kiválasztása")
                    }
                    DropdownMenu(expanded=countyOpen,onDismissRequest={countyOpen=false},modifier=Modifier.heightIn(max=300.dp)) {Counties.forEach {name->DropdownMenuItem(text={Text(name)},onClick={county=name;countyOpen=false})}}
                }
                if(type=="local") {
                    OutlinedTextField(value=city,onValueChange={city=it},label={Text("Település")},placeholder={Text("Például Székesfehérvár")},singleLine=true,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth())
                    Text("A helyi bérlet elfogadását a szolgáltatónál ellenőrizd.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Érvényesség",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(AppLayout.CompactGap),verticalArrangement=Arrangement.spacedBy(AppLayout.CompactGap)) {
                    FilterChip(thirtyDays,{thirtyDays=true;endDay=starts.plusDays(29).toEpochDay()},label={Text("30 napos")},leadingIcon=if(thirtyDays) ({Icon(Icons.Outlined.Link,null,Modifier.size(18.dp))}) else null,shape=CircleShape,modifier=Modifier.heightIn(min=48.dp))
                    FilterChip(!thirtyDays,{thirtyDays=false},label={Text("Egyéni")},shape=CircleShape,modifier=Modifier.heightIn(min=48.dp))
                }
                PassDateButton("Kezdőnap",starts) {calendar="start"}
                PassDateButton("Lejárat napja",expires) {calendar="end"}
                Text(if(thirtyDays) "A két dátum együtt változik, a kezdőnappal együtt 30 napot számolunk." else "A bérleten feltüntetett dátumokat add meg. A lejárat napjának végéig számolunk.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(expires.isBefore(starts)) Text("A lejárat nem előzheti meg a kezdőnapot.",color=MaterialTheme.colorScheme.error)
            }
            MainButton("Bérlet mentése",Icons.Outlined.Check,valid) {
                save(Pass(pass?.id ?: UUID.randomUUID().toString(),if(type=="county") county else null,starts,expires,if(type=="local") city.trim() else null,thirtyDays))
            }
        }
    }
    if(calendar!=null) {
        val date=if(calendar=="start") starts else expires
        val state=rememberDatePickerState(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest={calendar=null},shape=RoundedCornerShape(32.dp),confirmButton={TextButton(onClick={
            val picked=Instant.ofEpochMilli(state.selectedDateMillis ?: return@TextButton).atZone(ZoneOffset.UTC).toLocalDate()
            val dates=PassDates(starts,expires).let {if(calendar=="start") it.changeStart(picked,thirtyDays) else it.changeEnd(picked,thirtyDays)}
            startDay=dates.starts.toEpochDay();endDay=dates.expires.toEpochDay();calendar=null
        }){Text("Kész")}},dismissButton={TextButton(onClick={calendar=null}){Text("Mégse")}}){DatePicker(state,showModeToggle=false)}
    }
}

@Composable private fun PassDateButton(label: String, date: LocalDate, onClick: () -> Unit) {
    OutlinedButton(onClick=onClick,shape=RoundedCornerShape(24.dp),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),modifier=Modifier.fillMaxWidth().heightIn(min=72.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(passDate(date),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
        }
        Icon(Icons.Outlined.CalendarToday,null,Modifier.size(22.dp))
    }
}
