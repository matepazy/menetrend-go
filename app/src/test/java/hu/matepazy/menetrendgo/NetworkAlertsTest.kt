package hu.matepazy.menetrendgo

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NetworkAlertsTest {
    private val data = JSONObject(javaClass.classLoader!!.getResource("network-alerts-2026-11-01.json").readText()).getJSONObject("data")
    // 2026-11-01 19:58 Budapest, when the sample was recorded.
    private val now = 1_793_559_488_000L
    private val alerts = NetworkAlert.parseAll(data, now)

    @Test fun dropsPerTrainDelayReasons() {
        assertTrue(alerts.none { it.alert.title.contains("miatti késés") })
        assertEquals(2, alerts.count { it.operator == "1" })
    }

    @Test fun currentNoticesComeFirstWithMavLeading() {
        val firstUpcoming = alerts.indexOfFirst { it.upcoming(now) }
        assertTrue(firstUpcoming > 0)
        assertTrue(alerts.drop(firstUpcoming).all { it.upcoming(now) })
        assertEquals("MÁV", alerts.first().operatorName)
        assertTrue(alerts.first().stations.isNotEmpty() && alerts.first().lines.isEmpty())
        assertTrue(alerts.any { "Budapest-Kelenföld" in it.stations })
    }

    @Test fun repeatedWeekendNoticesCollapseToTheEarliest() {
        val replacement = alerts.filter { it.lines.any { line -> line.name == "4-6" } }
        assertEquals(1, replacement.size)
        assertEquals(1_794_869_400L, replacement.single().alert.startSeconds)
    }

    @Test fun usesHungarianTextAndCapitalisesBkkFragments() {
        val m2 = alerts.single { it.lines.any { line -> line.name == "M2" } }
        assertTrue(m2.alert.title, m2.alert.title.startsWith("Autóbuszos pótlás"))
        assertEquals("E41F18", m2.lines.single().color)
        assertEquals("BKK", m2.operatorName)
        val volan = alerts.filter { it.operator == "VBUSZ" }
        assertTrue(volan.isNotEmpty() && volan.all { "Route" !in it.alert.title })
        assertEquals("Terelés", volan.first().effectLabel)
    }

    @Test fun linesSortNaturally() {
        val parsed = NetworkAlert.parse(JSONObject("""{"id":"x","feed":"BKK","alertHeaderText":"teszt","entities":[
            {"__typename":"Route","shortName":"49","mode":"TRAM"},{"__typename":"Route","shortName":"7E","mode":"BUS"},
            {"__typename":"Route","shortName":"7","mode":"BUS"},{"__typename":"Route","shortName":"M4","mode":"SUBWAY"},
            {"__typename":"Route","shortName":"M2","mode":"SUBWAY"},{"__typename":"Route","shortName":"4","mode":"TRAM"}]}"""))!!
        assertEquals(listOf("4", "7", "7E", "49", "M2", "M4"), parsed.lines.map { it.name })
    }

    @Test fun endedNoticesAndOperatorCounts() {
        assertTrue(alerts.all { it.alert.endSeconds == null || it.alert.endSeconds!! * 1000 >= now })
        val ops = networkAlertOperators(alerts)
        assertEquals(alerts.size, ops.sumOf { it.second })
        assertEquals("1", ops.first().first)
    }

    @Test fun queryAsksForLinesAndStations() {
        assertTrue(MavQueries.networkAlerts.contains("... on StopOnRoute {route {shortName mode color textColor} stop {name}}"))
        assertTrue(MavQueries.networkAlerts.contains("feed"))
    }
}
