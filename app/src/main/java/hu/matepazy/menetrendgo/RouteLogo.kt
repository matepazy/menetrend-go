package hu.matepazy.menetrendgo

import org.json.JSONObject

/** MÁV's route mark and its own color pair, independent of map-marker colors.
 * Public OTP route.shortName/color/textColor, verified 2026-10-25.
 * Only decode the MNR2007 numeric entity; provider HTML is never rendered.
 */
data class RouteLogo(val code: Int, val foreground: String, val background: String) {
    val glyph: String get() = String(Character.toChars(code))

    companion object {
        private val mark = Regex("""<span\s+class=["']MNR2007["']\s*>\s*&#(\d+);\s*</span>""")
        private fun color(value: String): String? = value.removePrefix("#")
            .takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }

        fun parse(route: JSONObject): RouteLogo? {
            val source = route.optString("logoShortName").ifBlank { route.optString("shortName") }
            val code = mark.matchEntire(source.trim())?.groupValues?.get(1)?.toIntOrNull() ?: return null
            if(code !in 33..0x10ffff || code in 0xd800..0xdfff) return null
            return RouteLogo(code, color(route.optString("textColor")) ?: return null,
                color(route.optString("color")) ?: return null)
        }
    }
}
