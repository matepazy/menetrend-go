package hu.matepazy.menetrendgo

import java.io.Reader
import kotlin.math.*

data class GeoPoint(val lat: Double, val lon: Double)
enum class Coverage { COVERED, OUTSIDE, UNKNOWN }

/** Bundled, generalized administrative boundaries. Border-adjacent routes stay uncertain. */
class PassGeography(reader: Reader) {
    private data class County(val name: String, val ring: List<GeoPoint>) {
        val minLat=ring.minOf {it.lat}; val maxLat=ring.maxOf {it.lat}
        val minLon=ring.minOf {it.lon}; val maxLon=ring.maxOf {it.lon}
        fun contains(p: GeoPoint): Boolean {
            if(p.lat !in minLat..maxLat || p.lon !in minLon..maxLon) return false
            var inside=false
            ring.zipWithNext().forEach {(a,b) ->
                if((a.lat>p.lat)!=(b.lat>p.lat) && p.lon<(b.lon-a.lon)*(p.lat-a.lat)/(b.lat-a.lat)+a.lon) inside=!inside
            }
            return inside
        }
        fun near(p: GeoPoint): Boolean {
            if(p.lat !in (minLat-.003)..(maxLat+.003) || p.lon !in (minLon-.004)..(maxLon+.004)) return false
            return ring.zipWithNext().any {(a,b) ->
                val x=(b.lon-a.lon)*75_000; val y=(b.lat-a.lat)*111_000
                val px=(p.lon-a.lon)*75_000; val py=(p.lat-a.lat)*111_000
                val t=if(x*x+y*y==0.0) 0.0 else ((px*x+py*y)/(x*x+y*y)).coerceIn(0.0,1.0)
                hypot(px-t*x,py-t*y)<250
            }
        }
    }
    private val counties=reader.buffered().useLines {lines -> lines.filter {it.isNotBlank()}.map {line ->
        val (name,coords)=line.split('|',limit=2)
        County(name,coords.split(';').map {val (lon,lat)=it.split(',');GeoPoint(lat.toDouble(),lon.toDouble())})
    }.toList()}
    private val cache=object:LinkedHashMap<String,Coverage>(64,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,Coverage>?)=size>128
    }

    fun coverage(leg: Leg, allowed: Set<String>?): Coverage {
        val key="${leg.geometry}|${leg.from.lat},${leg.from.lon}|${leg.to.lat},${leg.to.lon}|${allowed?.sorted()}"
        return cache.getOrPut(key) {
            val points=runCatching {decodeRoute(leg.geometry)}.getOrDefault(emptyList())
            if(points.size<2) return@getOrPut Coverage.UNKNOWN
            val path=listOf(GeoPoint(leg.from.lat,leg.from.lon))+points+GeoPoint(leg.to.lat,leg.to.lon)
            var uncertain=false
            for((a,b) in path.zipWithNext()) {
                val steps=ceil(hypot((b.lon-a.lon)*75_000,(b.lat-a.lat)*111_000)/200).toInt().coerceAtLeast(1)
                // Includes endpoints; 250 m border guard exceeds the 200 m sample interval.
                for(i in 0..steps) {
                    val p=GeoPoint(a.lat+(b.lat-a.lat)*i/steps,a.lon+(b.lon-a.lon)*i/steps)
                    val inside=counties.filter {it.contains(p)}
                    val nearby=counties.filter {it.near(p)}
                    if(inside.none {allowed==null || it.name in allowed}) {
                        if(nearby.isEmpty()) return@getOrPut Coverage.OUTSIDE
                        uncertain=true
                    }
                    if(nearby.isNotEmpty() && (nearby.size==1 || nearby.any {allowed!=null && it.name !in allowed})) uncertain=true
                }
            }
            if(uncertain) Coverage.UNKNOWN else Coverage.COVERED
        }
    }
}

fun decodeRoute(encoded: String): List<GeoPoint> {
    if(encoded.isBlank()) return emptyList()
    var i=0;var lat=0;var lon=0
    fun coordinate(): Int {
        var result=0;var shift=0;var b: Int
        do {
            require(i<encoded.length && shift<=30)
            b=encoded[i++].code-63;require(b in 0..63)
            result=result or ((b and 31) shl shift);shift+=5
        } while(b>=32)
        return if(result and 1!=0) (result shr 1).inv() else result shr 1
    }
    return buildList {while(i<encoded.length) {lat+=coordinate();lon+=coordinate();add(GeoPoint(lat/1e5,lon/1e5))}}
}
