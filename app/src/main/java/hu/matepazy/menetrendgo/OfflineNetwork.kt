package hu.matepazy.menetrendgo

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.*

/** Read-only queries over every downloaded feed: departures, trip timetables, route timetables and journeys.
 * Every answer is scheduled data only and says so (realtime=false, [OFFLINE_PREFIX] trip state). */
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
    /** Footpaths up to [MAX_WALK_METERS] between any two stops, across feeds; minutes include the change. */
    private val footpaths: Pair<IntArray, IntArray> by lazy {
        val start = IntArray(totalStops + 1); val lists = arrayOfNulls<IntArray>(totalStops)
        for (g in 0 until totalStops) {
            val near = stopsNear(latOf(g), lonOf(g), MAX_WALK_METERS.toDouble()).filter { it.first != g }
            lists[g] = IntArray(near.size * 2).also { a -> near.forEachIndexed { i, (to, d) -> a[i * 2] = to; a[i * 2 + 1] = walkMinutes(d) } }
            start[g + 1] = start[g] + near.size
        }
        val flat = IntArray(start[totalStops] * 2)
        for (g in 0 until totalStops) lists[g]!!.copyInto(flat, start[g] * 2)
        start to flat
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
        val out = LinkedHashMap<String, Pair<Station, Int>>()
        feeds.forEachIndexed { f, feed ->
            for (s in 0 until feed.stopCount) {
                val name = normalizedName(feed.text(feed.stopName[s]))
                if (!words.all { name.contains(it) }) continue
                val stop = if (feed.stopParent[s] >= 0) feed.stopParent[s] else s
                val id = feed.text(feed.stopId[stop])
                if (id in out) continue
                val modes = LinkedHashSet<String>()
                feed.forEachStopPattern(s) { p, _ -> modes += feed.mode(feed.patternRoute[p]) }
                feed.children[stop].orEmpty().forEach { c -> feed.forEachStopPattern(c) { p, _ -> modes += feed.mode(feed.patternRoute[p]) } }
                // Names starting with the query lead, then names with a word starting with it; stations before street stops.
                val wordStart = name.split(' ').any { it.startsWith(words.first()) }
                val station = feed.stopParent[s] >= 0 || feed.children.containsKey(stop)
                val rank = (if (name.startsWith(words.first())) 0 else if (wordStart) 200 else 1000) + (if (station) 0 else 100) + name.length
                out[id] = Station(id, feed.text(feed.stopName[stop]), feed.lat(stop), feed.lon(stop),
                    type = if (feed.stopParent[s] >= 0 || feed.children.containsKey(stop)) "Station" else "Stop", modes = modes.toList()) to rank
            }
        }
        return out.values.sortedBy { it.second }.map { it.first }
            .fold(ArrayList<Station>()) { kept, stop ->
                // Directional stops of one name within 400 m read as one place in a suggestion list.
                if (kept.none { normalizedName(it.name) == normalizedName(stop.name) && distance(it.lat, it.lon, stop.lat, stop.lon) < 400 }) kept += stop
                kept
            }.take(limit)
    }

    // ---- Departure board ----
    fun departures(station: Station, now: Long, limit: Int = 30, horizonMinutes: Int = 1440): List<StopDeparture> {
        val stops = resolve(station)
        if (stops.isEmpty()) throw OfflineNotCovered()
        val out = ArrayList<StopDeparture>()
        val today = java.time.Instant.ofEpochMilli(now).atZone(Budapest).toLocalDate()
        val end = now + horizonMinutes * 60_000L
        for ((f, stop) in stops) {
            val feed = feeds[f]
            feed.forEachStopPattern(stop) { p, i ->
                if (!feed.canBoard(p, i)) return@forEachStopPattern
                for (t in feed.patternTripStart[p] until feed.patternTripStart[p + 1]) for (back in -1..1) {
                    val day = today.plusDays(back.toLong())
                    if (!feed.runsOn(t, day)) continue
                    val at = epoch(day, feed.departure(t, i))
                    if (at < now - 60_000 || at > end) continue
                    out += departure(f, t, i, day, at)
                }
            }
        }
        return out.distinctBy { it.key }.sortedBy { it.time.scheduled }.take(limit)
    }

    private fun departure(f: Int, t: Int, i: Int, day: LocalDate, at: Long): StopDeparture {
        val feed = feeds[f]; val p = feed.tripPattern[t]; val route = feed.patternRoute[p]
        val stop = feed.patternStop(p, i)
        val headsign = feed.text(feed.tripHeadsign[t]).ifBlank { feed.text(feed.stopName[feed.patternStop(p, feed.patternSize(p) - 1)]) }
        val leg = leg(f, t, i, i, day).copy(headsign = headsign)
        return StopDeparture(feed.text(feed.tripId[t]), headsign, ServiceTime(at, at, false),
            feed.text(feed.stopPlatform[stop]), false, leg, day.toString(), offline = true)
    }

    // ---- Trip timetable (same JSON shape as a MÁVPlusz trip) ----
    fun trip(id: String, day: String): JSONObject? {
        val date = LocalDate.parse(day)
        for ((f, feed) in feeds.withIndex()) {
            val t = feed.tripIndex[id] ?: continue
            if (!feed.runsOn(t, date)) continue
            return tripJson(f, t, date)
        }
        return null
    }
    private fun tripJson(f: Int, t: Int, day: LocalDate): JSONObject {
        val feed = feeds[f]; val p = feed.tripPattern[t]; val route = feed.patternRoute[p]
        val serviceDay = day.atStartOfDay(Budapest).toEpochSecond()
        val times = JSONArray()
        for (i in 0 until feed.patternSize(p)) {
            val arrival = (epoch(day, feed.arrival(t, i)) / 1000 - serviceDay).toInt()
            val departure = (epoch(day, feed.departure(t, i)) / 1000 - serviceDay).toInt()
            times.put(JSONObject().put("serviceDay", serviceDay).put("realtime", false).put("realtimeState", OFFLINE_STATE)
                .put("stop", stopJson(f, feed.patternStop(p, i)))
                .put("scheduledArrival", arrival).put("scheduledDeparture", departure)
                .put("realtimeArrival", arrival).put("realtimeDeparture", departure).put("arrivalDelay", 0).put("departureDelay", 0))
        }
        return JSONObject().put("gtfsId", feed.text(feed.tripId[t])).put("tripHeadsign", feed.text(feed.tripHeadsign[t]))
            .put("tripShortName", feed.text(feed.tripShortName[t])).put("offline", true).put("offlineFeed", feed.feedName)
            .put("route", routeJson(f, route)).put("stoptimes", times).put("vehiclePositions", JSONArray())
    }
    private fun stopJson(f: Int, stop: Int): JSONObject {
        val feed = feeds[f]
        return JSONObject().put("gtfsId", feed.text(feed.stopId[stop])).put("name", feed.text(feed.stopName[stop]))
            .put("lat", feed.lat(stop)).put("lon", feed.lon(stop)).put("platformCode", feed.text(feed.stopPlatform[stop]))
    }
    private fun routeJson(f: Int, route: Int): JSONObject {
        val feed = feeds[f]
        return JSONObject().put("shortName", routeLabel(f, route, -1)).put("longName", feed.text(feed.routeLong[route]))
            .put("mode", feed.mode(route)).put("color", hex(feed.routeColor[route])).put("textColor", hex(feed.routeTextColor[route]))
            .put("agency", JSONObject().put("name", feed.routeAgency[route].takeIf { it >= 0 }?.let { feed.text(feed.agencyName[it]) }.orEmpty()))
    }

    // ---- Route timetables ----
    data class RouteSummary(val ref: RouteRef, val label: String, val longName: String, val mode: String, val color: String,
                            val textColor: String, val agency: String, val feedName: String)
    data class RouteDirection(val headsign: String, val stops: List<Station>)
    data class RouteDeparture(val tripId: String, val time: Long, val headsign: String, val day: LocalDate, val leg: Leg)

    fun routes(query: String, limit: Int = 50): List<RouteSummary> {
        val q = normalizedName(query)
        val out = ArrayList<RouteSummary>()
        feeds.forEachIndexed { f, feed ->
            for (r in feed.routeId.indices) {
                val summary = routeSummary(f, r)
                if (q.isEmpty() || normalizedName(summary.label).startsWith(q) || normalizedName(summary.longName).contains(q)) out += summary
            }
        }
        return out.sortedWith(compareBy({ normalizedName(it.label) != q }, { it.label.length }, { it.label }, { it.longName })).take(limit)
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
    /** Directions of a route, longest stop sequence per terminus first, with that sequence's stops. */
    fun directions(route: RouteRef): List<RouteDirection> {
        val feed = feeds[route.feed]
        return (0 until feed.patternCount).filter { feed.patternRoute[it] == route.route }
            .groupBy { feed.patternStop(it, feed.patternSize(it) - 1) }
            .map { (last, patterns) ->
                val longest = patterns.maxBy { p -> feed.patternSize(p) * 100_000 + (feed.patternTripStart[p + 1] - feed.patternTripStart[p]) }
                RouteDirection(feed.text(feed.stopName[last]), (0 until feed.patternSize(longest)).map { station(route.feed, feed.patternStop(longest, it)) })
            }.sortedByDescending { it.stops.size }
    }
    /** Every departure of the route from [stopId] on [day], for the classic hour-by-hour timetable. */
    fun routeDepartures(route: RouteRef, directionHeadsign: String, stopId: String, day: LocalDate): List<RouteDeparture> {
        val feed = feeds[route.feed]
        val out = ArrayList<RouteDeparture>()
        for (p in 0 until feed.patternCount) {
            if (feed.patternRoute[p] != route.route) continue
            if (feed.text(feed.stopName[feed.patternStop(p, feed.patternSize(p) - 1)]) != directionHeadsign) continue
            for (i in 0 until feed.patternSize(p)) {
                if (feed.text(feed.stopId[feed.patternStop(p, i)]) != stopId || !feed.canBoard(p, i)) continue
                for (t in feed.patternTripStart[p] until feed.patternTripStart[p + 1]) {
                    if (!feed.runsOn(t, day)) continue
                    val headsign = feed.text(feed.tripHeadsign[t]).ifBlank { directionHeadsign }
                    out += RouteDeparture(feed.text(feed.tripId[t]), epoch(day, feed.departure(t, i)), headsign, day,
                        leg(route.feed, t, i, i, day).copy(headsign = headsign))
                }
            }
        }
        return out.sortedBy { it.time }
    }

    // ---- Journey planning (RAPTOR) ----
    fun plan(from: Station, to: Station, time: LocalDateTime, arriveBy: Boolean, count: Int = 6): List<Journey> {
        val origins = access(from); val targets = access(to)
        if (origins.isEmpty() || targets.isEmpty()) throw OfflineNotCovered()
        val base = time.toLocalDate().minusDays(1)
        val query = ((time.toLocalDate().toEpochDay() - base.toEpochDay()) * 1440 + time.hour * 60 + time.minute).toInt()
        val found = LinkedHashMap<String, Journey>()
        val midnight = epoch(base, 0)
        fun minutes(epochMillis: Long) = ((epochMillis - midnight) / 60_000).toInt()
        // Each search returns the fastest journeys from [start]; the next one starts just after the earliest of them.
        // Arrive-by searches forward from six hours earlier and keeps the latest journeys arriving in time.
        var start = if (arriveBy) query - 360 else query
        val deadline = if (arriveBy) query else query + 1440
        for (attempt in 0 until if (arriveBy) 40 else count * 3) {
            val journeys = raptor(origins, targets, base, start, deadline)
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

    private fun raptor(origins: Map<Int, Int>, targets: Map<Int, Int>, base: LocalDate, start: Int, horizon: Int): List<Journey> {
        val rounds = MAX_RIDES + 1
        val best = IntArray(totalStops) { Int.MAX_VALUE }
        val bestRound = IntArray(totalStops) { -1 }
        val arrival = Array(rounds) { IntArray(totalStops) { Int.MAX_VALUE } }
        val steps = Array(rounds) { arrayOfNulls<Step>(totalStops) }
        val (pathStart, paths) = footpaths
        fun improve(k: Int, g: Int, t: Int, step: Step): Boolean {
            if (t >= best[g]) return false
            arrival[k][g] = t; best[g] = t; bestRound[g] = k; steps[k][g] = step; return true
        }
        fun walkFrom(k: Int, reached: List<Int>, marked: BooleanArray) {
            for (g in reached) for (at in pathStart[g] until pathStart[g + 1]) {
                val to = paths[at * 2]
                if (improve(k, to, arrival[k][g] + paths[at * 2 + 1], Step.Walk(g, paths[at * 2 + 1]))) marked[to] = true
            }
        }
        var marked = BooleanArray(totalStops)
        for ((g, walk) in origins) if (improve(0, g, start + walk, Step.Start)) marked[g] = true
        walkFrom(0, origins.keys.filter { steps[0][it] === Step.Start }, marked)
        var bestTarget = Int.MAX_VALUE
        val results = ArrayList<Journey>()
        for (k in 1 until rounds) {
            // Boarding may use any earlier round's label; a stop reached by riding needs time to change.
            val ready = best.copyOf(); val readyRound = bestRound.copyOf()
            val queue = HashMap<Long, Int>()
            for (g in 0 until totalStops) if (marked[g]) {
                val (f, s) = ref(g)
                feeds[f].forEachStopPattern(s) { p, i ->
                    val key = (f.toLong() shl 32) or p.toLong()
                    val current = queue[key]; if (current == null || i < current) queue[key] = i
                }
            }
            val next = BooleanArray(totalStops); val rode = ArrayList<Int>()
            for ((key, first) in queue) {
                val f = (key ushr 32).toInt(); val p = key.toInt(); val feed = feeds[f]
                var trip = -1; var tripDay = 0; var boardAt = -1
                for (i in first until feed.patternSize(p)) {
                    val g = global(f, feed.patternStop(p, i))
                    if (trip >= 0 && feed.canAlight(p, i)) {
                        val at = tripDay * 1440 + feed.arrival(trip, i)
                        val from = global(f, feed.patternStop(p, boardAt))
                        if (at < bestTarget && improve(k, g, at, Step.Ride(f, trip, base.plusDays(tripDay.toLong()), boardAt, i, from, readyRound[from]))) {
                            if (!next[g]) rode += g
                            next[g] = true
                        }
                    }
                    if (ready[g] == Int.MAX_VALUE || !feed.canBoard(p, i)) continue
                    val needed = ready[g] + if (steps[readyRound[g]][g] is Step.Ride) CHANGE_MINUTES else 0
                    if (trip >= 0 && tripDay * 1440 + feed.departure(trip, i) < needed) continue
                    val earlier = earliestTrip(f, p, i, needed, base, trip, tripDay) ?: continue
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
                build(k, roundStop, targets.getValue(roundStop), steps, arrival, origins, base)?.let { results += it }
            }
            marked = next
            if (rode.isEmpty()) break
        }
        return results
    }

    private fun earliestTrip(f: Int, p: Int, i: Int, needed: Int, base: LocalDate, current: Int, currentDay: Int): Pair<Int, Int>? {
        val feed = feeds[f]
        val from = feed.patternTripStart[p]; val to = feed.patternTripStart[p + 1]
        // Only a trip leaving earlier than the one already held is worth switching to.
        var bestTrip = -1; var bestDay = 0; var bestTime = if (current >= 0) currentDay * 1440 + feed.departure(current, i) else Int.MAX_VALUE
        for (day in 0..2) {
            val local = needed - day * 1440
            // Trips are sorted by departure at every stop of a pattern.
            var lo = from; var hi = to
            while (lo < hi) { val mid = (lo + hi) ushr 1; if (feed.departure(mid, i) < local) lo = mid + 1 else hi = mid }
            val date = base.plusDays(day.toLong())
            for (t in lo until to) {
                val at = day * 1440 + feed.departure(t, i)
                if (at >= bestTime) break
                if (feed.runsOn(t, date)) { bestTrip = t; bestDay = day; bestTime = at; break }
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
        const val OFFLINE_STATE = "SCHEDULED"
        private const val CELL = 0.005
        const val MAX_WALK_METERS = 400
        private const val ACCESS_METERS = 800.0
        const val CHANGE_MINUTES = 2
        private const val MAX_RIDES = 5
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
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
        fun epoch(day: LocalDate, minutes: Int): Long =
            day.atTime(LocalTime.MIDNIGHT).plusMinutes(minutes.toLong()).atZone(Budapest).toInstant().toEpochMilli()
        private fun hex(color: Int) = if (color < 0) "" else "%06X".format(color)
    }
}

/** The offline data has no stop or trip matching the request. */
class OfflineNotCovered : IllegalStateException("Ehhez nincs letöltött offline menetrend.")
