package hu.matepazy.menetrendgo

import java.net.URI

enum class MapType(val id: String, val title: String, val summary: String) {
    Standard("standard", "Alap", "Utcák és települések · OpenStreetMap"),
    Satellite("satellite", "Műhold", "Műhold- és légifelvételek · Esri"),
    Hybrid("hybrid", "Hibrid", "Műholdkép utakkal és feliratokkal · Esri"),
    Rail("rail", "Vasúti", "Vasútvonalak és állomások · OpenRailwayMap"),
    Custom("custom", "Saját térkép", "A megadott csempeforrás");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Standard
    }
}

/** Leaflet XYZ raster templates only. Treat URLs as data, never HTML or script. */
fun validMapTileUrl(value: String): Boolean {
    if (value.length > 4096 || value.any { it.isWhitespace() || it.isISOControl() }) return false
    if (!listOf("{z}", "{x}", "{y}").all(value::contains)) return false
    val substituted = value.replace(Regex("\\{[zxysr]\\}"), "1")
    return runCatching {
        val uri = URI(substituted)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawFragment == null
    }.getOrDefault(false)
}
