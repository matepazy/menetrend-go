package hu.matepazy.menetrendgo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** A separate, bounded LRU cache. Android may reclaim it at any time. No prefetching. */
internal class FleetPhotoCache(private val directory: File) {
    companion object {
        const val MAX_PHOTOS = 12
        const val MAX_BYTES = 12L * 1024 * 1024
        const val MAX_PHOTO_BYTES = 2 * 1024 * 1024
    }

    private fun file(url: String): File = File(directory,
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) })

    @Synchronized fun read(url: String): ByteArray? {
        trim()
        val file = file(url)
        if (!file.isFile) return null
        if (file.length() > MAX_PHOTO_BYTES) { file.delete(); return null }
        return file.readBytes().also { file.setLastModified(System.currentTimeMillis()) }
    }

    @Synchronized fun write(url: String, bytes: ByteArray) {
        require(bytes.size in 1..MAX_PHOTO_BYTES)
        if (!directory.isDirectory && !directory.mkdirs()) return
        val target = file(url)
        val temporary = File.createTempFile("photo-", ".tmp", directory)
        try {
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) { target.delete(); temporary.renameTo(target) }
            target.setLastModified(System.currentTimeMillis())
        } finally { temporary.delete() }
        trim()
    }

    @Synchronized fun remove(url: String) { file(url).delete() }

    private fun trim() {
        val files = directory.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
        var bytes = 0L
        files.forEachIndexed { index, file ->
            bytes += file.length()
            if (index >= MAX_PHOTOS || bytes > MAX_BYTES) file.delete()
        }
    }
}

internal fun isFleetPhotoUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.host in setOf("upload.wikimedia.org", "thumb.wikimedia.org") &&
        uri.userInfo == null && uri.port == -1 && uri.path.startsWith("/wikipedia/commons/")
}.getOrDefault(false)

internal object FleetPhotoStore {
    private var cache: FleetPhotoCache? = null
    @Synchronized private fun cache(context: Context): FleetPhotoCache = cache
        ?: FleetPhotoCache(File(context.cacheDir, "fleet-photos")).also { cache = it }
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun load(context: Context, url: String): Bitmap = runInterruptible(Dispatchers.IO) {
        require(isFleetPhotoUrl(url))
        val cache = cache(context)
        // A malformed or reclaimed cache entry must not permanently prevent a fresh download.
        runCatching { cache.read(url) }.getOrNull()?.let { bytes ->
            decode(bytes)?.let { return@runInterruptible it }
            cache.remove(url)
        }
        val bytes = client.newCall(Request.Builder().url(url)
            .header("User-Agent", "MenetrendGO/1.3 (Android vehicle field guide)").build()).execute().use { response ->
            check(response.isSuccessful) { "Photo HTTP ${response.code}" }
            val body = checkNotNull(response.body)
            check(body.contentType()?.type == "image")
            check(body.contentLength() <= FleetPhotoCache.MAX_PHOTO_BYTES)
            body.byteStream().use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= FleetPhotoCache.MAX_PHOTO_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
        val bitmap = checkNotNull(decode(bytes)) { "Invalid photo" }
        runCatching { cache.write(url, bytes) } // A full disk must not hide a downloaded image.
        bitmap
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192) return null
        val options = BitmapFactory.Options().apply {
            while (bounds.outWidth / inSampleSize.coerceAtLeast(1) > 1600 ||
                bounds.outHeight / inSampleSize.coerceAtLeast(1) > 1600) {
                inSampleSize = inSampleSize.coerceAtLeast(1) * 2
            }
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
