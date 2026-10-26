package hu.matepazy.menetrendgo

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt

private enum class SettingsPage(val title: String) {
    Home("Beállítások"), Appearance("Megjelenés"), Notifications("Értesítések"),
    Data("Adatok és előzmények"), Updates("Frissítések"), About("Az alkalmazásról"), Developer("Fejlesztői beállítások");

    val parent: SettingsPage get() = when (this) {
        Developer -> About
        else -> Home
    }
}

@Composable fun SettingsScreen(vm: AppState) {
    var route by rememberSaveable { mutableStateOf(SettingsPage.Home.name) }
    val page = SettingsPage.valueOf(route)
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    PredictiveBackPane(
        enabled = page != SettingsPage.Home,
        onBack = { route = page.parent.name },
        preview = { SettingsContent(vm, page.parent, notificationsEnabled) {} }
    ) {
        SettingsContent(vm, page, notificationsEnabled) { route = it.name }
    }
}

@Composable private fun SettingsContent(
    vm: AppState,
    page: SettingsPage,
    notificationsEnabled: Boolean,
    navigate: (SettingsPage) -> Unit
) {
    val context = LocalContext.current
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var developerUnlockTaps by rememberSaveable { mutableIntStateOf(0) }
    // Each destination owns its scroll position, including the parent in a Back preview.
    key(page) {
        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier.fillMaxSize(), state = listState,
            contentPadding = screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)
        ) {
            item { PageTitle(page.title, back = if (page == SettingsPage.Home) null else ({ navigate(page.parent) })) }
            when (page) {
                SettingsPage.Home -> {
                    item {
                        SettingsGroup {
                            SettingsRow("Megjelenés", themeLabel(vm.theme), onClick = { navigate(SettingsPage.Appearance) })
                            SettingsRow("Értesítések", if (notificationsEnabled) "Engedélyezve az Androidban" else "Kikapcsolva az Androidban", onClick = { navigate(SettingsPage.Notifications) })
                            SettingsRow("Adatok és előzmények", if (vm.recents.isEmpty()) "Nincs mentett keresés" else "${vm.recents.size} mentett keresés", onClick = { navigate(SettingsPage.Data) })
                            SettingsRow("Frissítések", if (vm.updates.enabled) "Automatikus ellenőrzés bekapcsolva" else "Kézi ellenőrzés", onClick = { navigate(SettingsPage.Updates) })
                        }
                    }
                    item {
                        SettingsGroup {
                            SettingsRow("Az alkalmazásról", "MenetrendGO · ${BuildConfig.VERSION_NAME}", onClick = { navigate(SettingsPage.About) })
                        }
                    }
                }
                SettingsPage.Updates -> {
                    item { Column(verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) { UpdateSettings(vm.updates) } }
                }
                SettingsPage.Appearance -> {
                    item {
                        SettingsGroup {
                            Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
                                listOf("system", "light", "dark").forEach { value ->
                                    Row(
                                        Modifier.fillMaxWidth().selectable(vm.theme == value, role = Role.RadioButton, onClick = { vm.chooseTheme(value) })
                                            .padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 48.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text(themeLabel(value), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                        RadioButton(selected = vm.theme == value, onClick = null)
                                    }
                                }
                            }
                        }
                    }
                    item { SettingsNote("A Rendszer szerint beállítás az Android világos vagy sötét megjelenését követi.") }
                }
                SettingsPage.Notifications -> {
                    item {
                        SettingsGroup {
                            Column(Modifier.padding(AppLayout.PanelPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Bérlet-emlékeztetők", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(if (notificationsEnabled) "Az értesítések engedélyezve vannak." else "Az értesítések ki vannak kapcsolva.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("3 és 1 nappal a lejárat előtt, majd a lejárat után, 9 órakor.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SettingsRow("Android értesítésbeállítások", external = true, onClick = {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            })
                        }
                    }
                    item { SettingsNote("A bérleteket és a lejárati dátumokat a Bérletek oldalon kezelheted. Az Android energiatakarékossága késleltetheti az emlékeztetőket.") }
                }
                SettingsPage.Data -> {
                    item {
                        Glass(Modifier.fillMaxWidth()) {
                            Text("Ezen az eszközön", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text("Bérleteid és keresési előzményeid ezen az eszközön maradnak. Nincs fiók vagy analitika.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        SettingsGroup {
                            Column(Modifier.padding(AppLayout.PanelPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Keresési előzmények", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(if (vm.recents.isEmpty()) "Nincs mentett keresés." else "${vm.recents.size} korábbi útvonal a Tervezés oldalon.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = { confirmClear = true }, enabled = vm.recents.isNotEmpty()) { Text("Előzmények törlése") }
                            }
                        }
                    }
                    item { SettingsNote("A keresett megállók és az időpont közvetlenül a MÁV-hoz kerülnek. A térképcsempéket a kiválasztott térképszolgáltató tölti be (OpenStreetMap, Esri, OpenRailwayMap, Google vagy saját URL).") }
                }
                SettingsPage.About -> {
                    item {
                        Glass(Modifier.fillMaxWidth()) {
                            Text("MenetrendGO", modifier = Modifier.heightIn(min = 48.dp).clickable(
                                enabled = !vm.developerSettingsUnlocked,
                                onClick = {
                                    developerUnlockTaps++
                                    if (developerUnlockTaps >= 5) {
                                        vm.unlockDeveloperSettings()
                                        Toast.makeText(context, "Fejlesztői beállítások engedélyezve", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("${BuildConfig.VERSION_NAME} verzió", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Text("Magyarországi közösségi közlekedés, könnyebben átláthatóan. Tervezz utazást, nézd meg a járatok megállóit és kövesd a járműveket a térképen.", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(12.dp))
                            Text("Bérleteid érvényességét is nyilvántarthatod, és emlékeztetőt kérhetsz a lejáratukról.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item {
                        Glass(Modifier.fillMaxWidth()) {
                            Text("Adatok és függetlenség", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text("A menetrendi és járműadatok a MÁVPlusz rendszeréből származnak. A friss adatokhoz internetkapcsolat szükséges.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Text("A MenetrendGO független, nem hivatalos alkalmazás, nem áll kapcsolatban a MÁV-val. Az adatok hiányozhatnak vagy késhetnek.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (vm.developerSettingsUnlocked) {
                        item {
                            SettingsGroup {
                                SettingsRow("Fejlesztői beállítások", onClick = { navigate(SettingsPage.Developer) })
                            }
                        }
                    }
                }
                SettingsPage.Developer -> {
                    item {
                        SettingsGroup {
                            Row(
                                Modifier.fillMaxWidth().toggleable(vm.unofficialGoogleMapEnabled, role = Role.Switch, onValueChange = vm::chooseUnofficialGoogleMapEnabled).padding(AppLayout.PanelPadding),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Nem hivatalos Google térkép használata", style = MaterialTheme.typography.titleMedium)
                                    Text("Az Alap, Műhold és Hibrid térkép csempéit közvetlenül a Google-tól tölti be. Kikapcsolva az eredeti térképszolgáltatókat használja.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = vm.unofficialGoogleMapEnabled, onCheckedChange = null)
                            }
                        }
                    }
                    item {
                        MapRefreshSetting(vm.mapRefreshSeconds, vm::chooseMapRefreshSeconds)
                    }
                    item {
                        SettingsGroup {
                            Row(
                                Modifier.fillMaxWidth().toggleable(vm.showRawGraphQl, role = Role.Switch, onValueChange = vm::chooseRawGraphQl).padding(AppLayout.PanelPadding),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Nyers GraphQL adatok", style = MaterialTheme.typography.titleMedium)
                                    Text("Az utazás részleteinél megjelenik a Nyers adatok menüpont.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = vm.showRawGraphQl, onCheckedChange = null)
                            }
                        }
                    }
                    item {
                        SettingsGroup {
                            Row(
                                Modifier.fillMaxWidth().toggleable(vm.customMapEnabled, role = Role.Switch, onValueChange = vm::chooseCustomMapEnabled).padding(AppLayout.PanelPadding),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Saját térkép URL", style = MaterialTheme.typography.titleMedium)
                                    Text("Egyéni csempeforrás megadása a Térképtípus fiókban. Kikapcsolva az Alap térképre vált, ha saját forrást használsz.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = vm.customMapEnabled, onCheckedChange = null)
                            }
                        }
                    }
                    item { SettingsNote("OTP GraphQL · HTTPS. Az adatfeldolgozás és a gyorsítótár ezen az eszközön fut. Nincs külön MenetrendGO-kiszolgáló.") }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false }, shape = RoundedCornerShape(32.dp),
            title = { Text("Törlöd a keresési előzményeket?") },
            text = { Text("A mentett útvonalak eltűnnek a Tervezés oldalról. A bérleteid megmaradnak.") },
            confirmButton = { TextButton(onClick = { vm.clearRecents(); confirmClear = false }) { Text("Törlés") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Mégse") } }
        )
    }
}

private fun themeLabel(value: String) = when (value) {
    "light" -> "Világos"
    "dark" -> "Sötét"
    else -> "Rendszer szerint"
}

@Composable private fun MapRefreshSetting(seconds: Int, onChange: (Int) -> Unit) {
    var value by remember(seconds) { mutableFloatStateOf(seconds.toFloat()) }
    val selectedSeconds = value.roundToInt()
    SettingsGroup {
        Column(Modifier.padding(AppLayout.PanelPadding), verticalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Frissítési gyakoriság", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("$selectedSeconds s", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text("Az élő térkép járműadatainak automatikus frissítése.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = { onChange(selectedSeconds) },
                valueRange = 15f..90f,
                steps = 74,
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "Frissítési gyakoriság"
                    stateDescription = "$selectedSeconds másodperc"
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("15 s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("90 s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selectedSeconds < 45) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                    Icon(Icons.Outlined.WarningAmber, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                    Text("A 45 másodpercnél gyakoribb frissítés növelheti az adatforgalmat és az akkumulátor fogyasztását.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
        Column(content = content)
    }
}

@Composable private fun SettingsRow(title: String, summary: String? = null, external: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(if (external) Icons.Outlined.OpenInNew else Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun SettingsNote(text: String) {
    Text(text, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

