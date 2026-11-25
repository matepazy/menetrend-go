package hu.matepazy.menetrendgo

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/** The single place where provider mode vocabularies become [Mode]. */
object ModeMapping {
    private val otpAliases = mapOf("REPLACEMENT_BUS" to Mode.RAIL_REPLACEMENT_BUS, "TRAM_TRAIN" to Mode.TRAMTRAIN,
        "HEV" to Mode.SUBURBAN_RAILWAY, "BIKE" to Mode.BICYCLE)

    /** MÁVPlusz/OTP (and MOTIS, which shares the names) mode strings; unknown modes are [Mode.OTHER]. */
    fun fromOtp(value: String): Mode {
        val mode = value.trim().uppercase(Locale.ROOT)
        return otpAliases[mode] ?: Mode.entries.firstOrNull { it.name == mode } ?: Mode.OTHER
    }

    /** utas.hu uses OTP names with a few spellings of its own. */
    fun fromUtas(value: String) = fromOtp(value)

    /** GTFS route_type, basic and extended (Google's 100–1700 codes); unknown types count as a bus. */
    fun fromGtfsRouteType(type: Int): Mode = when (type) {
        0, in 900..999 -> Mode.TRAM
        1, 401, 402 -> Mode.SUBWAY
        2, 404, in 100..108, in 110..199 -> Mode.RAIL
        109 -> Mode.SUBURBAN_RAILWAY
        403, 400 -> Mode.TRAMTRAIN
        3, in 700..713, in 715..799 -> Mode.BUS
        714 -> Mode.RAIL_REPLACEMENT_BUS
        in 200..299 -> Mode.COACH
        4, in 1000..1099, 1200 -> Mode.FERRY
        5 -> Mode.CABLE_CAR
        6, in 1300..1399 -> Mode.GONDOLA
        7, 1400 -> Mode.FUNICULAR
        11, in 800..899 -> Mode.TROLLEYBUS
        12 -> Mode.MONORAIL
        else -> Mode.BUS
    }

    /** GBFS vehicle_types.form_factor (v2.3+). */
    fun fromGbfsFormFactor(value: String): Mode = when (value.trim().lowercase(Locale.ROOT)) {
        "bicycle", "cargo_bicycle" -> Mode.BICYCLE
        "scooter", "scooter_standing", "scooter_seated" -> Mode.SCOOTER
        "moped" -> Mode.MOPED
        "car" -> Mode.CAR
        else -> Mode.OTHER
    }
}

/** Versioned JSON for the canonical model, for what the app persists (saved places, journeys, widget rows).
 * Decoding is lenient about missing optional fields and strict about identity: no id, no entity. */
object TransitCodec {
    const val SCHEMA = 2

