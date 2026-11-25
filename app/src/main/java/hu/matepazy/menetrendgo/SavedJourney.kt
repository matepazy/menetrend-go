package hu.matepazy.menetrendgo

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One exact itinerary on its own date. The provider itinerary is stored verbatim so services, alerts and logos survive;
 * live positions and delays are dropped on load, because a stored delay is never current. The journey screen refreshes them.
 */
data class SavedJourney(val journey: Journey, val name: String = "", val note: String = "", val savedAt: Long = 0L) {
    val key get() = journey.key
    val origin get() = journey.legs.first().from.name
    val destination get() = journey.legs.last().to.name
    val title get() = name.ifBlank { "$origin → $destination" }
    val departure get() = journey.legs.first().departureTime.scheduled
    val arrival get() = journey.legs.last().arrivalTime.scheduled
    fun past(now: Long) = arrival < now

    fun json() = JSONObject().put("itinerary", JSONObject(journey.itineraryJson ?: itineraryJson(journey).toString()))
        .put("name", name).put("note", note).put("savedAt", savedAt)

    companion object {
        const val MAX_NAME_LENGTH = 60
        const val MAX_NOTE_LENGTH = 500
        fun of(journey: Journey, now: Long = System.currentTimeMillis()) =
            SavedJourney(scheduledOnly(journey.copy(rawGraphQl = null)), savedAt = now)
        fun parse(json: JSONObject): SavedJourney {
            val itinerary = json.getJSONObject("itinerary")
            val journey = Journey.parse(itinerary).copy(itineraryJson = itinerary.toString())
            require(journey.legs.isNotEmpty())
            return SavedJourney(scheduledOnly(journey), json.optString("name").trim().take(MAX_NAME_LENGTH),
                json.optString("note").take(MAX_NOTE_LENGTH), json.optLong("savedAt"))
        }
        private fun scheduledOnly(journey: Journey) = journey.copy(fetchedAt = 0L, legs = journey.legs.map {
            it.copy(start = it.departureTime.scheduled, end = it.arrivalTime.scheduled, realTime = false,
                departureDelay = 0, arrivalDelay = 0)
        })
    }
}

/** Minimal itinerary for journeys that did not come from a plan response (e.g. a restored tracking session). */
internal fun itineraryJson(journey: Journey) = JSONObject().put("legs", JSONArray(journey.legs.map { leg ->
    JSONObject().put("mode", leg.mode).put("startTime", leg.start).put("endTime", leg.end)
        .put("from", leg.from.json()).put("to", leg.to.json())
        .put("route", JSONObject().put("shortName", leg.route).put("color", leg.routeColor).put("longName", leg.routeLongName))
        .put("trip", JSONObject().put("gtfsId", leg.tripId).put("tripHeadsign", leg.headsign).put("trainCategoryName", leg.category))
        .put("intermediateStops", JSONArray(leg.intermediate.map { it.json() }))
        .put("legGeometry", JSONObject().put("points", leg.geometry))
        .put("realTime", leg.realTime).put("arrivalDelay", leg.arrivalDelay).put("departureDelay", leg.departureDelay)
})).put("duration", journey.duration).put("idSchema", Journey.ID_SCHEMA)

private val SavedDay = DateTimeFormatter.ofPattern("MMM d., EEEE", Locale.forLanguageTag("hu-HU"))
private val SavedDayWithYear = DateTimeFormatter.ofPattern("yyyy. MMM d., EEEE", Locale.forLanguageTag("hu-HU"))

/** "Ma", "Holnap", "Tegnap", otherwise a Hungarian weekday date; the year only appears outside the current one. */
fun savedJourneyDay(millis: Long, today: LocalDate = LocalDate.now(Budapest)): String {
    val date = Instant.ofEpochMilli(millis).atZone(Budapest).toLocalDate()
    return when (date) {
        today -> "Ma"
        today.plusDays(1) -> "Holnap"
        today.minusDays(1) -> "Tegnap"
        else -> date.format(if (date.year == today.year) SavedDay else SavedDayWithYear)
    }
}

/** Upcoming first by departure, then the ones that already ran, most recent first. */
fun sortSavedJourneys(items: List<SavedJourney>, now: Long) =
    items.filter { !it.past(now) }.sortedBy { it.departure } + items.filter { it.past(now) }.sortedByDescending { it.departure }
