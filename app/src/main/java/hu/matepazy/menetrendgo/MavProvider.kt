package hu.matepazy.menetrendgo

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/** MÁVPlusz as a canonical provider. It only asks MÁVPlusz: the offline fallback is the repository's job,
 * so this adapter uses [MavClient]'s online-only paths. Ids leave with the "mav:" namespace and enter without it. */
class MavProvider(private val client: MavClient, private val clock: () -> Long = System::currentTimeMillis) :
    StopSearchProvider, NearbyStopsProvider, DepartureBoardProvider, TripProvider, JourneyPlannerProvider, LivePositionsProvider {
    override val info = ProviderInfo(ProviderId.Mav, "MÁVPlusz", ProviderRole.Primary, priority = 100, coverage = Area.Hungary)

    private fun local(id: Gid): String? = id.takeIf { it.provider == ProviderId.Mav }?.local
    private fun <T> ok(value: T, kind: DataKind = DataKind.Scheduled) = ProviderResult.Ok(value, Provenance.of(ProviderId.Mav, clock(), kind))

    override suspend fun searchStops(query: StopQuery) = providerCall {
        ok(client.stations(query.text).take(query.limit).mapNotNull { it.toStop(ProviderId.Mav) })
    }

    override suspend fun stopsNear(lat: Double, lon: Double, radiusMeters: Int) = providerCall {
        if (!info.covers(lat, lon)) ProviderResult.NotCovered
        else ok(client.nearbyStops(lat, lon, radiusMeters.coerceIn(100, 15_000)).mapNotNull { it.toStop(ProviderId.Mav) })
    }

    override suspend fun departures(query: DepartureQuery) = providerCall {
        val id = local(query.stop) ?: return@providerCall ProviderResult.NotCovered
        val now = clock()
        val rows = client.onlineStopDepartures(id, query.from).take(query.limit).map { it.toDeparture(now) }
        ok(rows, if (rows.any { it.call.departure?.realtime == true }) DataKind.Live else DataKind.Scheduled)
    }

    override suspend fun trip(id: Gid, day: LocalDate, fresh: Boolean) = providerCall {
        val local = local(id) ?: return@providerCall ProviderResult.NotCovered
        val trip = client.onlineTrip(local, day.toString(), fresh, 75)
        ProviderResult.Ok(trip, trip.provenance)
    }

    override suspend fun plan(query: PlanQuery) = providerCall {
        val from = query.from.toStation(); val to = query.to.toStation()
        val journeys = client.onlinePlan(from, to, query.time, query.arriveBy, query.filters, query.vehicles)
        val now = clock()
        val itineraries = journeys.map { it.toItinerary(now) }
        ok(itineraries, if (itineraries.any { it.provenance.kind == DataKind.Live }) DataKind.Live else DataKind.Scheduled)
    }

    override suspend fun vehicles(area: Area, fresh: Boolean) = providerCall {
        if (!area.intersects(Area.Hungary)) return@providerCall ProviderResult.NotCovered
        val (list, fetchedSeconds) = client.vehicles(fresh)
        val provenance = Provenance.of(ProviderId.Mav, fetchedSeconds * 1000, DataKind.Live)
        ProviderResult.Ok(list.filter { (it.lat to it.lon) in area }.map { it.toVehiclePosition(provenance) }, provenance)
    }
}

// ---- Conversions from the app's current types. Pure and shared by the providers that still produce them. ----

/** A provider stop, or null for OpenStreetMap places, coordinates and the current location. */
internal fun Station.toStop(provider: ProviderId): Stop? {
    if (id.isBlank() || isPlace || currentLocationCalculatedAt != null) return null
    return Stop(Gid.of(provider, id), name, lat, lon, platform = platform, modes = modes.map(ModeMapping::fromOtp).toSet())
}

internal fun Station.toPlace(provider: ProviderId = ProviderId.Mav): Place = when {
    currentLocationCalculatedAt != null -> Place.CurrentLocation(lat, lon, currentLocationCalculatedAt)
    else -> toStop(provider)?.let(Place::StopPlace) ?: Place.Address(name, lat, lon, detail)
}

/** The reverse, for handing canonical places to [MavClient]; a foreign or synthetic stop becomes plain coordinates. */
internal fun Place.toStation(): Station = when (this) {
    is Place.StopPlace -> if (stop.id.provider == ProviderId.Mav) Station(stop.id.local, stop.name, stop.lat, stop.lon,
        modes = stop.modes.map { it.otp }, platform = stop.platform) else Station("", stop.name, stop.lat, stop.lon)
    is Place.Address -> Station("", name, lat, lon, detail = detail)
    is Place.CurrentLocation -> Station("", name, lat, lon, currentLocationCalculatedAt = calculatedAt)
}

