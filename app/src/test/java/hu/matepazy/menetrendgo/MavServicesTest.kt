package hu.matepazy.menetrendgo

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class MavServicesTest {
    @Test fun reservationAndSectionExemptionBothSurviveParsing() {
        val services=MavService.parseAll(JSONObject("""{"infoServices":[
          {"name":"Helyjegy váltása kötelező","fontCode":197,"fontCharSet":"MNR2007","fromStopIndex":0,"tillStopIndex":10,"fromStop":{"name":"Budapest-Nyugati"},"tillStop":{"name":"Szeged"}},
          {"name":"Helyjegy nélkül igénybe vehető 2. kocsiosztályon","fontCode":200,"fontCharSet":"MNR2007","fromStopIndex":0,"tillStopIndex":3,"fromStop":{"name":"Budapest-Nyugati"},"tillStop":{"name":"Ferihegy"}},
          {"name":"Helyjegy nélkül igénybe vehető 2. kocsiosztályon","fontCode":200,"fontCharSet":"MNR2007","fromStopIndex":8,"tillStopIndex":10,"fromStop":{"name":"Kistelek"},"tillStop":{"name":"Szeged"}}
        ]}"""))
        assertEquals(3,services.size)
        assertEquals("Budapest-Nyugati – Ferihegy",services[1].section)
        assertEquals(3,services[1].tillIndex)
        assertEquals("Kistelek – Szeged",services[2].section)
        assertEquals(197,services[0].glyph!!.codePointAt(0))
    }

    @Test fun absentOrUnrecognizedDataNeverInventsFacilitiesOrIcons() {
        assertTrue(MavService.parseAll(JSONObject()).isEmpty())
        val services=MavService.parseAll(JSONObject("""{"infoServices":[
          {"name":""},{"name":null},{"name":"Hidden","displayable":false},
          {"name":"Future service","fontCode":197,"fontCharSet":"Unknown"},
          {"name":"No icon","fontCode":null},
          {"name":"Invalid icon","fontCode":55296,"fontCharSet":"MNR2007"}
        ]}"""))
        assertEquals(3,services.size)
        assertTrue(services.all {it.glyph==null})
        assertTrue(services.all {it.section.isEmpty()})
    }

    @Test fun bothQueriesRequestTheServiceDescriptionsAndValiditySections() {
        val queries=listOf(MavQueries.trip("1:35050994","2026-10-25"),
            MavQueries.plan(47.51,19.05,46.239,20.143,LocalDateTime.of(2026,10,25,8,0),false))
        queries.forEach {
            assertTrue(it.contains("infoServices(language:\"hu\",onlyDisplayable:true)"))
            assertTrue(it.contains("fromStopIndex tillStopIndex fromStop {name} tillStop {name}"))
        }
    }
}
