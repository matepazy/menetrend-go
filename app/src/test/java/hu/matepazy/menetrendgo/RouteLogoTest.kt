package hu.matepazy.menetrendgo

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RouteLogoTest {
    private fun route(code: Int, foreground: String, background: String = "FEFEFE") = JSONObject()
        .put("shortName", "<span class=\"MNR2007\">&#$code;</span>")
        .put("color", background).put("textColor", foreground)

    @Test fun providerLogoKeepsItsOwnColorsRatherThanMapColors() {
        assertEquals(RouteLogo(474,"274F96","FEFEFE"),RouteLogo.parse(route(474,"274F96")))
        assertEquals(RouteLogo(477,"29803C","FEFEFE"),RouteLogo.parse(route(477,"29803C")))
        assertEquals(RouteLogo(383,"000000","FECC00"),RouteLogo.parse(route(383,"000000","FECC00")))
        assertEquals(RouteLogo(360,"00A0E3","FEFEFE"),RouteLogo.parse(route(360,"00A0E3")))
    }

    @Test fun normalizedJourneyAndVehicleDrawerRetainTheExactProviderMark() {
        val original=route(542,"29803C")
        val normalized=JSONObject(original.toString()).put("logoShortName",original.getString("shortName"))
            .put("shortName","1924")
        val station=JSONObject().put("name","Station").put("lat",47.0).put("lon",19.0)
        val leg=Leg.parse(JSONObject().put("mode","RAIL").put("startTime",0).put("endTime",60000)
            .put("from",station).put("to",station).put("route",normalized))
        assertEquals(RouteLogo.parse(original),leg.routeLogo)
        val vehicle=parseVehiclePosition(JSONObject().put("lat",47.0).put("lon",19.0)
            .put("trip",JSONObject().put("gtfsId","trip").put("tripShortName","1924")
                .put("route",original.put("mode","RAIL"))))!!
        assertEquals(leg.routeLogo,vehicle.badgeLeg().routeLogo)
        assertEquals("1924",vehicle.badgeLeg().route)
    }

    @Test fun plainTextAndInvalidMarksOrColorsUseTheOrdinaryBadge() {
        assertNull(RouteLogo.parse(route(474,"274F96").put("shortName","IC")))
        assertNull(RouteLogo.parse(route(474,"274F96").put("shortName","<b>&#474;</b>")))
        assertNull(RouteLogo.parse(route(0xd800,"274F96")))
        assertNull(RouteLogo.parse(route(0x110000,"274F96")))
        assertNull(RouteLogo.parse(route(474,"invalid")))
        assertNull(RouteLogo.parse(route(474,"274F96").apply {remove("textColor")}))
    }
}
