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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private enum class SettingsPage(val title: String) {
    Home("Beállítások"), Appearance("Megjelenés"), Notifications("Értesítések"),
    Updates("Frissítések"), Extras("Kiegészítő funkciók"), About("Az alkalmazásról"), Developer("Fejlesztői beállítások");

    val parent: SettingsPage get() = when (this) {
        Developer -> About
        else -> Home
    }
}

@Composable fun SettingsScreen(vm: AppState) {
    var route by rememberSaveable { mutableStateOf(SettingsPage.Home.name) }
    val page = SettingsPage.entries.firstOrNull { it.name == route } ?: SettingsPage.Home
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var notificationsEnabled by remember { mutableStateOf(PassNotifications.systemEnabled(context)) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = PassNotifications.systemEnabled(context)
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
                            SettingsRow("Értesítések", if (!notificationsEnabled) "Kikapcsolva az Androidban" else if (vm.passesEnabled) "${vm.passReminders.size} / ${PassReminder.entries.size} emlékeztető bekapcsolva" else "Android értesítésbeállítások", onClick = { navigate(SettingsPage.Notifications) })
                            SettingsRow("Frissítések", if (vm.updates.enabled) "Automatikus ellenőrzés bekapcsolva" else "Kézi ellenőrzés", onClick = { navigate(SettingsPage.Updates) })
                            SettingsRow("Kiegészítő funkciók", vm.extraFeaturesSummary, onClick = { navigate(SettingsPage.Extras) })
                        }
                    }
                    item {
                        SettingsGroup {
                            SettingsRow("Az alkalmazásról", "MenetrendGO · ${BuildConfig.VERSION_NAME}", onClick = { navigate(SettingsPage.About) })
                        }
                    }
                }
                SettingsPage.Extras -> {
                    item {
                        SettingsGroup {
                            Row(
                                Modifier.fillMaxWidth().toggleable(vm.journeyTrackingBetaEnabled, role = Role.Switch, onValueChange = vm::chooseJourneyTrackingBeta).padding(AppLayout.PanelPadding),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Leszállási jelzés (béta)", style = MaterialTheme.typography.titleMedium)
                                    Text("Kísérleti leszállási figyelmeztetés. Bekapcsolva csengő jelenik meg a leszállóhelyek mellett. Kikapcsoláskor az aktív jelzés is leáll.",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = vm.journeyTrackingBetaEnabled, onCheckedChange = null)
                            }
                        }
                    }
                    item {
                        SettingsGroup {
                            Row(
                                Modifier.fillMaxWidth().toggleable(vm.passesEnabled, role = Role.Switch, onValueChange = vm::choosePassesEnabled).padding(AppLayout.PanelPadding),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppLayout.CompactGap)) {
                                    Text("Bérletek", style = MaterialTheme.typography.titleMedium)
                                    Text("Bérleteid érvényességének nyilvántartása és lejárati emlékeztetők. Bekapcsolva a Bérletek menüpont megjelenik a navigációban. Kikapcsolva a mentett bérleteid megmaradnak, az emlékeztetők szünetelnek.",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = vm.passesEnabled, onCheckedChange = null)
                            }
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
                                listOf("system", "light", "dark", "oled").forEach { value ->
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
                    item { SettingsNote("A Rendszer szerint beállítás az Android világos vagy sötét megjelenését követi. Az OLED sötét mód tiszta fekete hátteret használ.") }
                }
                SettingsPage.Notifications -> {
                    if (vm.passesEnabled) item {
                        SettingsGroup {
                            Column(Modifier.padding(AppLayout.PanelPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Bérlet-emlékeztetők", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Értesítés 9 órakor.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PassReminder.entries.forEach { reminder ->
                                HorizontalDivider(Modifier.padding(horizontal = AppLayout.PanelPadding), color = MaterialTheme.colorScheme.outlineVariant)
                                PassReminderSwitch(reminder, reminder in vm.passReminders) { vm.choosePassReminder(reminder, it) }
                            }
                        }
                    }
                    item {
                        SettingsGroup {
                            if (!notificationsEnabled) {
                                Column(Modifier.padding(start = AppLayout.PanelPadding, end = AppLayout.PanelPadding, top = AppLayout.PanelPadding), verticalArrangement = Arrangement.spacedBy(AppLayout.LabelGap)) {
                                    Text("Az értesítések ki vannak kapcsolva", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("A választásaidat megőrizzük.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            SettingsRow(if (notificationsEnabled) "Android értesítésbeállítások" else "Beállítások megnyitása", external = true, onClick = {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            })
                        }
                    }
                    item { SettingsNote("Az Android energiatakarékossága késleltetheti az értesítéseket.") }
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
                        RapidRefreshSetting(vm.rapidRefresh, vm::chooseRapidRefresh)
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
}

@Composable internal fun PassReminderSwitch(reminder: PassReminder, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = AppLayout.PanelPadding, vertical = AppLayout.RelatedGap).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)
    ) {
        Text(reminder.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun themeLabel(value: String) = when (value) {
    "light" -> "Világos"
    "dark" -> "Sötét"
    "oled" -> "OLED sötét"
    else -> "Rendszer szerint"
}

@Composable internal fun RapidRefreshSetting(enabled: Boolean, onChange: (Boolean) -> Unit) {
    SettingsGroup {
        Row(
            Modifier.fillMaxWidth().toggleable(enabled, role = Role.Switch, onValueChange = onChange)
                .padding(AppLayout.PanelPadding),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Rapid Refresh", style = MaterialTheme.typography.titleMedium)
                Text("Bekapcsolva minden élő járműadatot mutató térkép 15, az indulások és az utazás részletei 30 másodpercenként frissülnek. Kikapcsolva mindegyik 75 másodpercenként frissül.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

@Composable internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
        Column(content = content)
    }
}

@Composable internal fun SettingsRow(title: String, summary: String? = null, external: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp).heightIn(min = 48.dp),
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

