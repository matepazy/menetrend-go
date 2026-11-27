package hu.matepazy.menetrendgo

import kotlin.math.abs

/**
 * Downloaded timetables fill the gaps in a working MÁVPlusz answer: networks the provider does not carry
 * (for example Mór's local buses) join live results instead of waiting for Offline mód. A live answer always
 * wins; an offline item is added only when nothing live already stands for it, and it stays marked offline.
 */
internal object OfflineSupplement {
    private const val SAME_STOP_METERS = 400.0
    private const val SAME_TIME_MILLIS = 60_000L

    private fun key(value: String) = OfflineNetwork.normalizedName(value)

    /** Offline stops that are not a live stop already: neither its provider id nor its name nearby. */
    fun stations(live: List<Station>, offline: List<Station>): List<Station> = live + offline.filter { o ->
        live.none { l -> l.localId == o.localId ||
            key(l.name) == key(o.name) && OfflineNetwork.distance(l.lat, l.lon, o.lat, o.lon) < SAME_STOP_METERS }
    }

    private fun sameRide(route: String, headsign: String, scheduled: Long, otherRoute: String, otherHeadsign: String, otherScheduled: Long) =
        abs(scheduled - otherScheduled) <= SAME_TIME_MILLIS &&
            (key(route) == key(otherRoute) || (headsign.isNotBlank() && key(headsign) == key(otherHeadsign)))

    /** Offline departures with no live departure of the same line (or destination) at the same minute. A full live
     * board ([limit] rows) ends at its last departure, so offline rows never run past what the provider showed. */
    fun departures(live: List<StopDeparture>, offline: List<StopDeparture>, limit: Int = 30): List<StopDeparture> {
        val until = if (live.size >= limit) live.maxOf { it.time.scheduled } else Long.MAX_VALUE
        val extra = offline.filter { o ->
            o.time.scheduled <= until && live.none { l ->
                sameTripId(l.tripId, o.tripId) || sameRide(l.leg.route, l.headsign, l.time.scheduled, o.leg.route, o.headsign, o.time.scheduled)
            }
        }
        return if (extra.isEmpty()) live else (live + extra).sortedBy { it.time.estimate }
    }

    private fun rides(journey: Journey) = journey.legs.filterNot { it.walking }

    /** Feeds may namespace one provider trip differently ("hkir:123" and "123"). */
    private fun sameTripId(a: String, b: String) = a.isNotBlank() && b.isNotBlank() &&
        (a == b || a.substringAfterLast(':') == b.substringAfterLast(':'))

    /** The same vehicle run, possibly boarded or left at other stops: the same trip, or the same line at overlapping times. */
    private fun sameTrip(a: Leg, b: Leg) = sameTripId(a.tripId, b.tripId) ||
        key(a.route) == key(b.route) && a.departureTime.scheduled < b.arrivalTime.scheduled && b.departureTime.scheduled < a.arrivalTime.scheduled

    /** Live always wins: an offline journey is dropped when every ride of it is a run some live journey already uses
     * (even between other stops) or one of [providerTrips] (runs the provider has, so its planner already weighed
     * them), or when a live journey beats it (leaves no earlier, arrives no later, changes no
     * more often). What stays uses at least one run the provider does not know. Offline journeys may run past the
     * provider's last result; [pageEdge] keeps paging on the provider's results, and re-merging every page drops an
     * offline journey that a later live one covers. */
    fun journeys(live: List<Journey>, offline: List<Journey>, vehicles: Set<SearchVehicle> = emptySet(),
                 providerTrips: Set<String> = emptySet()): List<Journey> {
        val liveRides = live.flatMap(::rides)
        val extra = filterJourneyVehicles(offline, vehicles).filter { o ->
            !rides(o).all { ride -> ride.tripId in providerTrips || liveRides.any { sameTrip(it, ride) } } &&
                live.none { l -> l.start >= o.start && l.end <= o.end && l.changes <= o.changes }
        }
        return if (extra.isEmpty()) live else (live + extra).sortedBy { it.start }
    }

    /** Where the next page starts: after the last (or before the first) provider journey, so offline journeys that
     * reach further never make paging skip provider results. Offline journeys count only when there is nothing else. */
    fun pageEdge(journeys: List<Journey>, earlier: Boolean): Long {
        val paged = journeys.filterNot { it.offline }.ifEmpty { journeys }
        return if (earlier) paged.minOf { it.end } - 60_000 else paged.maxOf { it.start } + 60_000
    }
}
