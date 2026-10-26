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

    @Test fun providerConfirmedClimateDoesNotRequireAMissingDisplayNotice() {
        val result = journey(leg("A vonat 2. osztályú kocsikkal közlekedik"))
        assertFalse(SearchFilter.AirConditioning.matches(result))
        val climate = result.copy(providerFilters = setOf(SearchFilter.AirConditioning))
        assertTrue(SearchFilter.AirConditioning.matches(climate))
        assertFalse(SearchFilter.FirstClass.matches(climate))
        assertEquals(listOf(climate), filterJourneys(listOf(result, climate), setOf(SearchFilter.AirConditioning)))
        assertFalse(SearchFilter.AirConditioning.matches(journey(leg().copy(mode = "WALK"))
            .copy(providerFilters = setOf(SearchFilter.AirConditioning))))
    }

    @Test fun premiumClassRequiresPremiumEvidenceOnEveryTransitLeg() {
        assertTrue(SearchFilter.PremiumClass.matches(journey(leg("Prémium osztály"))))
        assertTrue(SearchFilter.PremiumClass.matches(journey(leg("1+ osztály helyjeggyel"))))
        assertFalse(SearchFilter.PremiumClass.matches(journey(leg("1. osztályú kocsi"))))
        assertFalse(SearchFilter.PremiumClass.matches(journey(leg("Prémium osztály"), leg())))
        assertFalse(SearchFilter.PremiumClass.matches(journey(leg("Nincs prémium osztály"))))
    }

    @Test fun climateQueryUsesOfficialSearchCodeAndDefaultQueryHasNoConditions() {
        val time = java.time.LocalDateTime.of(2026, 10, 27, 8, 0)
        val filters = setOf(SearchFilter.AirConditioning, SearchFilter.FirstClass)
        val query = MavQueries.plan(a, b, time, false, filters)
        assertTrue(query.contains("searchParameters:[\"ESZKOZ_KERESESI_FELTETEL#10907842\"]"))
        assertTrue(MavQueries.plan(a, b, time, true, filters).contains("searchParameters:[\"ESZKOZ_KERESESI_FELTETEL#10907842\"]"))
        assertTrue(MavQueries.plan(a, b, time, false).contains("searchParameters:[]"))
    }

    @Test fun widerServiceSectionCoversSubjourneyButPartialRestrictionStillRejectsIt() {
        val anchors = listOf(
            service("Országos vonat").copy(from = a.name, till = b.name, fromIndex = 3, tillIndex = 8))
        val wholeTrain = service("Országbérlettel igénybe vehető")
            .copy(from = "Korábbi megálló", till = "Későbbi megálló", fromIndex = 0, tillIndex = 10)
        val selectedLeg = leg().copy(services = anchors + wholeTrain)
        assertTrue(SearchFilter.CountryPass.matches(journey(selectedLeg)))
        val restricted = service("Országbérlet nem érvényes")
            .copy(from = "Köztes megálló", till = b.name, fromIndex = 6, tillIndex = 8)
        assertFalse(SearchFilter.CountryPass.matches(journey(selectedLeg.copy(services = selectedLeg.services + restricted))))
        val outside = restricted.copy(fromIndex = 8, tillIndex = 10, from = b.name, till = "Későbbi megálló")
        assertTrue(SearchFilter.CountryPass.matches(journey(selectedLeg.copy(services = selectedLeg.services + outside))))
    }
}
