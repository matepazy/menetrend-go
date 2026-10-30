package hu.matepazy.menetrendgo

import org.json.JSONObject

enum class SavedSearchIcon(val title: String) {
    Bookmark("Könyvjelző"), Home("Otthon"), Work("Munka"), School("Iskola"),
    Favorite("Kedvenc"), Train("Vonat"), Shopping("Bevásárlás"), Explore("Kirándulás")
}

/** A reusable route deliberately has no date, time or arrival/departure mode. */
data class SavedSearch(val from: Station, val to: Station, val name: String, val icon: SavedSearchIcon) {
    val key get() = "${from.id}:${to.id}"
    fun json() = JSONObject().put("from", from.json()).put("to", to.json())
        .put("name", name).put("icon", icon.name)

    companion object {
        const val MAX_NAME_LENGTH = 60
        fun parse(json: JSONObject): SavedSearch {
            val from = Station.parse(json.getJSONObject("from"))
            val to = Station.parse(json.getJSONObject("to"))
            val name = json.getString("name").trim().take(MAX_NAME_LENGTH)
            require(canSaveRoute(from, to) && name.isNotEmpty())
            val icon = SavedSearchIcon.entries.firstOrNull { it.name == json.optString("icon") }
                ?: SavedSearchIcon.Bookmark
            return SavedSearch(from, to, name, icon)
        }
    }
}

fun canSaveRoute(from: Station?, to: Station?) =
    from != null && to != null && from.id.isNotBlank() && to.id.isNotBlank() && from.id != to.id
