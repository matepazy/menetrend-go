package hu.matepazy.menetrendgo

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.BitSet
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.*

/** Read-only queries over every downloaded feed: departures, trip timetables, route timetables and journeys.
 * Every answer is scheduled data only and says so (offline departures, journeys and trips with [DataKind.Offline]). */
class OfflineNetwork(val feeds: List<OfflineTimetable>) {
    /** A stop in a specific feed. */
    data class StopRef(val feed: Int, val stop: Int)
    data class RouteRef(val feed: Int, val route: Int)

    private val offsets = IntArray(feeds.size + 1).also { o -> feeds.forEachIndexed { i, f -> o[i + 1] = o[i] + f.stopCount } }
    private val totalStops = offsets.last()
    private fun global(feed: Int, stop: Int) = offsets[feed] + stop
    private fun feedOf(global: Int): Int { var f = 0; while (offsets[f + 1] <= global) f++; return f }
    private fun ref(global: Int) = feedOf(global).let { StopRef(it, global - offsets[it]) }
    private fun latOf(g: Int) = ref(g).let { feeds[it.feed].lat(it.stop) }
    private fun lonOf(g: Int) = ref(g).let { feeds[it.feed].lon(it.stop) }

    val isEmpty get() = feeds.isEmpty()

    // ---- Spatial index (≈550 m cells) and walking transfers ----
    private val grid: Map<Long, IntArray> by lazy {
        val cells = HashMap<Long, MutableList<Int>>()
        for (g in 0 until totalStops) cells.getOrPut(cell(latOf(g), lonOf(g))) { mutableListOf() } += g
        cells.mapValues { it.value.toIntArray() }
    }
    private fun cell(lat: Double, lon: Double) = (floor(lat / CELL).toLong() shl 32) or (floor(lon / CELL).toLong() and 0xFFFFFFFFL)
    fun stopsNear(lat: Double, lon: Double, radius: Double): List<Pair<Int, Double>> {
        val out = ArrayList<Pair<Int, Double>>()
        val span = ceil(radius / 50_000.0 / CELL).toInt().coerceAtLeast(1)
        val cy = floor(lat / CELL).toLong(); val cx = floor(lon / CELL).toLong()
        for (dy in -span..span) for (dx in -span..span) {
            grid[((cy + dy) shl 32) or ((cx + dx) and 0xFFFFFFFFL)]?.forEach { g ->
                val d = distance(lat, lon, latOf(g), lonOf(g)); if (d <= radius) out += g to d
            }
        }
        return out
    }
    private val footpaths by lazy { AtomicReferenceArray<IntArray>(totalStops) }

    /** Cache cross-feed walks as the planner reaches stops. Each entry stores destination and minutes. */
    private fun footpathsFrom(stop: Int): IntArray {
        footpaths.get(stop)?.let { return it }
        val nearby = stopsNear(latOf(stop), lonOf(stop), MAX_WALK_METERS.toDouble())
        val paths = IntArray(nearby.count { it.first != stop } * 2)
        var index = 0
        for ((to, distance) in nearby) {
            if (to == stop) continue
            paths[index++] = to
            paths[index++] = walkMinutes(distance)
        }
        val cached = if (paths.isEmpty()) NO_FOOTPATHS else paths
        return if (footpaths.compareAndSet(stop, null, cached)) cached else footpaths.get(stop)
    }

    // ---- Resolving app stations to offline stops ----
    /** Exact provider identity first (a station includes its platforms), then the same name nearby. */
    fun resolve(station: Station, nameRadius: Double = 250.0): List<StopRef> {
        val exact = feeds.flatMapIndexed { f, feed ->
            val stop = feed.stopIndex[station.id] ?: return@flatMapIndexed emptyList()
            listOf(StopRef(f, stop)) + feed.children[stop].orEmpty().map { StopRef(f, it) }
        }
        if (exact.isNotEmpty()) return exact
        if (station.lat == 0.0 && station.lon == 0.0) return emptyList()
        val wanted = normalizedName(station.name)
        return stopsNear(station.lat, station.lon, nameRadius).map { ref(it.first) }
            .filter { normalizedName(feeds[it.feed].text(feeds[it.feed].stopName[it.stop])) == wanted }
    }
    fun covers(station: Station) = resolve(station).isNotEmpty()

