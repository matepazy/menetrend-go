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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PassesScreen(vm: AppState) {
    var editingId by rememberSaveable {mutableStateOf<String?>(null)}
    val editing=vm.passes.firstOrNull {it.id==editingId}
    var add by rememberSaveable {mutableStateOf(false)}
    var remove by remember {mutableStateOf<Pass?>(null)}
    var showExpired by rememberSaveable {mutableStateOf(false)}
    var details by rememberSaveable {mutableStateOf<String?>(null)}
    val context=LocalContext.current
    var notificationsEnabled by remember {mutableStateOf(PassNotifications.systemEnabled(context))}
    val lifecycleOwner=LocalLifecycleOwner.current
    var today by remember {mutableStateOf(LocalDate.now(Budapest))}
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) { today=LocalDate.now(Budapest);delay(60_000) }
        }
    }
    DisposableEffect(lifecycleOwner,context) {
        val observer=LifecycleEventObserver {_,event ->
            if(event==Lifecycle.Event.ON_RESUME) notificationsEnabled=PassNotifications.systemEnabled(context)
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
    val current = vm.passes.filterNot {it.expires.isBefore(today)}.sortedBy {it.expires}
    val expired = vm.passes.filter {it.expires.isBefore(today)}.sortedBy {it.expires}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=screenContentPadding(),verticalArrangement=Arrangement.spacedBy(AppLayout.ItemGap)) {
        item {
            PageTitle("Bérletek", actions={
                if(vm.passes.isNotEmpty()) TextButton(onClick={editingId=null;add=true},modifier=Modifier.heightIn(min=48.dp)) {
                    Icon(Icons.Outlined.Add,null,Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp)); Text("Hozzáadás")
                }
            })
        }
        if(vm.passes.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("Mikor jár le a bérleted?",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                Text("Mentsd el az ország-, megye- vagy helyi bérleted dátumait, és kövesd itt a lejáratát.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                MainButton("Bérlet hozzáadása",Icons.Outlined.Add) {editingId=null;add=true}
            }
        }
        items(current,key={it.id}) {pass ->
            PassSummaryCard(pass,today,{editingId=pass.id;add=true},{remove=pass})
        }
        if(current.isEmpty() && expired.isNotEmpty()) item {
            Text("Nincs érvényes vagy közelgő bérleted.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(expired.isNotEmpty()) {
            item {
                TextButton(onClick={showExpired=!showExpired},modifier=Modifier.fillMaxWidth()
                    .semantics {stateDescription=if(showExpired) "Kibontva" else "Összecsukva"}) {
                    Text("Lejárt bérletek (${expired.size})",Modifier.weight(1f))
                    Icon(if(showExpired) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null)
                }
            }
            if(showExpired) items(expired,key={it.id}) {pass ->
                PassSummaryCard(pass,today,{editingId=pass.id;add=true},{remove=pass})
            }
        }
        if(vm.passes.isNotEmpty()) item {
            Surface(onClick={details="reminders"},shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.NotificationsNone,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("Emlékeztetők",style=MaterialTheme.typography.titleMedium)
                        Text(if(!notificationsEnabled) "Kikapcsolva az Androidban" else if(vm.passReminders.isEmpty()) "Kikapcsolva" else "${vm.passReminders.size} emlékeztető bekapcsolva",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            TextButton(onClick={details="info"},modifier=Modifier.heightIn(min=48.dp)) {
                Icon(Icons.Outlined.Info,null,Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text("Nem utazási igazolvány",style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    if(details!=null) AppBottomSheet(onDismissRequest={details=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=AppLayout.SheetPadding)
            .padding(bottom=AppLayout.SheetPadding),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            PageTitle(if(details=="reminders") "Emlékeztetők" else "A bérletekről",actions={
                IconButton(onClick={details=null}) {Icon(Icons.Outlined.Close,"Bezárás")}
            })
            if(details=="reminders") {
                Text("Az emlékeztetők a megadott napokon, 9 órakor érkeznek. Az Android energiatakarékossága késleltetheti őket.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                PassReminder.entries.forEach {reminder ->
                    PassReminderSwitch(reminder,reminder in vm.passReminders) {vm.choosePassReminder(reminder,it)}
                }
                if(!notificationsEnabled) Text("Az értesítések ki vannak kapcsolva az Androidban. Az emlékeztetőkhöz engedélyezd őket.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))}) {Text("Értesítések beállítása")}
            } else {
                Text("Ez egy helyi bérletnyilvántartás, nem utazási igazolvány. A bérletet a hivatalos alkalmazásban vagy eredeti formájában mutasd be.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.ujtarifa.hu/orszagberlet")))}) {
                    Text("Ország- és megyebérlet feltételei",Modifier.weight(1f))
                    Icon(Icons.Outlined.OpenInNew,null,Modifier.padding(start=8.dp).size(18.dp))
                }
            }
        }
    }
    if(add) PassEditor(editing,onDismiss={add=false}) {pass -> vm.addPass(pass);add=false;requestNotifications()}
    if(remove!=null) AlertDialog(onDismissRequest={remove=null},shape=RoundedCornerShape(32.dp),title={Text("Törlöd a bérletet?")},text={Text("A hozzá tartozó emlékeztetők is megszűnnek.")},confirmButton={TextButton(onClick={vm.removePass(remove!!);remove=null}){Text("Törlés")}},dismissButton={TextButton(onClick={remove=null}){Text("Mégse")}})
}

@Composable internal fun PassSummaryCard(pass: Pass, today: LocalDate, edit: () -> Unit, remove: () -> Unit) {
    var menuOpen by remember {mutableStateOf(false)}
    val days=ChronoUnit.DAYS.between(today,pass.expires)
    val upcoming=today.isBefore(pass.starts)
    val status=when {
        days<0 -> "Lejárt"
        upcoming -> "Érvényes ettől: ${passDate(pass.starts)}"
        days==0L -> "Ma jár le"
        days==1L -> "Holnap jár le"
        else -> "Még $days napig érvényes"
    }
    Glass(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top=4.dp)) {PassIcon(pass)}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(pass.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Text(status,style=MaterialTheme.typography.bodyLarge,fontWeight=FontWeight.Medium,
                    color=if(!upcoming && days in 0L..3L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Text("Lejárat: ${passDate(pass.expires)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick={menuOpen=true}) {Icon(Icons.Outlined.MoreVert,"${pass.name} műveletei")}
                DropdownMenu(expanded=menuOpen,onDismissRequest={menuOpen=false}) {
                    DropdownMenuItem(text={Text("Szerkesztés")},leadingIcon={Icon(Icons.Outlined.Edit,null)},onClick={menuOpen=false;edit()})
                    DropdownMenuItem(text={Text("Törlés")},leadingIcon={Icon(Icons.Outlined.DeleteOutline,null)},onClick={menuOpen=false;remove()})
                }
            }
        }
    }
}
