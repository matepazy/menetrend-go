package hu.matepazy.menetrendgo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** utas.hu (realCity) vehicles and schedules, used only in the Aggregált data-source mode.
 * Same request as the utas.hu web map; the country is fetched as tiles because the endpoint
 * silently returns an empty list when a half-span exceeds about one degree. */
class UtasClient(private val http: HttpTransport = HttpTransport.Default) {
    private val root = "https://utas.hu/api/query/v1/ws/otp/api/where"
    private val headers = mapOf("Accept" to "application/json", "Origin" to "https://utas.hu", "Referer" to "https://utas.hu/")
    private val lock = Mutex()
    private var cache: Pair<List<Vehicle>, Long>? = null
    private val tripLock = Mutex()
    private val trips = mutableMapOf<Pair<String, LocalDate>, Pair<JSONObject, Long>>()

    private fun request(tile: UtasTile): JSONObject = request(
        "vehicles-for-location",
        listOf(
            "lat" to tile.lat, "lon" to tile.lon, "latSpan" to tile.latSpan, "lonSpan" to tile.lonSpan
        ).joinToString("&") { (k, v) -> "$k=${String.format(Locale.ROOT, "%.5f", v)}" },
    )

    private fun request(endpoint: String, parameters: String): JSONObject {
        val query = "$parameters&key=$KEY&version=9&appVersion=$APP_VERSION&locale=hu"
        val response = try {
            http.execute(HttpRequest("$root/$endpoint.json?$query", headers, readTimeoutMillis = 20_000))
        } catch (e: ForcedOffline) { throw e
        } catch (e: java.io.IOException) { throw IllegalStateException("Nem sikerült kapcsolódni az utas.hu-hoz.", e) }
        check(response.ok) { "Az utas.hu adatai most nem érhetők el (${response.code})." }
        return JSONObject(response.body)
    }

