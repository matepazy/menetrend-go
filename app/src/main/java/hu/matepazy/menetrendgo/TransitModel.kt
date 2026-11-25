package hu.matepazy.menetrendgo

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/* The app-wide transit model every provider adapter translates into (docs/ADATRETEG.md).
 * Plain Kotlin without org.json, so merging and querying never touch provider JSON. */

/** A data provider namespace: "mav", "utas", "gtfs", later e.g. "motis" or "gbfs". */
@JvmInline value class ProviderId(val id: String) {
    init { require(id.matches(namespace)) { "Invalid provider id: $id" } }
    override fun toString() = id
    companion object {
        internal val namespace = Regex("[a-z][a-z0-9_]{0,23}")
        val Mav = ProviderId("mav")
        val Utas = ProviderId("utas")
        val Gtfs = ProviderId("gtfs")
    }
}

/** Namespaced entity id, "<provider>:<provider-local id>". Only the first colon separates, so
 * MÁVPlusz ids such as "1:005501234" or "BKK:F00123" stay intact as the local part. */
@JvmInline value class Gid(val raw: String) {
    val provider get() = ProviderId(raw.substringBefore(':'))
    val local get() = raw.substringAfter(':')
    override fun toString() = raw
    companion object {
        fun of(provider: ProviderId, local: String): Gid {
            require(local.isNotEmpty()) { "Empty local id for $provider" }
            return Gid("${provider.id}:$local")
        }
        fun parse(raw: String): Gid? {
            val colon = raw.indexOf(':')
            if (colon <= 0 || colon == raw.length - 1) return null
            return if (ProviderId.namespace.matches(raw.substring(0, colon))) Gid(raw) else null
        }
        /** Ids stored before namespacing: "utas:" ids were already namespaced, every other id is MÁVPlusz. */
        fun legacy(raw: String): Gid? = when {
            raw.isBlank() -> null
            raw.startsWith("utas:") -> Gid(raw)
            else -> of(ProviderId.Mav, raw)
        }
    }
}

/** Coarse family: vehicles from different families never merge, even with the same number. */
enum class ModeFamily { Rail, Road, Urban, Water, Cable, Micro, Walk, Other }

/** Every mode the app knows. Constant names equal the MÁVPlusz/OTP mode strings used across the UI,
 * so [otp] and [ModeMapping.fromOtp] are lossless for the modes OTP has. */
enum class Mode(val family: ModeFamily) {
    RAIL(ModeFamily.Rail), SUBURBAN_RAILWAY(ModeFamily.Rail), TRAMTRAIN(ModeFamily.Rail), MONORAIL(ModeFamily.Rail),
    BUS(ModeFamily.Road), COACH(ModeFamily.Road), RAIL_REPLACEMENT_BUS(ModeFamily.Road), TROLLEYBUS(ModeFamily.Road),
    TRAM(ModeFamily.Urban), SUBWAY(ModeFamily.Urban),
    FERRY(ModeFamily.Water),
    CABLE_CAR(ModeFamily.Cable), GONDOLA(ModeFamily.Cable), FUNICULAR(ModeFamily.Cable),
    BICYCLE(ModeFamily.Micro), SCOOTER(ModeFamily.Micro), MOPED(ModeFamily.Micro), CAR(ModeFamily.Other),
    WALK(ModeFamily.Walk), OTHER(ModeFamily.Other);
    val otp get() = name
    val transit get() = family != ModeFamily.Micro && family != ModeFamily.Walk && this != CAR && this != OTHER
}

/** Whether a value was observed live, comes from a provider timetable, or from a downloaded offline feed. */
enum class DataKind { Live, Scheduled, Offline }

/** Who contributed a value and how fresh it is; merged values keep every contributor. */
@Immutable data class Provenance(val sources: Set<ProviderId>, val fetchedAt: Long, val kind: DataKind) {
    /** Live beats scheduled beats offline: a merged value is as live as its liveliest contributor. */
    operator fun plus(other: Provenance) = Provenance(sources + other.sources, maxOf(fetchedAt, other.fetchedAt),
        if (kind.ordinal <= other.kind.ordinal) kind else other.kind)
    companion object {
        fun of(provider: ProviderId, fetchedAt: Long, kind: DataKind) = Provenance(setOf(provider), fetchedAt, kind)
    }
}

