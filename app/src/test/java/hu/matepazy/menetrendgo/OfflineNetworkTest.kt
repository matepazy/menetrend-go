package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class OfflineNetworkTest {
    private val today = LocalDate.of(2026, 11, 24)
    private fun feed(id: String, files: Map<String, String>) =
        GtfsCompactor.compact(GtfsSource { files[it]?.byteInputStream() }, id, id, today)
    private val calendar = "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\nS,1,1,1,1,1,1,1,20260101,20271231\n"

    // A railway A–B–C and, in another feed, a bus from a stop 100 m from B to D.
    private val rail = feed("rail", mapOf(
        "agency.txt" to "agency_id,agency_name\nM,Vasút\n",
        "routes.txt" to "route_id,agency_id,route_short_name,route_long_name,route_type,route_color\nR,M,,Fővonal,102,003A7E\n",
        "calendar.txt" to calendar,
        "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon,location_type,parent_station,platform_code\n" +
            "1:A,Aváros,47.000,19.000,1,,\n1:A_1,Aváros,47.000,19.000,0,1:A,2\n1:B,Bfalva,47.100,19.100,0,,\n1:C,Cváros,47.200,19.200,0,,\n",
        "trips.txt" to "route_id,service_id,trip_id,trip_headsign,trip_short_name\nR,S,1:T1,Cváros,SZ 100\nR,S,1:T2,Cváros,SZ 102\nR,S,1:N,Cváros,SZ 190\n",
        "stop_times.txt" to "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n" +
            "1:T1,08:00:00,08:00:00,1:A_1,1\n1:T1,08:30:00,08:31:00,1:B,2\n1:T1,09:00:00,09:00:00,1:C,3\n" +
            "1:T2,10:00:00,10:00:00,1:A_1,1\n1:T2,10:30:00,10:31:00,1:B,2\n1:T2,11:00:00,11:00:00,1:C,3\n" +
            "1:N,24:50:00,24:50:00,1:A_1,1\n1:N,25:20:00,25:20:00,1:B,2\n",
    ))
    private val bus = feed("bus", mapOf(
        "routes.txt" to "route_id,route_short_name,route_long_name,route_type\nB12,12,B–D,3\n",
        "calendar.txt" to calendar,
        "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\nb:B,\"Bfalva, vasútállomás\",47.1009,19.100\nb:D,Dtelep,47.150,19.150\n",
        "trips.txt" to "route_id,service_id,trip_id,trip_headsign\nB12,S,b:early,Dtelep\nB12,S,b:late,Dtelep\n",
        // The 08:33 bus leaves before the train's passengers can reach the stop.
        "stop_times.txt" to "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n" +
            "b:early,08:33:00,08:33:00,b:B,1\nb:early,08:50:00,08:50:00,b:D,2\nb:late,08:40:00,08:40:00,b:B,1\nb:late,09:00:00,09:00:00,b:D,2\n",
    ))
    private val network = OfflineNetwork(listOf(rail, bus))
    private val a = Station("1:A", "Aváros", 47.0, 19.0)
    private val d = Station("b:D", "Dtelep", 47.15, 19.15)

    @Test fun departuresIncludeStationPlatformsAndLastNightsTrips() {
        val board = network.departures(a, OfflineNetwork.epoch(today, 7 * 60))
        assertEquals(listOf("1:T1", "1:T2", "1:N"), board.map { it.tripId })
        assertTrue(board.all { it.offline && !it.time.realtime })
        assertEquals("2", board.first().platform)
        assertEquals("SZ 100", board.first().leg.route)
        assertEquals("Cváros", board.first().headsign)
        // Yesterday's 24:50 trip leaves at 00:50 today.
        val night = network.departures(a, OfflineNetwork.epoch(today, 30))
        assertEquals("1:N", night.first().tripId); assertEquals(today.minusDays(1).toString(), night.first().serviceDay)
    }

    @Test fun departureWindowIncludesItsEdgesAndLimitsUniqueTrips() {
        val duplicateFeeds = OfflineNetwork(listOf(rail, rail))
        val now = OfflineNetwork.epoch(today, 8 * 60 + 1)
        assertEquals(listOf("1:T1", "1:T2"),
            duplicateFeeds.departures(a, now, limit = 2, horizonMinutes = 119).map { it.tripId })
        assertEquals(listOf("1:T1"),
            duplicateFeeds.departures(a, now, limit = 2, horizonMinutes = 118).map { it.tripId })
    }

    @Test fun departureWindowUsesActualTimeWhenClocksGoForward() {
        val day = LocalDate.of(2027, 3, 28)
        val clockChange = GtfsCompactor.compact(GtfsSource { file ->
            when (file) {
                "routes.txt" -> "route_id,route_type\nR,3\n"
                "calendar.txt" -> calendar
                "stops.txt" -> "stop_id,stop_name,stop_lat,stop_lon\nA,Aváros,47,19\nB,Bfalva,47.1,19.1\n"
                "trips.txt" -> "route_id,service_id,trip_id\nR,S,gap\nR,S,after\n"
                "stop_times.txt" -> "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n" +
                    "gap,02:50:00,02:50:00,A,1\ngap,03:00:00,03:00:00,B,2\n" +
                    "after,03:10:00,03:10:00,A,1\nafter,03:20:00,03:20:00,B,2\n"
                else -> null
            }?.byteInputStream()
        }, "clock", "clock", day)
        val net = OfflineNetwork(listOf(clockChange))
        val now = OfflineNetwork.epoch(day, 3 * 60)
        assertEquals(listOf("after", "gap"), net.departures(a.copy(id = "A"), now, horizonMinutes = 60).map { it.tripId })
    }

    @Test fun unknownIdFallsBackToTheSameNameNearby() {
        val station = Station("OTHER:123", "Bfalva", 47.1004, 19.1)
        assertTrue(network.departures(station, OfflineNetwork.epoch(today, 7 * 60)).isNotEmpty())
        assertThrows(OfflineNotCovered::class.java) { network.departures(Station("x", "Sehol", 46.0, 18.0), 0L) }
    }

    @Test fun plansAcrossFeedsWithAWalkingTransfer() {
        val journeys = network.plan(a, d, LocalDateTime.of(today, java.time.LocalTime.of(7, 50)), arriveBy = false)
        val first = journeys.first()
        assertTrue(first.offline)
        assertEquals(listOf("RAIL", "WALK", "BUS"), first.legs.map { it.mode })
        assertEquals("1:T1", first.legs[0].tripId); assertEquals("b:late", first.legs[2].tripId)
        assertEquals(OfflineNetwork.epoch(today, 8 * 60), first.start)
        assertEquals(OfflineNetwork.epoch(today, 9 * 60), first.end)
        assertEquals(1, first.changes)
        assertTrue(first.legs.zipWithNext().all { (x, y) -> x.end <= y.start })
    }

    @Test fun arriveBySearchKeepsTheLatestJourneyInTime() {
        val journeys = network.plan(a, d, LocalDateTime.of(today, java.time.LocalTime.of(9, 10)), arriveBy = true)
        assertTrue(journeys.isNotEmpty())
        assertTrue(journeys.all { it.end <= OfflineNetwork.epoch(today, 9 * 60 + 10) })
        assertEquals(OfflineNetwork.epoch(today, 8 * 60), journeys.last().start)
    }

    @Test fun tripJsonMatchesTheProviderShape() {
        val trip = network.trip("1:T1", today.toString())!!
        assertTrue(trip.getBoolean("offline"))
        val times = trip.getJSONArray("stoptimes")
        assertEquals(3, times.length())
        val second = times.getJSONObject(1)
        assertEquals(8 * 3600 + 31 * 60, second.getInt("scheduledDeparture"))
        assertFalse(second.getBoolean("realtime"))
        assertEquals("1:B", second.getJSONObject("stop").getString("gtfsId"))
        assertEquals("003A7E", trip.getJSONObject("route").getString("color"))
        assertNull(network.trip("missing", today.toString()))
    }

    @Test fun routeTimetablesListDirectionsAndDepartures() {
        val route = network.routes("12").first()
        assertEquals("12", route.label); assertEquals("BUS", route.mode)
        val direction = network.directions(route.ref).single()
        assertEquals("Dtelep", direction.terminus)
        val times = network.routeDepartures(route.ref, direction, "b:B", today)
        assertEquals(listOf("b:early", "b:late"), times.map { it.tripId })
        assertEquals("Dtelep", times.first().destination)
        assertEquals(OfflineNetwork.epoch(today, 8 * 60 + 50), times.first().arrival)
    }

    @Test fun stationTimetablesIncludeEveryPlatform() {
        // Trips leave from platform 1:A_1, but the timetable is chosen at station level (1:A).
        val route = network.routes("").first { it.ref.feed == 0 }
        val direction = network.directions(route.ref).single()
        assertEquals(listOf("1:A", "1:B", "1:C"), direction.stops.map { it.id })
        val times = network.routeDepartures(route.ref, direction, "1:A", today)
        assertEquals(listOf("1:T1", "1:T2", "1:N"), times.map { it.tripId })
        assertEquals("2", times.first().platform)
        assertEquals(times, network.routeDepartures(route.ref, direction, "1:A_1", today))
    }

    /** Opt-in: OFFLINE_GTFS_SAMPLE=volan.zip prints line 8097 (Mór – Székesfehérvár) from real data. */
    @Test fun realLineTimetable() {
        val path = System.getenv("OFFLINE_GTFS_SAMPLE")?.split(File.pathSeparator)?.firstOrNull { "volan" in it } ?: return
        val now = LocalDate.now(Budapest)
        val real = OfflineNetwork(listOf(GtfsCompactor.compact(File(path), "v", "v", now)))
        val route = real.routes("8097").first()
        real.directions(route.ref).forEach { d ->
            val from = d.stops.first()
            val times = real.routeDepartures(route.ref, d, from.id, now.plusDays(1))
            println("OFFLINE 8097 → ${d.terminus}: ${d.stops.size} stops, from ${from.name} ${times.size} departures: ${times.take(6).joinToString { timeLabel(it.time) + "→" + it.destination }}")
            assertTrue(times.isNotEmpty())
        }
        println("OFFLINE lines listed: ${real.routes("").size}")
    }

    @Test fun shortWorkingsAndReturnTripsFormTwoDirections() {
        val line = feed("l", mapOf(
            "routes.txt" to "route_id,route_short_name,route_type\nL,8097,3\n", "calendar.txt" to calendar,
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon,location_type,parent_station\n" +
                "M,Mór,47.37,18.21,1,\nM_1,Mór,47.37,18.21,0,M\nM_2,Mór,47.37,18.21,0,M\nK,Csókakő,47.35,18.27,0,\nS,Székesfehérvár,47.19,18.41,0,\n",
            "trips.txt" to "route_id,service_id,trip_id\nL,S,full\nL,S,short\nL,S,back\nL,S,otherPlatform\n",
            "stop_times.txt" to "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n" +
                "full,06:00:00,06:00:00,M_1,1\nfull,06:15:00,06:15:00,K,2\nfull,06:50:00,06:50:00,S,3\n" +
                "short,07:00:00,07:00:00,M_2,1\nshort,07:15:00,07:15:00,K,2\n" +
                "otherPlatform,08:00:00,08:00:00,M_2,1\notherPlatform,08:15:00,08:15:00,K,2\notherPlatform,08:50:00,08:50:00,S,3\n" +
                "back,09:00:00,09:00:00,S,1\nback,09:35:00,09:35:00,K,2\nback,09:50:00,09:50:00,M_1,3\n",
        ))
        val net = OfflineNetwork(listOf(line))
        val route = net.routes("8097").single()
        val directions = net.directions(route.ref)
        assertEquals(listOf("Székesfehérvár", "Mór"), directions.map { it.terminus })
        val fromMor = net.routeDepartures(route.ref, directions[0], "M", today)
        assertEquals(listOf("full", "short", "otherPlatform"), fromMor.map { it.tripId })
        assertEquals("Csókakő", fromMor[1].destination)
        assertEquals(listOf("back"), net.routeDepartures(route.ref, directions[1], "S", today).map { it.tripId })
        assertTrue("no boarding at the terminus", net.routeDepartures(route.ref, directions[0], "S", today).isEmpty())
    }

    @Test fun catalogGroupsNamesAndUrls() {
        val feeds = OfflineFeedCatalog.parse(File("src/main/assets/offline/hu-feeds.json").readText())
        assertTrue(feeds.size > 50)
        assertEquals("mav", feeds.first().id)
        val gyongyos = feeds.single { it.id == "volan-gyongyos" }
        assertEquals("Gyöngyös helyi járatai (Volánbusz)", gyongyos.name)
        assertEquals("Szeged helyi járatai (Volánbusz)", feeds.single { it.id == "volan-szeged" }.name)
        assertEquals(OfflineFeedGroup.Coach, feeds.single { it.id == "volan-helykozi" }.group)
        assertEquals(OfflineFeedGroup.Other, feeds.single { it.id == "bkk-funicular" }.group)
        assertEquals("https://api.transitous.org/gtfs/hu_Kecskem%C3%A9t.gtfs.zip", feeds.single { it.id == "Kecskemét" }.url)
        assertNull("authenticated sources are only fetched through Transitous", feeds.single { it.id == "mav-gyermekvasut" }.originalUrl)
        assertEquals("Kecskemét helyi járatai", feeds.single { it.id == "Kecskemét" }.name)
    }

    /** Opt-in: OFFLINE_GTFS_SAMPLE=mav.zip;volan.zip;dkv.zip plans Budapest-Keleti → Debrecen, Szalók utca on real data. */
    @Test fun realNetworkPlanningSpeed() {
        val paths = System.getenv("OFFLINE_GTFS_SAMPLE")?.split(File.pathSeparator)?.takeIf { it.size >= 3 } ?: return
        val now = LocalDate.now(Budapest)
        val tables = paths.mapIndexed { i, p -> GtfsCompactor.compact(File(p), "f$i", "f$i", now) }
        val downloads = tables.map { table -> java.io.ByteArrayOutputStream().also(table::write).toByteArray() }
        fun <T> measure(label: String, block: () -> T): T {
            val started = System.nanoTime()
            return block().also { println("OFFLINE $label: ${(System.nanoTime() - started) / 1_000_000.0} ms") }
        }
        val from = Station("1:005510017", "Budapest-Keleti", 47.50028, 19.084167)
        val to = Station("debrecen:SP531", "Debrecen, Szalók utca", 47.5496, 21.6605)
        val date = now.plusDays(1)
        val start = date.atTime(7, 0)
        val deadline = date.atTime(12, 0)
        val boardTime = OfflineNetwork.epoch(date, 8 * 60)
        val real = measure("load") { OfflineNetwork(downloads.map { it.inputStream().use(OfflineTimetable::read) }) }
        fun plan(time: LocalDateTime, arriveBy: Boolean) =
            real.plan(from, to, time, arriveBy).map { it.copy(fetchedAt = 0) }
        val journeys = measure("plan (first)") { plan(start, false) }
        val board = measure("board (first)") { real.departures(from, boardTime) }
        val stops = measure("stop search (first)") { real.searchStops("debre") }
        val arriveBy = measure("arrive by (first)") { plan(deadline, true) }
        repeat(5) {
            assertEquals(journeys, measure("plan (repeat)") { plan(start, false) })
            assertEquals(arriveBy, measure("arrive by (repeat)") { plan(deadline, true) })
            assertEquals(board, measure("board (repeat)") { real.departures(from, boardTime) })
            assertEquals(stops, measure("stop search (repeat)") { real.searchStops("debre") })
        }
        assertTrue(journeys.isNotEmpty())
        assertTrue(arriveBy.isNotEmpty())
        assertTrue(board.isNotEmpty())
        assertTrue(stops.isNotEmpty())
    }

    @Test fun unsortedStopTimesAreGroupedOnASecondPass() {
        val interleaved = feed("x", mapOf(
            "routes.txt" to "route_id,route_type\nR,3\n", "calendar.txt" to calendar,
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\nP,P,47,19\nQ,Q,47.01,19\n",
            "trips.txt" to "route_id,service_id,trip_id\nR,S,t1\nR,S,t2\n",
            "stop_times.txt" to "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n" +
                "t1,08:00:00,08:00:00,P,1\nt2,09:00:00,09:00:00,P,1\nt1,08:10:00,08:10:00,Q,2\nt2,09:10:00,09:10:00,Q,2\n",
        ))
        assertEquals(2, interleaved.tripCount)
        assertEquals(8 * 60 + 10, interleaved.arrival(interleaved.tripIndex.getValue("t1"), 1))
    }
}

