package hu.matepazy.menetrendgo

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class JourneyAlertsTest {
    private val day = LocalDate.of(2026, 11, 1)
    // 2026-11-01 14:48 and 15:10 Budapest (CET).
    private val start = 1_793_540_880L
    private val end = 1_793_542_200L

    private fun alert(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }

    @Test fun mavDescriptionOnlyAlertBecomesTheHeadline() {
        val parsed = MavAlert.parse(alert("id" to "a", "alertHeaderText" to "", "alertDescriptionText" to "Vonat műszaki hibája miatti késés",
            "effectiveStartDate" to start, "effectiveEndDate" to end, "alertSeverityLevel" to "WARNING"))!!
        assertEquals("Vonat műszaki hibája miatti késés", parsed.title)
        assertEquals("", parsed.body)
        assertTrue(parsed.warning)
        assertEquals("Érvényes 14:48–15:10", alertValidity(parsed, day))
    }

    @Test fun prefersHungarianTranslationAndKeepsDistinctBody() {
        val parsed = MavAlert.parse(alert("id" to "b", "alertHeaderText" to "Route 43 will operate on a diversion",
            "alertDescriptionText" to "<p>English</p>", "alertSeverityLevel" to "UNKNOWN_SEVERITY", "alertEffect" to "DETOUR",
            "alertHeaderTextTranslations" to JSONArray().put(JSONObject().put("language", "en").put("text", "Route 43"))
                .put(JSONObject().put("language", "hu").put("text", "Módosított útvonalon közlekedik a 43-as")),
            "alertDescriptionTextTranslations" to JSONArray().put(JSONObject().put("language", "hu").put("text", "<p>Terelés</p>"))))!!
        assertEquals("Módosított útvonalon közlekedik a 43-as", parsed.title)
        assertEquals("<p>Terelés</p>", parsed.body)
        assertTrue("Detours are disruptive even without a severity level", parsed.warning)
    }

    @Test fun dropsEmptyInactiveAndDuplicateAlertsAndRanksWarningsFirst() {
        val leg = JSONObject().put("alerts", JSONArray()
            .put(alert("id" to "info", "alertHeaderText" to "Megállóhely áthelyezése", "alertDescriptionText" to ""))
            .put(alert("id" to "warn", "alertDescriptionText" to "Pálya állapota miatti késés", "alertSeverityLevel" to "WARNING",
                "effectiveStartDate" to start, "effectiveEndDate" to end))
            .put(alert("id" to "warn2", "alertDescriptionText" to "<b>Pálya állapota</b> miatti késés", "alertSeverityLevel" to "WARNING"))
            .put(alert("id" to "empty", "alertHeaderText" to "", "alertDescriptionText" to "<p><br></p>"))
            .put(alert("id" to "past", "alertDescriptionText" to "Lejárt", "effectiveStartDate" to start - 7200, "effectiveEndDate" to start - 3600)))
        val alerts = MavAlert.parseAll(leg, start * 1000, end * 1000)
        assertEquals(listOf("warn", "info"), alerts.map { it.id })
        // The same notice may arrive on both the leg and its trip.
        leg.put("trip", JSONObject().put("alerts", JSONArray()
            .put(alert("id" to "warn", "alertDescriptionText" to "Pálya állapota miatti késés", "alertSeverityLevel" to "WARNING"))
            .put(alert("id" to "tripOnly", "alertDescriptionText" to "Társvasúttól átvett késés", "alertSeverityLevel" to "WARNING",
                "effectiveStartDate" to start + 60, "effectiveEndDate" to end + 3600))))
        assertEquals(listOf("warn", "tripOnly", "info"), MavAlert.parseAll(leg, start * 1000, end * 1000).map { it.id })
    }

    @Test fun validityAddsDatesOnlyAwayFromTheTravelDay() {
        val longTerm = MavAlert("x", "Áthelyezett megálló", "", startSeconds = 1_740_567_600L, endSeconds = 1_801_069_200L)
        assertEquals("Érvényes 2025. febr. 26. – 2027. jan. 27.", alertValidity(longTerm, day))
        assertEquals("Érvényes 14:48 – visszavonásig", alertValidity(longTerm.copy(startSeconds = start, endSeconds = null), day))
        assertEquals("Érvényes eddig: nov. 2. 01:00", alertValidity(longTerm.copy(startSeconds = null, endSeconds = 1_793_577_600L), day))
        assertEquals("Érvényes júl. 1. – visszavonásig", alertValidity(longTerm.copy(startSeconds = 1_782_856_800L, endSeconds = null), day))
        assertNull(alertValidity(longTerm.copy(startSeconds = null, endSeconds = null), day))
    }

    @Test fun planQueryRequestsLegAlertsWithTranslations() {
        val query = MavQueries.plan(47.5, 19.0, 47.6, 19.1, java.time.LocalDateTime.of(2026, 11, 1, 14, 0), false)
        assertTrue(query.contains("alerts {id alertHeaderText alertDescriptionText"))
        assertTrue(query.contains("alertDescriptionTextTranslations {language text}"))
    }

    @Test fun plainTextStripsMarkupAndEntities() {
        assertEquals("Busz 7 & villamos 4", plainAlertText("<p><strong>Busz 7</strong>&nbsp;&amp;<br/>villamos 4</p>"))
    }
}