enum class TimeStatus { Scheduled, Realtime, Cancelled, Skipped }

/** One scheduled moment and, when known, its live estimate (epoch millis). */
@Immutable data class TimePoint(val scheduled: Long, val estimate: Long = scheduled, val status: TimeStatus = TimeStatus.Scheduled) {
    /** Live evidence: an estimate, or a cancellation or skip, which only live data reports. */
    val realtime get() = status != TimeStatus.Scheduled
    val serviceTime get() = ServiceTime(scheduled, if (realtime) estimate else scheduled, realtime)
    companion object {
        fun of(time: ServiceTime, cancelled: Boolean = false) = TimePoint(time.scheduled, time.estimate,
            when { cancelled -> TimeStatus.Cancelled; time.realtime -> TimeStatus.Realtime; else -> TimeStatus.Scheduled })
    }
}

@Immutable data class Agency(val id: Gid?, val name: String)

@Immutable data class TransitRoute(val id: Gid?, val shortName: String, val longName: String, val mode: Mode,
    val color: String = "", val textColor: String = "", val agency: Agency? = null, val logo: RouteLogo? = null)

/** A provider stop or station. A station's platforms point to it through [parentId]; [aliases] are
 * the same stop's ids at other providers, learnt from feeds or merges. [lat]/[lon] are NaN when the provider gave none. */
@Immutable data class Stop(val id: Gid, val name: String, val lat: Double, val lon: Double,
    val parentId: Gid? = null, val platform: String = "", val modes: Set<Mode> = emptySet(),
    val locality: String = "", val aliases: Set<Gid> = emptySet()) {
    fun knownAs(other: Gid) = other == id || other in aliases
}

/** Anywhere a journey can start or end. */
@Immutable sealed interface Place {
    val name: String; val lat: Double; val lon: Double
    data class StopPlace(val stop: Stop) : Place {
        override val name get() = stop.name; override val lat get() = stop.lat; override val lon get() = stop.lon
    }
    /** An OpenStreetMap place or address; [detail] is its secondary line. */
    data class Address(override val name: String, override val lat: Double, override val lon: Double, val detail: String = "") : Place
    /** The device location; never persisted. */
    data class CurrentLocation(override val lat: Double, override val lon: Double, val calculatedAt: Long) : Place {
        override val name get() = "Jelenlegi hely"
    }
}

/** GTFS pickup_type/drop_off_type. */
enum class Boarding { Regular, None, PhoneAgency, AskDriver;
    companion object { fun gtfs(value: Int) = entries.getOrElse(value) { Regular } }
}

/** A trip's stop at one stop; terminal calls have only one of the two times. [sequence] is the position in
 * the trip, [stopSequence] the provider's own GTFS stop_sequence when it reports one. */
@Immutable data class Call(val stop: Stop, val sequence: Int, val arrival: TimePoint?, val departure: TimePoint?,
    val platform: String = stop.platform, val pickup: Boarding = Boarding.Regular, val dropoff: Boarding = Boarding.Regular,
    val stopSequence: Int? = null) {
    /** Departure first, falling back to the arrival at the terminus. */
    val time get() = departure ?: arrival
    val cancelled get() = time?.status == TimeStatus.Cancelled || time?.status == TimeStatus.Skipped
}

typealias ServiceAlert = MavAlert
typealias ServiceFeature = MavService

/** What a departure, leg or vehicle needs to name and open a trip, without its stops. */
@Immutable data class TripSummary(val id: Gid, val route: TransitRoute, val headsign: String, val serviceDay: LocalDate,
    val shortName: String = "", val category: String = "")

/** GTFS-RT VehicleStopStatus: where a trip's vehicle is relative to [TripVehicle.stopId]. */
enum class VehicleStopStatus { IncomingAt, StoppedAt, InTransitTo;
    companion object { fun gtfsRt(value: String) = when (value) {
        "INCOMING_AT" -> IncomingAt; "STOPPED_AT" -> StoppedAt; "IN_TRANSIT_TO" -> InTransitTo; else -> null } }
}

/** A physical vehicle the provider attaches to a trip: its identity, last fix (when given) and stop progress.
 * [id] is the provider's own vehicle id; [stopArrival] is epoch millis. */
