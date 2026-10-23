package hu.matepazy.menetrendgo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

internal fun stationSearchKey(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

/** Stable sorting retains the provider's relevance order when there is no local history. */
internal fun rankStations(query: String, stations: List<Station>, usage: Map<String, Int>): List<Station> {
    val town = stationSearchKey(query).removeSuffix("*").trim()
    fun priority(station: Station): Int {
        val name = stationSearchKey(station.name)
        val place = name.substringBefore(',').removeSuffix("*").trim()
        return when {
            name.endsWith('*') && place == town -> 0
            name == town && station.type == "Station" && "RAIL" in station.modes -> 1
            place == town && (name.substringAfter(',', "").trim() in
                setOf("autobusz-allomas", "vasutallomas")) -> 1
            else -> 2
        }
    }
    return stations.distinctBy { it.id }.sortedWith(compareBy<Station> { priority(it) }
        .thenByDescending { usage[it.id] ?: 0 })
}

/** Bounded, device-local query cache. Cached names are useful even without a connection. */
internal class StationSearch(context: Context, private val fetch: suspend (String) -> List<Station>) {
    private val prefs = context.getSharedPreferences("station_search", Context.MODE_PRIVATE)
    private val lock = Mutex()
    private val maxAge = 7L * 24 * 60 * 60 * 1000

    private fun entries(): List<JSONObject> = runCatching {
        JSONArray(prefs.getString("queries", "[]")).objects()
    }.getOrDefault(emptyList())
    private fun usage(): Map<String, Int> = runCatching {
        val json = JSONObject(prefs.getString("usage", "{}") ?: "{}")
        json.keys().asSequence().associateWith { json.optInt(it) }
    }.getOrDefault(emptyMap())
    private fun decode(entry: JSONObject): List<Station> = entry.array("stations").mapNotNull {
        runCatching { Station.parse(it) }.getOrNull()
    }
    private fun cached(query: String, all: List<JSONObject>): List<Station> {
        val key = stationSearchKey(query)
        val exact = all.find { it.optString("query") == key }
        if (exact != null) return decode(exact)
        return all.flatMap(::decode).distinctBy { it.id }.filter { stationSearchKey(it.name).contains(key) }
    }

    suspend fun cached(query: String): List<Station> = withContext(Dispatchers.IO) {
        lock.withLock { rankStations(query, cached(query, entries()), usage()) }
    }

    suspend fun search(query: String): List<Station> = withContext(Dispatchers.IO) {
        val key = stationSearchKey(query)
        require(key.length in 2..100)
        val fresh = lock.withLock { entries().find {
            it.optString("query") == key && System.currentTimeMillis() - it.optLong("time") in 0 until maxAge
        } }
        if (fresh != null) return@withContext lock.withLock { rankStations(query, decode(fresh), usage()) }
        // Fetch outside the lock, so typing and local selections never wait for the network.
        val result = fetch(query)
        lock.withLock {
            val entry = JSONObject().put("query", key).put("time", System.currentTimeMillis())
                .put("stations", JSONArray(result.map { it.json() }))
            prefs.edit().putString("queries", JSONArray((listOf(entry) + entries()
                .filterNot { it.optString("query") == key }).take(50)).toString()).apply()
            rankStations(query, result, usage())
        }
    }

    suspend fun selected(station: Station) = withContext(Dispatchers.IO) {
        lock.withLock {
            val counts = usage().toMutableMap()
            counts[station.id] = ((counts[station.id] ?: 0) + 1).coerceAtMost(1_000_000)
            val bounded = counts.entries.sortedByDescending { it.value }.take(500).associate { it.toPair() }
            prefs.edit().putString("usage", JSONObject(bounded).toString()).apply()
        }
    }
}
