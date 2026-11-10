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
    suspend fun nearbyStops(lat: Double, lon: Double, radius: Int): List<Station> = withContext(Dispatchers.IO) {
        parseMapStops(request("/index/graphql", MavQueries.nearbyStops(lat, lon, radius)))
    }
    /** Fetch shapes only for visible markers, rather than repeating every shape in the live feed. */
    suspend fun vehiclePaths(ids: List<String>): JSONObject = withContext(Dispatchers.IO) {
        val selected=ids.distinct().take(24).filter {it.length in 1..250}
        if(selected.isEmpty()) return@withContext JSONObject()
        val query=selected.mapIndexed {i,id->"p$i:trip(id:${MavQueries.quote(id)}) {geometry}"}.joinToString(" ","{","}")
        val data=request("/index/graphql",query)
        JSONObject().apply {
            selected.forEachIndexed {i,id->
                data.optJSONObject("p$i")?.optJSONArray("geometry")?.let {put(id,it)}
            }
        }
    }
    suspend fun stopDepartures(id: String, now: Long = System.currentTimeMillis()): List<StopDeparture> = withContext(Dispatchers.IO) {
        parseStopDepartures(request("/index/graphql", MavQueries.stopDepartures(id, now)), now)
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
    suspend fun plan(from: Station,to: Station,time: LocalDateTime,arrive: Boolean, filters: Set<SearchFilter> = emptySet(), vehicles: Set<SearchVehicle> = emptySet()): List<Journey> = withContext(Dispatchers.IO) {
        var rawResponse: String? = null
        val data=request("/index/graphql",MavQueries.plan(from,to,time,arrive,filters,vehicles)) {rawResponse=it}
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
            Journey.parse(itinerary).copy(rawGraphQl=rawResponse,fetchedAt=System.currentTimeMillis(),
                providerFilters=filters.filter { it.providerSearchCode != null }.toSet())
        }.filter {it.legs.isNotEmpty()}.sortedBy {it.start}
    }
    suspend fun vehicles(forceRefresh: Boolean = false, refreshSeconds: Int = 75): Pair<List<Vehicle>,Long> = withContext(Dispatchers.IO) {
        vehicleLock.withLock {
            val now=System.currentTimeMillis()/1000
            vehicleCache?.takeIf {!forceRefresh && now-it.second<refreshSeconds.coerceIn(15,90)} ?: run {
                val data=request("/index/graphql",MavQueries.vehicles)
                check(data.has("vehiclePositions")) {"A MÁV járműadatai most nem érhetők el."}
                val items=data.array("vehiclePositions").mapNotNull(::parseVehiclePosition)
                (items to System.currentTimeMillis()/1000).also {vehicleCache=it}
            }
        }
    }
    suspend fun trip(id: String,day: String,forceRefresh: Boolean = false,refreshSeconds: Int = 75): JSONObject = withContext(Dispatchers.IO) {
        require(id.length in 1..250);LocalDate.parse(day)
        tripLock.withLock {
            val key="$id|$day";val now=System.currentTimeMillis()/1000
            trips[key]?.takeIf {!forceRefresh && now-it.second<refreshSeconds.coerceIn(15,90)}?.first ?: run {
                val result=request("/index/graphql",MavQueries.trip(id,day)).optJSONObject("trip")
                    ?: throw IllegalStateException("Ehhez a járathoz nincs részletes menetrend.")
                normalizeRoute(result.optJSONObject("route"),result.optString("tripShortName").ifBlank {result.optString("trainCategoryName")})
                if(trips.size>100) trips.clear()
                trips[key]=result to System.currentTimeMillis()/1000
                result
            }
        }
    }
    /** Tracking only needs arrival evidence, not facilities, vehicles or route descriptions. */
    suspend fun trackingTrip(id: String, day: String): JSONObject = withContext(Dispatchers.IO) {
        request("/index/graphql", MavQueries.trackingTrip(id, day)).optJSONObject("trip")
            ?: throw IllegalStateException("Ehhez a járathoz nincs részletes menetrend.")
    }

    private var networkAlertCache: Pair<List<NetworkAlert>,Long>?=null
    /** Line and station notices across every feed; cached briefly because the response is large. */
    suspend fun networkAlerts(forceRefresh: Boolean = false): Pair<List<NetworkAlert>,Long> = withContext(Dispatchers.IO) {
        val now=System.currentTimeMillis()
        networkAlertCache?.takeIf {!forceRefresh && now-it.second<120_000} ?: run {
            val data=request("/index/graphql",MavQueries.networkAlerts)
            check(data.has("alerts")) {"A MÁV közleményei most nem érhetők el."}
            (NetworkAlert.parseAll(data,now) to now).also {networkAlertCache=it}
        }
    }
    private fun normalizeRoute(route: JSONObject?,fallback: String?) {
        if(route?.optString("shortName")?.contains("MNR2007")==true)
            route.put("logoShortName",route.optString("shortName"))
        if(route?.optString("shortName")?.contains('<')==true) route.put("shortName",fallback?.takeIf {it.isNotBlank()} ?: "Vonat")
        route?.put("shortName",hungarianServiceLabel(route.optString("shortName")))
    }
}

