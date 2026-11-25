package hu.matepazy.menetrendgo

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class StationIdsTest {
    @Test fun sourcesAndLocalIds() {
        assertEquals("mav", StationIds.source("mav:1:0055"))
        assertEquals("1:0055", StationIds.local("mav:1:0055"))
        assertEquals("gtfs", StationIds.source("gtfs:hu_volan:12"))
        assertEquals("hu_volan:12", StationIds.local("gtfs:hu_volan:12"))
        assertEquals("osm", StationIds.source("osm:node/1"))
        // Bare ids, including MÁVPlusz feed prefixes that look like namespaces, are MÁVPlusz ids.
        assertEquals("mav", StationIds.source("hkir:hkir_8070"))
        assertEquals("hkir:hkir_8070", StationIds.local("hkir:hkir_8070"))
        assertEquals("BKK:F00123", StationIds.local("BKK:F00123"))
        assertNull(StationIds.source(""))
    }

    @Test fun migrationIsIdempotentAndLeavesPlacesAlone() {
        assertEquals("mav:1:0055", StationIds.migrate("1:0055"))
        assertEquals("mav:hkir:hkir_8070", StationIds.migrate("hkir:hkir_8070"))
        assertEquals("mav:1:0055", StationIds.migrate(StationIds.migrate("1:0055")))
        assertEquals("osm:node/1", StationIds.migrate("osm:node/1"))
        assertEquals("gtfs:x", StationIds.migrate("gtfs:x"))
        assertEquals("", StationIds.migrate(""))
    }

    private fun station(id: String, name: String = "Mór") = Station(id, name, 47.37, 18.2).json()

    @Test fun savedPairsSearchCacheAndUsageAreRewritten() {
        val pairs = JSONArray().put(JSONObject().put("from", station("1:0055")).put("to", station("osm:node/9", "Dóm tér"))
            .put("name", "Haza")).toString()
        val migrated = JSONArray(DataMigration.migrateStationPairs(pairs)).getJSONObject(0)
        assertEquals("mav:1:0055", Station.parse(migrated.getJSONObject("from")).id)
        assertEquals("osm:node/9", Station.parse(migrated.getJSONObject("to")).id)
        assertEquals("Haza", migrated.getString("name"))
        assertEquals(DataMigration.migrateStationPairs(pairs), DataMigration.migrateStationPairs(DataMigration.migrateStationPairs(pairs)))

        val cache = JSONArray().put(JSONObject().put("query", "mor").put("time", 1).put("stations", JSONArray().put(station("1:0055")))).toString()
        assertEquals("mav:1:0055", JSONArray(DataMigration.migrateSearchCache(cache)).getJSONObject(0).getJSONArray("stations").getJSONObject(0).getString("id"))

        val usage = JSONObject(DataMigration.migrateUsage(JSONObject().put("1:0055", 3).put("mav:1:0055", 2).put("osm:node/9", 1).toString()))
        assertEquals(5, usage.getInt("mav:1:0055"))
        assertEquals(1, usage.getInt("osm:node/9"))
        assertFalse(usage.has("1:0055"))

        assertEquals("mav:1:0055", JSONObject(DataMigration.migrateStationJson(station("1:0055").toString())!!).getString("id"))
        assertEquals("not json", DataMigration.migrateStationPairs("not json"))
        assertNull(DataMigration.migrateStationJson("not json"))
    }

    @Test fun mavItinerariesAreNamespacedOnLoadAndAppWrittenOnesAreKept() {
        val mav = JSONObject("""{"duration":60,"legs":[{"mode":"RAIL","startTime":1000,"endTime":61000,"realTime":false,
            "from":{"name":"Mór","lat":47.37,"lon":18.2,"stop":{"gtfsId":"1:0070"}},"to":{"name":"Kisbér","lat":47.5,"lon":18.0,"stop":{"gtfsId":"1:0071"}},
            "intermediateStops":[{"gtfsId":"1:0075","name":"Bakonysárkány","lat":47.4,"lon":18.1}],
            "route":{"shortName":"S"},"trip":{"gtfsId":"1:T"}}]}""")
        val journey = Journey.parse(mav)
        assertEquals("mav:1:0070", journey.legs[0].from.id)
        assertEquals("mav:1:0075", journey.legs[0].intermediate.single().id)
        // A minimal app-written itinerary (offline journeys, tracking sessions) keeps whatever source each stop had.
        val offline = journey.copy(legs = journey.legs.map { it.copy(from = it.from.copy(id = "gtfs:1:0070")) })
        val reloaded = Journey.parse(itineraryJson(offline))
        assertEquals("gtfs:1:0070", reloaded.legs[0].from.id)
        assertEquals("mav:1:0071", reloaded.legs[0].to.id)
        assertEquals("1:T", reloaded.legs[0].tripId)
    }

    @Test fun onlyMavplusStopsAreSentToMavplusByIdEverythingElseByCoordinates() {
        val time = LocalDateTime.of(2026, 11, 25, 12, 0)
        val mav = Station("mav:1:0055", "Budapest-Nyugati", 47.51, 19.05)
        val legacy = Station("1:0055", "Budapest-Nyugati", 47.51, 19.05)
        val offline = Station("gtfs:hu_volan:9", "Eger", 47.9, 20.37)
        assertTrue(MavQueries.plan(mav, offline, time, false).contains("fromPlace:\"Budapest-Nyugati::1:0055\""))
        assertTrue(MavQueries.plan(legacy, offline, time, false).contains("fromPlace:\"Budapest-Nyugati::1:0055\""))
        assertTrue(MavQueries.plan(mav, offline, time, false).contains("to:{lat:47.9,lon:20.37}"))
    }

    @Test fun offlineOnlyStopsNeverAskMavplus() = runBlocking<Unit> {
        var requests = 0
        val repository = TransitRepository(MavClient { requests++; HttpResponse(200, "{}") }, UtasClient { HttpResponse(500, "") },
            { null }, TransitCache())
        assertThrows(ProviderUnavailable::class.java) { runBlocking { repository.stopDepartures("gtfs:hu_volan:9") } }
        assertEquals(0, requests)
    }
}
