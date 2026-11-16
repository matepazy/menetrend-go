package hu.matepazy.menetrendgo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale

/** utas.hu (realCity) live vehicles, used only in the Aggregált data-source mode.
 * Same request as the utas.hu web map; the country is fetched as tiles because the endpoint
 * silently returns an empty list when a half-span exceeds about one degree. */
class UtasClient {
    private val root = "https://utas.hu/api/query/v1/ws/otp/api/where"
    private val lock = Mutex()
    private var cache: Pair<List<Vehicle>, Long>? = null

    private fun request(tile: UtasTile): JSONObject {
        val query = listOf(
            "lat" to tile.lat, "lon" to tile.lon, "latSpan" to tile.latSpan, "lonSpan" to tile.lonSpan
        ).joinToString("&") { (k, v) -> "$k=${String.format(Locale.ROOT, "%.5f", v)}" } +
            "&key=$KEY&version=9&appVersion=$APP_VERSION&locale=hu"
        val connection = URI("$root/vehicles-for-location.json?$query").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000; connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Origin", "https://utas.hu")
            connection.setRequestProperty("Referer", "https://utas.hu/")
            connection.setRequestProperty("User-Agent", "MenetrendGO/${BuildConfig.VERSION_NAME} (Android)")
            check(connection.responseCode in 200..299) { "Az utas.hu adatai most nem érhetők el (${connection.responseCode})." }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Nem sikerült kapcsolódni az utas.hu-hoz.", e)
        } finally { connection.disconnect() }
    }

    /** Cached like [MavClient.vehicles]. A failed refresh keeps the last good list for up to
     * [STALE_SECONDS], so one bad poll doesn't make every bus vanish from the map. */
    suspend fun vehicles(forceRefresh: Boolean = false, refreshSeconds: Int = 75): Pair<List<Vehicle>, Long> = withContext(Dispatchers.IO) {
        lock.withLock {
            val now = System.currentTimeMillis() / 1000
            cache?.takeIf { !forceRefresh && now - it.second < refreshSeconds.coerceIn(15, 90) }?.let { return@withLock it }
            try {
                val responses = coroutineScope {
                    UtasTile.hungary.map { tile -> async { runCatching { request(tile) } } }.awaitAll()
                }
                responses.forEach { r -> r.exceptionOrNull()?.let { if (it is CancellationException) throw it } }
                val ok = responses.mapNotNull { it.getOrNull() }
                if (ok.isEmpty()) throw responses.first().exceptionOrNull() ?: IllegalStateException("Az utas.hu nem adott választ.")
                val merged = ok.flatMap(::parseUtasVehicles).distinctBy { it.id }
                (merged to System.currentTimeMillis() / 1000).also { cache = it }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                cache?.takeIf { now - it.second <= STALE_SECONDS } ?: throw e
            }
        }
    }

    companion object {
        private const val KEY = "ride-web"
        private const val APP_VERSION = "8063.340705.20261103"
        private const val STALE_SECONDS = 90L
    }
}

/** One request area; spans are half-extents, as utas.hu expects. */
internal data class UtasTile(val lat: Double, val lon: Double, val latSpan: Double, val lonSpan: Double) {
    companion object {
        /** Hungary (45.7–48.6 N, 16.1–22.9 E) as 2×4 tiles: half-spans 0.725° × 0.85°. */
        val hungary: List<UtasTile> = run {
            val (minLat, maxLat, minLon, maxLon) = listOf(45.7, 48.6, 16.1, 22.9)
            val rows = 2; val cols = 4
            val dLat = (maxLat - minLat) / rows; val dLon = (maxLon - minLon) / cols
            (0 until rows).flatMap { r -> (0 until cols).map { c ->
                UtasTile(minLat + dLat * (r + .5), minLon + dLon * (c + .5), dLat / 2, dLon / 2)
            } }
        }
    }
}

