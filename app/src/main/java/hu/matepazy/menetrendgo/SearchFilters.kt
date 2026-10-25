package hu.matepazy.menetrendgo

import java.util.Locale

enum class SearchFilter(val title: String, val summary: String, val pattern: Regex? = null) {
    Direct("Közvetlen járatok", "Átszállás nélküli utazások."),
    AirConditioning("Légkondicionálás", "Légkondicionált jármű minden szakaszon.", Regex("légkondicion|klimatiz|klím")),
    FirstClass("1. osztály", "Első osztályú kocsi minden szakaszon.", Regex("1\\.\\s*(?:kocsi)?osztály|első osztály")),
    SecondClass("2. osztály", "Másodosztályú kocsi minden szakaszon.", Regex("2\\.\\s*(?:kocsi)?osztály|másod(?:ik)?osztály|második osztály")),
    CountryPass("Országbérlettel", "A szolgáltató által jelzett elfogadás. Helyjegy szükséges lehet.", Regex("ország.?bérlet")),
    Bicycle("Kerékpárszállítás", "Kerékpár szállítható minden szakaszon. Foglalás szükséges lehet.", Regex("kerékpár|bicikli")),
    Accessible("Akadálymentes utazás", "Kerekesszékes utazásra jelzett járművek. Előzetes bejelentés szükséges lehet.", Regex("kerekesszék|mozgáskorlátozott|akadályment|alacsonypadló")),
    Wifi("Wi-Fi", "Jelzett fedélzeti internet minden szakaszon.", Regex("wi[\\s‐‑–-]?fi|wlan|internet")),
    Power("Konnektor / USB", "Jelzett töltési lehetőség minden szakaszon.", Regex("konnektor|csatlakozóaljzat|dugalj|töltő|usb|230\\s*v")),
    Dining("Étkezés / bisztró", "Jelzett étkezési szolgáltatás minden szakaszon.", Regex("büfé|bisztró|étkezőkocsi|étterem|étkezési|vendéglát")),
    Sleeper("Háló- / fekvőhely", "Háló- vagy fekvőhelyes kocsi minden szakaszon.", Regex("hálókocsi|fekvőhely|hálóhely|kusett"));

    fun matches(journey: Journey): Boolean {
        val transit = journey.legs.filterNot { it.walking }
        if (this == Direct) return transit.size == 1
        return transit.isNotEmpty() && transit.all { leg ->
            // A section-specific statement must cover the complete selected leg.
            // Indices alone cannot establish coverage: OTP does not expose boarding indices here.
            val relevant = leg.services.filter { service ->
                (service.from.isBlank() || service.from == leg.from.name) &&
                    (service.till.isBlank() || service.till == leg.to.name) &&
                    !(service.from.isBlank() && service.till.isBlank() &&
                        (service.fromIndex != null || service.tillIndex != null))
            }
            val statements = relevant.filter { pattern!!.containsMatchIn(it.name.lowercase(Locale.ROOT)) }
            statements.any { positive(it.name) } && statements.none { !positive(it.name) }
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

internal fun filterJourneys(journeys: List<Journey>, filters: Set<SearchFilter>): List<Journey> =
    journeys.filter { journey -> filters.all { it.matches(journey) } }
