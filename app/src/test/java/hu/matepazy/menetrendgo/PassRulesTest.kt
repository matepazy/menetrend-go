package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PassRulesTest {
    private val pass=Pass("test",null,LocalDate.of(2026,10,1),LocalDate.of(2026,10,22))
    private fun at(day: String)=java.time.LocalDateTime.parse(day).atZone(Budapest).toInstant().toEpochMilli()
    private fun rail(start: String,end: String): Journey {
        val s=Station("a","A",47.0,19.0)
        return Journey(3600,listOf(Leg("RAIL",at(start),at(end),s,s,"IC","1:trip",false,0,emptyList(),"")))
    }
    @Test fun expirationUsesBudapestCalendarDay() {
        assertTrue(pass.validAt(at("2026-10-22T23:59:59")))
        assertFalse(pass.validAt(at("2026-10-23T00:00:00")))
        assertFalse(pass.validAt(at("2026-09-30T23:59:59")))
    }
    @Test fun journeyCrossingExpirationIsNotCovered() {
        val result=passAdvice(listOf(pass),rail("2026-10-22T23:50","2026-10-23T00:20"))
        assertEquals("A mentett bérlet ekkor nem érvényes",result.title)
        assertFalse(result.confirmed)
    }
    @Test fun activeRailPassDoesNotInventReservationValidity() {
        val result=passAdvice(listOf(pass),rail("2026-10-22T12:00","2026-10-22T13:00"))
        assertFalse(result.confirmed)
        assertEquals("A járati érvényesség nem igazolható",result.title)
    }
    @Test fun nextDayDepartureIsExplicit() {
        assertEquals("Holnap",dayLabel(at("2026-10-23T04:25"),LocalDate.of(2026,10,22)))
        assertEquals("Ma",dayLabel(at("2026-10-22T23:50"),LocalDate.of(2026,10,22)))
    }
    @Test fun busWithUnverifiedTerritoryRemainsUnknown() {
        val from=Station("hu","Budapest",47.5,19.0)
        val to=Station("at","Wien",48.2,16.37)
        val journey=Journey(7200,listOf(Leg("COACH",at("2026-10-22T12:00"),at("2026-10-22T14:00"),from,to,"Busz","hkir:example",false,0,emptyList(),"")))
        assertEquals("A járati érvényesség nem igazolható",passAdvice(listOf(pass),journey).title)
    }
}