/** A transit leg's endpoint always has a provider stop; a missing id keeps its coordinates under a synthetic "@lat,lon" id. */
private fun Station.callStop(provider: ProviderId) = toStop(provider)
    ?: Stop(Gid.of(provider, "@$lat,$lon"), name, lat, lon, platform = platform)

private fun Leg.route(): TransitRoute = TransitRoute(null, routeShortName.ifBlank { route }, routeLongName, ModeMapping.fromOtp(mode),
    routeColor, "", agencyName.takeIf { it.isNotBlank() }?.let { Agency(null, it) }, routeLogo)

private fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(Budapest).toLocalDate()

internal fun StopDeparture.toDeparture(fetchedAt: Long, provider: ProviderId = if (offline) ProviderId.Gtfs else ProviderId.Mav): Departure {
    val trip = TripSummary(Gid.of(provider, tripId), leg.route(), headsign,
        runCatching { LocalDate.parse(serviceDay) }.getOrElse { day(time.scheduled) }, leg.route, leg.category)
    val call = Call(leg.from.callStop(provider), 0, null, TimePoint.of(time, cancelled), platform)
    val kind = when { offline -> DataKind.Offline; time.realtime -> DataKind.Live; else -> DataKind.Scheduled }
    return Departure(trip, call, Provenance.of(provider, fetchedAt, kind))
}

/** OTP legs carry no service date; the boarding day stands in for it, which is off by one only for a trip
 * boarded after midnight that began the evening before. Intermediate stops carry no times in [Leg]. */
internal fun Journey.toItinerary(fetchedAt: Long): Itinerary {
    val provider = if (offline) ProviderId.Gtfs else ProviderId.Mav
    val legs = legs.map { leg ->
        if (leg.walking) JourneyLeg.Walk(leg.from.toPlace(provider), leg.to.toPlace(provider), leg.start, leg.end, leg.geometry)
        else {
            val departure = leg.departureTime; val arrival = leg.arrivalTime
            JourneyLeg.Ride(TripSummary(Gid.of(provider, leg.tripId.ifBlank { "@${leg.start}" }), leg.route(), leg.headsign,
                day(departure.scheduled), leg.route, leg.category),
                Call(leg.from.callStop(provider), 0, null, TimePoint.of(departure)),
                Call(leg.to.callStop(provider), leg.intermediate.size + 1, TimePoint.of(arrival), null),
                leg.intermediate.mapIndexed { i, s -> Call(s.callStop(provider), i + 1, null, null) },
                leg.geometry, leg.services, leg.alerts)
        }
    }
    val kind = when { offline -> DataKind.Offline; this.legs.any { it.realTime && !it.walking } -> DataKind.Live; else -> DataKind.Scheduled }
    return Itinerary(legs, Provenance.of(provider, this.fetchedAt.takeIf { it > 0 } ?: fetchedAt, kind), itineraryJson)
}

internal fun Vehicle.toVehiclePosition(provenance: Provenance): VehiclePosition {
    val sourceIds = buildSet {
        if (sources and VehicleSources.MAV != 0) add(ProviderId.Mav)
        if (sources and VehicleSources.UTAS != 0) add(ProviderId.Utas)
    }
    val tripId = mavTripId?.let { Gid.of(ProviderId.Mav, it) } ?: utasTrip?.let { Gid.of(ProviderId.Utas, it.id) }
    val route = TransitRoute(null, routeShortName.ifBlank { name }, routeLongName, ModeMapping.fromOtp(mode), routeColor, "",
        agencyName.takeIf { it.isNotBlank() }?.let { Agency(null, it) }, routeLogo)
    return VehiclePosition(Gid.legacy(id) ?: Gid.of(ProviderId.Mav, "@$lat,$lon"), name, route, lat, lon,
        provenance.copy(sources = sourceIds.ifEmpty { provenance.sources }), tripId,
        utasTrip?.serviceDate ?: positionUpdatedSeconds?.let { day(it * 1000) },
        headsign, heading, speedMetersPerSecond, positionUpdatedSeconds, model, registration, uicCode)
}

/** A MÁVPlusz trip as a canonical [Trip]. Stop times are local timetable seconds of the row's service date
 * (its serviceDay, else the trip's first one, else [day]), so they stay right across a clock change ([vehicleStopTime]). */
