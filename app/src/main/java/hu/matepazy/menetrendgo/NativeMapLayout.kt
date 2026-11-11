package hu.matepazy.menetrendgo

import java.text.Normalizer
import java.util.Locale
import kotlin.math.*

internal data class MapPixel(val x:Double,val y:Double)

/** Stable world pixels prevent the collision priority from changing as the camera pans. */
internal fun mapWorldPixel(stop:Station,zoom:Double):MapPixel {
    val size=256*2.0.pow(zoom)
    val sin=sin(stop.lat.coerceIn(-85.05112878,85.05112878)*PI/180)
    return MapPixel((stop.lon+180)/360*size,(.5-ln((1+sin)/(1-sin))/(4*PI))*size)
}

internal fun groupNativeMapStops(stops:List<Station>):List<List<Station>> {
    val groups=mutableListOf<MutableList<Station>>()
    val names=mutableMapOf<String,MutableList<MutableList<Station>>>()
    fun distance(a:Station,b:Station):Double {
        val lat=(b.lat-a.lat)*PI/180;val lon=(b.lon-a.lon)*PI/180
        val h=sin(lat/2).pow(2)+cos(a.lat*PI/180)*cos(b.lat*PI/180)*sin(lon/2).pow(2)
        return 12742000*asin(sqrt(h.coerceIn(0.0,1.0)))
    }
    stops.sortedBy {it.id}.forEach {stop->
        val name=Normalizer.normalize(stop.name,Normalizer.Form.NFC).trim().replace(Regex("\\s+")," ").lowercase(Locale.forLanguageTag("hu"))
        val candidates=names.getOrPut(name) {mutableListOf()}
        val group=if(name.isEmpty())null else candidates.firstOrNull {g->g.all {distance(stop,it)<=300}}
        if(group!=null)group.add(stop) else mutableListOf(stop).also {groups.add(it);candidates.add(it)}
    }
    return groups
}

internal fun selectNativeMapStops(stops:List<Station>,zoom:Double):Set<String> {
    fun priority(s:Station)=
        (if(s.modes.any {it in setOf("RAIL","SUBURBAN_RAILWAY","SUBWAY")})100 else 0)+
            (if(s.type.equals("station",true))20 else 0)+min(s.modes.distinct().size,10)
    val cells=mutableMapOf<Pair<Int,Int>,MutableList<MapPixel>>()
    val selected=mutableSetOf<String>()
    stops.sortedWith(compareByDescending<Station> {priority(it)}.thenBy {it.id}).forEach {stop->
        val point=mapWorldPixel(stop,zoom)
        val x=floor(point.x/24).toInt();val y=floor(point.y/24).toInt()
        val collides=(-1..1).any {dx->(-1..1).any {dy->cells[x+dx to y+dy].orEmpty().any {abs(it.x-point.x)<24&&abs(it.y-point.y)<24}}}
        if(!collides) {selected.add(stop.id);cells.getOrPut(x to y) {mutableListOf()}.add(point)}
    }
    return selected
}

internal fun nativeMapStopOutline(points:List<MapPixel>,padding:Double):List<MapPixel> {
    val padded=points.flatMap {p->(0 until 24).map {i->val angle=i*PI/12;MapPixel(p.x+cos(angle)*padding,p.y+sin(angle)*padding)}}
        .sortedWith(compareBy<MapPixel> {it.x}.thenBy {it.y})
    fun half(sequence:List<MapPixel>):List<MapPixel> {
        val hull=mutableListOf<MapPixel>()
        sequence.forEach {p->
            while(hull.size>1) {
                val a=hull[hull.lastIndex-1];val b=hull.last()
                if((b.x-a.x)*(p.y-a.y)-(b.y-a.y)*(p.x-a.x)>0)break
                hull.removeAt(hull.lastIndex)
            }
            hull.add(p)
        }
        return hull.dropLast(1)
    }
    return half(padded)+half(padded.reversed())
}
