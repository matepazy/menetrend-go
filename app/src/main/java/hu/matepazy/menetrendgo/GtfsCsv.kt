package hu.matepazy.menetrendgo

import java.io.Reader

/** Streaming RFC 4180 reader for GTFS text files: one row in memory at a time, columns found by header name.
 * GTFS publishers reorder columns freely, so every lookup goes through the header. */
internal class GtfsCsv(source: Reader) {
    private val reader: Reader = if (source.markSupported()) source else source.buffered(1 shl 16)
    private val fields = ArrayList<String>(16)
    private val field = StringBuilder(64)
    private var eof = false
    val header: Map<String, Int>

    init {
        header = if (readRow()) fields.mapIndexed { i, name -> name.trim().trimStart { it.code == 0xFEFF } to i }.toMap() else emptyMap()
    }

    fun column(name: String): Int = header[name] ?: -1
    /** Advance to the next non-empty row; false at end of file. */
    fun next(): Boolean {
        while (readRow()) if (fields.size > 1 || fields[0].isNotEmpty()) return true
        return false
    }
    operator fun get(column: Int): String = if (column in 0 until fields.size) fields[column] else ""

    private fun readRow(): Boolean {
        if (eof) return false
        fields.clear(); field.setLength(0)
        var quoted = false
        var any = false
        while (true) {
            val c = reader.read()
            if (c < 0) {
                eof = true
                if (!any && fields.isEmpty()) return false
                fields += field.toString(); return true
            }
            any = true
            val ch = c.toChar()
            if (quoted) {
                if (ch == '"') {
                    reader.mark(1)
                    if (reader.read() == '"'.code) field.append('"') else { reader.reset(); quoted = false }
                } else field.append(ch)
                continue
            }
            when (ch) {
                '"' -> quoted = true
                ',' -> { fields += field.toString(); field.setLength(0) }
                '\r' -> {}
                '\n' -> { fields += field.toString(); return true }
                else -> field.append(ch)
            }
        }
    }
}

/** GTFS "HH:MM:SS" (hours may exceed 24) → whole minutes after the service day's midnight; -1 when blank. */
internal fun gtfsMinutes(value: String): Int {
    val text = value.trim()
    if (text.isEmpty()) return -1
    val first = text.indexOf(':'); val second = text.indexOf(':', first + 1)
    if (first <= 0) return -1
    val hours = text.substring(0, first).toIntOrNull() ?: return -1
    val minutes = text.substring(first + 1, if (second < 0) text.length else second).toIntOrNull() ?: return -1
    return hours * 60 + minutes
}

/** GTFS route_type (basic and extended) → the MÁVPlusz/OTP mode names the rest of the app uses. */
internal fun gtfsMode(type: Int): String = ModeMapping.fromGtfsRouteType(type).otp