    suspend fun trip(id: String, date: LocalDate, refreshSeconds: Int): JSONObject = withContext(Dispatchers.IO) {
        tripLock.withLock {
            val key = id to date
            val now = System.currentTimeMillis() / 1000
            trips[key]?.takeIf { now - it.second < refreshSeconds.coerceIn(15, 90) }?.first ?: run {
                val tripId = URLEncoder.encode(id, "UTF-8")
                val serviceDate = date.format(DateTimeFormatter.BASIC_ISO_DATE)
                val detail = parseUtasTrip(
                    request("trip-details", "tripId=$tripId&date=$serviceDate"), id, date,
                )
                if (trips.size >= 100) trips.clear()
                trips[key] = detail to System.currentTimeMillis() / 1000
                detail
            }
        }
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
        private const val APP_VERSION = "8126.344625.20261117"
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

private fun JSONObject.utasText(key: String) = if (isNull(key)) "" else optString(key).trim()
    .takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()

private fun utasDate(value: String): LocalDate? = try {
    LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
} catch (_: DateTimeParseException) { null }

/** Parses one vehicles-for-location.json envelope. Rejects provider errors and the
 * out-of-range / limit-exceeded states, which would otherwise look like an empty map. */
internal fun parseUtasVehicles(body: JSONObject): List<Vehicle> {
    val code = body.optInt("code", 200)
    check(code == 200) { "Az utas.hu hibát jelzett ($code)." }
    val data = body.optJSONObject("data") ?: throw IllegalStateException("Az utas.hu hiányos választ adott.")
    check(!data.optBoolean("outOfRange") && !data.optBoolean("limitExceeded")) { "Az utas.hu lekérdezési területe túl nagy." }
    val refs = data.optJSONObject("references") ?: JSONObject()
    val routes = refs.optJSONObject("routes") ?: JSONObject()
    val trips = refs.optJSONObject("trips") ?: JSONObject()
    val agencies = refs.optJSONObject("agencies") ?: JSONObject()
    return data.array("list").mapNotNull { v ->
        val vehicleId = v.utasText("vehicleId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val location = v.optJSONObject("location") ?: return@mapNotNull null
        val lat = location.optDouble("lat"); val lon = location.optDouble("lon")
        if (!lat.isFinite() || !lon.isFinite() || lat !in 40.0..52.0 || lon !in 12.0..28.0) return@mapNotNull null
        val tripId = v.utasText("tripId")
        val route = routes.optJSONObject(v.utasText("routeId"))
        val trip = trips.optJSONObject(tripId)
        val mode = utasMode(v.utasText("vehicleRouteType").ifBlank { route?.utasText("type").orEmpty() }.ifBlank { "BUS" })
        val routeName = route?.utasText("shortName").orEmpty()
        val tripName = trip?.utasText("tripShortName").orEmpty()
        // Same naming rule as MÁVPlusz vehicles, so a bus looks the same whichever provider reports it.
        val name = (if (modeFamily(mode) == 0) tripName else routeName)
            .ifBlank { routeName }.ifBlank { tripName }.ifBlank { "Járat" }
        Vehicle(
            id = utasOtpTripId(tripId) ?: "utas:$vehicleId", name = hungarianServiceLabel(name),
            headsign = trip?.utasText("tripHeadsign").orEmpty().ifBlank { v.utasText("label") },
            mode = mode, lat = lat, lon = lon,
            routeColor = route?.utasText("color").orEmpty(), routeLongName = route?.utasText("longName").orEmpty(),
            heading = v.optDouble("bearing").takeIf { it.isFinite() && it >= 0 && it <= 360 }?.rem(360.0),
            model = v.utasText("model"), registration = v.utasText("licensePlate"),
            routeShortName = routeName, vehicleId = vehicleId,
            agencyName = agencies.optJSONObject(route?.utasText("agencyId").orEmpty())?.utasText("name").orEmpty(),
            positionUpdatedSeconds = v.optLong("lastUpdateTime", 0L).takeIf { it > 0 },
            sources = VehicleSources.UTAS,
            utasTrip = tripId.takeIf { it.isNotBlank() }?.let {
                val date = utasDate(v.utasText("serviceDate")) ?: utasDate(v.utasText("vertex").substringBefore(':'))
                UtasTripRef(it, date, vehicleId)
            },
        )
    }
}

/** Adapt the trip envelope for the existing live vehicle drawer and map stop overlay. */
internal fun parseUtasTrip(body: JSONObject, id: String, date: LocalDate): JSONObject {
    val data = body.optJSONObject("data")
    val entry = data?.optJSONObject("entry")
    check(body.optInt("code") == 200 && data != null && !data.optBoolean("limitExceeded") &&
        entry != null && entry.utasText("tripId") == id && utasDate(entry.utasText("serviceDate")) == date
    ) { "Ehhez a járathoz nincs részletes menetrend az utas.hu-n." }
    val references = data.optJSONObject("references")?.optJSONObject("stops")
    val times = entry.array("stopTimes").mapNotNull { time ->
        val arrival = time.optLong("arrivalTime").takeIf { it > 0 }
        val departure = time.optLong("departureTime").takeIf { it > 0 }
        if (arrival == null && departure == null) return@mapNotNull null
        val idAtStop = time.utasText("stopId")
        val reference = references?.optJSONObject(idAtStop)
        val stop = JSONObject().put("gtfsId", idAtStop)
            .put("name", reference?.utasText("name").orEmpty().ifBlank { "Ismeretlen megálló" })
            .put("platformCode", reference?.utasText("platformCode").orEmpty())
        reference?.optDouble("lat")?.takeIf { it in -90.0..90.0 }?.let { stop.put("lat", it) }
        reference?.optDouble("lon")?.takeIf { it in -180.0..180.0 }?.let { stop.put("lon", it) }

        val predictedArrival = time.optLong("predictedArrivalTime").takeIf { it > 0 && arrival != null }
        val predictedDeparture = time.optLong("predictedDepartureTime").takeIf { it > 0 && departure != null }
        JSONObject().put("stop", stop)
            .put("stopSequence", time.optInt("stopSequence", -1))
            .put("scheduledArrival", arrival)
            .put("scheduledDeparture", departure)
            .put("realtimeArrival", if (arrival == null) predictedDeparture else predictedArrival)
            .put("realtimeDeparture", predictedDeparture)
            .put("realtime", !time.optBoolean("predictionScheduled") &&
                (predictedArrival != null || predictedDeparture != null))
    }
    check(times.isNotEmpty()) { "Ehhez a járathoz nincs megállóadat az utas.hu-n." }

    val detail = JSONObject().put("usesUnixStopTimes", true).put("stoptimes", JSONArray(times))
    val vehicle = entry.optJSONObject("vehicle")
    val updated = vehicle?.optLong("lastUpdateTime") ?: 0L
    val now = body.optLong("currentTime") / 1000
    if (vehicle != null && updated > 0 && now - updated in 0..UTAS_MAX_AGE_SECONDS) {
        val relationship = JSONObject().put("status", vehicle.utasText("status"))
            .put("stopSequence", vehicle.optInt("stopSequence", -1))
            .put("stop", JSONObject().put("gtfsId", vehicle.utasText("stopId")))
        val position = JSONObject().put("vehicleId", vehicle.utasText("vehicleId"))
            .put("stopRelationship", relationship)
        detail.put("vehiclePositions", JSONArray().put(position))
    }
    return detail
}
