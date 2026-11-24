package hu.matepazy.menetrendgo

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** A feed compacted onto this device. [lastServiceDay] is the last day with any trip. */
data class InstalledOfflineFeed(val id: String, val name: String, val group: OfflineFeedGroup, val license: String?,
                                val bytes: Long, val builtAt: Long, val sourceUpdated: Long, val sourceBytes: Long,
                                val firstDay: LocalDate, val lastServiceDay: LocalDate, val trips: Int, val stops: Int) {
    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("group", group.name).put("license", license ?: "")
        .put("bytes", bytes).put("builtAt", builtAt).put("sourceUpdated", sourceUpdated).put("sourceBytes", sourceBytes)
        .put("firstDay", firstDay.toString()).put("lastServiceDay", lastServiceDay.toString()).put("trips", trips).put("stops", stops)
    companion object {
        fun parse(o: JSONObject) = InstalledOfflineFeed(o.getString("id"), o.getString("name"),
            runCatching { OfflineFeedGroup.valueOf(o.getString("group")) }.getOrDefault(OfflineFeedGroup.City),
            o.optString("license").ifBlank { null }, o.getLong("bytes"), o.getLong("builtAt"), o.optLong("sourceUpdated"),
            o.optLong("sourceBytes"), LocalDate.parse(o.getString("firstDay")), LocalDate.parse(o.getString("lastServiceDay")),
            o.optInt("trips"), o.optInt("stops"))
    }
}

sealed interface OfflineTask {
    object Queued : OfflineTask
    data class Downloading(val read: Long, val total: Long) : OfflineTask
    data class Processing(val fraction: Float) : OfflineTask
    data class Failed(val message: String) : OfflineTask
}

/** Downloaded offline timetables: storage, the download queue's live state and the merged query network.
 * Files live in the app's private storage (files/offline); nothing is shared or uploaded. */
object OfflineSchedules {
    private lateinit var app: Context
    private val dir get() = File(app.filesDir, "offline").apply { mkdirs() }
    private val indexFile get() = File(dir, "index.json")
    private val catalogFile get() = File(dir, "catalog.json")
    private val prefs get() = app.getSharedPreferences("offline_schedules", Context.MODE_PRIVATE)

    var installed by mutableStateOf<List<InstalledOfflineFeed>>(emptyList()); private set
    val tasks = mutableStateMapOf<String, OfflineTask>()
    var autoRefresh by mutableStateOf(true); private set
    val totalBytes get() = installed.sumOf { it.bytes }

    private val networkLock = Mutex()
    @Volatile private var network: OfflineNetwork? = null
    private val http by lazy {
        OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        installed = runCatching { JSONArray(indexFile.readText()).objects().map(InstalledOfflineFeed::parse) }.getOrDefault(emptyList())
            .filter { file(it.id).isFile }
        autoRefresh = prefs.getBoolean("auto_refresh", true)
        queue().forEach { tasks[it.id] = OfflineTask.Queued }
    }

    fun chooseAutoRefresh(context: Context, enabled: Boolean) {
        autoRefresh = enabled
        prefs.edit().putBoolean("auto_refresh", enabled).apply()
        OfflineScheduleJob.scheduleRefresh(context)
    }

    private fun safeName(id: String) = id.lowercase().replace(Regex("[^a-z0-9-]"), "_") + "-" + Integer.toHexString(id.hashCode())
    private fun file(id: String) = File(dir, safeName(id) + ".mgtt")

    // ---- Queries ----
    /** Every installed feed merged into one network, loaded once and kept until the set changes. Null without feeds. */
    suspend fun network(): OfflineNetwork? {
        if (!::app.isInitialized) return null
        network?.let { return it }
        return networkLock.withLock {
            network ?: withContext(Dispatchers.IO) {
                val feeds = installed.mapNotNull { feed -> runCatching { file(feed.id).inputStream().use(OfflineTimetable::read) }.getOrNull() }
                if (feeds.isEmpty()) null else OfflineNetwork(feeds).also { network = it }
            }
        }
    }
    val hasData get() = ::app.isInitialized && installed.isNotEmpty()

