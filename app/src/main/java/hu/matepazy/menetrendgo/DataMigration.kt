package hu.matepazy.menetrendgo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * One-time, idempotent rewrites of saved data, run in [MenetrendGoApp.onCreate] before anything reads it.
 *
 * Schema 2 namespaces station ids ([StationIds]): recents, saved searches, widget stops and the station search
 * cache get "mav:" for bare MÁVPlusz ids. Saved journeys and the tracking session need no rewrite: an itinerary
 * without `idSchema` is read as MÁVPlusz JSON and namespaced on load ([Journey.parse]).
 */
internal object DataMigration {
    const val SCHEMA = 2
    private const val KEY = "data_schema"

    fun run(context: Context) {
        val main = context.getSharedPreferences("menetrendgo", Context.MODE_PRIVATE)
        if (main.getInt(KEY, 1) >= SCHEMA) return
        // apply() updates the in-memory preferences at once, so every later reader in this process sees the rewrite;
        // if the process dies before the disk write, the idempotent migration simply runs again.
        val widgets = context.getSharedPreferences("stop_widgets", Context.MODE_PRIVATE)
        val widgetEdit = widgets.edit()
        widgets.all.forEach { (key, value) ->
            if (key.startsWith("station_") && value is String) migrateStationJson(value)?.let { widgetEdit.putString(key, it) }
        }
        widgetEdit.apply()
        val search = context.getSharedPreferences("station_search", Context.MODE_PRIVATE)
        val searchEdit = search.edit()
        search.getString("queries", null)?.let { searchEdit.putString("queries", migrateSearchCache(it)) }
        search.getString("usage", null)?.let { searchEdit.putString("usage", migrateUsage(it)) }
        searchEdit.apply()
        val mainEdit = main.edit()
        listOf("recents", "saved_searches").forEach { key -> main.getString(key, null)?.let { mainEdit.putString(key, migrateStationPairs(it)) } }
        mainEdit.putInt(KEY, SCHEMA).apply()
    }

    private fun JSONObject.migrateId(): JSONObject = put("id", StationIds.migrate(optString("id")))

    /** One stored station; null when it is not readable JSON (left as it is). */
    internal fun migrateStationJson(raw: String): String? = runCatching { JSONObject(raw).migrateId().toString() }.getOrNull()

    /** Recents and saved searches: objects whose "from" and "to" are stations. Unreadable data is kept as is. */
    internal fun migrateStationPairs(raw: String): String = runCatching {
        val items = JSONArray(raw)
        items.objects().forEach { item -> listOf("from", "to").forEach { item.optJSONObject(it)?.migrateId() } }
        items.toString()
    }.getOrDefault(raw)

    internal fun migrateSearchCache(raw: String): String = runCatching {
        val entries = JSONArray(raw)
        entries.objects().forEach { entry -> entry.array("stations").forEach { it.migrateId() } }
        entries.toString()
    }.getOrDefault(raw)

    /** Selection counts keyed by station id; counts of ids that now coincide are added. */
    internal fun migrateUsage(raw: String): String = runCatching {
        val old = JSONObject(raw)
        val counts = LinkedHashMap<String, Int>()
        old.keys().forEach { id -> StationIds.migrate(id).let { counts[it] = (counts[it] ?: 0) + old.optInt(id) } }
        JSONObject(counts as Map<*, *>).toString()
    }.getOrDefault(raw)
}