internal fun parseTripJson(trip: JSONObject, day: LocalDate, provider: ProviderId, provenance: Provenance): Trip {
    fun JSONObject.text(key: String) = if (isNull(key)) "" else optString(key).trim().takeUnless { it.equals("null", true) }.orEmpty()
    fun JSONObject.seconds(key: String) = if (isNull(key)) null else optLong(key, -1).takeIf { it >= 0 }
    fun JSONObject.number(key: String) = if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
    val route = trip.optJSONObject("route") ?: JSONObject()
    val transitRoute = TransitRoute(null, route.text("shortName"), route.text("longName"), ModeMapping.fromOtp(route.text("mode")),
        route.text("color"), route.text("textColor"), route.optJSONObject("agency")?.text("name")?.takeIf { it.isNotBlank() }?.let { Agency(null, it) },
        RouteLogo.parse(route))
    val rows = trip.array("stoptimes")
    fun serviceDate(seconds: Long) = Instant.ofEpochSecond(seconds).atZone(Budapest).toLocalDate()
    val firstServiceDate = rows.firstNotNullOfOrNull { it.seconds("serviceDay") }?.let(::serviceDate) ?: day
    var live = false
    val calls = rows.mapIndexedNotNull { index, row ->
        val stop = tripStop(row.optJSONObject("stop") ?: return@mapIndexedNotNull null, provider) ?: return@mapIndexedNotNull null
        // A row without its own serviceDay shares its trip's origin rather than guessing local midnight.
        val date = row.seconds("serviceDay")?.let(::serviceDate) ?: firstServiceDate
        val state = row.text("realtimeState")
        fun time(kind: String): TimePoint? {
            val scheduled = row.seconds("scheduled$kind") ?: return null
            val estimate = row.seconds("realtime$kind")
            val realtime = row.optBoolean("realtime") && estimate != null
            if (realtime) live = true
            val status = when { state == "CANCELED" || state == "CANCELLED" -> TimeStatus.Cancelled; state == "SKIPPED" -> TimeStatus.Skipped
                realtime -> TimeStatus.Realtime; else -> TimeStatus.Scheduled }
            return TimePoint(vehicleStopTime(date, scheduled), vehicleStopTime(date, if (realtime) estimate!! else scheduled), status)
        }
        Call(stop, index, time("Arrival"), time("Departure"), stopSequence = row.optInt("stopSequence", -1).takeIf { it >= 0 })
    }
    val vehicles = trip.array("vehiclePositions").map { v ->
        val relationship = v.optJSONObject("stopRelationship")
        TripVehicle(v.text("vehicleId"), v.text("label"), v.text("vehicleModel"), v.text("licensePlate"), v.text("uicCode"),
            v.number("lat")?.takeIf { it in -90.0..90.0 }, v.number("lon")?.takeIf { it in -180.0..180.0 },
            v.number("heading")?.takeIf { it in 0.0..360.0 }?.rem(360.0), v.number("speed")?.takeIf { it >= 0 },
            v.optLong("lastUpdated", 0L).takeIf { it > 0 },
            relationship?.let { VehicleStopStatus.gtfsRt(it.text("status")) },
            relationship?.optJSONObject("stop")?.text("gtfsId")?.takeIf { it.isNotBlank() }?.let { Gid.of(provider, it) },
            relationship?.optInt("stopSequence", -1)?.takeIf { it >= 0 },
            relationship?.seconds("arrivalTime")?.let { vehicleStopTime(firstServiceDate, it) })
    }
    val summary = TripSummary(Gid.of(provider, trip.text("gtfsId").ifBlank { "@${calls.firstOrNull()?.time?.scheduled}" }), transitRoute,
        trip.text("tripHeadsign").ifBlank { calls.lastOrNull()?.stop?.name.orEmpty() }, day,
        trip.text("tripShortName"), trip.text("trainCategoryName"))
    return Trip(summary, calls, provenance.copy(kind = if (live && provenance.kind == DataKind.Scheduled) DataKind.Live else provenance.kind),
        MavService.parseAll(trip), vehicles = vehicles)
}

/** A stop needs an id, or a name with coordinates (the tracking query sends no ids); missing coordinates stay NaN. */
internal fun tripStop(o: JSONObject, provider: ProviderId): Stop? {
    fun text(key: String) = if (o.isNull(key)) "" else o.optString(key).trim().takeUnless { it.equals("null", true) }.orEmpty()
    val lat = o.optDouble("lat").takeIf { it in -90.0..90.0 } ?: Double.NaN
    val lon = o.optDouble("lon").takeIf { it in -180.0..180.0 } ?: Double.NaN
    val id = text("gtfsId"); val name = text("name")
    if (id.isBlank() && (name.isBlank() || lat.isNaN() || lon.isNaN())) return null
    return Stop(if (id.isNotBlank()) Gid.of(provider, id) else Gid.of(provider, "@$lat,$lon"), name, lat, lon, platform = text("platformCode"))
}
