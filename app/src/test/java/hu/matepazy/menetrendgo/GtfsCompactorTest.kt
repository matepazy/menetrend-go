package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

/** A tiny two-operator network: a railway A–B–C and a bus line C–D, with the GTFS quirks we rely on. */
internal object SampleGtfs {
    val today: LocalDate = LocalDate.of(2026, 11, 24) // Tuesday
    val files = mapOf(
        "agency.txt" to "agency_id,agency_name\nR,Vasút Zrt.\nB,Busz Kft.\n",
        // Columns deliberately out of the usual order.
        "routes.txt" to "route_type,route_id,agency_id,route_short_name,route_long_name,route_color,route_text_color\n" +
            "2,R1,R,,Fővonal,003A7E,FFFFFF\n3,B1,B,12,\"Állomás, C – D\",009EE3,FFFFFF\n3,B2,B,99,Sosem,,\n",
        "calendar.txt" to "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n" +
            "WD,1,1,1,1,1,0,0,20260101,20271231\nNEVER,0,0,0,0,0,0,0,20260101,20271231\n",
        "calendar_dates.txt" to "service_id,date,exception_type\nWD,20261125,2\nSAT,20261128,1\nOLD,20250101,1\n",
        "stops.txt" to "﻿stop_id,stop_name,stop_lat,stop_lon,location_type,parent_station,platform_code\n" +
            "A,Aváros,47.0,19.0,1,,\nA_1,Aváros,47.0,19.0,0,A,1\nB,Bfalva,47.1,19.1,0,,\nC,\"Cváros, vasútállomás\",47.2,19.2,0,,\n" +
            "D,Dtelep,47.3,19.3,0,,\nZ,Unused,46.0,18.0,0,,\nE,Entrance,47.0,19.0,2,A,\n",
        "trips.txt" to "route_id,service_id,trip_id,trip_headsign,trip_short_name\n" +
            "R1,WD,T1,Cváros,SZ 100\nR1,WD,T2,Cváros,SZ 102\nR1,WD,T3,Cváros,IC 900\nB1,SAT,T4,Dtelep,\nB2,NEVER,T5,Sehova,\n" +
            "R1,WD,T6,Cváros,SZ 104\n",
        "stop_times.txt" to "trip_id,stop_sequence,stop_id,arrival_time,departure_time,pickup_type,drop_off_type\n" +
            // T1 slow train, rows out of order and a blank intermediate time.
            "T1,3,C,08:40:00,08:40:00,,1\nT1,1,A_1,08:00:00,08:00:00,,\nT1,2,B,,,0,0\n" +
            "T2,1,A_1,09:00:00,09:00:00,,\nT2,2,B,09:20:00,09:21:00,,\nT2,3,C,09:40:00,09:40:00,,1\n" +
            // T3 departs after T1 but arrives first: it overtakes, so it needs its own lane.
            "T3,1,A_1,08:10:00,08:10:00,,\nT3,2,B,08:15:00,08:15:00,,\nT3,3,C,08:30:00,08:30:00,,1\n" +
            "T4,1,C,24:50:00,24:50:00,,\nT4,2,D,25:05:00,25:05:00,1,\n" +
            "T5,1,C,10:00:00,10:00:00,,\nT5,2,D,10:10:00,10:10:00,,\n" +
            "T6,1,A_1,10:00:00,10:00:00,,\nT6,2,B,10:20:00,10:21:00,,\nT6,3,C,10:40:00,10:40:00,,1\n",
    )
    val source = GtfsSource { name -> files[name]?.byteInputStream(Charsets.UTF_8) }
    fun timetable(id: String = "test") = GtfsCompactor.compact(source, id, "Teszt", today)
}

class GtfsCompactorTest {
    private val table = SampleGtfs.timetable()

    @Test fun csvHandlesQuotesBomAndReorderedColumns() {
        val csv = GtfsCsv("﻿a,b\n\"x, \"\"y\"\"\",2\r\n\n3,4".reader())
        assertEquals(0, csv.column("a"))
        assertTrue(csv.next()); assertEquals("x, \"y\"", csv[0]); assertEquals("2", csv[1])
        assertTrue(csv.next()); assertEquals("4", csv[csv.column("b")])
        assertFalse(csv.next())
    }

    @Test fun keepsOnlyTripsRunningInTheWindow() {
        val ids = (0 until table.tripCount).map { table.text(table.tripId[it]) }.toSet()
        assertEquals(setOf("T1", "T2", "T3", "T4", "T6"), ids)
        assertEquals(SampleGtfs.today.minusDays(1), table.firstDay)
        val t1 = table.tripIndex.getValue("T1")
        assertTrue(table.runsOn(t1, SampleGtfs.today))
        assertFalse("calendar_dates removal", table.runsOn(t1, SampleGtfs.today.plusDays(1)))
        assertFalse("weekend", table.runsOn(t1, LocalDate.of(2026, 11, 28)))
        assertTrue("calendar_dates addition", table.runsOn(table.tripIndex.getValue("T4"), LocalDate.of(2026, 11, 28)))
        assertFalse("outside window", table.runsOn(t1, SampleGtfs.today.plusDays(60)))
    }

