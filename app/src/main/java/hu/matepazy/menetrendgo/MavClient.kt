package hu.matepazy.menetrendgo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Same OTP queries and normalization as hovamegy.hu, executed on the device.
 * No proxy, account, custom endpoint, or local development server is required.
 */
class MavClient {
    private val root="https://mavplusz.hu/otp2-backend/otp/routers/default"
    private val vehicleLock=Mutex()
    private var vehicleCache: Pair<List<Vehicle>,Long>?=null
    private val trips=mutableMapOf<String,Pair<JSONObject,Long>>()
    private val tripLock=Mutex()

    private fun request(path: String, query: String?=null, onRawResponse: ((String) -> Unit)?=null): JSONObject {
        val connection=URI(root+path).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=10_000;connection.readTimeout=40_000
            connection.setRequestProperty("Accept","application/json")
            connection.setRequestProperty("Origin","https://mavplusz.hu")
            connection.setRequestProperty("Referer","https://mavplusz.hu/")
            connection.setRequestProperty("User-Agent","MenetrendGO/${BuildConfig.VERSION_NAME} (Android)")
            if(query!=null) {
                connection.requestMethod="POST";connection.doOutput=true
                connection.setRequestProperty("Content-Type","application/json; charset=utf-8")
                connection.outputStream.use {it.write(JSONObject().put("query",query).toString().toByteArray(Charsets.UTF_8))}
            }
            check(connection.responseCode in 200..299) {"A MÁV adatai most nem érhetők el (${connection.responseCode}). Próbáld újra."}
            val raw=connection.inputStream.bufferedReader().use {it.readText()}
            val body=JSONObject(raw)
            onRawResponse?.invoke(raw)
            if(query==null) return body
            check(!body.has("errors") && body.optJSONObject("data")!=null) {"A MÁV hiányos választ adott. Próbáld újra."}
            return body.getJSONObject("data")
        } catch(e: java.io.IOException) {
            throw IllegalStateException("Nem sikerült kapcsolódni a MÁV-hoz. Ellenőrizd az internetkapcsolatot.",e)
        } finally {connection.disconnect()}
    }
    suspend fun stations(query: String): List<Station> = withContext(Dispatchers.IO) {
        require(query.trim().length in 2..100)
        request("/geocode/stations?q="+URLEncoder.encode(query.trim(),"UTF-8")+"&limit=10").array("features").mapNotNull {feature->
            val coords=feature.optJSONObject("geometry")?.optJSONArray("coordinates")
            val properties=feature.optJSONObject("properties")
            val modes=properties?.optJSONArray("modes")
            if(coords==null || coords.length()<2) null else Station(feature.optString("id"),properties?.optString("name",query) ?: query,coords.getDouble(1),coords.getDouble(0),
                properties?.optString("type").orEmpty(),if(modes==null) emptyList() else (0 until modes.length()).map {modes.getString(it)})
        }.distinctBy {it.id}
    }
    suspend fun plan(from: Station,to: Station,time: LocalDateTime,arrive: Boolean): List<Journey> = withContext(Dispatchers.IO) {
        var rawResponse: String? = null
        val data=request("/index/graphql",MavQueries.plan(from.lat,from.lon,to.lat,to.lon,time,arrive)) {rawResponse=it}
        data.optJSONObject("plan")?.array("itineraries").orEmpty().map {itinerary->
            itinerary.array("legs").forEach {leg->
                normalizeRoute(leg.optJSONObject("route"),leg.optJSONObject("trip")?.optString("tripShortName"))
                listOf("from" to from,"to" to to).forEach {(key,station)->
                    val place=leg.optJSONObject(key)
                    if(place?.optString("name") in listOf("Origin","Destination")) place?.put("name",station.name)
                }
            }
            // Keep the original envelope before route/name normalization mutates the JSON.
            // Each result retains its own search batch, including paginated departures.
            Journey.parse(itinerary).copy(rawGraphQl=rawResponse)
        }.filter {it.legs.isNotEmpty()}.sortedBy {it.start}
    }
    suspend fun vehicles(): Pair<List<Vehicle>,Long> = withContext(Dispatchers.IO) {
        vehicleLock.withLock {
            val now=System.currentTimeMillis()/1000
            vehicleCache?.takeIf {now-it.second<75} ?: run {
                val data=request("/index/graphql",MavQueries.vehicles)
                check(data.has("vehiclePositions")) {"A MÁV járműadatai most nem érhetők el."}
                val items=data.array("vehiclePositions").mapNotNull {v->
                    val trip=v.optJSONObject("trip")
                    val id=trip?.optString("gtfsId").orEmpty()
                    if(id.isBlank() || v.isNull("lat") || v.isNull("lon")) null else Vehicle(id,
                        hungarianServiceLabel(trip!!.optString("tripShortName").ifBlank {v.optString("label").ifBlank {"Járat"}}),
                        trip.optString("tripHeadsign"),trip.optJSONObject("route")?.optString("mode").orEmpty(),v.getDouble("lat"),v.getDouble("lon"),
                        trip.optJSONObject("route")?.optString("color").orEmpty(),trip.optString("trainCategoryName"),
                        trip.optJSONObject("route")?.optString("longName").orEmpty())
                }
                (items to System.currentTimeMillis()/1000).also {vehicleCache=it}
            }
        }
    }
    suspend fun trip(id: String,day: String): JSONObject = withContext(Dispatchers.IO) {
        require(id.length in 1..250);LocalDate.parse(day)
        tripLock.withLock {
            val key="$id|$day";val now=System.currentTimeMillis()/1000
            trips[key]?.takeIf {now-it.second<75}?.first ?: run {
                val result=request("/index/graphql",MavQueries.trip(id,day)).optJSONObject("trip")
                    ?: throw IllegalStateException("Ehhez a járathoz nincs részletes menetrend.")
                normalizeRoute(result.optJSONObject("route"),result.optString("tripShortName").ifBlank {result.optString("trainCategoryName")})
                if(trips.size>100) trips.clear()
                trips[key]=result to System.currentTimeMillis()/1000
                result
            }
        }
    }
    private fun normalizeRoute(route: JSONObject?,fallback: String?) {
        if(route?.optString("shortName")?.contains('<')==true) route.put("shortName",fallback?.takeIf {it.isNotBlank()} ?: "Vonat")
        route?.put("shortName",hungarianServiceLabel(route.optString("shortName")))
    }
}

