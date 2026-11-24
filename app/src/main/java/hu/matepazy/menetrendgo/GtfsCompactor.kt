package hu.matepazy.menetrendgo

import java.io.File
import java.io.InputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

/** Named GTFS text files; null when the file is absent. */
fun interface GtfsSource { fun open(name: String): InputStream? }

fun zipGtfsSource(zip: ZipFile) = GtfsSource { name ->
    zip.getEntry(name)?.let(zip::getInputStream)
        ?: zip.entries().asSequence().firstOrNull { it.name.substringAfterLast('/') == name }?.let(zip::getInputStream)
}

/** Turns a raw GTFS feed into an [OfflineTimetable] for a rolling window of service days.
 *
 * Only trips running inside the window survive, together with the stops, routes and agencies they use.
 * Shapes, fares, transfers and every unused column are dropped; stop_times is streamed and never
 * held as text. [progress] receives 0..1 while the large stop_times file is read. */
object GtfsCompactor {
    /** Yesterday is kept so trips running past midnight still show this morning. */
    const val PAST_DAYS = 1
    const val WINDOW_DAYS = 30

    fun compact(source: GtfsSource, feedId: String, feedName: String, today: LocalDate,
                sourceUpdated: Long = 0L, days: Int = WINDOW_DAYS, stopTimesBytes: Long = 0L,
                progress: (Float) -> Unit = {}): OfflineTimetable {
        val firstDay = today.minusDays(PAST_DAYS.toLong())
        val dayCount = (days + PAST_DAYS + 1).coerceAtMost(63)
        val strings = StringTable()

        fun csv(name: String, required: Boolean = true): GtfsCsv? {
            val stream = source.open(name) ?: if (required) throw IllegalStateException("A menetrendcsomagból hiányzik: $name") else return null
            return GtfsCsv(stream.reader(Charsets.UTF_8))
        }

        // Agencies
        val agencyIds = HashMap<String, Int>(); val agencyNames = ArrayList<Int>()
        csv("agency.txt", required = false)?.run {
            val id = column("agency_id"); val name = column("agency_name")
            while (next()) { agencyIds[this[id]] = agencyNames.size; agencyNames += strings.of(this[name].trim()) }
        }

        // Services → day bit masks
        val services = HashMap<String, Long>()
        val dateFormat = DateTimeFormatter.BASIC_ISO_DATE
        fun bit(date: LocalDate): Int = (date.toEpochDay() - firstDay.toEpochDay()).toInt()
        csv("calendar.txt", required = false)?.run {
            val id = column("service_id"); val start = column("start_date"); val end = column("end_date")
            val weekday = DayOfWeek.entries.map { column(it.name.lowercase()) }
            while (next()) {
                val from = runCatching { LocalDate.parse(this[start].trim(), dateFormat) }.getOrNull() ?: continue
                val to = runCatching { LocalDate.parse(this[end].trim(), dateFormat) }.getOrNull() ?: continue
                var mask = 0L
                for (b in 0 until dayCount) {
                    val day = firstDay.plusDays(b.toLong())
                    if (!day.isBefore(from) && !day.isAfter(to) && this[weekday[day.dayOfWeek.ordinal]].trim() == "1") mask = mask or (1L shl b)
                }
                services[this[id]] = mask
            }
        }
        csv("calendar_dates.txt", required = false)?.run {
            val id = column("service_id"); val date = column("date"); val type = column("exception_type")
            while (next()) {
                val day = runCatching { LocalDate.parse(this[date].trim(), dateFormat) }.getOrNull() ?: continue
                val b = bit(day)
                if (b !in 0 until dayCount) { services.putIfAbsent(this[id], 0L); continue }
                val current = services[this[id]] ?: 0L
                services[this[id]] = if (this[type].trim() == "2") current and (1L shl b).inv() else current or (1L shl b)
            }
        }

        // Routes
        class RouteRow(val id: Int, val short: Int, val long: Int, val type: Int, val color: Int, val text: Int, val agency: Int)
        val routes = HashMap<String, Int>(); val routeRows = ArrayList<RouteRow>()
        csv("routes.txt")!!.run {
            val id = column("route_id"); val short = column("route_short_name"); val long = column("route_long_name")
            val type = column("route_type"); val color = column("route_color"); val text = column("route_text_color"); val agency = column("agency_id")
            while (next()) {
                routes[this[id]] = routeRows.size
                routeRows += RouteRow(strings.of(this[id]), strings.optional(this[short]), strings.optional(this[long]),
                    this[type].trim().toIntOrNull() ?: 3, hexColor(this[color]), hexColor(this[text]),
                    agencyIds[this[agency]] ?: if (agencyNames.size == 1) 0 else -1)
            }
        }

        // Trips that run in the window
        class TripRow(val id: String, val route: Int, val days: Long, val headsign: Int, val shortName: Int)
        val tripIndex = HashMap<String, Int>(); val tripRows = ArrayList<TripRow>()
        csv("trips.txt")!!.run {
            val id = column("trip_id"); val route = column("route_id"); val service = column("service_id")
            val headsign = column("trip_headsign"); val short = column("trip_short_name")
            while (next()) {
                val days = services[this[service]] ?: 0L
                val routeAt = routes[this[route]] ?: continue
                if (days == 0L) continue
                tripIndex[this[id]] = tripRows.size
                tripRows += TripRow(this[id], routeAt, days, strings.optional(this[headsign]), strings.optional(this[short]))
            }
        }
        check(tripRows.isNotEmpty()) { "A csomagban nincs járat a következő $days napra." }

        // Stops
        class StopRow(val id: String, val name: String, val lat: Int, val lon: Int, val platform: String, val parent: String)
        val stopIndexById = HashMap<String, Int>(); val stopRows = ArrayList<StopRow>()
        csv("stops.txt")!!.run {
            val id = column("stop_id"); val name = column("stop_name"); val lat = column("stop_lat"); val lon = column("stop_lon")
            val platform = column("platform_code"); val parent = column("parent_station"); val type = column("location_type")
            while (next()) {
                val kind = this[type].trim().ifEmpty { "0" }
                if (kind != "0" && kind != "1") continue
                stopIndexById[this[id]] = stopRows.size
                stopRows += StopRow(this[id], this[name].trim(), ((this[lat].toDoubleOrNull() ?: 0.0) * 1e6).toInt(),
                    ((this[lon].toDoubleOrNull() ?: 0.0) * 1e6).toInt(), this[platform].trim(), this[parent].trim())
            }
        }

        // stop_times, one trip at a time. Feeds are nearly always grouped by trip; when one is not,
        // the file is read a second time and every row is grouped first, at a higher memory cost.
        class Pattern(val route: Int, val stops: IntArray, val flags: ByteArray) {
            override fun equals(other: Any?) = other is Pattern && route == other.route && stops.contentEquals(other.stops) && flags.contentEquals(other.flags)
            override fun hashCode() = (route * 31 + stops.contentHashCode()) * 31 + flags.contentHashCode()
        }
        class BuiltTrip(val row: Int, val times: ShortArray)
        val patterns = LinkedHashMap<Pattern, MutableList<BuiltTrip>>()
        val rows = TripRows()
        /** Orders the buffered rows of trip [t] by stop_sequence and files the trip under its pattern. */
        fun finish(t: Int) {
            val n = rows.size
            if (n < 2) return
            val order = rows.sortedBySequence()
            val times = IntArray(n * 2)
            for (i in 0 until n) { times[i * 2] = rows.arr[order[i]]; times[i * 2 + 1] = rows.dep[order[i]] }
            if (!interpolate(times) || times.last() > Short.MAX_VALUE) return
            val pattern = Pattern(tripRows[t].route, IntArray(n) { rows.stop[order[it]] }, ByteArray(n) { rows.flags[order[it]] })
            patterns.getOrPut(pattern) { mutableListOf() } += BuiltTrip(t, ShortArray(n * 2) { times[it].toShort() })
        }
        fun readStopTimes(onRow: (trip: Int, seq: Int, stop: Int, arr: Int, dep: Int, flags: Int) -> Boolean) {
            val counting = CountingStream(source.open("stop_times.txt") ?: throw IllegalStateException("A menetrendcsomagból hiányzik: stop_times.txt"))
            counting.use {
                GtfsCsv(counting.reader(Charsets.UTF_8)).run {
                    val trip = column("trip_id"); val arr = column("arrival_time"); val dep = column("departure_time")
                    val stop = column("stop_id"); val seq = column("stop_sequence"); val pickup = column("pickup_type"); val drop = column("drop_off_type")
                    var lastTripText = ""; var lastTrip = -1; var lines = 0
                    while (next()) {
                        if (++lines and 0x3FFF == 0 && stopTimesBytes > 0) progress((counting.count.toFloat() / stopTimesBytes).coerceIn(0f, 1f))
                        val tripText = this[trip]
                        if (tripText != lastTripText) { lastTripText = tripText; lastTrip = tripIndex[tripText] ?: -1 }
                        if (lastTrip < 0) continue
                        val stopAt = stopIndexById[this[stop]] ?: continue
                        val a = gtfsMinutes(this[arr]); val d = gtfsMinutes(this[dep])
                        val flags = (if (this[pickup].trim() == "1") 1 else 0) or (if (this[drop].trim() == "1") 2 else 0)
                        if (!onRow(lastTrip, this[seq].trim().toIntOrNull() ?: 0, stopAt, if (a < 0) d else a, if (d < 0) a else d, flags)) return
                    }
                }
            }
        }
        val finished = BooleanArray(tripRows.size)
        var current = -1; var grouped = true
        readStopTimes { trip, seq, stop, arr, dep, flags ->
            if (trip != current) {
                if (current >= 0) { finish(current); finished[current] = true }
                if (finished[trip]) { grouped = false; return@readStopTimes false }
                current = trip; rows.clear()
            }
            rows.add(seq, stop, arr, dep, flags); true
        }
        if (grouped && current >= 0) finish(current)
        if (!grouped) {
            patterns.clear()
            val all = Array(tripRows.size) { TripRows(4) }
            readStopTimes { trip, seq, stop, arr, dep, flags -> all[trip].add(seq, stop, arr, dep, flags); true }
            for (t in tripRows.indices) { rows.copyFrom(all[t]); all[t] = TripRows(0); finish(t) }
        }
        progress(1f)
        check(patterns.isNotEmpty()) { "A csomag járataihoz nincs használható megállóadat." }

        // Split patterns so trips never overtake each other, then lay everything out.
        val usedStops = BooleanArray(stopRows.size); val usedRoutes = BooleanArray(routeRows.size)
        val outPatterns = ArrayList<Pair<Pattern, List<BuiltTrip>>>()
        for ((pattern, trips) in patterns) {
            trips.sortWith(compareBy({ it.times[1] }, { it.times.last() }))
            val lanes = ArrayList<MutableList<BuiltTrip>>()
            for (trip in trips) {
                val lane = lanes.firstOrNull { lane -> lane.last().times.let { prev -> prev.indices.all { prev[it] <= trip.times[it] } } }
                if (lane != null) lane += trip else lanes += mutableListOf(trip)
            }
            lanes.forEach { outPatterns += pattern to it }
            pattern.stops.forEach { usedStops[it] = true }
            usedRoutes[pattern.route] = true
        }
        // Keep parents of used platforms so a station still groups its stops.
        for (s in stopRows.indices) if (usedStops[s]) stopIndexById[stopRows[s].parent]?.let { usedStops[it] = true }
        val stopMap = IntArray(stopRows.size) { -1 }; var stopOut = 0
        for (s in stopRows.indices) if (usedStops[s]) stopMap[s] = stopOut++
        val routeMap = IntArray(routeRows.size) { -1 }; var routeOut = 0
        for (r in routeRows.indices) if (usedRoutes[r]) routeMap[r] = routeOut++
        val keptStops = stopRows.indices.filter { usedStops[it] }
        val keptRoutes = routeRows.indices.filter { usedRoutes[it] }

        val patternStopStart = IntArray(outPatterns.size + 1)
        val patternTripStart = IntArray(outPatterns.size + 1)
        outPatterns.forEachIndexed { p, (pattern, trips) ->
            patternStopStart[p + 1] = patternStopStart[p] + pattern.stops.size
            patternTripStart[p + 1] = patternTripStart[p] + trips.size
        }
        val patternStops = IntArray(patternStopStart.last()); val patternFlags = ByteArray(patternStopStart.last())
        val tripCount = patternTripStart.last()
        val tripTimes = IntArray(tripCount); val outTrips = ArrayList<BuiltTrip>(tripCount)
        var timeCount = 0
        outPatterns.forEachIndexed { p, (pattern, trips) ->
            pattern.stops.forEachIndexed { i, s -> patternStops[patternStopStart[p] + i] = stopMap[s] }
            pattern.flags.copyInto(patternFlags, patternStopStart[p])
            trips.forEach { tripTimes[outTrips.size] = timeCount; timeCount += it.times.size; outTrips += it }
        }
        val times = ShortArray(timeCount)
        outTrips.forEachIndexed { t, trip -> trip.times.copyInto(times, tripTimes[t]) }

        fun routes(field: (RouteRow) -> Int) = IntArray(keptRoutes.size) { field(routeRows[keptRoutes[it]]) }
        fun stops(field: (StopRow) -> Int) = IntArray(keptStops.size) { field(stopRows[keptStops[it]]) }
        fun trips(field: (TripRow) -> Int) = IntArray(tripCount) { field(tripRows[outTrips[it].row]) }
        return OfflineTimetable(
            feedId = feedId, feedName = feedName, firstDay = firstDay, dayCount = dayCount,
            builtAt = System.currentTimeMillis(), sourceUpdated = sourceUpdated,
            agencyName = agencyNames.toIntArray(),
            routeId = routes { it.id }, routeShort = routes { it.short }, routeLong = routes { it.long }, routeType = routes { it.type },
            routeColor = routes { it.color }, routeTextColor = routes { it.text }, routeAgency = routes { it.agency },
            stopId = stops { strings.of(it.id) }, stopName = stops { strings.of(it.name) }, stopLat = stops { it.lat }, stopLon = stops { it.lon },
            stopPlatform = stops { strings.optional(it.platform) },
            stopParent = stops { row -> stopIndexById[row.parent]?.let { stopMap[it] } ?: -1 },
            patternRoute = IntArray(outPatterns.size) { routeMap[outPatterns[it].first.route] }, patternStopStart = patternStopStart,
            patternStops = patternStops, patternStopFlags = patternFlags, patternTripStart = patternTripStart,
            tripId = trips { strings.of(it.id) }, tripHeadsign = trips { it.headsign }, tripShortName = trips { it.shortName },
            tripDays = LongArray(tripCount) { tripRows[outTrips[it].row].days }, tripTimes = tripTimes, times = times,
            // Interned last: the arguments above may still add strings.
            strings = strings.toArray(),
        )
    }

