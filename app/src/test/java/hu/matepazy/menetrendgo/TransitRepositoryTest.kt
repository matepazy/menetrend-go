package hu.matepazy.menetrendgo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

class TransitCacheTest {
    private var now = 1_000L
    private val cache = TransitCache(maxEntries = 2) { now }

    @Test fun freshValuesAreReusedUntilTheyAgeOrAreForced() = runBlocking<Unit> {
        var loads = 0
        suspend fun get(force: Boolean = false) = cache.get("k", 100, force) { ++loads }
        assertEquals(1, get()); now += 99
        assertEquals(1, get()); now += 1
        assertEquals(2, get())
        assertEquals(3, get(force = true))
    }

    @Test fun concurrentCallersShareOneLoad() = runBlocking<Unit> {
        val loads = AtomicInteger(); val gate = CompletableDeferred<Unit>()
        val callers = (1..5).map { async { cache.get("k", 100) { loads.incrementAndGet(); gate.await(); "v" } } }
        repeat(5) { yield() }
        gate.complete(Unit)
        assertEquals(List(5) { "v" }, callers.awaitAll())
        assertEquals(1, loads.get())
    }

    @Test fun failuresFallBackToARecentValueOnlyWithinTheStaleWindow() = runBlocking<Unit> {
        cache.get("k", 10) { "old" }
        now += 50
        assertEquals("old", cache.get("k", 10, staleMillis = 60) { error("down") })
        now += 20
        assertThrows(IllegalStateException::class.java) { runBlocking { cache.get("k", 10, staleMillis = 60) { error("down") } } }
        assertThrows(IllegalStateException::class.java) { runBlocking { cache.get("x", 10) { error("down") } } }
    }

    @Test fun cancellingTheLoaderLetsWaitingCallersRetry() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>()
        val owner = launch { cache.get("k", 100) { started.complete(Unit); CompletableDeferred<String>().await() } }
        started.await()
        val waiter = async { cache.get("k", 100) { "retried" } }
        yield()
        owner.cancel()
        assertEquals("retried", waiter.await())
    }

    @Test fun theLeastRecentlyUsedEntryIsEvicted() = runBlocking<Unit> {
        var loads = 0
        cache.get("a", 1_000) { ++loads }; cache.get("b", 1_000) { ++loads }
        cache.get("a", 1_000) { ++loads }          // a is now the most recent
        cache.get("c", 1_000) { ++loads }          // evicts b
        assertEquals(3, loads)
        cache.get("a", 1_000) { ++loads }; assertEquals(3, loads)
        cache.get("b", 1_000) { ++loads }; assertEquals(4, loads)
    }
}

class TransitRepositoryTest {
    private val today = LocalDate.of(2026, 11, 24)
    @After fun reset() = OfflineStatus.providerReachable()

    private val mavRequests = AtomicInteger()
    private var mavCode = 200
    private val mav = MavClient { request ->
        mavRequests.incrementAndGet()
        val body = request.body.orEmpty()
        HttpResponse(mavCode, if (mavCode != 200) "" else when {
            "vehiclePositions(" in body -> JSONObject().put("data", JSONObject().put("vehiclePositions", JSONArray().put(JSONObject(
                """{"vehicleId":"m1","lat":47.5,"lon":19.0,"licensePlate":"ABC-123","lastUpdated":1,
                   "trip":{"gtfsId":"1:T1","tripShortName":"100","route":{"shortName":"S10","mode":"RAIL"}}}"""))))
            "trip(id:" in body && "1:T1" in body ->JSONObject().put("data", JSONObject().put("trip", JSONObject("""{"gtfsId":"1:T1","stoptimes":[
                {"stop":{"gtfsId":"1:A","name":"A","lat":47.0,"lon":19.0},"scheduledDeparture":28800}]}""")))
            else -> JSONObject().put("data", JSONObject())
        }.toString())
    }
    private var utasFails = false
    private val utas = UtasClient { _ ->
        if (utasFails) throw java.io.IOException("down")
        HttpResponse(200, JSONObject().put("code", 200).put("data", JSONObject().put("list", JSONArray())).toString())
    }
    private val calendar = "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\nS,1,1,1,1,1,1,1,20260101,20271231\n"
    private val network = OfflineNetwork(listOf(GtfsCompactor.compact(GtfsSource { mapOf(
        "routes.txt" to "route_id,route_short_name,route_long_name,route_type\nR,,Fővonal,2\n",
        "calendar.txt" to calendar,
        "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\n1:A,Aváros,47.0,19.0\n1:B,Bfalva,47.1,19.1\n",
        "trips.txt" to "route_id,service_id,trip_id,trip_headsign\nR,S,1:T1,Bfalva\nR,S,L1,Bfalva\n",
        "stop_times.txt" to "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n1:T1,08:00:00,08:00:00,1:A,1\n1:T1,08:30:00,08:30:00,1:B,2\n" +
            "L1,09:00:00,09:00:00,1:A,1\nL1,09:20:00,09:20:00,1:B,2\n",
    )[it]?.byteInputStream() }, "rail", "Vasút", today)))
    private var offlineAvailable = true
    private fun repository() = TransitRepository(mav, utas, { network.takeIf { offlineAvailable } }, TransitCache())