@Immutable data class TripVehicle(val id: String, val label: String = "", val model: String = "", val plate: String = "",
    val uicCode: String = "", val lat: Double? = null, val lon: Double? = null, val heading: Double? = null,
    val speedMetersPerSecond: Double? = null, val updatedSeconds: Long? = null,
    val stopStatus: VehicleStopStatus? = null, val stopId: Gid? = null, val stopSequence: Int? = null, val stopArrival: Long? = null)

/** [feedName] names the downloaded timetable an offline trip comes from. */
@Immutable data class Trip(val summary: TripSummary, val calls: List<Call>, val provenance: Provenance,
    val services: List<ServiceFeature> = emptyList(), val alerts: List<ServiceAlert> = emptyList(),
    val vehicles: List<TripVehicle> = emptyList(), val feedName: String = "", val shapeRef: String? = null) {
    val offline get() = provenance.kind == DataKind.Offline
    val id get() = summary.id
    fun callAt(stop: Gid) = calls.firstOrNull { it.stop.knownAs(stop) || it.stop.parentId == stop }
}

@Immutable data class Departure(val trip: TripSummary, val call: Call, val provenance: Provenance) {
    val key get() = "${trip.id}:${call.time?.scheduled}"
}

/** One part of a journey. */
@Immutable sealed interface JourneyLeg {
    val start: Long; val end: Long
    data class Ride(val trip: TripSummary, val board: Call, val alight: Call, val intermediate: List<Call> = emptyList(),
        val geometry: String = "", val services: List<ServiceFeature> = emptyList(), val alerts: List<ServiceAlert> = emptyList()) : JourneyLeg {
        override val start get() = board.departure?.estimate ?: board.arrival?.estimate ?: 0L
        override val end get() = alight.arrival?.estimate ?: alight.departure?.estimate ?: 0L
    }
    data class Walk(val from: Place, val to: Place, override val start: Long, override val end: Long, val geometry: String = "") : JourneyLeg
    /** Reserved for bike/scooter legs; no provider produces it yet. */
    data class Rental(val system: Gid, val vehicleType: Mode, val from: Place, val to: Place,
        override val start: Long, override val end: Long, val geometry: String = "") : JourneyLeg
}

/** A planned journey. [providerState] is opaque provider data needed to refresh or page it (e.g. the MÁVPlusz request). */
@Immutable data class Itinerary(val legs: List<JourneyLeg>, val provenance: Provenance, val providerState: String? = null) {
    val start get() = legs.first().start
    val end get() = legs.last().end
    val rides get() = legs.filterIsInstance<JourneyLeg.Ride>()
    val changes get() = (rides.size - 1).coerceAtLeast(0)
    val key get() = legs.joinToString("|") { leg -> when (leg) {
        is JourneyLeg.Ride -> "${leg.trip.id}:${leg.board.departure?.scheduled}:${leg.board.stop.name}"
        is JourneyLeg.Walk -> "walk:${leg.start}:${leg.from.name}"
        is JourneyLeg.Rental -> "${leg.system}:${leg.start}:${leg.from.name}"
    } }
}

/** A live vehicle. [tripId] and [serviceDay] open its timetable when known; [observedAt] is the provider's GPS timestamp in epoch seconds. */
@Immutable data class VehiclePosition(val id: Gid, val label: String, val route: TransitRoute, val lat: Double, val lon: Double,
    val provenance: Provenance, val tripId: Gid? = null, val serviceDay: LocalDate? = null, val headsign: String = "",
    val heading: Double? = null, val speedMetersPerSecond: Double? = null, val observedAt: Long? = null,
    val model: String = "", val plate: String = "", val uicCode: String = "")

/* Reserved for bike and scooter sharing (GBFS); no provider or UI uses them yet. */
@Immutable data class RentalSystem(val id: Gid, val name: String, val operator: String = "", val url: String = "")
@Immutable data class RentalStation(val id: Gid, val system: Gid, val name: String, val lat: Double, val lon: Double,
    val provenance: Provenance, val capacity: Int? = null, val vehiclesAvailable: Map<Mode, Int> = emptyMap(),
    val docksAvailable: Int? = null, val renting: Boolean = true, val returning: Boolean = true)
@Immutable data class RentalVehicle(val id: Gid, val system: Gid, val type: Mode, val lat: Double, val lon: Double,
    val provenance: Provenance, val batteryPercent: Int? = null, val rangeMeters: Int? = null, val stationId: Gid? = null)
