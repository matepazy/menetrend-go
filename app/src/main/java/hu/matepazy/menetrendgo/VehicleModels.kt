package hu.matepazy.menetrendgo

import android.content.res.AssetManager
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

data class ModelPhoto(val asset: String, val caption: String, val author: String,
    val source: String, val license: String, val licenseUrl: String)
data class VehicleModelProfile(val id: String, val title: String, val aliases: List<String>,
    val description: String, val facts: List<String>, val sources: List<Pair<String, String>>,
    val photos: List<ModelPhoto>)

/** Only explicit aliases match: a 12 m bus must never inherit the articulated variant's photo. */
fun normalizedVehicleModel(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "")

fun findVehicleModel(profiles: List<VehicleModelProfile>, name: String): VehicleModelProfile? {
    val key = normalizedVehicleModel(name)
    if (key.isEmpty()) return null
    return profiles.firstOrNull { profile -> profile.aliases.any { normalizedVehicleModel(it) == key } }
}

object VehicleModelCatalog {
    fun load(assets: AssetManager): List<VehicleModelProfile> {
        val rows = JSONArray(assets.open("vehicle_models/catalog.json").bufferedReader().use { it.readText() })
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            fun strings(key: String) = row.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) } }
            val photos = row.getJSONArray("photos")
            val sources = row.getJSONArray("sources")
            VehicleModelProfile(row.getString("id"), row.getString("title"), strings("aliases"),
                row.getString("description"), strings("facts"),
                (0 until sources.length()).map { sources.getJSONObject(it).let { s -> s.getString("label") to s.getString("url") } },
                (0 until photos.length()).map { photos.getJSONObject(it).let { p ->
                    ModelPhoto(p.getString("asset"), p.getString("caption"), p.getString("author"),
                        p.getString("source"), p.getString("license"), p.getString("licenseUrl"))
                } })
        }
    }
}