    // ---- Catalog ----
    /** The Transitous list, refreshed from GitHub at most daily; the last copy or the bundled snapshot otherwise. */
    suspend fun catalog(forceRefresh: Boolean = false): Pair<List<OfflineFeed>, Boolean> = withContext(Dispatchers.IO) {
        val fresh = catalogFile.isFile && System.currentTimeMillis() - catalogFile.lastModified() < 24 * 3600_000L
        if (!fresh || forceRefresh) {
            val body = runCatching {
                http.newCall(Request.Builder().url(OfflineFeedCatalog.SOURCE_URL).header("User-Agent", userAgent).build()).execute().use { response ->
                    check(response.isSuccessful); response.body!!.string()
                }
            }.getOrNull()
            if (body != null && runCatching { OfflineFeedCatalog.parse(body) }.getOrNull()?.isNotEmpty() == true) {
                catalogFile.writeText(body); return@withContext OfflineFeedCatalog.parse(body) to true
            }
        }
        val saved = runCatching { OfflineFeedCatalog.parse(catalogFile.readText()) }.getOrNull()
        if (saved != null && saved.isNotEmpty()) return@withContext saved to fresh
        OfflineFeedCatalog.parse(app.assets.open(OfflineFeedCatalog.BUNDLED_ASSET).bufferedReader().use { it.readText() }) to false
    }

