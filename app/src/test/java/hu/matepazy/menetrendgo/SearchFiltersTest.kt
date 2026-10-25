package hu.matepazy.menetrendgo

import org.junit.Assert.*
import org.junit.Test

class SearchFiltersTest {
    private val a = Station("a", "Budapest", 47.5, 19.0)
    private val b = Station("b", "Szeged", 46.2, 20.1)
    private fun service(name: String) = MavService(name, null, "", null, null, "", "")
    private fun leg(vararg names: String) = Leg("RAIL", 0, 60_000, a, b, "IC", "trip", false, 0,
        emptyList(), "", services = names.map(::service))
    private fun journey(vararg legs: Leg) = Journey(60, legs.toList())

    @Test fun selectedConditionsMustMatchEveryTransitLegAndIgnoreWalking() {
        val matching = leg("Légkondicionált kocsi", "A vonatban 1. osztályú kocsi is közlekedik")
        val filters = setOf(SearchFilter.AirConditioning, SearchFilter.FirstClass)
        assertEquals(1, filterJourneys(listOf(journey(matching, matching.copy(mode = "WALK"))), filters).size)
        assertTrue(filterJourneys(listOf(journey(matching, leg("Légkondicionált kocsi"))), filters).isEmpty())
        assertTrue(filterJourneys(listOf(journey(leg())), filters).isEmpty())
        assertTrue(filterJourneys(listOf(journey(matching.copy(mode = "WALK"))), filters).isEmpty())
    }

    @Test fun negativeOrInformationalStatementsDoNotConfirmAcceptance() {
        assertTrue(SearchFilter.CountryPass.matches(journey(leg("Országbérlettel igénybe vehető"))))
        assertFalse(SearchFilter.CountryPass.matches(journey(leg("Országbérlet nem érvényes"))))
        assertFalse(SearchFilter.CountryPass.matches(journey(leg("Országbérlettel kapcsolatos információ"))))
        assertFalse(SearchFilter.CountryPass.matches(journey(leg("Országnapijeggyel igénybe vehető"))))
        assertFalse(SearchFilter.Bicycle.matches(journey(leg("Kerékpár nem szállítható"))))
        assertTrue(SearchFilter.Bicycle.matches(journey(leg("Kerékpárhely foglalása kötelező"))))
        assertFalse(SearchFilter.FirstClass.matches(journey(leg("Nincs 1. osztály"))))
        assertFalse(SearchFilter.FirstClass.matches(journey(leg("Prémium szolgáltatás"))))
    }

    @Test fun sectionDataMustCoverTheSelectedLegAndConflictingStatementsFail() {
        val acceptance = service("Országbérlettel igénybe vehető")
        fun matches(vararg services: MavService) = SearchFilter.CountryPass.matches(journey(leg().copy(services = services.toList())))
        assertTrue(matches(acceptance.copy(from = a.name, till = b.name, fromIndex = 2, tillIndex = 6)))
        assertFalse(matches(acceptance.copy(from = "Kistelek", till = b.name)))
        assertFalse(matches(acceptance.copy(fromIndex = 2, tillIndex = 6)))
        assertFalse(matches(acceptance, service("Országbérlet nem érvényes")))
    }

    @Test fun directRequiresExactlyOneTransitLegAndNoFiltersPreserveResults() {
        assertTrue(SearchFilter.Direct.matches(journey(leg(), leg().copy(mode = "WALK"))))
        assertFalse(SearchFilter.Direct.matches(journey(leg(), leg())))
        assertFalse(SearchFilter.Direct.matches(journey(leg().copy(mode = "WALK"))))
        val results = listOf(journey(leg()), journey(leg(), leg()))
        assertEquals(results, filterJourneys(results, emptySet()))
    }

    @Test fun additionalFacilitiesRequirePositiveEvidenceOnEveryTransitLeg() {
        val examples = mapOf(
            SearchFilter.SecondClass to listOf("A vonat 2. osztályú kocsikkal közlekedik", "Másodosztályú kocsi"),
            SearchFilter.Wifi to listOf("Wi-Fi szolgáltatás", "WIFI", "Vezeték nélküli internet"),
            SearchFilter.Power to listOf("230 V csatlakozóaljzat", "USB töltő", "Konnektor"),
            SearchFilter.Dining to listOf("Bisztrószolgáltatás", "Étkezőkocsi", "Büfékocsi"),
            SearchFilter.Sleeper to listOf("Hálókocsi", "Fekvőhelyes kocsi", "Kusett")
        )
        examples.forEach { (filter, descriptions) ->
            descriptions.forEach { description ->
                assertTrue("$filter: $description", filter.matches(journey(leg(description))))
                assertFalse("Missing second-leg evidence: $filter", filter.matches(journey(leg(description), leg())))
                assertFalse("Negated evidence: $filter", filter.matches(journey(leg("Nincs $description"))))
                assertFalse("Conflicting evidence: $filter", filter.matches(journey(leg(description, "Nincs $description"))))
                assertFalse("Partial section: $filter", filter.matches(journey(leg().copy(
                    services = listOf(service(description).copy(from = "Kistelek", till = b.name))))))
            }
        }
        assertFalse(SearchFilter.SecondClass.matches(journey(leg("1. osztályú kocsi"))))
        assertFalse(SearchFilter.Sleeper.matches(journey(leg("Alvószékes kocsi"))))
        val allFacilities = leg(*examples.values.map { it.first() }.toTypedArray())
        assertEquals(1, filterJourneys(listOf(journey(allFacilities), journey(leg())), examples.keys).size)
    }
}