    /** Stop search by name for when the provider's search is unreachable: platforms fold into their station,
     * and same-named stops of one feed close together appear once. */
    fun searchStops(query: String, limit: Int = 10): List<Station> {
        val words = normalizedName(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        require(limit >= 0)
        if (limit == 0) return emptyList()
        val first = words.first()
        val wordPrefix = " $first"
        val matches = LinkedHashMap<String, StopMatch>()
        for ((f, feed) in feeds.withIndex()) {
            val names = feed.normalizedStopNames
            for (s in 0 until feed.stopCount) {
                val name = names[s]
                if (!words.all { name.contains(it) }) continue
                val stop = feed.stationOf(s)
                val id = feed.text(feed.stopId[stop])
                if (id in matches) continue
                // Names starting with the query lead, then names with a word starting with it; stations before street stops.
                val station = feed.stopParent[s] >= 0 || feed.children.containsKey(stop)
                val rank = (if (name.startsWith(first)) 0 else if (wordPrefix in name) 200 else 1000) +
                    (if (station) 0 else 100) + name.length
                matches[id] = StopMatch(f, s, stop, station, rank)
            }
        }
        val results = ArrayList<Station>()
        val byName = HashMap<String, MutableList<Station>>()
        for ((id, match) in matches.entries.sortedBy { it.value.rank }) {
            val feed = feeds[match.feed]
            val stop = match.station
            val lat = feed.lat(stop)
            val lon = feed.lon(stop)
            // Directional stops of one name within 400 m appear as one suggestion.
            val sameName = byName.getOrPut(feed.normalizedStopNames[stop]) { ArrayList() }
            if (sameName.any { distance(it.lat, it.lon, lat, lon) < 400 }) continue
            val modes = LinkedHashSet<String>()
            feed.forEachStopPattern(match.stop) { p, _ -> modes += feed.mode(feed.patternRoute[p]) }
            feed.children[stop].orEmpty().forEach { child ->
                feed.forEachStopPattern(child) { p, _ -> modes += feed.mode(feed.patternRoute[p]) }
            }
            val result = Station(id, feed.text(feed.stopName[stop]), lat, lon,
                type = if (match.isStation) "Station" else "Stop", modes = modes.toList())
            results += result
            sameName += result
            if (results.size == limit) break
        }
        return results
    }

    private data class StopMatch(val feed: Int, val stop: Int, val station: Int, val isStation: Boolean, val rank: Int)

    // ---- Departure board ----
    fun departures(station: Station, now: Long, limit: Int = 30, horizonMinutes: Int = 1440): List<StopDeparture> {
        val stops = resolve(station)
        if (stops.isEmpty()) throw OfflineNotCovered()
        require(limit >= 0)
        if (limit == 0) return emptyList()
        val candidates = ArrayList<DepartureCandidate>()
        val today = Instant.ofEpochMilli(now).atZone(Budapest).toLocalDate()
        val days = List(3) { BoardDay(today.plusDays(it - 1L)) }
        val serviceMasks = feeds.map { feed -> LongArray(days.size) { feed.serviceDayMask(days[it].date) } }
        val start = now - 60_000L
        val end = now + horizonMinutes * 60_000L
        var patternOrder = 0
        for ((f, stop) in stops) {
            val feed = feeds[f]
            val masks = serviceMasks[f]
            feed.forEachStopPattern(stop) { p, i ->
                if (!feed.canBoard(p, i)) return@forEachStopPattern
                val order = patternOrder++
                val from = feed.patternTripStart[p]
                val to = feed.patternTripStart[p + 1]
                if (from == to) return@forEachStopPattern
                for ((d, day) in days.withIndex()) {
                    val mask = masks[d]
                    if (mask == 0L) continue
                    // Local times on clock-change days need the same per-trip conversion as trip details.
                    val linear = feed.departure(to - 1, i) < day.transitionMinute
                    val first = if (linear) {
                        val minute = Math.floorDiv(start - day.midnight - 1, 60_000L) + 1
                        feed.firstDeparture(from, to, i, minute)
                    } else from
                    for (t in first until to) {
                        val minutes = feed.departure(t, i)
                        val at = if (linear) day.midnight + minutes * 60_000L else epoch(day.date, minutes)
                        if (linear && at > end) break
                        if (feed.tripDays[t] and mask != 0L && at in start..end) {
                            candidates += DepartureCandidate(f, t, i, day.date, at, order)
                        }
                    }
                }
            }
        }
        candidates.sortWith(compareBy({ it.time }, { it.patternOrder }, { it.trip }, { it.day }))
        return candidates.asSequence().distinctBy { feeds[it.feed].let { feed -> feed.text(feed.tripId[it.trip]) } to it.time }
            .take(limit).map { departure(it.feed, it.trip, it.stopIndex, it.day, it.time) }.toList()
    }

    private class BoardDay(val date: LocalDate) {
        private val start = date.atStartOfDay(Budapest)
        val midnight = start.toInstant().toEpochMilli()
        val transitionMinute = if (start.toLocalDateTime() != date.atStartOfDay()) 0L else {
            Budapest.rules.nextTransition(start.toInstant().minusNanos(1))?.let {
                Duration.between(date.atStartOfDay(), minOf(it.dateTimeBefore, it.dateTimeAfter)).toMinutes()
            } ?: Long.MAX_VALUE
        }
    }

    private data class DepartureCandidate(val feed: Int, val trip: Int, val stopIndex: Int, val day: LocalDate,
                                          val time: Long, val patternOrder: Int)

    private fun departure(f: Int, t: Int, i: Int, day: LocalDate, at: Long): StopDeparture {
        val feed = feeds[f]; val p = feed.tripPattern[t]; val route = feed.patternRoute[p]
        val stop = feed.patternStop(p, i)
        val headsign = feed.text(feed.tripHeadsign[t]).ifBlank { feed.text(feed.stopName[feed.patternStop(p, feed.patternSize(p) - 1)]) }
        val leg = leg(f, t, i, i, day).copy(headsign = headsign)
        return StopDeparture(feed.text(feed.tripId[t]), headsign, ServiceTime(at, at, false),
            feed.text(feed.stopPlatform[stop]), false, leg, day.toString(), offline = true)
    }

    // ---- Trip timetable ----
    fun trip(id: String, day: String): Trip? {
        val date = LocalDate.parse(day)
        for ((f, feed) in feeds.withIndex()) {
            val t = feed.tripIndex[id] ?: continue
            if (!feed.runsOn(t, date)) continue
            return trip(f, t, date)
        }
        return null
    }
    private fun trip(f: Int, t: Int, day: LocalDate): Trip {
        val feed = feeds[f]; val p = feed.tripPattern[t]; val route = feed.patternRoute[p]
        val calls = (0 until feed.patternSize(p)).map { i ->
            val stop = feed.patternStop(p, i)
            Call(Stop(Gid.of(ProviderId.Gtfs, feed.text(feed.stopId[stop])), feed.text(feed.stopName[stop]), feed.lat(stop), feed.lon(stop),
                platform = feed.text(feed.stopPlatform[stop])), i,
                TimePoint(epoch(day, feed.arrival(t, i))), TimePoint(epoch(day, feed.departure(t, i))))
        }
        val agency = feed.routeAgency[route].takeIf { it >= 0 }?.let { feed.text(feed.agencyName[it]) }.orEmpty()
        val transitRoute = TransitRoute(null, routeLabel(f, route, -1), feed.text(feed.routeLong[route]), ModeMapping.fromOtp(feed.mode(route)),
            hex(feed.routeColor[route]), hex(feed.routeTextColor[route]), agency.takeIf { it.isNotBlank() }?.let { Agency(null, it) })
        val summary = TripSummary(Gid.of(ProviderId.Gtfs, feed.text(feed.tripId[t])), transitRoute,
            feed.text(feed.tripHeadsign[t]).ifBlank { calls.last().stop.name }, day, feed.text(feed.tripShortName[t]))
        return Trip(summary, calls, Provenance.of(ProviderId.Gtfs, feed.builtAt, DataKind.Offline), feedName = feed.feedName)
    }

    // ---- Route timetables ----
    data class RouteSummary(val ref: RouteRef, val label: String, val longName: String, val mode: String, val color: String,
                            val textColor: String, val agency: String, val feedName: String)
    /** One travel direction of a line: its main terminus and every station any of its trips serves, in travel order.
     * Stations are identified at station level (a bus station's platforms are one stop). */
    data class RouteDirection(val terminus: String, val stops: List<Station>, internal val patterns: List<Int>)
    /** A departure from the chosen stop, with where that trip ends and when it gets there. */
    data class RouteDeparture(val tripId: String, val time: Long, val destination: String, val arrival: Long,
                              val platform: String, val day: LocalDate, val leg: Leg)

    fun routes(query: String): List<RouteSummary> {
        val q = normalizedName(query)
        val out = ArrayList<RouteSummary>()
        feeds.forEachIndexed { f, feed ->
            for (r in feed.routeId.indices) {
                val summary = routeSummary(f, r)
                if (q.isEmpty() || normalizedName(summary.label).startsWith(q) || normalizedName(summary.longName).contains(q)) out += summary
            }
        }
        // Exact line numbers first, then numeric order (2 before 10), then by name.
        return out.sortedWith(compareBy<RouteSummary>({ normalizedName(it.label) != q },
            { it.label.takeWhile(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE }, { it.label }, { it.longName }))
    }
    fun routeSummary(f: Int, r: Int): RouteSummary {
        val feed = feeds[f]
        val label = routeLabel(f, r, -1)
        // Lines without a usable long name are described by the termini of their longest stop sequence.
        val long = feed.text(feed.routeLong[r]).takeUnless { it.isBlank() || it == label } ?: feed.routePatterns[r].orEmpty()
            .maxByOrNull { feed.patternSize(it) }?.let { p ->
                "${feed.text(feed.stopName[feed.patternStop(p, 0)])} – ${feed.text(feed.stopName[feed.patternStop(p, feed.patternSize(p) - 1)])}"
            }.orEmpty()
        return RouteSummary(RouteRef(f, r), label, long, feed.mode(r), hex(feed.routeColor[r]),
            hex(feed.routeTextColor[r]), feed.routeAgency[r].takeIf { it >= 0 }?.let { feed.text(feed.agencyName[it]) }.orEmpty(), feed.feedName)
    }

    /** A stop's station: its parent when it is a platform. */
    private fun OfflineTimetable.stationOf(stop: Int) = stopParent[stop].takeIf { it >= 0 } ?: stop
    private fun OfflineTimetable.stations(pattern: Int) = IntArray(patternSize(pattern)) { stationOf(patternStop(pattern, it)) }

    /** At most two directions: trips are grouped by whether they run the same way as the line's longest trip.
     * Short workings and platform variants join their direction instead of becoming directions of their own. */
    fun directions(route: RouteRef): List<RouteDirection> {
        val feed = feeds[route.feed]
        val patterns = feed.routePatterns[route.route].orEmpty()
        if (patterns.isEmpty()) return emptyList()
        fun weight(p: Int) = feed.patternSize(p) * 100_000 + (feed.patternTripStart[p + 1] - feed.patternTripStart[p])
        /** +1 when [p] visits the reference's stations in the same order, -1 when reversed, 0 when it cannot tell. */
        fun orientation(p: Int, reference: IntArray): Int {
            val at = HashMap<Int, Int>(); reference.forEachIndexed { i, s -> at.putIfAbsent(s, i) }
            val shared = feed.stations(p).asList().mapNotNull { at[it] }
            if (shared.size < 2) return 0
            return if (shared.last() > shared.first()) 1 else if (shared.last() < shared.first()) -1 else 0
        }
        val main = patterns.maxBy(::weight)
        val mainStations = feed.stations(main)
        val forward = patterns.filter { orientation(it, mainStations) >= 0 }.toMutableList()
        val backward = patterns.filter { orientation(it, mainStations) < 0 }.toMutableList()
        // Patterns sharing too little with the main trip are placed by the opposite direction's main trip when there is one.
        if (backward.isNotEmpty()) {
            val back = feed.stations(backward.maxBy(::weight))
            forward.filter { orientation(it, mainStations) == 0 && orientation(it, back) > 0 }.forEach { forward -= it; backward += it }
        }
        return listOf(forward, backward).filter { it.isNotEmpty() }.map { group ->
            val reference = group.maxBy(::weight)
            // Merge every pattern's stations into the reference order, inserting extra stops after their predecessor.
            val order = feed.stations(reference).distinct().toMutableList()
            group.sortedByDescending(::weight).forEach { p ->
                var previous = -1
                feed.stations(p).forEach { s ->
                    val at = order.indexOf(s)
                    if (at >= 0) previous = at else { order.add(previous + 1, s); previous += 1 }
                }
            }
            val terminus = feed.text(feed.stopName[feed.stationOf(feed.patternStop(reference, feed.patternSize(reference) - 1))])
            RouteDirection(terminus, order.map { station(route.feed, it) }, group)
        }
    }

    /** Every departure in [direction] from [stationId] (a station or one of its platforms) on [day], in time order. */
    fun routeDepartures(route: RouteRef, direction: RouteDirection, stationId: String, day: LocalDate): List<RouteDeparture> {
        val feed = feeds[route.feed]
        val wanted = feed.stopIndex[stationId]?.let { feed.stationOf(it) } ?: return emptyList()
        val out = ArrayList<RouteDeparture>()
        for (p in direction.patterns) {
            val last = feed.patternSize(p) - 1
            for (i in 0 until last) {
                if (feed.stationOf(feed.patternStop(p, i)) != wanted || !feed.canBoard(p, i)) continue
                for (t in feed.patternTripStart[p] until feed.patternTripStart[p + 1]) {
                    if (!feed.runsOn(t, day)) continue
                    val destination = feed.text(feed.stopName[feed.patternStop(p, last)])
                    out += RouteDeparture(feed.text(feed.tripId[t]), epoch(day, feed.departure(t, i)), destination,
                        epoch(day, feed.arrival(t, last)), feed.text(feed.stopPlatform[feed.patternStop(p, i)]), day,
                        leg(route.feed, t, i, last, day).copy(headsign = destination))
                }
            }
        }
        return out.distinctBy { it.tripId to it.time }.sortedBy { it.time }
    }

    // ---- Journey planning (RAPTOR) ----
    fun plan(from: Station, to: Station, time: LocalDateTime, arriveBy: Boolean, count: Int = 6): List<Journey> {
        val origins = access(from); val targets = access(to)
        if (origins.isEmpty() || targets.isEmpty()) throw OfflineNotCovered()
        val base = time.toLocalDate().minusDays(1)
        val query = ((time.toLocalDate().toEpochDay() - base.toEpochDay()) * 1440 + time.hour * 60 + time.minute).toInt()
        val found = LinkedHashMap<String, Journey>()
        val serviceDays = List(3) { base.plusDays(it.toLong()) }
        val serviceMasks = feeds.map { feed -> serviceDays.map(feed::serviceDayMask).toLongArray() }
        val workspace = RaptorWorkspace(totalStops)
        val midnight = epoch(base, 0)
        fun minutes(epochMillis: Long) = ((epochMillis - midnight) / 60_000).toInt()
        // Each search returns the fastest journeys from [start]; the next one starts just after the earliest of them.
        // Arrive-by searches forward from six hours earlier and keeps the latest journeys arriving in time.
        var start = if (arriveBy) query - 360 else query
        val deadline = if (arriveBy) query else query + 1440
        for (attempt in 0 until if (arriveBy) 40 else count * 3) {
            val journeys = raptor(origins, targets, serviceDays, serviceMasks, start, deadline, workspace)
            if (journeys.isEmpty()) break
            journeys.forEach { found.putIfAbsent(it.key, it) }
            start = journeys.minOf { minutes(it.start) } + 1
            if (arriveBy && start > query) break
            if (!arriveBy && dominantJourneys(found.values).size >= count) break
        }
        val sorted = dominantJourneys(found.values).sortedBy { it.start }
        return if (arriveBy) sorted.filter { minutes(it.end) <= query }.takeLast(count) else sorted.take(count)
    }

    /** Drops a journey when another leaves no earlier, arrives no later and changes no more often,
     * so a feeder bus one minute earlier does not repeat the same onward journey. */
    internal fun dominantJourneys(journeys: Collection<Journey>): List<Journey> = journeys.filter { j ->
        journeys.none { k -> k !== j && k.start >= j.start && k.end <= j.end && k.changes <= j.changes &&
            (k.start > j.start || k.end < j.end || k.changes < j.changes || k.key < j.key) }
    }

    /** Boarding points for an endpoint with their walking minutes: exact stops at 0, everything within walking range. */
    private fun access(station: Station): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        resolve(station, nameRadius = 600.0).forEach { out[global(it.feed, it.stop)] = 0 }
        if (out.isEmpty() && (station.lat != 0.0 || station.lon != 0.0)) {
            // Town aggregates and places: every stop of the same town name nearby, else plain walking distance.
            val town = normalizedName(station.townName)
            if (station.stopName.isEmpty() && town.isNotEmpty()) stopsNear(station.lat, station.lon, 8_000.0).forEach { (g, _) ->
                val r = ref(g); if (normalizedName(feeds[r.feed].text(feeds[r.feed].stopName[r.stop]).substringBefore(',')) == town) out[g] = 0
            }
            if (out.isEmpty()) stopsNear(station.lat, station.lon, ACCESS_METERS).forEach { (g, d) -> out[g] = walkingMinutes(d) }
        }
        return out
    }

    private sealed class Step {
        /** Rode [trip] from [fromStop], which had been reached in round [fromRound]. */
        class Ride(val feed: Int, val trip: Int, val day: LocalDate, val board: Int, val alight: Int, val fromStop: Int, val fromRound: Int) : Step()
        class Walk(val fromStop: Int, val minutes: Int) : Step()
        object Start : Step()
    }

    /** One plan may run many searches. Reuse its stop labels without sharing them across requests. */
    private class RaptorWorkspace(stops: Int) {
        val best = IntArray(stops)
        val bestRound = IntArray(stops)
        val arrival = Array(MAX_RIDES + 1) { IntArray(stops) }
        val steps = Array(MAX_RIDES + 1) { arrayOfNulls<Step>(stops) }
        val ready = IntArray(stops)
        val readyRound = IntArray(stops)

        fun reset() {
            best.fill(Int.MAX_VALUE)
            bestRound.fill(-1)
            arrival.forEach { it.fill(Int.MAX_VALUE) }
            steps.forEach { it.fill(null) }
        }
    }

    private fun raptor(origins: Map<Int, Int>, targets: Map<Int, Int>, serviceDays: List<LocalDate>,
                       serviceMasks: List<LongArray>, start: Int, horizon: Int, workspace: RaptorWorkspace): List<Journey> {
        workspace.reset()
        val best = workspace.best
        val bestRound = workspace.bestRound
        val arrival = workspace.arrival
        val steps = workspace.steps
        fun record(k: Int, g: Int, t: Int, step: Step) {
            arrival[k][g] = t; best[g] = t; bestRound[g] = k; steps[k][g] = step
        }
        fun walkFrom(k: Int, reached: List<Int>, marked: BitSet) {
            for (g in reached) {
                val paths = footpathsFrom(g)
                for (at in paths.indices step 2) {
                    val to = paths[at]
                    val minutes = paths[at + 1]
                    val time = arrival[k][g] + minutes
                    if (time < best[to]) {
                        record(k, to, time, Step.Walk(g, minutes))
                        marked.set(to)
                    }
                }
            }
        }
        var marked = BitSet(totalStops)
        for ((g, walk) in origins) {
            record(0, g, start + walk, Step.Start)
            marked.set(g)
        }
        walkFrom(0, origins.keys.toList(), marked)
        var bestTarget = Int.MAX_VALUE
        val results = ArrayList<Journey>()
        val ready = workspace.ready
        val readyRound = workspace.readyRound
        for (k in 1..MAX_RIDES) {
            // Boarding may use any earlier round's label; a stop reached by riding needs time to change.
            best.copyInto(ready)
            bestRound.copyInto(readyRound)
            val queue = HashMap<Long, Int>()
            var markedStop = marked.nextSetBit(0)
            while (markedStop >= 0) {
                val g = markedStop
                val (f, s) = ref(g)
                feeds[f].forEachStopPattern(s) { p, i ->
                    val key = (f.toLong() shl 32) or p.toLong()
                    val current = queue[key]; if (current == null || i < current) queue[key] = i
                }
                markedStop = marked.nextSetBit(g + 1)
            }
            val next = BitSet(totalStops); val rode = ArrayList<Int>()
            for ((key, first) in queue) {
                val f = (key ushr 32).toInt(); val p = key.toInt(); val feed = feeds[f]
                var trip = -1; var tripDay = 0; var boardAt = -1
                for (i in first until feed.patternSize(p)) {
                    val g = global(f, feed.patternStop(p, i))
                    if (trip >= 0 && feed.canAlight(p, i)) {
                        val at = tripDay * 1440 + feed.arrival(trip, i)
                        if (at < bestTarget && at < best[g]) {
                            val from = global(f, feed.patternStop(p, boardAt))
                            record(k, g, at, Step.Ride(f, trip, serviceDays[tripDay], boardAt, i, from, readyRound[from]))
                            if (!next[g]) rode += g
                            next.set(g)
                        }
                    }
                    if (ready[g] == Int.MAX_VALUE || !feed.canBoard(p, i)) continue
                    val needed = ready[g] + if (steps[readyRound[g]][g] is Step.Ride) CHANGE_MINUTES else 0
                    if (trip >= 0 && tripDay * 1440 + feed.departure(trip, i) < needed) continue
                    val earlier = earliestTrip(feed, p, i, needed, serviceMasks[f], trip, tripDay) ?: continue
                    trip = earlier.first; tripDay = earlier.second; boardAt = i
                }
            }
            walkFrom(k, rode, next)
            // A journey with this many rides counts only when it arrives earlier than every journey with fewer.
            var roundBest = Int.MAX_VALUE; var roundStop = -1
            for ((g, walk) in targets) {
                val t = arrival[k][g]; if (t == Int.MAX_VALUE) continue
                if (t + walk < roundBest) { roundBest = t + walk; roundStop = g }
            }
            if (roundStop >= 0 && roundBest < bestTarget && roundBest <= horizon) {
                bestTarget = roundBest
                build(k, roundStop, targets.getValue(roundStop), steps, arrival, origins, serviceDays.first())?.let { results += it }
            }
            marked = next
            if (rode.isEmpty()) break
        }
        return results
    }

    private fun earliestTrip(feed: OfflineTimetable, p: Int, i: Int, needed: Int, serviceMasks: LongArray,
                             current: Int, currentDay: Int): Pair<Int, Int>? {
        val from = feed.patternTripStart[p]; val to = feed.patternTripStart[p + 1]
        // Only a trip leaving earlier than the one already held is worth switching to.
        var bestTrip = -1; var bestDay = 0; var bestTime = if (current >= 0) currentDay * 1440 + feed.departure(current, i) else Int.MAX_VALUE
        if (needed >= bestTime) return null
        val patternDays = feed.patternServiceDays[p]
        for (day in serviceMasks.indices) {
            val mask = serviceMasks[day]
            if (patternDays and mask == 0L) continue
            val dayOffset = day * 1440
            if (dayOffset + feed.departure(from, i) >= bestTime) break
            val local = needed - dayOffset
            for (t in feed.firstDeparture(from, to, i, local.toLong()) until to) {
                val at = dayOffset + feed.departure(t, i)
                if (at >= bestTime) break
                if (feed.tripDays[t] and mask != 0L) { bestTrip = t; bestDay = day; bestTime = at; break }
            }
        }
        return if (bestTrip >= 0) bestTrip to bestDay else null
    }

    private fun build(k: Int, target: Int, egress: Int, steps: Array<Array<Step?>>, arrival: Array<IntArray>,
                      origins: Map<Int, Int>, base: LocalDate): Journey? {
        val legs = ArrayList<Leg>()
        var round = k; var g = target
        val endTime = epoch(base, arrival[k][target] + egress)
        var guard = 0
        while (guard++ < 64) {
            when (val step = steps[round][g] ?: return null) {
                is Step.Start -> {
                    val walk = origins[g] ?: 0
                    if (walk > 0) legs += walkLeg(null, g, epoch(base, arrival[round][g] - walk), epoch(base, arrival[round][g]))
                    break
                }
                is Step.Walk -> {
                    legs += walkLeg(step.fromStop, g, epoch(base, arrival[round][step.fromStop]), epoch(base, arrival[round][g]))
                    g = step.fromStop
                }
                is Step.Ride -> {
                    legs += leg(step.feed, step.trip, step.board, step.alight, step.day)
                    g = step.fromStop; round = step.fromRound
                }
            }
        }
        legs.reverse()
        if (legs.none { !it.walking }) return null
        if (egress > 0) legs += walkLeg(target, null, legs.last().end, endTime)
        // Walks before the first ride end exactly when the ride starts.
        val firstRide = legs.indexOfFirst { !it.walking }
        if (firstRide > 0) {
            val ride = legs[firstRide]
            var end = ride.start
            for (w in firstRide - 1 downTo 0) { val d = legs[w].end - legs[w].start; legs[w] = legs[w].copy(start = end - d, end = end); end -= d }
        }
        val duration = ((legs.last().end - legs.first().start) / 1000).toInt()
        return Journey(duration, legs, fetchedAt = System.currentTimeMillis(), offline = true)
    }

    private fun walkLeg(from: Int?, to: Int?, start: Long, end: Long): Leg {
        val a = from?.let { stationOf(it) } ?: Station("", "", 0.0, 0.0)
        val b = to?.let { stationOf(it) } ?: Station("", "", 0.0, 0.0)
        return Leg("WALK", start, end, a, b, "WALK", "", false, 0, emptyList(), "")
    }
    private fun stationOf(g: Int) = ref(g).let { station(it.feed, it.stop) }
    private fun station(f: Int, stop: Int): Station {
        val feed = feeds[f]
        val parent = feed.stopParent[stop]
        val id = feed.text(feed.stopId[if (parent >= 0) parent else stop])
        return Station(id, feed.text(feed.stopName[stop]), feed.lat(stop), feed.lon(stop), platform = feed.text(feed.stopPlatform[stop]))
    }

    /** A ride from position [board] to [alight] of trip [t] on service [day], shaped like a provider leg. */
    private fun leg(f: Int, t: Int, board: Int, alight: Int, day: LocalDate): Leg {
        val feed = feeds[f]; val p = feed.tripPattern[t]; val route = feed.patternRoute[p]
        val label = routeLabel(f, route, t)
        val start = epoch(day, feed.departure(t, board)); val end = epoch(day, feed.arrival(t, alight))
        val intermediate = if (alight > board + 1) (board + 1 until alight).map { station(f, feed.patternStop(p, it)) } else emptyList()
        return Leg(feed.mode(route), start, end, station(f, feed.patternStop(p, board)), station(f, feed.patternStop(p, alight)),
            label, feed.text(feed.tripId[t]), false, 0, intermediate, "", hex(feed.routeColor[route]), "",
            feed.text(feed.tripHeadsign[t]), feed.text(feed.routeLong[route]),
            feed.routeAgency[route].takeIf { it >= 0 }?.let { feed.text(feed.agencyName[it]) }.orEmpty(),
            routeShortName = feed.text(feed.routeShort[route]))
    }
    /** Trains are named by their train number; everything else by its public line number. */
    private fun routeLabel(f: Int, route: Int, trip: Int): String {
        val feed = feeds[f]
        val short = feed.text(feed.routeShort[route]); val tripName = if (trip >= 0) feed.text(feed.tripShortName[trip]) else ""
        val rail = modeFamily(feed.mode(route)) == 0
        val raw = if (rail) tripName.ifBlank { short } else short.ifBlank { tripName }
        return hungarianServiceLabel(raw.ifBlank { feed.text(feed.routeLong[route]).take(24) }.ifBlank { "Járat" }.substringBefore('/'))
    }

    companion object {
        private const val CELL = 0.005
        const val MAX_WALK_METERS = 400
        private const val ACCESS_METERS = 800.0
        const val CHANGE_MINUTES = 2
        private const val MAX_RIDES = 5
        private val NO_FOOTPATHS = IntArray(0)
        private val NAME_MARKS = Regex("\\p{M}+")
        private val NAME_SEPARATORS = Regex("[^a-z0-9]+")
        /** Street distance is taken as 1.3x the straight line, walked at 75 m/min. */
        fun walkingMinutes(meters: Double) = ceil(meters * 1.3 / 75.0).toInt()
        /** A change on foot also includes time to get off and find the next stop. */
        fun walkMinutes(meters: Double) = CHANGE_MINUTES + walkingMinutes(meters)
        fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 6_371_000.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        }
        fun normalizedName(value: String) = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(NAME_MARKS, "").replace(NAME_SEPARATORS, " ").trim()
        fun epoch(day: LocalDate, minutes: Int): Long =
            day.atTime(LocalTime.MIDNIGHT).plusMinutes(minutes.toLong()).atZone(Budapest).toInstant().toEpochMilli()
        private fun hex(color: Int) = if (color < 0) "" else "%06X".format(color)
    }
}

/** The offline data has no stop or trip matching the request. */
class OfflineNotCovered : IllegalStateException("Ehhez nincs letöltött offline menetrend.")
