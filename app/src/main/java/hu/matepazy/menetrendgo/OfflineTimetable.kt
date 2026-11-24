package hu.matepazy.menetrendgo

import java.io.*
import java.time.LocalDate
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** One feed's compacted timetable, the only offline representation kept on the device.
 *
 * Trips running the same stop sequence (with the same boarding rules) share a pattern, and a pattern's
 * trips never overtake each other, so its trips are sorted at every stop (what RAPTOR needs).
 * Times are whole minutes after the service day's midnight (16-bit) and may exceed 1440. Service days are
 * bits of [tripDays] counted from [firstDay]; anything outside the window was dropped while compacting.
 * Text lives once in [strings]; every name and id array holds indexes into it (-1 = none). */
class OfflineTimetable(
    val feedId: String, val feedName: String, val firstDay: LocalDate, val dayCount: Int,
    val builtAt: Long, val sourceUpdated: Long,
    val strings: Array<String>,
    val agencyName: IntArray,
    val routeId: IntArray, val routeShort: IntArray, val routeLong: IntArray, val routeType: IntArray,
    val routeColor: IntArray, val routeTextColor: IntArray, val routeAgency: IntArray,
    val stopId: IntArray, val stopName: IntArray, val stopLat: IntArray, val stopLon: IntArray,
    val stopPlatform: IntArray, val stopParent: IntArray,
    val patternRoute: IntArray, val patternStopStart: IntArray, val patternStops: IntArray,
    /** Per pattern stop: bit 0 = no boarding, bit 1 = no alighting. */
    val patternStopFlags: ByteArray,
    val patternTripStart: IntArray,
    val tripId: IntArray, val tripHeadsign: IntArray, val tripShortName: IntArray, val tripDays: LongArray,
    /** Offset of the trip's (arrival, departure) pairs in [times]. */
    val tripTimes: IntArray, val times: ShortArray,
) {
    val lastDay: LocalDate get() = firstDay.plusDays(dayCount - 1L)
    /** The last day any kept trip runs; publishers often cover less than the whole window. */
    val lastServiceDay: LocalDate? get() {
        var mask = 0L; tripDays.forEach { mask = mask or it }
        return if (mask == 0L) null else firstDay.plusDays(63L - java.lang.Long.numberOfLeadingZeros(mask))
    }
    val stopCount get() = stopId.size
    val patternCount get() = patternRoute.size
    val tripCount get() = tripId.size
    fun text(index: Int): String = if (index < 0) "" else strings[index]
    fun patternSize(pattern: Int) = patternStopStart[pattern + 1] - patternStopStart[pattern]
    fun patternStop(pattern: Int, index: Int) = patternStops[patternStopStart[pattern] + index]
    fun canBoard(pattern: Int, index: Int) = patternStopFlags[patternStopStart[pattern] + index].toInt() and 1 == 0 && index < patternSize(pattern) - 1
    fun canAlight(pattern: Int, index: Int) = patternStopFlags[patternStopStart[pattern] + index].toInt() and 2 == 0 && index > 0
    fun arrival(trip: Int, index: Int) = times[tripTimes[trip] + index * 2].toInt()
    fun departure(trip: Int, index: Int) = times[tripTimes[trip] + index * 2 + 1].toInt()
    /** Whether the trip runs on [day]; false outside the downloaded window. */
    fun runsOn(trip: Int, day: LocalDate): Boolean {
        val bit = day.toEpochDay() - firstDay.toEpochDay()
        return bit in 0 until dayCount && tripDays[trip] ushr bit.toInt() and 1L == 1L
    }
    fun mode(route: Int) = gtfsMode(routeType[route])
    fun lat(stop: Int) = stopLat[stop] / 1e6
    fun lon(stop: Int) = stopLon[stop] / 1e6

    // Derived lookups are built on first use, never stored.
    val tripPattern: IntArray by lazy {
        IntArray(tripCount).also { out -> for (p in 0 until patternCount) for (t in patternTripStart[p] until patternTripStart[p + 1]) out[t] = p }
    }
    val stopIndex: Map<String, Int> by lazy { HashMap<String, Int>(stopCount * 2).also { m -> for (s in 0 until stopCount) m[strings[stopId[s]]] = s } }
    val tripIndex: Map<String, Int> by lazy { HashMap<String, Int>(tripCount * 2).also { m -> for (t in 0 until tripCount) m[strings[tripId[t]]] = t } }
    /** CSR index of (pattern, position) pairs serving each stop. */
    private val stopRefs: Pair<IntArray, IntArray> by lazy {
        val start = IntArray(stopCount + 1)
        for (p in 0 until patternCount) for (i in 0 until patternSize(p)) start[patternStop(p, i) + 1]++
        for (s in 0 until stopCount) start[s + 1] += start[s]
        val fill = start.copyOf(); val refs = IntArray(start[stopCount] * 2)
        for (p in 0 until patternCount) for (i in 0 until patternSize(p)) {
            val at = fill[patternStop(p, i)]++; refs[at * 2] = p; refs[at * 2 + 1] = i
        }
        start to refs
    }
    inline fun forEachStopPattern(stop: Int, action: (pattern: Int, index: Int) -> Unit) {
        val (start, refs) = stopPatternRefs()
        for (at in start[stop] until start[stop + 1]) action(refs[at * 2], refs[at * 2 + 1])
    }
    @PublishedApi internal fun stopPatternRefs() = stopRefs
    val routePatterns: Map<Int, List<Int>> by lazy { (0 until patternCount).groupBy { patternRoute[it] } }
    val children: Map<Int, List<Int>> by lazy { (0 until stopCount).filter { stopParent[it] >= 0 }.groupBy { stopParent[it] } }

    fun write(out: OutputStream) {
        val data = DataOutputStream(BufferedOutputStream(DeflaterOutputStream(out, Deflater(Deflater.BEST_COMPRESSION), 1 shl 16), 1 shl 16))
        data.writeInt(MAGIC); data.writeInt(VERSION)
        data.writeUTF(feedId); data.writeUTF(feedName); data.writeLong(firstDay.toEpochDay()); data.writeInt(dayCount)
        data.writeLong(builtAt); data.writeLong(sourceUpdated)
        data.varint(strings.size); strings.forEach { data.writeUTF(it) }
        listOf(agencyName, routeId, routeShort, routeLong, routeType, routeColor, routeTextColor, routeAgency,
            stopId, stopName).forEach { data.ints(it) }
        data.ints(stopLat, delta = true); data.ints(stopLon, delta = true)
        data.ints(stopPlatform); data.ints(stopParent)
        data.ints(patternRoute); data.ints(patternStopStart, delta = true); data.ints(patternStops)
        data.varint(patternStopFlags.size); data.write(patternStopFlags)
        data.ints(patternTripStart, delta = true)
        data.ints(tripId); data.ints(tripHeadsign); data.ints(tripShortName)
        data.varint(tripDays.size); tripDays.forEach { data.writeLong(it) }
        data.ints(tripTimes, delta = true)
        // Times within a trip only grow, so per-trip deltas stay tiny and compress well.
        data.varint(times.size)
        for (t in 0 until tripCount) {
            val from = tripTimes[t]; val to = if (t + 1 < tripCount) tripTimes[t + 1] else times.size
            var previous = 0
            for (i in from until to) { data.varint(zigzag(times[i] - previous)); previous = times[i].toInt() }
        }
        data.flush(); data.close()
    }

    companion object {
        private const val MAGIC = 0x4D47_5454 // "MGTT"
        const val VERSION = 1

        fun read(input: InputStream): OfflineTimetable {
            val data = DataInputStream(BufferedInputStream(InflaterInputStream(input), 1 shl 16))
            check(data.readInt() == MAGIC && data.readInt() == VERSION) { "Ismeretlen offline menetrendfájl." }
            val feedId = data.readUTF(); val feedName = data.readUTF()
            val firstDay = LocalDate.ofEpochDay(data.readLong()); val dayCount = data.readInt()
            val builtAt = data.readLong(); val sourceUpdated = data.readLong()
            val strings = Array(data.varint()) { data.readUTF() }
            val agencyName = data.ints(); val routeId = data.ints(); val routeShort = data.ints(); val routeLong = data.ints()
            val routeType = data.ints(); val routeColor = data.ints(); val routeTextColor = data.ints(); val routeAgency = data.ints()
            val stopId = data.ints(); val stopName = data.ints()
            val stopLat = data.ints(delta = true); val stopLon = data.ints(delta = true)
            val stopPlatform = data.ints(); val stopParent = data.ints()
            val patternRoute = data.ints(); val patternStopStart = data.ints(delta = true); val patternStops = data.ints()
            val flags = ByteArray(data.varint()).also { data.readFully(it) }
            val patternTripStart = data.ints(delta = true)
            val tripId = data.ints(); val tripHeadsign = data.ints(); val tripShortName = data.ints()
            val tripDays = LongArray(data.varint()) { data.readLong() }
            val tripTimes = data.ints(delta = true)
            val times = ShortArray(data.varint())
            for (t in tripTimes.indices) {
                val from = tripTimes[t]; val to = if (t + 1 < tripTimes.size) tripTimes[t + 1] else times.size
                var previous = 0
                for (i in from until to) { previous += unzigzag(data.varint()); times[i] = previous.toShort() }
            }
            return OfflineTimetable(feedId, feedName, firstDay, dayCount, builtAt, sourceUpdated, strings, agencyName,
                routeId, routeShort, routeLong, routeType, routeColor, routeTextColor, routeAgency,
                stopId, stopName, stopLat, stopLon, stopPlatform, stopParent,
                patternRoute, patternStopStart, patternStops, flags, patternTripStart,
                tripId, tripHeadsign, tripShortName, tripDays, tripTimes, times)
        }

        private fun zigzag(v: Int) = (v shl 1) xor (v shr 31)
        private fun unzigzag(v: Int) = (v ushr 1) xor -(v and 1)
        private fun DataOutputStream.varint(value: Int) {
            var v = value
            while (v and 0x7F.inv() != 0) { writeByte(v and 0x7F or 0x80); v = v ushr 7 }
            writeByte(v)
        }
        private fun DataInputStream.varint(): Int {
            var shift = 0; var result = 0
            while (true) {
                val b = readUnsignedByte()
                result = result or (b and 0x7F shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
        private fun DataOutputStream.ints(values: IntArray, delta: Boolean = false) {
            varint(values.size); var previous = 0
            values.forEach { v -> varint(zigzag(if (delta) v - previous else v)); previous = v }
        }
        private fun DataInputStream.ints(delta: Boolean = false): IntArray {
            var previous = 0
            return IntArray(varint()) { val v = unzigzag(varint()); (if (delta) previous + v else v).also { previous = it } }
        }
    }
}