/** Pure query builders: validate coordinates and escape every string literal. */
object MavQueries {
    fun nearbyStops(lat: Double, lon: Double, radius: Int): String {
        point(lat, lon)
        require(radius in 100..15000)
        return """{stopsByRadius(lat:$lat,lon:$lon,radius:$radius,first:500){edges{node{stop{gtfsId name lat lon platformCode vehicleMode parentStation{gtfsId name lat lon}}}}}}"""
    }
    fun stopDepartures(id: String, now: Long): String {
        require(id.length in 1..250 && now > 0)
        return """{stop(id:${quote(id)}){gtfsId name stoptimesWithoutPatterns(startTime:${now/1000},numberOfDepartures:30,timeRange:86400,omitCanceled:false){serviceDay scheduledDeparture realtimeDeparture realtime realtimeState pickupType headsign stop{gtfsId name lat lon platformCode} trip{gtfsId tripShortName tripHeadsign trainCategoryName route{shortName longName mode color textColor}}}}}"""
    }
    private const val services = """infoServices(language:"hu",onlyDisplayable:true) {name fontCode fontCharSet displayable fromStopIndex tillStopIndex fromStop {name} tillStop {name}}"""
    private const val alerts = """alerts {id alertHeaderText alertDescriptionText alertUrl effectiveStartDate effectiveEndDate alertSeverityLevel alertEffect
        alertHeaderTextTranslations {language text} alertDescriptionTextTranslations {language text}}"""
    private const val line = "shortName mode color textColor"
    /** Entities only name lines and stations; trip-only notices are dropped after parsing. */
    const val networkAlerts = """{alerts {id feed alertHeaderText alertDescriptionText alertUrl effectiveStartDate effectiveEndDate alertSeverityLevel alertEffect
        alertHeaderTextTranslations {language text} alertDescriptionTextTranslations {language text}
        entities {__typename ... on Route {$line} ... on Stop {name} ... on StopOnRoute {route {$line} stop {name}}}}}"""
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
    // Preserve provider identity: a MetaStation (CITY*) denotes all of a city's
    // stations. Its coordinates are only a display point, not a routing endpoint.
    // This is the same name::id format used by the MÁVPlusz planner.
    private fun endpoint(key: String,station: Station): String =
        if(station.id.isNotBlank()) "${key}Place:${quote("${station.name}::${station.id}")}"
        else "$key:${point(station.lat,station.lon)}"

    fun plan(from: Station,to: Station,time: LocalDateTime,arrive: Boolean, filters: Set<SearchFilter> = emptySet(), vehicles: Set<SearchVehicle> = emptySet()): String =
        planEndpoints(endpoint("from",from),endpoint("to",to),time,arrive,filters,vehicles)

    fun plan(fromLat: Double,fromLon: Double,toLat: Double,toLon: Double,time: LocalDateTime,arrive: Boolean): String =
        planEndpoints("from:${point(fromLat,fromLon)}","to:${point(toLat,toLon)}",time,arrive)

    private fun planEndpoints(from: String,to: String,time: LocalDateTime,arrive: Boolean, filters: Set<SearchFilter> = emptySet(), vehicles: Set<SearchVehicle> = emptySet()): String = """
        {plan($from,$to,date:${quote(time.toLocalDate().toString())},time:${quote(time.format(DateTimeFormatter.ofPattern("HH:mm")))},arriveBy:$arrive,numItineraries:12,
        transportModes:[${searchVehicleModes(vehicles).joinToString(",") { "{mode:$it}" }}],minTransferTime:120,maxWalkDistance:2000,
        searchParameters:[${filters.sortedBy { it.ordinal }.mapNotNull { it.providerSearchCode }.joinToString(",", transform=::quote)}]) {
        itineraries {duration walkTime waitingTime legs {mode startTime endTime duration distance realTime departureDelay arrivalDelay
        from {name lat lon stop {gtfsId platformCode}} to {name lat lon stop {gtfsId platformCode}} intermediateStops {gtfsId name lat lon platformCode} legGeometry {points}
        $services
        $alerts
        route {shortName longName mode color textColor agency {name}} trip {gtfsId tripShortName tripHeadsign trainCategoryName $alerts}}}}}
    """.trimIndent()
    val vehicles="""{vehiclePositions(swLat:45.5,swLon:16.1,neLat:48.7,neLon:22.8,modes:[RAIL,RAIL_REPLACEMENT_BUS,COACH,BUS,SUBURBAN_RAILWAY,TRAMTRAIN]) {vehicleId lastUpdated lat lon speed heading label vehicleModel licensePlate uicCode trip {gtfsId tripShortName tripHeadsign trainCategoryName route {shortName mode color textColor longName agency {name}}}}}"""
    fun trip(id: String,day: String): String {
        require(id.length in 1..250);LocalDate.parse(day)
        return """{trip(id:${quote(id)},serviceDay:${quote(day)}) {gtfsId tripHeadsign trainCategoryName trainName tripShortName $services route {shortName longName(language:"hu")} stoptimes {serviceDay realtime realtimeState stop {gtfsId name lat lon platformCode} realtimeArrival realtimeDeparture arrivalDelay departureDelay scheduledArrival scheduledDeparture} vehiclePositions {vehicleId vehicleModel licensePlate lastUpdated stopRelationship {status stop {gtfsId} arrivalTime departureTime}}}}"""
    }
    fun trackingTrip(id: String, day: String): String {
        require(id.length in 1..250); LocalDate.parse(day)
        return """{trip(id:${quote(id)},serviceDay:${quote(day)}) {gtfsId stoptimes {serviceDay realtime scheduledArrival realtimeArrival stop {name lat lon}}}}"""
    }
}