    @Test fun dropsUnusedStopsButKeepsStationParents() {
        val ids = (0 until table.stopCount).map { table.text(table.stopId[it]) }.toSet()
        assertEquals(setOf("A", "A_1", "B", "C", "D"), ids)
        val platform = table.stopIndex.getValue("A_1")
        assertEquals(table.stopIndex.getValue("A"), table.stopParent[platform])
        assertEquals("1", table.text(table.stopPlatform[platform]))
        assertEquals("Cváros, vasútállomás", table.text(table.stopName[table.stopIndex.getValue("C")]))
        assertEquals(19.3, table.lon(table.stopIndex.getValue("D")), 1e-6)
    }

    @Test fun ordersStopsAndInterpolatesBlankTimes() {
        val t1 = table.tripIndex.getValue("T1"); val p = table.tripPattern[t1]
        assertEquals(listOf("A_1", "B", "C"), (0 until table.patternSize(p)).map { table.text(table.stopId[table.patternStop(p, it)]) })
        assertEquals(8 * 60 + 20, table.arrival(t1, 1))
        assertEquals(8 * 60 + 40, table.arrival(t1, 2))
        assertFalse("drop_off_type=1 at the terminus", table.canAlight(p, 2))
        assertFalse("no boarding at the last stop", table.canBoard(p, 2))
        assertTrue(table.canBoard(p, 0))
    }

    @Test fun overtakingTripsGetTheirOwnPattern() {
        val p1 = table.tripPattern[table.tripIndex.getValue("T1")]
        val p2 = table.tripPattern[table.tripIndex.getValue("T2")]
        val p3 = table.tripPattern[table.tripIndex.getValue("T3")]
        assertEquals(p1, p2)
        assertNotEquals(p1, p3)
        for (p in 0 until table.patternCount) {
            val trips = table.patternTripStart[p] until table.patternTripStart[p + 1]
            for (i in 0 until table.patternSize(p)) trips.zipWithNext().forEach { (a, b) -> assertTrue(table.departure(a, i) <= table.departure(b, i)) }
        }
    }

    @Test fun keepsTimesPastMidnightAndRouteMetadata() {
        val t4 = table.tripIndex.getValue("T4")
        assertEquals(24 * 60 + 50, table.departure(t4, 0))
        val route = table.patternRoute[table.tripPattern[t4]]
        assertEquals("12", table.text(table.routeShort[route]))
        assertEquals("Állomás, C – D", table.text(table.routeLong[route]))
        assertEquals(0x009EE3, table.routeColor[route])
        assertEquals("Busz Kft.", table.text(table.agencyName[table.routeAgency[route]]))
        assertEquals("BUS", table.mode(route))
    }

    @Test fun binaryRoundTripPreservesEverything() {
        val bytes = ByteArrayOutputStream().also { table.write(it) }.toByteArray()
        val copy = OfflineTimetable.read(ByteArrayInputStream(bytes))
        assertEquals(table.feedId, copy.feedId); assertEquals(table.firstDay, copy.firstDay); assertEquals(table.dayCount, copy.dayCount)
        assertArrayEquals(table.strings, copy.strings)
        assertArrayEquals(table.times, copy.times); assertArrayEquals(table.tripTimes, copy.tripTimes)
        assertArrayEquals(table.stopLat, copy.stopLat); assertArrayEquals(table.stopParent, copy.stopParent)
        assertArrayEquals(table.patternStops, copy.patternStops); assertArrayEquals(table.patternStopFlags, copy.patternStopFlags)
        assertArrayEquals(table.tripDays, copy.tripDays); assertArrayEquals(table.routeColor, copy.routeColor)
    }

    @Test fun modesFollowExtendedRouteTypes() {
        assertEquals("RAIL", gtfsMode(102)); assertEquals("RAIL_REPLACEMENT_BUS", gtfsMode(714)); assertEquals("COACH", gtfsMode(200))
        assertEquals("TRAMTRAIN", gtfsMode(403)); assertEquals("TROLLEYBUS", gtfsMode(800)); assertEquals("TRAM", gtfsMode(900))
        assertEquals("SUBURBAN_RAILWAY", gtfsMode(109)); assertEquals("BUS", gtfsMode(704))
    }

    /** Opt-in measurement against a real feed: OFFLINE_GTFS_SAMPLE=/path/feed.zip ./gradlew :app:testDebugUnitTest --tests '*GtfsCompactorTest.realFeed*' -i */
    @Test fun realFeedSizeAndSpeed() {
        val path = System.getenv("OFFLINE_GTFS_SAMPLE") ?: return
        path.split(File.pathSeparator).forEach { zip ->
            val started = System.nanoTime()
            val real = GtfsCompactor.compact(File(zip), "sample", "Minta", LocalDate.now(Budapest))
            val compacted = System.nanoTime()
            val bytes = ByteArrayOutputStream().also { real.write(it) }.toByteArray()
            val written = System.nanoTime()
            val read = OfflineTimetable.read(ByteArrayInputStream(bytes))
            val loaded = System.nanoTime()
            println("OFFLINE $zip: zip=${File(zip).length() / 1024} KiB -> ${bytes.size / 1024} KiB; trips=${real.tripCount} patterns=${real.patternCount} stops=${real.stopCount} times=${real.times.size}; " +
                "compact=${(compacted - started) / 1_000_000} ms write=${(written - compacted) / 1_000_000} ms read=${(loaded - written) / 1_000_000} ms")
            assertEquals(real.tripCount, read.tripCount)
        }
    }
}
