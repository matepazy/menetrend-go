package hu.matepazy.menetrendgo

import java.text.Normalizer
import java.util.Locale

/** MÁVPlusz's public category marker palette, verified 2026-10-23.
 * Source: https://mavplusz.hu/main.56c42ecd9e04ed9c.js (Wae / Zae).
 * Use the category's fillColor, not the common dark-blue marker background.
 * Other transport modes retain their provider route colors.
 */
fun trainCategoryColor(leg: Leg): String? {
    if (leg.mode != "RAIL") return null
    for (label in listOf(leg.category, leg.routeLongName, leg.route)) {
        val normalized = Normalizer.normalize(label, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").uppercase(Locale.ROOT)
        val tokens = Regex("[A-Z]+").findAll(normalized).map { it.value }.toSet()
        when {
            tokens.any { it in setOf("IC", "EC", "INTERCITY", "EUROCITY") } -> return "274F96"
            tokens.any { it in setOf("EX", "EXPRESSZ", "EXPRESSZVONAT") } -> return "C82227"
            tokens.any { it in setOf("IR", "INTERREGIO", "SEBES", "SEBESVONAT") } -> return "29803C"
            tokens.any { it in setOf("Z", "ZONAZO") } -> return "FECC00"
            tokens.any { it in setOf("G", "GYORSITOTT") } -> return "B0CB1F"
            tokens.any { it in setOf("S", "SZEMELY", "SZEMELYVONAT") } -> return "00A0E3"
        }
    }
    return "000000" // Official unclassified rail fallback; never use the GraphQL rail color.
}
