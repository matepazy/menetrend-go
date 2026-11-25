package hu.matepazy.menetrendgo

import org.json.JSONObject
import java.time.LocalDate

/** A MÁVPlusz-shaped trip JSON fixture as the canonical [Trip] the app now uses. */
internal fun mavTrip(json: JSONObject, day: String = "2026-11-08"): Trip =
    parseTripJson(json, LocalDate.parse(day), ProviderId.Mav, Provenance.of(ProviderId.Mav, 0, DataKind.Scheduled))
