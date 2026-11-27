package hu.matepazy.menetrendgo

import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

enum class OfflineFeedGroup(val title: String) {
    Rail("Vasút"), Coach("Helyközi autóbusz"), City("Városi közlekedés"), Other("Hajó, komp, sikló és libegő")
}

/** One downloadable Transitous GTFS source. [url] is Transitous' cleaned-up copy; [originalUrl] is the publisher's file. */
data class OfflineFeed(val id: String, val name: String, val group: OfflineFeedGroup, val url: String,
                       val originalUrl: String?, val license: String?,
                       /** For local transport: the town it serves and who runs it, as listed in the town picker. */
                       val town: String = name, val operator: String = "")

/** Hungarian static GTFS sources listed by Transitous (transitous.org/sources), with Hungarian display names.
 * Realtime, bike-share and disabled entries are skipped. Publishers' private download headers are never read. */
object OfflineFeedCatalog {
    const val SOURCE_URL = "https://raw.githubusercontent.com/public-transport/transitous/main/feeds/hu.json"
    const val SOURCES_PAGE = "https://transitous.org/sources/"
    const val BUNDLED_ASSET = "offline/hu-feeds.json"

    fun processedUrl(id: String) = "https://api.transitous.org/gtfs/hu_" + URLEncoder.encode(id, "UTF-8").replace("+", "%20") + ".gtfs.zip"

    /** Community feeds Transitous does not list, downloaded straight from their publishers' releases. */
    val COMMUNITY = listOf(
        OfflineFeed("mor", "Mór helyi járatai", OfflineFeedGroup.City, "https://github.com/matepazy/mor-gtfs/releases/latest/download/mor-gtfs-menetrendgo.zip",
            null, "CC-BY-SA-4.0 AND ODbL-1.0", "Mór", "M-Bussal"),
    )

    /** The Transitous sources plus [COMMUNITY]; empty when the Transitous list has no usable source. */
    fun parse(json: String): List<OfflineFeed> = parseTransitous(json).let { feeds ->
        if (feeds.isEmpty()) feeds else (feeds + COMMUNITY).distinctBy { it.id }
            .sortedWith(compareBy({ it.group.ordinal }, { order(it.id) }, { collator.getCollationKey(it.name) }))
    }

    private fun parseTransitous(json: String): List<OfflineFeed> = JSONObject(json).array("sources").mapNotNull { source ->
        val spec = source.optString("spec").ifBlank { "gtfs" }
        if (spec != "gtfs" || source.optBoolean("skip")) return@mapNotNull null
        val id = source.optString("name").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val type = source.optString("type")
        if (type != "http" && type != "url" && type != "transitland-atlas") return@mapNotNull null
        val plain = type != "transitland-atlas" && !source.has("http-options") && !source.has("function")
        val original = source.optString("url").takeIf { plain && it.startsWith("https://") }
        val (town, operator) = townAndOperator(id, original)
        OfflineFeed(id, displayName(id, original), group(id), processedUrl(id), original,
            source.optJSONObject("license")?.optString("spdx-identifier")?.takeIf { it.isNotBlank() }, town, operator)
    }

    internal val collator: java.text.Collator = java.text.Collator.getInstance(Locale.forLanguageTag("hu"))
    /** Trains and coaches across the country: the one download most riders need. */
    val STARTER = listOf("mav", "gysev", "volan-helykozi")

    private val towns = mapOf(
        "bkk" to ("Budapest" to "BKK"), "budaors" to ("Budaörs" to "Helyi járatok"), "urom" to ("Üröm" to "Helyi járatok"),
        "dkv" to ("Debrecen" to "DKV"), "mvk" to ("Miskolc" to "MVK"), "szkt" to ("Szeged" to "SZKT, villamos és troli"),
        "vbusz" to ("Veszprém" to "V-Busz"), "tukebusz" to ("Pécs" to "Tüke Busz"), "blaguss-agora" to ("Szombathely" to "Blaguss Agora"),
        "paksbusz" to ("Paks" to "Helyi járatok"), "kiskunhalas" to ("Kiskunhalas" to "Helyi járatok"), "oroshaza" to ("Orosháza" to "Helyi járatok"),
    )
    internal fun townAndOperator(id: String, originalUrl: String?): Pair<String, String> {
        towns[id.lowercase()]?.let { return it }
        if (id.startsWith("volan-") && id != "volan-helykozi") return displayName(id, originalUrl).substringBefore(" helyi") to "Volánbusz"
        val name = displayName(id, originalUrl)
        return name.substringBefore(" helyi járatai") to (if (name.endsWith(" helyi járatai")) "Helyi járatok" else "")
    }
    private fun order(id: String) = listOf("mav", "volan-helykozi", "bkk", "gysev").indexOf(id).let { if (it < 0) 99 else it }

