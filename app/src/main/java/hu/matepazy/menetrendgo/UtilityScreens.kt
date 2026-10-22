package hu.matepazy.menetrendgo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

private val Counties=listOf("Baranya","Bács-Kiskun","Békés","Borsod-Abaúj-Zemplén","Csongrád-Csanád","Fejér","Győr-Moson-Sopron","Hajdú-Bihar","Heves","Jász-Nagykun-Szolnok","Komárom-Esztergom","Nógrád","Pest","Somogy","Szabolcs-Szatmár-Bereg","Tolna","Vas","Veszprém","Zala")

@Composable fun PassesScreen(vm: AppState) {
    var editing by remember {mutableStateOf<Pass?>(null)}
    var add by remember {mutableStateOf(false)}
    var remove by remember {mutableStateOf<Pass?>(null)}
    val context=LocalContext.current
    var notificationsEnabled by remember {mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())}
    val lifecycleOwner=LocalLifecycleOwner.current
    var today by remember {mutableStateOf(LocalDate.now(Budapest))}
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) { today=LocalDate.now(Budapest);delay(60_000) }
        }
    }
    DisposableEffect(lifecycleOwner,context) {
        val observer=LifecycleEventObserver {_,event ->
            if(event==Lifecycle.Event.ON_RESUME) notificationsEnabled=NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {lifecycleOwner.lifecycle.removeObserver(observer)}
    }
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsEnabled=granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        if(granted) vm.passes.forEach {PassNotifications.schedule(context,it)}
    }
    fun requestNotifications() {
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) permissions.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,top=20.dp,end=20.dp,bottom=20.dp+LocalBottomOverlayInset.current),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {PageTitle("Bérletek","Lejárat és emlékeztetők, egy helyen")}
        item {MainButton("Bérlet hozzáadása",Icons.Outlined.Add){editing=null;add=true}}
        if(vm.passes.isEmpty()) item {InfoPanel("Mikor jár le a bérleted?","Add hozzá a bérlet típusát és a rajta szereplő dátumokat. Három és egy nappal a lejárat előtt, majd a lejárat után emlékeztetünk.")}
        items(vm.passes,key={it.id}) {pass ->
            Glass(Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.CreditCard,null,tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(14.dp));Text(pass.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(8.dp));Text("${pass.starts} – ${pass.expires}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                val days=ChronoUnit.DAYS.between(today,pass.expires)
                val label=when {today.isBefore(pass.starts)->"Még nem érvényes";days<0->"Lejárt";days==0L->"Ma jár le";else->"Még $days napig érvényes"}
                Spacer(Modifier.height(12.dp));Text(label,style=MaterialTheme.typography.titleMedium,color=if(days<1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Row {
                    TextButton(onClick={editing=pass;add=true}) {Text("Szerkesztés")}
                    TextButton(onClick={remove=pass}) {Text("Törlés")}
                }
            }
        }
        if(vm.passes.isNotEmpty()) item {
            if(!notificationsEnabled) {
                InfoPanel("Az értesítések ki vannak kapcsolva","A bérlet dátumát továbbra is követheted. Az emlékeztetőkhöz engedélyezd az értesítéseket az Android beállításaiban.")
                TextButton(onClick={context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))}){Text("Értesítések beállítása")}
            } else Text("Emlékeztetők: 3 és 1 nappal korábban, majd lejárat után, 9 órakor. Az Android energiatakarékossága késleltetheti őket.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {Text("Ez egy helyi bérletnyilvántartás, nem utazási igazolvány. A bérletet a hivatalos alkalmazásban vagy eredeti formájában mutasd be.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item {TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.ujtarifa.hu/orszagberlet")))}){Text("Hivatalos felhasználási feltételek");Icon(Icons.Outlined.OpenInNew,null,Modifier.padding(start=8.dp).size(16.dp))}}
    }
    if(add) PassEditor(editing,onDismiss={add=false}) {pass -> vm.addPass(pass);add=false;requestNotifications()}
    if(remove!=null) AlertDialog(onDismissRequest={remove=null},shape=RoundedCornerShape(32.dp),title={Text("Törlöd a bérletet?")},text={Text("A hozzá tartozó emlékeztetők is megszűnnek.")},confirmButton={TextButton(onClick={vm.removePass(remove!!);remove=null}){Text("Törlés")}},dismissButton={TextButton(onClick={remove=null}){Text("Mégse")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PassEditor(pass: Pass?, onDismiss: () -> Unit, save: (Pass) -> Unit) {
    var country by remember {mutableStateOf(pass?.county==null)}
    var county by remember {mutableStateOf(pass?.county ?: "Fejér")}
    var countyOpen by remember {mutableStateOf(false)}
    var starts by remember {mutableStateOf(pass?.starts ?: LocalDate.now(Budapest))}
    var expires by remember {mutableStateOf(pass?.expires ?: LocalDate.now(Budapest).plusMonths(1).minusDays(1))}
    var calendar by remember {mutableStateOf<String?>(null)}
    ModalBottomSheet(onDismissRequest=onDismiss,shape=RoundedCornerShape(topStart=36.dp,topEnd=36.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(if(pass==null) "Új bérlet" else "Bérlet szerkesztése",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(country,{country=true},label={Text("Országbérlet")},shape=CircleShape)
                FilterChip(!country,{country=false},label={Text("Vármegyebérlet")},shape=CircleShape)
            }
            if(!country) Box {
                OutlinedButton(onClick={countyOpen=true},shape=CircleShape,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(county);Icon(Icons.Outlined.ExpandMore,null)}
                DropdownMenu(expanded=countyOpen,onDismissRequest={countyOpen=false},modifier=Modifier.heightIn(max=300.dp)) {Counties.forEach {name->DropdownMenuItem(text={Text(name)},onClick={county=name;countyOpen=false})}}
            }
            OutlinedButton(onClick={calendar="start"},shape=CircleShape,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text("Érvényes ettől: $starts")}
            OutlinedButton(onClick={calendar="end"},shape=CircleShape,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text("Lejárat napja: $expires")}
            Text("A bérleten feltüntetett dátumokat add meg. A lejárat napjának végéig számolunk.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(expires.isBefore(starts)) Text("A lejárat nem előzheti meg a kezdőnapot.",color=MaterialTheme.colorScheme.error)
            MainButton("Bérlet mentése",Icons.Outlined.Check,!expires.isBefore(starts)) {save(Pass(pass?.id ?: UUID.randomUUID().toString(),if(country) null else county,starts,expires))}
        }
    }
    if(calendar!=null) {
        val date=if(calendar=="start") starts else expires
        val state=rememberDatePickerState(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest={calendar=null},shape=RoundedCornerShape(32.dp),confirmButton={TextButton(onClick={
            val picked=Instant.ofEpochMilli(state.selectedDateMillis ?: return@TextButton).atZone(ZoneOffset.UTC).toLocalDate()
            if(calendar=="start") starts=picked else expires=picked;calendar=null
        }){Text("Kész")}},dismissButton={TextButton(onClick={calendar=null}){Text("Mégse")}}){DatePicker(state,showModeToggle=false)}
    }
}

@Composable fun SettingsScreen(vm: AppState) {
    val context=LocalContext.current
    var developer by rememberSaveable {mutableStateOf(false)}
    BackHandler(enabled=developer) {developer=false}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,top=20.dp,end=20.dp,bottom=20.dp+LocalBottomOverlayInset.current),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item {PageTitle(if(developer) "Fejlesztői beállítások" else "Beállítások",back=if(developer) ({developer=false}) else null)}
        if(!developer) {
        item {Glass(Modifier.fillMaxWidth()) {
            Text("Megjelenés",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            listOf("system" to "Rendszer szerint","light" to "Világos","dark" to "Sötét").forEach {(value,label)->
                Row(Modifier.fillMaxWidth().clickable {vm.chooseTheme(value)}.padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically){RadioButton(vm.theme==value,{vm.chooseTheme(value)});Text(label)}
            }
        }}
        } else {
        item {Glass(Modifier.fillMaxWidth()) {
            Text("Adatforrás",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp));Text("Közvetlen MÁV-kapcsolat · OTP GraphQL. Az adatfeldolgozás és a gyorsítótár ezen az eszközön fut. Nincs külön MenetrendGO-kiszolgáló.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp));Text("mavplusz.hu · HTTPS\nJárműadatok frissítése: 75 másodpercenként, amíg a térképet vagy a járatot nézed.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp));Text("Verzió: ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.bodySmall)
        }}
        item {TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/matepazy/hovamegy.hu")))}){Text("Backend alapja: hovamegy.hu")}}
        }
        if(!developer) {
        item {Glass(Modifier.fillMaxWidth()) {
            Text("Adatok és értesítések",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp));Text("Bérleteid és keresési előzményeid ezen az eszközön maradnak. Nincs fiók vagy analitika. A keresett megállók és az időpont közvetlenül a MÁV-hoz kerülnek; a térképcsempéket az OpenStreetMap szolgáltatja.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={vm.clearRecents()}){Text("Keresési előzmények törlése")}
            TextButton(onClick={context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))}){Text("Android értesítésbeállítások")}
        }}
        item {Text("MenetrendGO · ${BuildConfig.VERSION_NAME}\nNem hivatalos alkalmazás. Az adatok hiányozhatnak vagy késhetnek.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item {TextButton(onClick={developer=true}){Text("Fejlesztői beállítások")}}
        }
    }
}
