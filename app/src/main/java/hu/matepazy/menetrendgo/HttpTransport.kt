package hu.matepazy.menetrendgo

import java.net.HttpURLConnection
import java.net.URI

/** One HTTP request. A [body] makes it a JSON POST. */
data class HttpRequest(val url: String, val headers: Map<String, String> = emptyMap(), val body: String? = null,
    val connectTimeoutMillis: Int = 10_000, val readTimeoutMillis: Int = 20_000)

/** [body] is read only for 2xx answers; other codes leave it empty. */
data class HttpResponse(val code: Int, val body: String) { val ok get() = code in 200..299 }

/** The only way providers reach the network, so a test can stand in for it and Offline mód cannot be bypassed.
 * Connection failures surface as [java.io.IOException]; the provider decides what they mean. */
fun interface HttpTransport {
    fun execute(request: HttpRequest): HttpResponse

    companion object {
        val userAgent get() = "MenetrendGO/${BuildConfig.VERSION_NAME} (Android)"
        /** The device transport: refuses before connecting while Offline mód is on. */
        val Default = HttpTransport { request ->
            OfflineStatus.requireNetwork()
            val connection = URI(request.url).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = request.connectTimeoutMillis; connection.readTimeout = request.readTimeoutMillis
                connection.setRequestProperty("User-Agent", userAgent)
                request.headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
                request.body?.let { body ->
                    connection.requestMethod = "POST"; connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                HttpResponse(code, if (code in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else "")
            } finally { connection.disconnect() }
        }
    }
}
