package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PassRulesTest {
    @Test fun localPassNeverActsAsNationalCoverage() {
        val local = pass.copy(localCity="Székesfehérvár")
        assertEquals("Székesfehérvár · helyi bérlet", local.name)
        val result = passAdvice(listOf(local), localJourney(), geography)
        assertFalse(result.confirmed)
        assertEquals("A helyi bérlet elfogadását ellenőrizd", result.title)
        assertTrue(passAdvice(listOf(local, pass), localJourney(), geography).confirmed)
    }
    private val geography=PassGeography(java.io.File("src/main/assets/hungary-counties.txt").reader())
    private val pass=Pass("test",null,LocalDate.of(2026,10,1),LocalDate.of(2026,10,22))
    private fun at(day: String)=java.time.LocalDateTime.parse(day).atZone(Budapest).toInstant().toEpochMilli()
    private fun rail(start: String,end: String): Journey {
        val s=Station("a","Mór",47.369567,18.215948)
        val to=Station("b","Székesfehérvár",47.188536,18.409479)
        return Journey(3600,listOf(Leg("RAIL",at(start),at(end),s,to,"IC","1:trip",false,0,emptyList(),encodeRoute(listOf(GeoPoint(s.lat,s.lon),GeoPoint(to.lat,to.lon))),category="InterCity",agencyName="MÁV Személyszállítási Zrt.")))
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
        val result=passAdvice(listOf(pass),rail("2026-10-22T12:00","2026-10-22T13:00"),geography)
        assertFalse(result.confirmed)
        assertEquals("Országbérlet · a helyjegyet ellenőrizd",result.title)
    }
    @Test fun nextDayDepartureIsExplicit() {
        assertEquals("Holnap",dayLabel(at("2026-10-23T04:25"),LocalDate.of(2026,10,22)))
        assertEquals("",dayLabel(at("2026-10-22T23:50"),LocalDate.of(2026,10,22)))
    }
    @Test fun busWithUnverifiedTerritoryRemainsUnknown() {
        val from=Station("hu","Budapest",47.5,19.0)
        val to=Station("at","Wien",48.2,16.37)
        val journey=Journey(7200,listOf(Leg("COACH",at("2026-10-22T12:00"),at("2026-10-22T14:00"),from,to,"Busz","hkir:example",false,0,emptyList(),"")))
        assertEquals("A szolgáltató elfogadását ellenőrizni kell",passAdvice(listOf(pass),journey).title)
    }

    private fun localJourney(mode: String="COACH", category: String=""): Journey {
        val base=rail("2026-10-22T12:00","2026-10-22T13:00")
        return base.copy(legs=base.legs.map {it.copy(mode=mode,route="8096",category=category)})
    }
    @Test fun nationalAndMatchingCountyPassCoverDomesticCoach() {
        assertTrue(passAdvice(listOf(pass),localJourney(),geography).confirmed)
        assertTrue(passAdvice(listOf(pass.copy(county="Fejér")),localJourney(),geography).confirmed)
        assertFalse(passAdvice(listOf(pass.copy(county="Pest")),localJourney(),geography).confirmed)
    }
    @Test fun ordinaryRailCanBeUsedWithoutUsualSeatReservation() {
        assertTrue(passAdvice(listOf(pass.copy(county="Fejér")),localJourney("RAIL","Passenger train"),geography).confirmed)
    }
    @Test fun countyPassDoesNotConfirmIntercityAndMissingGeometryRemainsUnknown() {
        assertEquals("A vonat helyjegymentességét ellenőrizni kell",passAdvice(listOf(pass.copy(county="Fejér")),rail("2026-10-22T12:00","2026-10-22T13:00"),geography).title)
        val j=localJourney().let {it.copy(legs=it.legs.map {leg -> leg.copy(geometry="")})}
        assertFalse(passAdvice(listOf(pass),j,geography).confirmed)
    }
    @Test fun nationalPassDoesNotCoverForeignRoute() {
        val j=localJourney().let {j -> j.copy(legs=j.legs.map {leg ->
            val to=Station("at","Wien",48.2,16.37)
            leg.copy(to=to,geometry=encodeRoute(listOf(GeoPoint(leg.from.lat,leg.from.lon),GeoPoint(to.lat,to.lon))))
        })}
        assertEquals("A bérlet nem fedi le a teljes útvonalat",passAdvice(listOf(pass),j,geography).title)
    }
    @Test fun countyBoundariesAndMalformedGeometryAreNotGuessed() {
        val j=localJourney().legs.first()
        assertEquals(Coverage.UNKNOWN,geography.coverage(j.copy(geometry="invalid"),null))
        // Decoder also rejects a truncated variable-length value.
        assertTrue(runCatching {decodeRoute("~")}.isFailure)
    }
}

internal fun encodeRoute(points: List<GeoPoint>): String = buildString {
    var lat=0;var lon=0
    fun part(delta: Int) {
        var value=if(delta<0) (delta shl 1).inv() else delta shl 1
        while(value>=32) {append(((value and 31) or 32).plus(63).toChar());value=value shr 5}
        append((value+63).toChar())
    }
    points.forEach {p -> val a=kotlin.math.round(p.lat*1e5).toInt();val b=kotlin.math.round(p.lon*1e5).toInt();part(a-lat);part(b-lon);lat=a;lon=b}
}
