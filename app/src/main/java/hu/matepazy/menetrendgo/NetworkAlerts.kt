package hu.matepazy.menetrendgo

import org.json.JSONObject
import java.util.Locale

/** A line named by a notice, with its provider colours (hex without '#', may be blank). */
data class AlertLine(val name: String, val mode: String, val color: String = "", val textColor: String = "")

/** A network-wide notice: one provider alert with the operator, effect and the lines and stations it names. */
data class NetworkAlert(val alert: MavAlert, val operator: String, val effect: String,
    val lines: List<AlertLine> = emptyList(), val stations: List<String> = emptyList()) {
    val id get() = alert.id
    val operatorName get() = operatorName(operator)
    val effectLabel get() = effectLabels[effect]
    fun upcoming(now: Long) = (alert.startSeconds ?: 0) * 1000 > now

    companion object {
        private val effectLabels = mapOf("NO_SERVICE" to "Nem közlekedik", "REDUCED_SERVICE" to "Korlátozott közlekedés",
            "SIGNIFICANT_DELAYS" to "Késések", "DETOUR" to "Terelés", "STOP_MOVED" to "Megállóáthelyezés",
            "ADDITIONAL_SERVICE" to "Többletjárat", "MODIFIED_SERVICE" to "Módosított menetrend",
            "ACCESSIBILITY_ISSUE" to "Akadálymentesség")
        private val operatorOrder = listOf("1", "BKK", "VBUSZ")

        fun operatorName(feed: String) = when (feed) {
            "1" -> "MÁV"
            "BKK" -> "BKK"
            "VBUSZ" -> "Volánbusz"
            else -> feed.uppercase(Locale.ROOT)
        }

        /** MÁV attaches a delay reason to every late train; those belong to the journey, not the network list. */
        fun parse(o: JSONObject): NetworkAlert? {
            val entities = o.array("entities")
            if (entities.isNotEmpty() && entities.all { it.optString("__typename") == "Trip" }) return null
            val alert = MavAlert.parse(o) ?: return null
            val lines = entities.mapNotNull { e -> (e.optJSONObject("route") ?: e.takeIf { it.optString("__typename") == "Route" }) }
                .mapNotNull { r -> r.optString("shortName").trim().takeIf { it.isNotBlank() && it != "null" }
                    ?.let { AlertLine(hungarianServiceLabel(it), r.optString("mode"), r.hex("color"), r.hex("textColor")) } }
                .distinctBy { it.name }.sortedWith(compareBy(lineOrder) { it.name })
            val stations = entities.mapNotNull { e -> (e.optJSONObject("stop") ?: e.takeIf { it.optString("__typename") == "Stop" }) }
                .mapNotNull { s -> s.optString("name").trim().takeIf { it.isNotBlank() && it != "null" } }.distinct()
            // BKK's Hungarian headers are sentence fragments ("autóbuszos pótlás …").
            val header = alert.header.trim().replaceFirstChar { it.titlecase(Locale.forLanguageTag("hu")) }
            return NetworkAlert(alert.copy(header = header), o.optString("feed"),
                o.optString("alertEffect"), lines, stations)
        }

        /** Current and planned notices, one per text: BKK repeats a notice per segment and per weekend, so the
         *  earliest period stands for the rest. Current notices come first, then by operator, severity and recency. */
        fun parseAll(data: JSONObject, now: Long): List<NetworkAlert> =
            data.array("alerts").mapNotNull(::parse).filter { it.alert.endSeconds == null || it.alert.endSeconds * 1000 >= now }
                .sortedBy { it.alert.startSeconds ?: 0 }
                .groupBy { it.operator to plainAlertText(it.alert.title) + "|" + plainAlertText(it.alert.body) }.values
                .map { same -> same.first().copy(lines = same.flatMap { it.lines }.distinctBy { it.name }.sortedWith(compareBy(lineOrder) { it.name }),
                    stations = same.flatMap { it.stations }.distinct()) }
                .sortedWith(compareBy<NetworkAlert> { it.upcoming(now) }
                    .thenBy { operatorOrder.indexOf(it.operator).let { i -> if (i < 0) operatorOrder.size else i } }
                    .thenByDescending { it.alert.warning }
                    .thenComparator { a, b -> if (a.upcoming(now)) compareValues(a.alert.startSeconds, b.alert.startSeconds)
                        else compareValues(b.alert.startSeconds, a.alert.startSeconds) })

        private fun JSONObject.hex(key: String) = optString(key).removePrefix("#").takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }.orEmpty()
        /** Natural order: M2 before M4, 4 before 49, 7 before 7E. */
        private val lineOrder = Comparator<String> { a, b ->
            fun key(s: String) = Regex("^(\\D*)(\\d*)(.*)$").find(s)!!.destructured
            val (pa, na, ra) = key(a); val (pb, nb, rb) = key(b)
            compareValuesBy(pa to na, pb to nb, { it.first }, { it.second.toIntOrNull() ?: Int.MAX_VALUE }).takeIf { it != 0 } ?: ra.compareTo(rb)
        }
    }
}

/** Operator tabs with counts, in the list's own order. */
internal fun networkAlertOperators(alerts: List<NetworkAlert>): List<Pair<String, Int>> =
    alerts.groupingBy { it.operator }.eachCount().toList().sortedBy { (op, _) -> alerts.indexOfFirst { it.operator == op } }
