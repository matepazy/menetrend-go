package hu.matepazy.menetrendgo

import kotlinx.coroutines.CancellationException

/** Keep the loaded search pages, refreshing exact service/stop occurrences in place. */
internal suspend fun refreshDepartureJourneys(journeys: List<Journey>,
    fetchTrip: suspend (String, String) -> Trip): List<Journey> {
    val trips = mutableMapOf<Pair<String, String>, Result<Trip>>()
    return journeys.map { journey ->
        val times = refreshJourneyLegTimes(journey, emptyMap()) { id, day ->
            val key = id to day
            val result = trips[key] ?: try { Result.success(fetchTrip(id, day)) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { Result.failure<Trip>(e) }
            trips[key] = result
            result.getOrThrow()
        }
        journey.copy(legs = journey.legs.mapIndexed { index, leg ->
            val fresh = times[index]
            if (fresh == null || fresh.departure.failed || fresh.arrival.failed) leg else {
                val departure = fresh.departure.time
                val arrival = fresh.arrival.time
                leg.copy(start = departure.estimate, end = arrival.estimate,
                    realTime = departure.realtime || arrival.realtime,
                    departureDelay = ((departure.estimate - departure.scheduled) / 1000).toInt(),
                    arrivalDelay = ((arrival.estimate - arrival.scheduled) / 1000).toInt())
            }
        })
    }
}
