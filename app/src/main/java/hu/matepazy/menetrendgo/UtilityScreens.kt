package hu.matepazy.menetrendgo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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

internal val Counties=listOf("Baranya","Bács-Kiskun","Békés","Borsod-Abaúj-Zemplén","Csongrád-Csanád","Fejér","Győr-Moson-Sopron","Hajdú-Bihar","Heves","Jász-Nagykun-Szolnok","Komárom-Esztergom","Nógrád","Pest","Somogy","Szabolcs-Szatmár-Bereg","Tolna","Vas","Veszprém","Zala")

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
        if(vm.passes.isEmpty()) item {InfoPanel("Mikor jár le a bérleted?","Ország-, vármegye- vagy helyi bérletet is menthetsz. Add meg a rajta szereplő dátumokat. Három és egy nappal a lejárat előtt, majd a lejárat után emlékeztetünk.")}
        items(vm.passes.sortedWith(compareBy<Pass> {it.expires.isBefore(today)}.thenBy {it.expires}),key={it.id}) {pass ->
            Glass(Modifier.fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(if(pass.localCity!=null) Icons.Outlined.LocationCity else Icons.Outlined.CreditCard,null,tint=MaterialTheme.colorScheme.primary)
                    Text(pass.name,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    IconButton(onClick={editing=pass;add=true}) {Icon(Icons.Outlined.Edit,"${pass.name} szerkesztése")}
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement=Arrangement.spacedBy(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    listOf("Kezdőnap" to pass.starts,"Lejárat" to pass.expires).forEach {(label,date)->
                        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(passDate(date),style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Medium)
                        }
                    }
                }
                val days=ChronoUnit.DAYS.between(today,pass.expires)
                val label=when {today.isBefore(pass.starts)->"Még nem érvényes";days<0->"Lejárt";days==0L->"Ma jár le";else->"Még $days napig érvényes"}
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(label,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,color=if(days<1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    IconButton(onClick={remove=pass}) {Icon(Icons.Outlined.DeleteOutline,"${pass.name} törlése",tint=MaterialTheme.colorScheme.onSurfaceVariant)}
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
        item {TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.ujtarifa.hu/orszagberlet")))}){Text("Ország- és vármegyebérlet feltételei");Icon(Icons.Outlined.OpenInNew,null,Modifier.padding(start=8.dp).size(16.dp))}}
    }
    if(add) PassEditor(editing,onDismiss={add=false}) {pass -> vm.addPass(pass);add=false;requestNotifications()}
    if(remove!=null) AlertDialog(onDismissRequest={remove=null},shape=RoundedCornerShape(32.dp),title={Text("Törlöd a bérletet?")},text={Text("A hozzá tartozó emlékeztetők is megszűnnek.")},confirmButton={TextButton(onClick={vm.removePass(remove!!);remove=null}){Text("Törlés")}},dismissButton={TextButton(onClick={remove=null}){Text("Mégse")}})
}

@Composable fun SettingsScreen(vm: AppState) {
    var developer by rememberSaveable {mutableStateOf(false)}
    PredictiveBackPane(enabled=developer,onBack={developer=false},preview={SettingsContent(vm,false) {}}) {
        SettingsContent(vm,developer) {developer=it}
    }
}

@Composable private fun SettingsContent(vm: AppState, developer: Boolean, onDeveloper: (Boolean) -> Unit) {
    val context=LocalContext.current
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,top=20.dp,end=20.dp,bottom=20.dp+LocalBottomOverlayInset.current),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item {PageTitle(if(developer) "Fejlesztői beállítások" else "Beállítások",back=if(developer) ({onDeveloper(false)}) else null)}
        if(!developer) {
        item {Glass(Modifier.fillMaxWidth()) {
            Text("Megjelenés",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.selectableGroup()) {
                listOf("system" to "Rendszer szerint","light" to "Világos","dark" to "Sötét").forEach {(value,label)->
                    Row(Modifier.fillMaxWidth().heightIn(min=56.dp)
                        .selectable(selected=vm.theme==value,role=Role.RadioButton,onClick={vm.chooseTheme(value)}),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected=vm.theme==value,onClick=null)
                        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }}
        } else {
        item {Glass(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp)
                .toggleable(value=vm.showRawGraphQl,role=Role.Switch,onValueChange=vm::chooseRawGraphQl),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("Nyers GraphQL adatok mutatása",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                Switch(checked=vm.showRawGraphQl,onCheckedChange=null)
            }
            Spacer(Modifier.height(8.dp))
            Text("Az utazás részleteinél megjelenik a Nyers adatok menüpont.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
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
        item {TextButton(onClick={onDeveloper(true)}){Text("Fejlesztői beállítások")}}
        }
    }
}