    @Test fun tripsComeFromMavplusAndAreCachedPerFreshness() = runBlocking<Unit> {
        val repo = repository()
        val trip = repo.trip("1:T1", today.toString())
        assertEquals(DataKind.Scheduled, trip.provenance.kind)
        repo.trip("1:T1", today.toString())
        assertEquals(1, mavRequests.get())
        repo.trip("1:T1", today.toString(), forceRefresh = true)
        assertEquals(2, mavRequests.get())
    }

    @Test fun unavailableMavplusFallsBackToOfflineTimetablesOnlyWhenTheyCoverIt() = runBlocking<Unit> {
        mavCode = 503
        val repo = repository()
        val trip = repo.trip("1:T1", today.toString())
        assertTrue(trip.offline)
        assertEquals("Vasút", trip.feedName)
        assertThrows(ProviderUnavailable::class.java) { runBlocking { repo.trip("1:missing", today.toString()) } }
        offlineAvailable = false
        assertThrows(ProviderUnavailable::class.java) { runBlocking { repo.trip("1:T1", today.toString()) } }
    }

    @Test fun offlineAnswersAreNeverCachedAsLiveOnes() = runBlocking<Unit> {
        val repo = repository()
        mavCode = 503
        assertTrue(repo.trip("1:T1", today.toString()).offline)
        mavCode = 200
        assertFalse(repo.trip("1:T1", today.toString()).offline)
    }

    @Test fun offlineOnlyNetworksJoinAWorkingLiveAnswer() = runBlocking<Unit> {
        val repo = repository()
        // MÁVPlusz answers but knows none of these: stops, journeys and trips come from the timetables.
        assertEquals(listOf("gtfs:1:B"), repo.stations("Bfalva").map { it.id })
        val a = Station("gtfs:1:A", "Aváros", 47.0, 19.0); val b = Station("gtfs:1:B", "Bfalva", 47.1, 19.1)
        val journeys = repo.plan(a, b, today.atTime(7, 0), false)
        assertTrue(journeys.isNotEmpty() && journeys.all { it.offline })
        assertTrue(repo.trip("L1", today.toString()).offline)
        assertFalse(repo.trip("1:T1", today.toString()).offline)
        offlineAvailable = false
        assertTrue(repo.stations("Bfalva").isEmpty())
        assertThrows(IllegalStateException::class.java) { runBlocking { repo.trip("L1", today.toString()) } }
    }

    @Test fun aggregatedVehiclesDegradeToOneSourceWithANotice() = runBlocking<Unit> {
        val repo = repository()
        utasFails = true
        val single = repo.vehicles(DataSourceMode.Aggregated, force = true)
        assertEquals(listOf("1:T1"), single.vehicles.map { it.id })
        assertEquals("Az utas.hu most nem érhető el, csak a MÁVPlusz járművei láthatók.", single.notice)
        mavCode = 503
        assertThrows(ProviderUnavailable::class.java) { runBlocking { repo.vehicles(DataSourceMode.Aggregated, force = true) } }
        mavCode = 200
        assertNull(repository().vehicles(DataSourceMode.Single).notice)
    }

    @Test fun cancellationIsNeverTurnedIntoAFallback() = runBlocking<Unit> {
        val repo = TransitRepository(MavClient { throw CancellationException() }, utas, { network }, TransitCache())
        assertThrows(CancellationException::class.java) { runBlocking { repo.trip("1:T1", today.toString()) } }
    }
}
