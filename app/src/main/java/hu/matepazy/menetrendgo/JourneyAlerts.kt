package hu.matepazy.menetrendgo

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** A provider disruption notice. Text may contain provider HTML; times are epoch seconds. */
data class MavAlert(val id: String, val header: String, val description: String, val url: String = "",
    val startSeconds: Long? = null, val endSeconds: Long? = null, val warning: Boolean = false) {
    /** MÁV's own feed sends only a description, so it becomes the headline. */
    val title get() = header.ifBlank { description }
    val body get() = if (header.isBlank()) "" else description.takeUnless { plainAlertText(it) == plainAlertText(header) }.orEmpty()

    fun activeDuring(startMillis: Long, endMillis: Long): Boolean =
        (startSeconds == null || startSeconds * 1000 <= endMillis) && (endSeconds == null || endSeconds * 1000 >= startMillis)

    companion object {
        private val disruptiveEffects = setOf("NO_SERVICE", "REDUCED_SERVICE", "SIGNIFICANT_DELAYS", "DETOUR", "STOP_MOVED")

        fun parse(o: JSONObject): MavAlert? {
            fun JSONObject.text(key: String) = if (isNull(key)) "" else optString(key).trim()
            // The default text follows the server locale; prefer an explicit Hungarian translation.
            fun translated(key: String) = o.optJSONArray("${key}Translations")?.objects()
                ?.firstOrNull { it.optString("language").startsWith("hu") }?.text("text")?.takeIf(String::isNotBlank)
                ?: o.text(key)
            val header = translated("alertHeaderText")
            val description = translated("alertDescriptionText")
            if (plainAlertText(header).isBlank() && plainAlertText(description).isBlank()) return null
            fun seconds(key: String) = if (o.isNull(key)) null else o.optLong(key).takeIf { it > 0 }
            val severity = o.text("alertSeverityLevel")
            return MavAlert(o.text("id").ifBlank { "$header|$description" }, header, description,
                o.text("alertUrl").takeIf { it.startsWith("https://") || it.startsWith("http://") }.orEmpty(),
                seconds("effectiveStartDate"), seconds("effectiveEndDate"),
                severity == "WARNING" || severity == "SEVERE" || o.text("alertEffect") in disruptiveEffects)
        }

        /** Leg alerts cover the route and served stops. Trip alerts are merged in too, because
         *  MÁV delay notices start when reported and OTP may not attach them to the leg. */
        fun parseAll(leg: JSONObject, startMillis: Long, endMillis: Long): List<MavAlert> =
            (leg.array("alerts") + leg.optJSONObject("trip")?.array("alerts").orEmpty()).mapNotNull(::parse).filter { it.activeDuring(startMillis, endMillis) }
                .distinctBy { it.id }.distinctBy { plainAlertText(it.title) to plainAlertText(it.body) }
                .sortedByDescending { it.warning }
    }
}

/** Plain text for comparisons and accessibility; the UI renders the original markup. */
internal fun plainAlertText(html: String): String = html
    .replace(Regex("<(br|/p|/li|/div)[^>]*>", RegexOption.IGNORE_CASE), " ")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
    .replace("&lt;", "<").replace("&gt;", ">")
    .replace(Regex("\\s+"), " ").trim()

private val hungarianMonths = listOf("jan.", "febr.", "márc.", "ápr.", "máj.", "jún.", "júl.", "aug.", "szept.", "okt.", "nov.", "dec.")
private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** “Érvényes 14:48–17:57”, adding dates only where they differ from the travel day. */
internal fun alertValidity(alert: MavAlert, travelDay: LocalDate): String? {
    fun zoned(seconds: Long?) = seconds?.let { Instant.ofEpochSecond(it).atZone(Budapest) }
    val start = zoned(alert.startSeconds)
    val end = zoned(alert.endSeconds)
    fun label(time: ZonedDateTime): String {
        val date = time.toLocalDate()
        val day = "${hungarianMonths[date.monthValue - 1]} ${date.dayOfMonth}."
        return when {
            date == travelDay -> time.format(clock)
            // Whole-day notices start at midnight; the date alone reads better.
            date.year == travelDay.year && time.hour == 0 && time.minute == 0 -> day
            date.year == travelDay.year -> "$day ${time.format(clock)}"
            else -> "${date.year}. $day"
        }
    }
    return when {
        start != null && end != null -> "Érvényes ${label(start)} – ${label(end)}"
            .let { if (start.toLocalDate() == travelDay && end.toLocalDate() == travelDay) it.replace(" – ", "–") else it }
        end != null -> "Érvényes eddig: ${label(end)}"
        start != null -> "Érvényes ${label(start)} – visszavonásig"
        else -> null
    }
}