/** Pure query builders: validate coordinates and escape every string literal. */
object MavQueries {
    fun quote(value: String): String = buildString {
        append('"')
        value.forEach {c->when(c) {
            '"'->append("\\\"");'\\'->append("\\\\");'\n'->append("\\n");'\r'->append("\\r");'\t'->append("\\t")
            else->if(c.code<32) append("\\u"+c.code.toString(16).padStart(4,'0')) else append(c)
        }}
        append('"')
    }
    private fun point(lat: Double,lon: Double): String {
        require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0)
        return "{lat:$lat,lon:$lon}"
    }
    fun plan(fromLat: Double,fromLon: Double,toLat: Double,toLon: Double,time: LocalDateTime,arrive: Boolean): String = """
        {plan(from:${point(fromLat,fromLon)},to:${point(toLat,toLon)},date:${quote(time.toLocalDate().toString())},time:${quote(time.format(DateTimeFormatter.ofPattern("HH:mm")))},arriveBy:$arrive,numItineraries:12,
        transportModes:[{mode:RAIL},{mode:COACH},{mode:BUS},{mode:SUBURBAN_RAILWAY},{mode:TRAMTRAIN},{mode:WALK}],minTransferTime:120,maxWalkDistance:2000) {
        itineraries {duration walkTime waitingTime legs {mode startTime endTime duration distance realTime departureDelay arrivalDelay
        from {name lat lon} to {name lat lon} intermediateStops {name lat lon} legGeometry {points}
        route {shortName longName mode color agency {name}} trip {gtfsId tripShortName tripHeadsign trainCategoryName}}}}}
    """.trimIndent()
    val vehicles="""{vehiclePositions(swLat:45.5,swLon:16.1,neLat:48.7,neLon:22.8,modes:[RAIL,RAIL_REPLACEMENT_BUS,COACH,SUBURBAN_RAILWAY,TRAMTRAIN]) {vehicleId lat lon speed heading label trip {gtfsId tripShortName tripHeadsign trainCategoryName route {mode color longName}}}}"""
    fun trip(id: String,day: String): String {
        require(id.length in 1..250);LocalDate.parse(day)
        return """{trip(id:${quote(id)},serviceDay:${quote(day)}) {gtfsId tripHeadsign trainCategoryName trainName tripShortName route {shortName longName(language:"hu")} stoptimes {serviceDay realtime stop {name lat lon platformCode} realtimeArrival realtimeDeparture arrivalDelay departureDelay scheduledArrival scheduledDeparture}}}"""
    }
}