    fun compact(zip: File, feedId: String, feedName: String, today: LocalDate, sourceUpdated: Long = 0L,
                progress: (Float) -> Unit = {}): OfflineTimetable = ZipFile(zip).use { file ->
        val size = file.entries().asSequence().firstOrNull { it.name.substringAfterLast('/') == "stop_times.txt" }?.size ?: 0L
        compact(zipGtfsSource(file), feedId, feedName, today, sourceUpdated, stopTimesBytes = size, progress = progress)
    }

    /** Fill blank intermediate times linearly; false when the first or last time is missing or times run backwards. */
    internal fun interpolate(times: IntArray): Boolean {
        val n = times.size / 2
        if (times[0] < 0 || times[(n - 1) * 2] < 0) return false
        var known = 0
        for (i in 1 until n) {
            if (times[i * 2] < 0) continue
            for (j in known + 1 until i) {
                val v = times[known * 2 + 1] + (times[i * 2] - times[known * 2 + 1]) * (j - known) / (i - known)
                times[j * 2] = v; times[j * 2 + 1] = v
            }
            known = i
        }
        for (i in 0 until n) if (times[i * 2 + 1] < times[i * 2]) times[i * 2 + 1] = times[i * 2]
        for (i in 1 until times.size) if (times[i] < times[i - 1]) return false
        return true
    }

