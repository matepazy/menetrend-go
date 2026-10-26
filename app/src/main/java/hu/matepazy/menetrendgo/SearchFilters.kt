package hu.matepazy.menetrendgo

import java.util.Locale

enum class SearchFilter(val title: String, val summary: String, val pattern: Regex? = null) {
    Direct("Közvetlen járatok", "Átszállás nélküli utazások."),
    AirConditioning("Légkondicionálás", "Légkondicionált jármű minden szakaszon.", Regex("légkondicion|klimatiz|klím")),
    FirstClass("1. osztály", "Első osztályú kocsi minden szakaszon.", Regex("1\\.\\s*(?:kocsi)?osztály|első osztály")),
    PremiumClass("1+ osztály", "Prémium osztály minden szakaszon.", Regex("prémium|1\\s*\\+\\.?\\s*(?:kocsi)?osztály")),
    SecondClass("2. osztály", "Másodosztályú kocsi minden szakaszon.", Regex("2\\.\\s*(?:kocsi)?osztály|másod(?:ik)?osztály|második osztály")),
    CountryPass("Országbérlettel", "A szolgáltató által jelzett elfogadás. Helyjegy szükséges lehet.", Regex("ország.?bérlet")),
    Bicycle("Kerékpárszállítás", "Kerékpár szállítható minden szakaszon. Foglalás szükséges lehet.", Regex("kerékpár|bicikli")),
    Accessible("Akadálymentes utazás", "Kerekesszékes utazásra jelzett járművek. Előzetes bejelentés szükséges lehet.", Regex("kerekesszék|mozgáskorlátozott|akadályment|alacsonypadló")),
    Dining("Étkezés / bisztró", "Jelzett étkezési szolgáltatás minden szakaszon.", Regex("büfé|bisztró|étkezőkocsi|étterem|étkezési|vendéglát")),
    Sleeper("Háló- / fekvőhely", "Háló- vagy fekvőhelyes kocsi minden szakaszon.", Regex("hálókocsi|fekvőhely|hálóhely|kusett"));

    // Official MÁVPlusz client mapping (snowflake icon). Climate is a planner
    // condition, not an infoServices notice; never infer it from train category.
    val providerSearchCode: String? get() = when (this) {
        AirConditioning -> "ESZKOZ_KERESESI_FELTETEL#10907842"
        else -> null
    }

    fun matches(journey: Journey): Boolean {
        val transit = journey.legs.filterNot { it.walking }
        if (this == Direct) return transit.size == 1
        if (transit.isNotEmpty() && this in journey.providerFilters) return true
        return transit.isNotEmpty() && transit.all { leg ->
            val statements = leg.services.filter { pattern!!.containsMatchIn(it.name.lowercase(Locale.ROOT)) }
            val coverage = ServiceCoverage(leg)
            statements.any { positive(it.name) && coverage.covers(it) } &&
                statements.none { !positive(it.name) && coverage.overlaps(it) }
        }
    }

    private fun positive(value: String): Boolean {
        val name = value.lowercase(Locale.ROOT)
        if (Regex("\\bnem\\b|kizár|tilos|nincs|nem szállít|nem vehető").containsMatchIn(name)) return false
        return when (this) {
            CountryPass -> Regex("igénybe vehető|érvényes|elfogad|használható").containsMatchIn(name)
            Bicycle -> Regex("szállíthat|szállítás|kerékpárhely|kerékpárszállító").containsMatchIn(name)
            else -> true
        }
    }
}

/** Resolve trip indices only from provider notices anchored to the selected stops.
 * Wider notices can then cover a subjourney, while partial bans still take effect.
 */
private class ServiceCoverage(val leg: Leg) {
    private fun index(name: String): Int? = leg.services.flatMap {
        listOfNotNull(it.fromIndex?.takeIf { _ -> it.from == name },
            it.tillIndex?.takeIf { _ -> it.till == name })
    }.distinct().singleOrNull()
    private val start = index(leg.from.name)
    private val end = index(leg.to.name)
    private fun unscoped(s: MavService) = s.from.isBlank() && s.till.isBlank() && s.fromIndex == null && s.tillIndex == null
    fun covers(s: MavService): Boolean {
        if (unscoped(s)) return true
        if (start != null && end != null && start < end && s.fromIndex != null && s.tillIndex != null)
            return s.fromIndex <= start && s.tillIndex >= end
        return (s.from.isBlank() || s.from == leg.from.name) &&
            (s.till.isBlank() || s.till == leg.to.name) && (s.from.isNotBlank() || s.till.isNotBlank())
    }
    fun overlaps(s: MavService): Boolean {
        if (unscoped(s)) return true
        if (start != null && end != null && start < end && s.fromIndex != null && s.tillIndex != null)
            return s.fromIndex < end && s.tillIndex > start
        // An unresolved restriction must not silently become positive evidence.
        return true
    }
}

internal fun filterJourneys(journeys: List<Journey>, filters: Set<SearchFilter>): List<Journey> =
    journeys.filter { journey -> filters.all { it.matches(journey) } }
