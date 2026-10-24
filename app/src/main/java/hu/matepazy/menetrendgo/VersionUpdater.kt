package hu.matepazy.menetrendgo

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
)

data class GitHubRelease(
    val tag_name: String,
    val prerelease: Boolean,
    val name: String?,
    val body: String?,
    val assets: List<GitHubAsset>,
    val draft: Boolean = false,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpdateAvailable(
        val version: String,
        val notes: String,
        val downloadUrl: String,
        val isBeta: Boolean = version.substringBefore('+').contains('-'),
        val isVersionSwitch: Boolean = false,
    ) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Completed(val apkFile: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

object SemanticVersion {
    fun parse(version: String): Triple<Int, Int, Int> {
        val clean = version.trimStart('v', 'V').substringBefore('-').substringBefore('+')
        val parts = clean.split('.')
        return Triple(
            parts.getOrNull(0)?.toIntOrNull() ?: 0,
            parts.getOrNull(1)?.toIntOrNull() ?: 0,
            parts.getOrNull(2)?.toIntOrNull() ?: 0,
        )
    }

    fun isNewer(remote: String, local: String): Boolean = compare(remote, local) > 0

    fun compare(remote: String, local: String): Int {
        val (remoteMajor, remoteMinor, remotePatch) = parse(remote)
        val (localMajor, localMinor, localPatch) = parse(local)
        if (remoteMajor != localMajor) return remoteMajor.compareTo(localMajor)
        if (remoteMinor != localMinor) return remoteMinor.compareTo(localMinor)
        if (remotePatch != localPatch) return remotePatch.compareTo(localPatch)

        val remotePrerelease = prereleaseParts(remote)
        val localPrerelease = prereleaseParts(local)
        if (remotePrerelease == null) return if (localPrerelease == null) 0 else 1
        if (localPrerelease == null) return -1
        for (index in 0 until minOf(remotePrerelease.size, localPrerelease.size)) {
            val remotePart = remotePrerelease[index]
            val localPart = localPrerelease[index]
            val remoteNumber = remotePart.toBigIntegerOrNull()
            val localNumber = localPart.toBigIntegerOrNull()
            val comparison = when {
                remoteNumber != null && localNumber != null -> remoteNumber.compareTo(localNumber)
                remoteNumber != null -> -1
                localNumber != null -> 1
                else -> remotePart.compareTo(localPart)
            }
            if (comparison != 0) return comparison
        }
        return remotePrerelease.size.compareTo(localPrerelease.size)
    }

    private fun prereleaseParts(version: String): List<String>? {
        val withoutMetadata = version.substringBefore('+')
        if ('-' !in withoutMetadata) return null
        // Accept MenetrendGo's beta1 spelling as well as the semver beta.1 spelling.
        val suffix = withoutMetadata.substringAfter('-')
            .replace(Regex("^beta(\\d+)$"), "beta.$1")
        return suffix.split('.')
    }
}

object VersionUpdater {
    private const val ReleasesUrl = "https://api.github.com/repos/matepazy/menetrend-go/releases"
    private val menetrendgoApkName = Regex(
        "^menetrendgo-v\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?\\.apk$",
        RegexOption.IGNORE_CASE,
    )

    private val client = OkHttpClient()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val releasesAdapter = moshi.adapter<List<GitHubRelease>>(
        Types.newParameterizedType(List::class.java, GitHubRelease::class.java),
    )

    fun checkForUpdates(
        currentVersion: String,
        includePrereleases: Boolean,
        onResult: (UpdateState) -> Unit,
    ) {
        fetchReleases { result ->
            onResult(result.fold(
                onSuccess = { updateStateForReleases(it, currentVersion, includePrereleases) },
                onFailure = { UpdateState.Error(it.message ?: "Nem sikerült betölteni a kiadásokat.") },
            ))
        }
    }

    fun fetchReleases(onResult: (Result<List<GitHubRelease>>) -> Unit) {
        fetchReleasePage(onResult, page = 1, releases = emptyList())
    }

    private fun fetchReleasePage(
        onResult: (Result<List<GitHubRelease>>) -> Unit,
        page: Int,
        releases: List<GitHubRelease>,
    ) {
        val request = Request.Builder()
            .url("$ReleasesUrl?per_page=100&page=$page")
            .header("Accept", "application/vnd.github.v3+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(Result.failure(IOException("Nincs kapcsolat a GitHubbal. Ellenőrizd az internetkapcsolatot, majd próbáld újra.")))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onResult(Result.failure(IOException(when (it.code) {
                            404 -> "A GitHub-kiadások még nem érhetők el. Próbáld újra később."
                            403, 429 -> "A GitHub átmenetileg korlátozza a kéréseket. Próbáld újra később."
                            else -> "Nem sikerült betölteni a kiadásokat (HTTP ${it.code}). Próbáld újra."
                        })))
                        return
                    }

                    try {
                        val allReleases = releases +
                            releasesAdapter.fromJson(it.body?.string().orEmpty()).orEmpty()
                        if (it.header("Link").orEmpty().contains("rel=\"next\"")) {
                            fetchReleasePage(onResult, page + 1, allReleases)
                        } else {
                            onResult(Result.success(allReleases))
                        }
                    } catch (error: Exception) {
                        onResult(Result.failure(IOException("Nem sikerült feldolgozni a kiadásokat. Próbáld újra később.", error)))
                    }
                }
            }
        })
    }

    internal fun updateStateForReleases(
        releases: List<GitHubRelease>,
        currentVersion: String,
        includePrereleases: Boolean,
    ): UpdateState {
        val targetRelease = releases
            .filter { !it.draft && (includePrereleases || !it.prerelease) }
            .maxWithOrNull { first, second ->
                val comparison = SemanticVersion.compare(first.tag_name, second.tag_name)
                if (comparison != 0) comparison else second.prerelease.compareTo(first.prerelease)
            } ?: return UpdateState.Idle
        if (!SemanticVersion.isNewer(targetRelease.tag_name, currentVersion)) return UpdateState.Idle

        return releaseUpdate(targetRelease)
            ?: UpdateState.Error("Van új kiadás, de még nincs hozzá APK-fájl. Próbáld újra később.")
    }

    internal fun releaseUpdate(release: GitHubRelease): UpdateState.UpdateAvailable? {
        val apk = release.assets.firstOrNull { isMenetrendGoApkAssetName(it.name) }
            ?: release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return null
        return UpdateState.UpdateAvailable(
            version = release.tag_name,
            notes = release.body?.takeIf { it.isNotBlank() } ?: "Ehhez a kiadáshoz nem tartozik leírás.",
            downloadUrl = apk.browser_download_url,
            isBeta = release.prerelease || release.tag_name.substringBefore('+').contains('-'),
        )
    }

    internal fun managedVersions(
        releases: List<GitHubRelease>,
        currentVersion: String,
    ): List<UpdateState.UpdateAvailable> {
        val installedStable = !currentVersion.substringBefore('+').contains('-')
        // Stable releases advance the version code beyond their betas and older releases.
        val published = releases.filter {
            !it.draft && (!installedStable || SemanticVersion.compare(it.tag_name, currentVersion) >= 0)
        }
        val stable = published.filter { !it.prerelease && !it.tag_name.substringBefore('+').contains('-') }
            .maxWithOrNull { a, b -> SemanticVersion.compare(a.tag_name, b.tag_name) }
        return (listOfNotNull(stable) + published.filter {
            it.prerelease || it.tag_name.substringBefore('+').contains('-')
        }.sortedWith { a, b -> SemanticVersion.compare(b.tag_name, a.tag_name) })
            .mapNotNull(::releaseUpdate).distinctBy { it.version }
            .map { it.copy(isVersionSwitch = true) }
    }

    internal fun isMenetrendGoApkAssetName(name: String): Boolean = menetrendgoApkName.matches(name)

    fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit,
        onCompleted: (File) -> Unit,
        onError: (String) -> Unit,
        fileName: String = "menetrendgo_update.apk",
    ) {
        if (!downloadUrl.startsWith("https://") || !fileName.matches(Regex("[A-Za-z0-9.+_-]+\\.apk"))) {
            onError("Érvénytelen letöltési hivatkozás. Keress újra frissítést.")
            return
        }
        client.newCall(Request.Builder().url(downloadUrl).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("A letöltés megszakadt. Ellenőrizd az internetkapcsolatot, majd próbáld újra.")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onError("Nem sikerült letölteni az APK-t (HTTP ${it.code}). Próbáld újra.")
                        return
                    }
                    val body = it.body
                    if (body == null) {
                        onError("A letöltött fájl üres. Próbáld újra később.")
                        return
                    }

                    val directory = File(context.cacheDir, "updates").apply { mkdirs() }
                    val apkFile = File(directory, fileName)
                    try {
                        directory.listFiles()?.filter { old -> old != apkFile }?.forEach { old -> old.delete() }
                        if (apkFile.exists()) apkFile.delete()
                        val totalBytes = body.contentLength()
                        var bytesRead = 0L
                        val buffer = ByteArray(8_192)
                        body.byteStream().use { input ->
                            FileOutputStream(apkFile).use { output ->
                                var read = input.read(buffer)
                                while (read != -1) {
                                    output.write(buffer, 0, read)
                                    bytesRead += read
                                    if (totalBytes > 0) onProgress(bytesRead.toFloat() / totalBytes)
                                    read = input.read(buffer)
                                }
                            }
                        }
                        if (bytesRead == 0L || (totalBytes >= 0 && bytesRead != totalBytes)) throw IOException("Incomplete APK")
                        onCompleted(apkFile)
                    } catch (error: Exception) {
                        apkFile.delete()
                        onError("Nem sikerült elmenteni az APK-t. Ellenőrizd a szabad tárhelyet, majd próbáld újra.")
                    }
                }
            }
        })
    }
}

object ApkInstaller {
    fun canInstallPackages(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    fun installApk(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