    private fun hexColor(value: String): Int = value.trim().removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16) ?: -1

    private class StringTable {
        private val index = HashMap<String, Int>(); private val values = ArrayList<String>()
        fun of(value: String): Int = index.getOrPut(value) { values += value; values.size - 1 }
        fun optional(value: String): Int = value.trim().let { if (it.isEmpty()) -1 else of(it) }
        fun toArray() = values.toTypedArray()
    }

    /** Growable buffer of one trip's stop_times rows. */
    private class TripRows(capacity: Int = 64) {
        var size = 0; private set
        var seq = IntArray(capacity); var stop = IntArray(capacity); var arr = IntArray(capacity); var dep = IntArray(capacity)
        var flags = ByteArray(capacity)
        fun clear() { size = 0 }
        fun add(seq: Int, stop: Int, arr: Int, dep: Int, flags: Int) {
            if (size == this.seq.size) {
                val grown = maxOf(8, size * 2)
                this.seq = this.seq.copyOf(grown); this.stop = this.stop.copyOf(grown); this.arr = this.arr.copyOf(grown)
                this.dep = this.dep.copyOf(grown); this.flags = this.flags.copyOf(grown)
            }
            this.seq[size] = seq; this.stop[size] = stop; this.arr[size] = arr; this.dep[size] = dep; this.flags[size] = flags.toByte(); size++
        }
        fun copyFrom(other: TripRows) {
            clear(); for (i in 0 until other.size) add(other.seq[i], other.stop[i], other.arr[i], other.dep[i], other.flags[i].toInt())
        }
        /** Row indexes in stop_sequence order (insertion sort: rows are nearly always already in order). */
        fun sortedBySequence(): IntArray {
            val order = IntArray(size) { it }
            for (i in 1 until size) {
                val value = order[i]; var j = i - 1
                while (j >= 0 && seq[order[j]] > seq[value]) { order[j + 1] = order[j]; j-- }
                order[j + 1] = value
            }
            return order
        }
    }

    private class CountingStream(private val input: InputStream) : InputStream() {
        var count = 0L; private set
        override fun read(): Int = input.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = input.read(b, off, len).also { if (it > 0) count += it }
        override fun close() = input.close()
    }
}