    /** Download size of the Transitous copy, without downloading it. */
    suspend fun downloadSize(feed: OfflineFeed): Long? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(feed.url).head().header("User-Agent", userAgent).build()).execute().use { r ->
                r.header("Content-Length")?.toLongOrNull()?.takeIf { r.isSuccessful }
            }
        }.getOrNull()
    }

    // ---- Queue ----
    private fun queue(): List<OfflineFeed> = runCatching {
        JSONArray(prefs.getString("queue", "[]")).objects().map {
            OfflineFeed(it.getString("id"), it.getString("name"), OfflineFeedGroup.valueOf(it.getString("group")), it.getString("url"),
                it.optString("originalUrl").ifBlank { null }, it.optString("license").ifBlank { null })
        }
    }.getOrDefault(emptyList())
    private fun saveQueue(feeds: List<OfflineFeed>) = prefs.edit().putString("queue", JSONArray(feeds.map {
        JSONObject().put("id", it.id).put("name", it.name).put("group", it.group.name).put("url", it.url)
            .put("originalUrl", it.originalUrl ?: "").put("license", it.license ?: "")
    }).toString()).apply()

    @Synchronized fun enqueue(context: Context, feed: OfflineFeed) {
        val current = queue()
        if (current.none { it.id == feed.id }) saveQueue(current + feed)
        tasks[feed.id] = OfflineTask.Queued
        OfflineScheduleJob.startDownloads(context)
    }
    @Synchronized fun cancel(id: String) {
        saveQueue(queue().filterNot { it.id == id })
        if (tasks[id] !is OfflineTask.Downloading && tasks[id] !is OfflineTask.Processing) tasks.remove(id)
        cancelled += id
    }
    private val cancelled = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    @Synchronized internal fun nextQueued(): OfflineFeed? = queue().firstOrNull()
    @Synchronized private fun dequeue(id: String) = saveQueue(queue().filterNot { it.id == id })
    fun dismissFailure(id: String) { if (tasks[id] is OfflineTask.Failed) tasks.remove(id) }

    /** Installed feeds as catalog entries, for refreshing them. */
    internal fun installedAsFeeds(catalog: List<OfflineFeed>): List<OfflineFeed> = installed.map { item ->
        catalog.firstOrNull { it.id == item.id }
            ?: OfflineFeed(item.id, item.name, item.group, OfflineFeedCatalog.processedUrl(item.id), null, item.license)
    }

    fun delete(id: String) {
        file(id).delete()
        installed = installed.filterNot { it.id == id }
        saveIndex(); network = null
        OfflineScheduleJob.scheduleRefresh(app)
    }

    private fun saveIndex() { indexFile.writeText(JSONArray(installed.map { it.json() }).toString()) }

    // ---- Download and compaction (runs inside OfflineScheduleJob) ----
    /** Download and compact one queued feed. With [onlyIfChanged], a feed whose source is unchanged and whose
     * window still has two weeks left is kept as is. */
    internal suspend fun process(feed: OfflineFeed, onlyIfChanged: Boolean = false) = withContext(Dispatchers.IO) {
        cancelled -= feed.id
        val previous = installed.firstOrNull { it.id == feed.id }
        val zip = File(app.cacheDir, "offline-download").apply { mkdirs() }.let { File(it, safeName(feed.id) + ".zip") }
        try {
            if (!onlyIfChanged) tasks[feed.id] = OfflineTask.Downloading(0, -1)
            val (sourceUpdated, sourceBytes) = download(feed, zip, if (onlyIfChanged) previous else null) ?: run {
                tasks.remove(feed.id); return@withContext
            }
            check(feed.id !in cancelled) { "Megszakítva." }
            tasks[feed.id] = OfflineTask.Processing(0f)
            val table = GtfsCompactor.compact(zip, feed.id, feed.name, LocalDate.now(Budapest), sourceUpdated) { fraction ->
                if (feed.id in cancelled) throw CancelledDownload()
                tasks[feed.id] = OfflineTask.Processing(fraction * .9f)
            }
            zip.delete()
            val temp = File(dir, safeName(feed.id) + ".tmp")
            temp.outputStream().use { table.write(it) }
            check(temp.renameTo(file(feed.id)) || (file(feed.id).delete() && temp.renameTo(file(feed.id)))) { "Nem sikerült menteni a menetrendet." }
            val entry = InstalledOfflineFeed(feed.id, feed.name, feed.group, feed.license, file(feed.id).length(), table.builtAt,
                sourceUpdated, sourceBytes, table.firstDay, table.lastServiceDay ?: table.lastDay, table.tripCount, table.stopCount)
            installed = (installed.filterNot { it.id == feed.id } + entry)
            saveIndex(); network = null
            tasks.remove(feed.id)
            OfflineScheduleJob.scheduleRefresh(app)
        } catch (e: CancelledDownload) {
            tasks.remove(feed.id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            tasks[feed.id] = OfflineTask.Failed("Ez a menetrend túl nagy ennek a készüléknek.")
        } catch (e: Exception) {
            if (feed.id in cancelled) tasks.remove(feed.id)
            else tasks[feed.id] = OfflineTask.Failed(if (e is IOException) "A letöltés nem sikerült. Ellenőrizd az internetkapcsolatot."
                else e.message ?: "A feldolgozás nem sikerült.")
        } finally {
            zip.delete()
            dequeue(feed.id)
        }
    }

    private class CancelledDownload : Exception()

    /** Streams the feed to [zip]; returns (Last-Modified, size), or null when [previous] is still current. */
    private fun download(feed: OfflineFeed, zip: File, previous: InstalledOfflineFeed?): Pair<Long, Long>? {
        for (url in listOfNotNull(feed.url, feed.originalUrl)) {
            val request = Request.Builder().url(url).header("User-Agent", userAgent).apply {
                if (previous != null && previous.sourceUpdated > 0) header("If-Modified-Since", java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                    .format(java.time.Instant.ofEpochMilli(previous.sourceUpdated).atZone(java.time.ZoneOffset.UTC)))
            }.build()
            http.newCall(request).execute().use { response ->
                if (response.code == 304 && previous != null &&
                    !previous.lastServiceDay.isBefore(LocalDate.now(Budapest).plusDays(14))) return null
                if (response.code == 404 && url != feed.originalUrl && feed.originalUrl != null) return@use
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Üres válasz")
                val total = body.contentLength()
                if (previous != null && response.header("Last-Modified") == null && total == previous.sourceBytes &&
                    !previous.lastServiceDay.isBefore(LocalDate.now(Budapest).plusDays(14))) return null
                var read = 0L; var reported = 0L
                body.byteStream().use { input ->
                    zip.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            if (feed.id in cancelled) throw CancelledDownload()
                            val n = input.read(buffer); if (n < 0) break
                            output.write(buffer, 0, n); read += n
                            if (read - reported > 256 * 1024) { reported = read; tasks[feed.id] = OfflineTask.Downloading(read, total) }
                        }
                    }
                }
                val modified = response.headers.getDate("Last-Modified")?.time ?: 0L
                return modified to read
            }
        }
        throw IOException("A menetrend nem érhető el.")
    }

    private val userAgent get() = "MenetrendGO/${BuildConfig.VERSION_NAME} (Android)"
}
