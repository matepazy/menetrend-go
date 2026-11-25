package hu.matepazy.menetrendgo

import java.util.Locale

/** Offline GTFS has no MNR2007 marks, so rail legs would show bare text ("SZ 4421") where MÁVPlusz shows its mark.
 * The marks below reuse MÁVPlusz's own code points and colour pairs (route.shortName/textColor/color, verified 2026-11-25);
 * the glyphs come from the app's own pictogram font. A line mark (S10, G43, Z30, H5; MÁV puts it in route_long_name) wins over the train category,
 * as it does online. Unknown names keep the text label rather than guessing. */
object OfflineRouteMarks {
    private const val WHITE = "FEFEFE"
    private const val BLUE = "00A0E3"; private const val NAVY = "274F96"; private const val RED = "C82227"; private const val GREEN = "29803C"
    private const val LIME = "B0CB1F"; private const val YELLOW = "FECC00"

    /** Category mark, its Hungarian service name, and its foreground on a light ground. */
    private data class Category(val code: Int, val name: String, val foreground: String)
    private val categories: Map<String, Category> = buildMap {
        fun put(c: Category, vararg names: String) = names.forEach { put(it, c) }
        put(Category(484, "Személyvonat", BLUE), "sz", "személyvonat")
        put(Category(474, "InterCity", NAVY), "ic", "intercity")
        put(Category(473, "Gyorsvonat", RED), "gy", "gyorsvonat")
        put(Category(482, "Sebesvonat", GREEN), "s", "sv", "sebesvonat")
        put(Category(477, "InterRégió", GREEN), "ir", "interrégió", "interregio")
        put(Category(472, "Expresszvonat", RED), "ex", "expressz", "expresszvonat")
        put(Category(467, "EuroCity", NAVY), "ec", "eurocity")
        put(Category(470, "EuroNight", NAVY), "en", "euronight")
        put(Category(471, "EuroRegio", BLUE), "er", "euroregio")
        put(Category(480, "railjet", NAVY), "rj", "railjet")
        put(Category(481, "railjet xpress", NAVY), "rjx")
    }
    private val replacement = Category(536, "Vonatpótló autóbusz", "FFFFFF")
    private val replacementNames = setOf("pótló", "autóbusz", "busz", "vonatpótló")

    private val lines: Map<String, Int> = mapOf(
        "S10" to 335, "S12" to 350, "S21" to 352, "S30" to 354, "S36" to 357, "S40" to 358, "S42" to 359, "S50" to 360,
        "S60" to 362, "S70" to 363, "S71" to 364, "S72" to 365, "S74" to 543, "S76" to 446, "S77" to 508, "S80" to 366,
        "S150" to 368, "S210" to 369, "S220" to 370, "S225" to 513, "S431" to 372, "S440" to 374, "S447" to 605,
        "S750" to 376, "S780" to 377, "S820" to 379, "S889" to 677,
        "G10" to 387, "G30" to 400, "G40" to 399, "G43" to 390, "G44" to 401, "G50" to 411, "G60" to 391, "G70" to 392,
        "G71" to 393, "G80" to 412,
        "Z30" to 383, "Z42" to 525, "Z50" to 384, "Z60" to 385, "Z70" to 386, "Z72" to 417,
        "IR85" to 541, "IR87" to 542,
        "H5" to 440, "H6" to 441, "H7" to 442, "H8" to 443, "H9" to 444)
    private val hev = mapOf("H5" to "821066", "H6" to "814F26", "H7" to "F47D35", "H8" to "ED677E", "H9" to "F27689")

    private fun lineMark(routeShort: String): RouteLogo? {
        val name = routeShort.trim().uppercase(Locale.ROOT)
        val code = lines[name] ?: return null
        return when (name.first()) {
            'Z' -> RouteLogo(code, "000000", YELLOW)
            'G' -> RouteLogo(code, LIME, "FFFFFF")
            'H' -> RouteLogo(code, "FFFFFF", hev.getValue(name))
            'I' -> RouteLogo(code, GREEN, "FFFFFF")
            else -> RouteLogo(code, BLUE, "FFFFFF")
        }
    }

    /** The category word of a GTFS trip name such as "SZ 4421", "InterCity 518" or "Pótló 162/165". */
    private fun categoryWord(tripName: String): String =
        tripName.trim().substringBefore(' ').takeIf { tripName.trim().contains(' ') }.orEmpty().lowercase(Locale.ROOT)

    private fun category(mode: String, tripName: String, routeLong: String = ""): Category? {
        val word = categoryWord(tripName).ifEmpty { categoryWord(routeLong) }
        return when (mode) {
            "RAIL" -> categories[word]
            "RAIL_REPLACEMENT_BUS" -> replacement.takeIf { word in replacementNames }
            else -> null
        }
    }

    /** The route mark to draw for an offline ride, or null to keep its text label. */
    fun logo(mode: String, routeShort: String, routeLong: String, tripName: String): RouteLogo? {
        if (mode != "RAIL" && mode != "SUBURBAN_RAILWAY" && mode != "RAIL_REPLACEMENT_BUS") return null
        lineMark(routeShort)?.let { return it }
        lineMark(routeLong)?.let { return it }
        val category = category(mode, tripName, routeLong) ?: return null
        return RouteLogo(category.code, category.foreground, if (category === replacement) "C70078" else WHITE)
    }

    /** The Hungarian service name ("Személyvonat") for screen readers and details, or "" when unknown. */
    fun serviceName(mode: String, routeLong: String, tripName: String): String = category(mode, tripName, routeLong)?.name.orEmpty()
}
