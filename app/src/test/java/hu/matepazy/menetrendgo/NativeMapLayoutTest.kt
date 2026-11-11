package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test

class NativeMapLayoutTest {
    private fun stop(id:String,name:String,lat:Double=47.0,lon:Double=19.0,modes:List<String> = emptyList(),type:String="")=Station(id,name,lat,lon,type,modes)
    @Test fun normalizesNamesAndGroupsWithoutMergingDistantNamesakesOrChains() {
        val items=listOf(stop("b"," Olof  Palme sétány "),stop("a","Olof Palme sétány",lon=19.001),
            stop("c","Olof Palme sétány",lon=19.005),stop("d","Másik megálló",lon=19.001))
        val groups=groupNativeMapStops(items)
        assertEquals(listOf(listOf("a","b"),listOf("c"),listOf("d")),groups.map {g->g.map {it.id}})
        assertEquals(groups,groupNativeMapStops(items.reversed()))
    }
    @Test fun collisionSelectionPrefersRailAndStableProviderIdentity() {
        val bus=stop("a","Busz",modes=listOf("BUS"))
        val rail=stop("z","Vasút",modes=listOf("RAIL"))
        val far=stop("far","Távoli",lon=19.01)
        assertEquals(setOf("z","far"),selectNativeMapStops(listOf(bus,rail,far),16.0))
        assertEquals(selectNativeMapStops(listOf(bus,rail,far),16.0),selectNativeMapStops(listOf(far,rail,bus),16.0))
    }
    @Test fun enclosureContainsPaddedPlatformExtentAtEverySide() {
        val outline=nativeMapStopOutline(listOf(MapPixel(10.0,10.0),MapPixel(100.0,20.0)),18.0)
        assertEquals(-8.0,outline.minOf {it.x},.001)
        assertEquals(118.0,outline.maxOf {it.x},.001)
        assertEquals(-8.0,outline.minOf {it.y},.001)
        assertEquals(38.0,outline.maxOf {it.y},.001)
    }
}