class OfflineStopSearchTest {
    @Test fun platformsFoldIntoTheirStationAndAccentsDoNotMatter() {
        val table = SampleGtfs.timetable()
        val found = OfflineNetwork(listOf(table)).searchStops("avaros")
        assertEquals(listOf("A"), found.map { it.id })
        assertEquals("Station", found.single().type)
        assertTrue("RAIL" in found.single().modes)
        assertTrue(OfflineNetwork(listOf(table)).searchStops("cvaros vasut").single().name.startsWith("Cváros"))
    }
}

class OfflineJourneyFilterTest {
    private fun journey(start: Long, end: Long, rides: Int, id: String): Journey {
        val st = Station("", "", 0.0, 0.0)
        val legs = (0 until rides).map { Leg("BUS", start + it, end, st, st, id, "$id$it", false, 0, emptyList(), "") }
        return Journey(((end - start) / 1000).toInt(), legs)
    }
    @Test fun earlierFeedersToTheSameArrivalAreDropped() {
        val network = OfflineNetwork(emptyList())
        val kept = network.dominantJourneys(listOf(
            journey(1000, 9000, 3, "a"), journey(2000, 9000, 3, "b"), journey(3000, 9000, 3, "c"), journey(4000, 12000, 1, "direct")))
        assertEquals(listOf("c", "direct"), kept.map { it.legs.first().route })
    }
}
