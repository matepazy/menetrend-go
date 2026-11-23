package hu.matepazy.menetrendgo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Third-party software and data shipped in the APK, with the notices their licenses require.
 * [asset] is the bundled license text; [note] covers components whose terms are only linked.
 * Keep in sync with the release runtime classpath (`gradlew :app:dependencies --configuration releaseRuntimeClasspath`).
 */
internal class OpenSourceNotice(val name: String, val author: String, val license: String, val url: String, val asset: String? = null, val note: String? = null)

private const val Apache = "licenses/apache-2.0.txt"

internal val openSourceNotices = listOf(
    OpenSourceNotice("AndroidX és Jetpack Compose", "The Android Open Source Project", "Apache 2.0", "https://developer.android.com/jetpack/androidx", Apache),
    OpenSourceNotice("Kotlin és kotlinx.coroutines", "JetBrains s.r.o.", "Apache 2.0", "https://kotlinlang.org", Apache),
    OpenSourceNotice("OkHttp és Okio", "Square, Inc.", "Apache 2.0", "https://square.github.io/okhttp/", Apache),
    OpenSourceNotice("Moshi", "Square, Inc.", "Apache 2.0", "https://github.com/square/moshi", Apache),
    OpenSourceNotice("Markwon", "Dimitry Ivanov", "Apache 2.0", "https://github.com/noties/Markwon", Apache),
    OpenSourceNotice("commonmark-java", "Atlassian Pty Ltd", "BSD 2-Clause", "https://github.com/commonmark/commonmark-java", "licenses/commonmark-LICENSE.txt"),
    OpenSourceNotice("Guava ListenableFuture", "The Guava Authors", "Apache 2.0", "https://github.com/google/guava", Apache),
    OpenSourceNotice("JSpecify", "The JSpecify Authors", "Apache 2.0", "https://jspecify.dev", Apache),
    OpenSourceNotice("Leaflet", "Volodymyr Agafonkin, CloudMade", "BSD 2-Clause", "https://leafletjs.com", "leaflet-LICENSE.txt"),
    OpenSourceNotice("Barlow betűkészlet", "The Barlow Project Authors", "SIL OFL 1.1", "https://github.com/jpt/barlow", "transit-pictograms-LICENSE.txt"),
    OpenSourceNotice("Public Suffix List", "Mozilla Foundation", "MPL 2.0", "https://publicsuffix.org",
        note = "Az OkHttp a domainek ellenőrzéséhez a Public Suffix List egy tömörített példányát tartalmazza. A lista forrása a https://publicsuffix.org/list/public_suffix_list.dat címen érhető el, és a Mozilla Public License 2.0 (https://mozilla.org/MPL/2.0/) feltételei vonatkoznak rá."),
    OpenSourceNotice("geoBoundaries megyehatárok", "William & Mary geoLab", "CC0 1.0", "https://www.geoboundaries.org", "hungary-counties-LICENSE.md"),
    OpenSourceNotice("Google Maps SDK for Android", "Google LLC", "Google Maps Platform feltételek", "https://cloud.google.com/maps-platform/terms",
        note = "Az alaptérképet a Google Maps SDK for Android jeleníti meg. Használatára a Google Maps Platform szolgáltatási feltételei és a Google adatvédelmi irányelvei vonatkoznak."),
)

/** One component's notice: who made it, under which license, and the full text when it is bundled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun OpenSourceNoticeDrawer(notice: OpenSourceNotice, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dark = LocalAppDarkTheme.current
    val toolbar = MaterialTheme.colorScheme.surface
    AppBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("oss-drawer"), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().heightIn(max = LocalWindowInfo.current.containerDpSize.height * .85f)
            .padding(horizontal = AppLayout.SheetPadding).padding(bottom = AppLayout.SheetPadding), verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppLayout.ItemGap)) {
                PageTitle(notice.name, "${notice.license} · ${notice.author}")
                notice.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedButton({ openInAppBrowser(context, notice.url, dark, toolbar) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(AppLayout.LabelGap))
                    Text("Projekt megnyitása")
                }
                notice.asset?.let { LegalAssetText(it) }
            }
            FilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Bezárás") }
        }
    }
}
