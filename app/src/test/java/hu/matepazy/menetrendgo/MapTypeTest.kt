package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test

class MapTypeTest {
    @Test fun acceptsRasterTemplatesAndQueryTokens() {
        assertTrue(validMapTileUrl("https://tiles.example.com/{z}/{x}/{y}.png?key=abc&scale={r}"))
        assertTrue(validMapTileUrl("https://{s}.example.com/{z}/{x}/{y}.jpg"))
    }
    @Test fun rejectsUnsupportedOrUnsafeTemplates() {
        listOf("", "https://example.com/map", "http://example.com/{z}/{x}/{y}.png",
            "javascript:alert(1)", "https://user:pass@example.com/{z}/{x}/{y}",
            "https://example.com/{z}/{x}/{y}#fragment", "https://example.com/{z}/{x}/{y}?a={unknown}",
            "https://example.com/ {z}/{x}/{y}", "https://example.com/{z}/{x}/{y}\n").forEach {
            assertFalse(it, validMapTileUrl(it))
        }
    }
    @Test fun unknownSavedTypeFallsBackToStandard() {
        assertEquals(MapType.Standard, MapType.fromId("removed"))
        assertEquals(MapType.Standard, MapType.fromId(null))
        assertEquals(MapType.Rail, MapType.fromId("rail"))
    }
    @Test fun mapHtmlDisablesZoomControlAndSupportsDoubleTapSwipeZoom() {
        val html = java.io.File("src/main/assets/map.html").readText()
        assertTrue(html.contains("zoomControl:false"))
        assertFalse(html.contains("leaflet-control-zoom"))
        assertFalse(html.contains("map.zoomControl"))
        assertTrue(html.contains("zoomSnap:0"))
        assertTrue(html.contains("startZoom+(pt.y-downPt.y)/pxPerZoom"))
    }
}