    internal fun group(id: String): OfflineFeedGroup {
        val key = id.lowercase()
        return when {
            key in setOf("mav", "gysev", "mav-gyermekvasut", "kecskemeti-kisvasut") -> OfflineFeedGroup.Rail
            key == "volan-helykozi" -> OfflineFeedGroup.Coach
            "ferry" in key || "funicular" in key || "chairlift" in key || key == "bahart" -> OfflineFeedGroup.Other
            else -> OfflineFeedGroup.City
        }
    }

    private val names = mapOf(
        "mav" to "MÁV-csoport vonatai", "gysev" to "GYSEV vonatai", "mav-gyermekvasut" to "Gyermekvasút",
        "kecskemeti-kisvasut" to "Kecskeméti kisvasút", "volan-helykozi" to "Volánbusz helyközi járatai",
        "bkk" to "Budapest (BKK)", "bkk-funicular" to "Budavári Sikló", "bkk-chairlift" to "Zugligeti libegő",
        "budaors" to "Budaörs helyi járatai", "urom" to "Üröm helyi járatai", "dunakeszi-ferry" to "Dunakeszi komp",
        "pasztor-ferry" to "Pásztor komp", "dkv" to "Debrecen (DKV)", "mvk" to "Miskolc (MVK)", "szkt" to "Szeged (SZKT)",
        "vbusz" to "Veszprém (V-Busz)", "tukebusz" to "Pécs (Tüke Busz)", "blaguss-agora" to "Szombathely (Blaguss Agora)",
        "paksbusz" to "Paks helyi járatai", "bahart" to "Balatoni hajójáratok (BAHART)", "bahart-ferry" to "Szántód–Tihany komp (BAHART)",
        "kiskunhalas" to "Kiskunhalas helyi járatai", "oroshaza" to "Orosháza helyi járatai",
    )

    /** Curated name, else the town named in the publisher's file name (it keeps the accents), else the id itself. */
    internal fun displayName(id: String, originalUrl: String?): String {
        names[id.lowercase()]?.let { return it }
        if (id.startsWith("volan-")) {
            val town = originalUrl?.let { URLDecoder.decode(it, "UTF-8") }?.substringAfterLast("_-_", "")
                ?.substringBefore(".gtfs")?.substringBefore("_hkir")?.takeIf { it.isNotBlank() }
                ?: id.removePrefix("volan-")
            return "${capitalize(town)} helyi járatai (Volánbusz)"
        }
        val words = id.replace('-', ' ')
        return if (words.any { it.isUpperCase() } || words.any { it.code > 127 }) "${capitalize(words)} helyi járatai" else capitalize(words)
    }

    private fun capitalize(value: String) = value.split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.titlecase(Locale.forLanguageTag("hu")) }
    }
}

private val offlineMonths = listOf("jan.", "febr.", "márc.", "ápr.", "máj.", "jún.", "júl.", "aug.", "szept.", "okt.", "nov.", "dec.")

/** "850 kB", "1,9 MB", "50 MB": Hungarian decimal comma, one decimal below 10 MB. */
internal fun offlineBytesLabel(bytes: Long): String = when {
    bytes < 1_000_000 -> "${((bytes + 999) / 1000).coerceAtLeast(1)} kB"
    bytes < 10_000_000 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1e6).replace('.', ',')
    else -> "${(bytes + 500_000) / 1_000_000} MB"
}

internal fun offlineDayLabel(day: java.time.LocalDate) = "${offlineMonths[day.monthValue - 1]} ${day.dayOfMonth}."

/** How long a downloaded timetable lasts, and whether it needs attention. */
internal fun offlineValidity(lastServiceDay: java.time.LocalDate, today: java.time.LocalDate): Pair<String, Boolean> = when {
    lastServiceDay.isBefore(today) -> "Lejárt: ${offlineDayLabel(lastServiceDay)} óta nincs benne járat" to true
    !lastServiceDay.isAfter(today.plusDays(3)) -> "Hamarosan lejár: ${offlineDayLabel(lastServiceDay)}" to true
    else -> "Érvényes: ${offlineMonths[lastServiceDay.monthValue - 1]} ${lastServiceDay.dayOfMonth}-ig" to false
}
