package hu.matepazy.menetrendgo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** OpenStreetMap places are routed by coordinates; their ids only keep history and saved searches stable. */
internal const val PlaceIdPrefix = "osm:"
internal val Station.isPlace get() = id.startsWith(PlaceIdPrefix)

private fun searchWords(value: String) = stationSearchKey(value).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

// Hungary's bounding box (lon/lat); also limits Photon results.
private const val HuWest = 16.11; private const val HuSouth = 45.74; private const val HuEast = 22.90; private const val HuNorth = 48.59
private fun Station.inHungary() = lat in HuSouth..HuNorth && lon in HuWest..HuEast

/**
 * Stops always win. Places are only worth showing when no domestic stop name contains every typed word,
 * so "Westend" (which MÁV matches to Austrian Westendorf) or "Kossuth Lajos utca 12" fall back to
 * OpenStreetMap while "Győr" never does.
 */
internal fun stopMatchIsWeak(query: String, stations: List<Station>): Boolean {
    val words = searchWords(query.removeSuffix("*"))
    if (words.isEmpty()) return false
    return stations.filter { it.inHungary() }.none { station ->
        val name = searchWords(station.name)
        words.all { word -> name.any { it.startsWith(word) } || (word.length >= 4 && name.any { word in it }) }
    }
}

private val placeKinds = mapOf(
    "mall" to "Bevásárlóközpont", "supermarket" to "Szupermarket", "department_store" to "Áruház",
    "hospital" to "Kórház", "clinic" to "Rendelő", "doctors" to "Orvosi rendelő", "pharmacy" to "Gyógyszertár",
    "school" to "Iskola", "university" to "Egyetem", "college" to "Főiskola", "kindergarten" to "Óvoda",
    "townhall" to "Városháza", "post_office" to "Posta", "police" to "Rendőrség", "library" to "Könyvtár",
    "museum" to "Múzeum", "theatre" to "Színház", "cinema" to "Mozi", "stadium" to "Stadion", "sports_centre" to "Sportközpont",
    "park" to "Park", "zoo" to "Állatkert", "castle" to "Vár", "church" to "Templom", "place_of_worship" to "Templom",
    "hotel" to "Szálloda", "restaurant" to "Étterem", "cafe" to "Kávézó", "attraction" to "Látnivaló",
    "airport" to "Repülőtér", "aerodrome" to "Repülőtér", "parking" to "Parkoló", "marketplace" to "Piac",
)

private fun placeKind(o: JSONObject): String = when {
    o.optString("type") == "street" -> "Utca"
    o.optString("type") == "city" -> "Település"
    o.optString("type") in setOf("district", "locality") || o.optString("osm_key") == "place" -> "Településrész"
    o.optString("name").isBlank() && o.optString("housenumber").isNotBlank() -> "Cím"
    else -> placeKinds[o.optString("osm_value")].orEmpty()
}

/** Kinds that drive the row icon; the planner treats every place the same. */
internal enum class PlaceKind { Named, Address, Street, Area }

internal fun Station.placeKind(): PlaceKind = when (type) {
    "PlaceAddress" -> PlaceKind.Address
    "PlaceStreet" -> PlaceKind.Street
    "PlaceArea" -> PlaceKind.Area
    else -> PlaceKind.Named
}

/**
 * Parse a Photon GeoJSON response into Hungarian places, skipping transit stops the provider already covers.
 * Streets sink below named places, addresses and areas; Photon's relevance order holds within each group.
 */
internal fun parsePlaces(body: JSONObject, limit: Int = 5): List<Station> = body.array("features").mapNotNull { feature ->
    val o = feature.optJSONObject("properties") ?: return@mapNotNull null
    val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
    if (coords.length() < 2 || !o.optString("countrycode").equals("HU", true)) return@mapNotNull null
    val key = o.optString("osm_key"); val value = o.optString("osm_value")
    if (key == "public_transport" || key == "railway" && value in setOf("station", "halt", "tram_stop", "stop")
        || key == "highway" && value == "bus_stop" || key == "boundary") return@mapNotNull null
    val street = listOf(o.optString("street"), o.optString("housenumber")).filter { it.isNotBlank() }.joinToString(" ")
    val city = o.optString("city").ifBlank { o.optString("county") }
    val title = o.optString("name").ifBlank { street }.ifBlank { return@mapNotNull null }
    val address = listOf(city, street.takeIf { it != title }.orEmpty())
        .filter { it.isNotBlank() && it != title }.distinct().joinToString(", ")
    val type = when {
        o.optString("type") == "street" -> "PlaceStreet"
        o.optString("type") in setOf("city", "district", "locality") || key == "place" -> "PlaceArea"
        o.optString("name").isBlank() -> "PlaceAddress"
        else -> "Place"
    }
    val id = o.optString("osm_type") + o.optLong("osm_id")
    Station(PlaceIdPrefix + id, title, coords.getDouble(1), coords.getDouble(0), type,
        detail = listOf(placeKind(o), address).filter { it.isNotBlank() }.joinToString(" · "))
}.distinctBy { it.name to it.detail }.sortedBy { it.placeKind() == PlaceKind.Street }.take(limit)

/** Photon (komoot), an open-source OpenStreetMap geocoder built for search-as-you-type. Only the typed text is sent. */
internal class PlaceSearch(private val root: String = "https://photon.komoot.io") {
    private val cache = object : LinkedHashMap<String, List<Station>>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Station>>?) = size > 40
    }

    suspend fun search(query: String): List<Station> = withContext(Dispatchers.IO) {
        val key = stationSearchKey(query)
        require(key.length in 3..100)
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        if (OfflineStatus.forced) return@withContext emptyList()
        // Within Hungary's bounding box; neighbouring countries are dropped again by country code.
        val url = "$root/api/?q=" + URLEncoder.encode(query.trim(), "UTF-8") + "&limit=10&bbox=$HuWest,$HuSouth,$HuEast,$HuNorth"
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        val result = try {
            connection.connectTimeout = 8000; connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "MenetrendGO/${BuildConfig.VERSION_NAME} (Android)")
            check(connection.responseCode == 200) { "A helyek most nem érhetők el." }
            parsePlaces(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
        } catch (e: java.io.IOException) {
            throw IllegalStateException("A helyek nem tölthetők be. Ellenőrizd az internetkapcsolatot.", e)
        } finally { connection.disconnect() }
        synchronized(cache) { cache[key] = result }
        result
    }
}