/** utas.hu mode names → the app's MÁVPlusz/OTP mode names, so icons and filters match. */
internal fun utasMode(value: String): String = when (val mode = value.trim().uppercase(Locale.ROOT)) {
    "REPLACEMENT_BUS" -> "RAIL_REPLACEMENT_BUS"
    "TRAM_TRAIN" -> "TRAMTRAIN"
    else -> mode
}

/** utas.hu trip id → the same trip's MÁVPlusz OTP gtfsId, so the vehicle opens a timetable.
 * Verified live on 2026-11-16: BKK_D123 → BKK:D123, mav_36437232 → 1:36437232,
 * hkir_8070_154-4 → hkir:hkir_8070_154-4. Other feeds (e.g. Miskolc "mvk") use a different id space. */
internal fun utasOtpTripId(tripId: String): String? {
    val prefix = tripId.substringBefore('_', ""); val rest = tripId.substringAfter('_', "")
    if (prefix.isEmpty() || rest.isEmpty()) return null
    return when (prefix) {
        "BKK" -> "BKK:$rest"
        "mav" -> "1:$rest"
        "hkir" -> "hkir:$tripId"
        else -> null
    }
}

/** Parses one vehicles-for-location.json envelope. Rejects provider errors and the
 * out-of-range / limit-exceeded states, which would otherwise look like an empty map. */
internal fun parseUtasVehicles(body: JSONObject): List<Vehicle> {
    fun JSONObject.text(key: String) = if (isNull(key)) "" else optString(key).trim()
        .takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
    val code = body.optInt("code", 200)
    check(code == 200) { "Az utas.hu hibát jelzett ($code)." }
    val data = body.optJSONObject("data") ?: throw IllegalStateException("Az utas.hu hiányos választ adott.")
    check(!data.optBoolean("outOfRange") && !data.optBoolean("limitExceeded")) { "Az utas.hu lekérdezési területe túl nagy." }
    val refs = data.optJSONObject("references") ?: JSONObject()
    val routes = refs.optJSONObject("routes") ?: JSONObject()
    val trips = refs.optJSONObject("trips") ?: JSONObject()
    val agencies = refs.optJSONObject("agencies") ?: JSONObject()
    return data.array("list").mapNotNull { v ->
        val vehicleId = v.text("vehicleId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val location = v.optJSONObject("location") ?: return@mapNotNull null
        val lat = location.optDouble("lat"); val lon = location.optDouble("lon")
        if (!lat.isFinite() || !lon.isFinite() || lat !in 40.0..52.0 || lon !in 12.0..28.0) return@mapNotNull null
        val route = routes.optJSONObject(v.text("routeId"))
        val trip = trips.optJSONObject(v.text("tripId"))
        val mode = utasMode(v.text("vehicleRouteType").ifBlank { route?.text("type").orEmpty() }.ifBlank { "BUS" })
        val routeName = route?.text("shortName").orEmpty()
        val tripName = trip?.text("tripShortName").orEmpty()
        // Same naming rule as MÁVPlusz vehicles, so a bus looks the same whichever provider reports it.
        val name = (if (modeFamily(mode) == 0) tripName else routeName)
            .ifBlank { routeName }.ifBlank { tripName }.ifBlank { "Járat" }
        Vehicle(
            id = utasOtpTripId(v.text("tripId")) ?: "utas:$vehicleId", name = hungarianServiceLabel(name),
            headsign = trip?.text("tripHeadsign").orEmpty().ifBlank { v.text("label") },
            mode = mode, lat = lat, lon = lon,
            routeColor = route?.text("color").orEmpty(), routeLongName = route?.text("longName").orEmpty(),
            heading = v.optDouble("bearing").takeIf { it.isFinite() && it >= 0 && it <= 360 }?.rem(360.0),
            model = v.text("model"), registration = v.text("licensePlate"),
            routeShortName = routeName, vehicleId = vehicleId,
            agencyName = agencies.optJSONObject(route?.text("agencyId").orEmpty())?.text("name").orEmpty(),
            positionUpdatedSeconds = v.optLong("lastUpdateTime", 0L).takeIf { it > 0 },
            sources = VehicleSources.UTAS,
        )
    }
}