    // ---- Small helpers ----
    private fun JSONObject.text(key: String) = if (isNull(key)) "" else optString(key)
    private fun JSONObject.longOrNull(key: String) = if (has(key) && !isNull(key)) getLong(key) else null
    private fun JSONObject.doubleOrNull(key: String) = if (has(key) && !isNull(key)) getDouble(key) else null
    private fun JSONObject.intOrNull(key: String) = if (has(key) && !isNull(key)) getInt(key) else null
    private fun JSONObject.gid(key: String) = Gid.parse(text(key))
    private fun JSONObject.putText(key: String, value: String) = apply { if (value.isNotEmpty()) put(key, value) }
    private fun JSONObject.putOpt(key: String, value: Any?) = apply { if (value != null) put(key, value) }
    private fun <T> JSONArray?.mapObjects(block: (JSONObject) -> T?): List<T> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.let(block) }
    private fun mode(value: String) = Mode.entries.firstOrNull { it.name == value } ?: Mode.OTHER

    // ---- Provenance ----
    fun encode(p: Provenance): JSONObject = JSONObject().put("src", JSONArray(p.sources.map { it.id }.sorted()))
        .put("at", p.fetchedAt).put("kind", p.kind.name)
    fun decodeProvenance(o: JSONObject?): Provenance? {
        o ?: return null
        val sources = o.optJSONArray("src")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(ProviderId.namespace::matches)?.let(::ProviderId) } }.orEmpty()
        val kind = DataKind.entries.firstOrNull { it.name == o.text("kind") } ?: return null
        return Provenance(sources.toSet(), o.optLong("at"), kind)
    }

    // ---- Times ----
    fun encode(t: TimePoint): JSONObject = JSONObject().put("s", t.scheduled)
        .apply { if (t.estimate != t.scheduled) put("e", t.estimate) }
        .apply { if (t.status != TimeStatus.Scheduled) put("st", t.status.name) }
    fun decodeTime(o: JSONObject?): TimePoint? {
        val scheduled = o?.longOrNull("s") ?: return null
        return TimePoint(scheduled, o.longOrNull("e") ?: scheduled,
            TimeStatus.entries.firstOrNull { it.name == o.text("st") } ?: TimeStatus.Scheduled)
    }

    // ---- Routes and stops ----
    fun encode(r: TransitRoute): JSONObject = JSONObject().putOpt("id", r.id?.raw).putText("short", r.shortName)
        .putText("long", r.longName).put("mode", r.mode.name).putText("color", r.color).putText("text", r.textColor)
        .apply { r.agency?.let { put("agency", JSONObject().putOpt("id", it.id?.raw).put("name", it.name)) } }
        .apply { r.logo?.let { put("logo", JSONObject().put("code", it.code).put("fg", it.foreground).put("bg", it.background)) } }
    fun decodeRoute(o: JSONObject?): TransitRoute? {
        o ?: return null
        val agency = o.optJSONObject("agency")?.let { Agency(it.gid("id"), it.text("name")) }
        val logo = o.optJSONObject("logo")?.let { l -> l.intOrNull("code")?.let { RouteLogo(it, l.text("fg"), l.text("bg")) } }
        return TransitRoute(o.gid("id"), o.text("short"), o.text("long"), mode(o.text("mode")),
            o.text("color"), o.text("text"), agency, logo)
    }

    fun encode(s: Stop): JSONObject = JSONObject().put("id", s.id.raw).put("name", s.name).put("lat", s.lat).put("lon", s.lon)
        .putOpt("parent", s.parentId?.raw).putText("platform", s.platform).putText("locality", s.locality)
        .apply { if (s.modes.isNotEmpty()) put("modes", JSONArray(s.modes.map { it.name }.sorted())) }
        .apply { if (s.aliases.isNotEmpty()) put("aliases", JSONArray(s.aliases.map { it.raw }.sorted())) }
    fun decodeStop(o: JSONObject?): Stop? {
        o ?: return null
        val id = o.gid("id") ?: return null
        val lat = o.doubleOrNull("lat") ?: return null; val lon = o.doubleOrNull("lon") ?: return null
        fun strings(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        return Stop(id, o.text("name"), lat, lon, o.gid("parent"), o.text("platform"),
            strings("modes").map(::mode).toSet(), o.text("locality"), strings("aliases").mapNotNull(Gid::parse).toSet())
    }

    /** The device location is never persisted. */
    fun encode(p: Place): JSONObject = when (p) {
        is Place.StopPlace -> encode(p.stop).put("t", "stop")
        is Place.Address -> JSONObject().put("t", "addr").put("name", p.name).put("lat", p.lat).put("lon", p.lon).putText("detail", p.detail)
        is Place.CurrentLocation -> throw IllegalArgumentException("The current location is not persisted")
    }
    fun decodePlace(o: JSONObject?): Place? = when (o?.text("t")) {
        "stop" -> decodeStop(o)?.let(Place::StopPlace)
        "addr" -> {
            val lat = o.doubleOrNull("lat"); val lon = o.doubleOrNull("lon")
            if (lat == null || lon == null) null else Place.Address(o.text("name"), lat, lon, o.text("detail"))
        }
        else -> null
    }

    // ---- Calls and trips ----
    fun encode(c: Call): JSONObject = JSONObject().put("stop", encode(c.stop)).put("seq", c.sequence)
        .apply { c.arrival?.let { put("arr", encode(it)) }; c.departure?.let { put("dep", encode(it)) } }
        .apply { if (c.platform != c.stop.platform) put("platform", c.platform) }
        .apply { if (c.pickup != Boarding.Regular) put("pickup", c.pickup.name); if (c.dropoff != Boarding.Regular) put("dropoff", c.dropoff.name) }
    fun decodeCall(o: JSONObject?): Call? {
        o ?: return null
        val stop = decodeStop(o.optJSONObject("stop")) ?: return null
        fun boarding(key: String) = Boarding.entries.firstOrNull { it.name == o.text(key) } ?: Boarding.Regular
        return Call(stop, o.optInt("seq"), decodeTime(o.optJSONObject("arr")), decodeTime(o.optJSONObject("dep")),
            if (o.has("platform")) o.text("platform") else stop.platform, boarding("pickup"), boarding("dropoff"))
    }

    fun encode(t: TripSummary): JSONObject = JSONObject().put("id", t.id.raw).put("route", encode(t.route))
        .put("headsign", t.headsign).put("day", t.serviceDay.toString()).putText("short", t.shortName).putText("category", t.category)
    fun decodeTripSummary(o: JSONObject?): TripSummary? {
        o ?: return null
        val id = o.gid("id") ?: return null
        val route = decodeRoute(o.optJSONObject("route")) ?: return null
        val day = runCatching { LocalDate.parse(o.text("day")) }.getOrNull() ?: return null
        return TripSummary(id, route, o.text("headsign"), day, o.text("short"), o.text("category"))
    }

    fun encode(d: Departure): JSONObject = JSONObject().put("trip", encode(d.trip)).put("call", encode(d.call)).put("prov", encode(d.provenance))
    fun decodeDeparture(o: JSONObject?): Departure? {
        o ?: return null
        val trip = decodeTripSummary(o.optJSONObject("trip")) ?: return null
        val call = decodeCall(o.optJSONObject("call")) ?: return null
        val provenance = decodeProvenance(o.optJSONObject("prov")) ?: return null
        return Departure(trip, call, provenance)
    }

    // ---- Services and alerts ----
    fun encode(s: ServiceFeature): JSONObject = JSONObject().put("name", s.name).putOpt("font", s.fontCode).putText("charset", s.fontCharSet)
        .putOpt("fromIdx", s.fromIndex).putOpt("tillIdx", s.tillIndex).putText("from", s.from).putText("till", s.till)
    fun decodeService(o: JSONObject?): ServiceFeature? {
        val name = o?.text("name")?.takeIf { it.isNotBlank() } ?: return null
        return MavService(name, o.intOrNull("font"), o.text("charset"), o.intOrNull("fromIdx"), o.intOrNull("tillIdx"), o.text("from"), o.text("till"))
    }
    fun encode(a: ServiceAlert): JSONObject = JSONObject().put("id", a.id).putText("header", a.header).putText("body", a.description)
        .putText("url", a.url).putOpt("start", a.startSeconds).putOpt("end", a.endSeconds).apply { if (a.warning) put("warning", true) }
    fun decodeAlert(o: JSONObject?): ServiceAlert? {
        val id = o?.text("id")?.takeIf { it.isNotBlank() } ?: return null
        return MavAlert(id, o.text("header"), o.text("body"), o.text("url"), o.longOrNull("start"), o.longOrNull("end"), o.optBoolean("warning"))
    }

    // ---- Journeys ----
    fun encode(leg: JourneyLeg): JSONObject = when (leg) {
        is JourneyLeg.Ride -> JSONObject().put("t", "ride").put("trip", encode(leg.trip)).put("board", encode(leg.board)).put("alight", encode(leg.alight))
            .apply { if (leg.intermediate.isNotEmpty()) put("via", JSONArray(leg.intermediate.map(::encode))) }
            .putText("geom", leg.geometry)
            .apply { if (leg.services.isNotEmpty()) put("services", JSONArray(leg.services.map(::encode))) }
            .apply { if (leg.alerts.isNotEmpty()) put("alerts", JSONArray(leg.alerts.map(::encode))) }
        is JourneyLeg.Walk -> JSONObject().put("t", "walk").put("from", encode(leg.from)).put("to", encode(leg.to))
            .put("start", leg.start).put("end", leg.end).putText("geom", leg.geometry)
        is JourneyLeg.Rental -> JSONObject().put("t", "rental").put("system", leg.system.raw).put("vehicle", leg.vehicleType.name)
            .put("from", encode(leg.from)).put("to", encode(leg.to)).put("start", leg.start).put("end", leg.end).putText("geom", leg.geometry)
    }
    fun decodeLeg(o: JSONObject?): JourneyLeg? = when (o?.text("t")) {
        "ride" -> {
            val trip = decodeTripSummary(o.optJSONObject("trip")); val board = decodeCall(o.optJSONObject("board")); val alight = decodeCall(o.optJSONObject("alight"))
            if (trip == null || board == null || alight == null) null else JourneyLeg.Ride(trip, board, alight,
                o.optJSONArray("via").mapObjects(::decodeCall), o.text("geom"),
                o.optJSONArray("services").mapObjects(::decodeService), o.optJSONArray("alerts").mapObjects(::decodeAlert))
        }
        "walk" -> {
            val from = decodePlace(o.optJSONObject("from")); val to = decodePlace(o.optJSONObject("to"))
            if (from == null || to == null) null else JourneyLeg.Walk(from, to, o.optLong("start"), o.optLong("end"), o.text("geom"))
        }
        "rental" -> {
            val system = o.gid("system"); val from = decodePlace(o.optJSONObject("from")); val to = decodePlace(o.optJSONObject("to"))
            if (system == null || from == null || to == null) null
            else JourneyLeg.Rental(system, mode(o.text("vehicle")), from, to, o.optLong("start"), o.optLong("end"), o.text("geom"))
        }
        else -> null
    }

    /** A whole itinerary with its schema version; a leg that cannot be read voids the itinerary rather than shortening it. */
    fun encode(i: Itinerary): JSONObject = JSONObject().put("schema", SCHEMA).put("legs", JSONArray(i.legs.map(::encode)))
        .put("prov", encode(i.provenance)).putOpt("state", i.providerState)
    fun decodeItinerary(o: JSONObject?): Itinerary? {
        if (o == null || o.optInt("schema") != SCHEMA) return null
        val raw = o.optJSONArray("legs") ?: return null
        val legs = (0 until raw.length()).map { decodeLeg(raw.optJSONObject(it)) ?: return null }
        if (legs.isEmpty()) return null
        val provenance = decodeProvenance(o.optJSONObject("prov")) ?: return null
        return Itinerary(legs, provenance, if (o.isNull("state")) null else o.getString("state"))
    }
}
