package hu.matepazy.menetrendgo

import com.squareup.moshi.Moshi
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class VehicleModelTest {
    @Test fun busSeatingKeepsItsSourceAndConfigurationScope() {
        val json = Moshi.Builder().build()
        @Suppress("UNCHECKED_CAST")
        val catalog = json.adapter(List::class.java)
            .fromJson(File("src/main/assets/vehicle_models/catalog.json").readText()) as List<Map<String, Any>>
        @Suppress("UNCHECKED_CAST")
        val research = json.adapter(Map::class.java)
            .fromJson(File("../research/fleet/bus-seating.json").readText()) as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val records = research["records"] as List<Map<String, Any>>
        val buses = catalog.filter { row ->
            (row["specifications"] as List<*>).any { spec ->
                val value = (spec as Map<*, *>)["value"].toString()
                value.contains("autóbusz", ignoreCase = true) || value.contains("Minibusz", ignoreCase = true)
            }
        }
        assertEquals(buses.map { it["id"] }.toSet(), records.map { it["id"] }.toSet())
        assertEquals("Each bus needs one research record", buses.size, records.size)
        for (bus in buses) {
            val id = bus["id"]
            val record = records.single { it["id"] == id }
            val seats = (bus["specifications"] as List<*>).map { it as Map<*, *> }
                .filter { it["label"] == "Utasülőhelyek" }
            assertEquals("$id needs exactly one seating field", 1, seats.size)
            assertEquals(record["value"], seats.single()["value"])
            assertTrue("$id needs an explicit variant/source scope", record["scope"].toString().isNotBlank())
            val counts = record["seatedPassengers"] as List<*>
            if (record["status"] == "verified") {
                assertTrue("$id needs seated passenger counts", counts.isNotEmpty())
                assertTrue(counts.all { (it as Number).toInt() in 1..90 })
                assertTrue("$id needs a seating citation in the app", (bus["sources"] as List<*>)
                    .any { (it as Map<*, *>)["url"] == record["url"] })
                assertTrue("$id needs visible configuration qualification", record["value"].toString().contains("("))
            } else {
                assertEquals("unverified", record["status"])
                assertTrue("Unknown seating must not acquire an estimate", counts.isEmpty())
                assertFalse(record["value"].toString().any(Char::isDigit))
            }
        }
        fun count(id: String) = (records.single { it["id"] == id }["seatedPassengers"] as List<*>)
            .map { (it as Number).toInt() }
        // Distinguish the driver, a folding seat, and the two Citaro K generations.
        assertEquals(listOf(26, 1), count("en95"))
        assertEquals(listOf(26), count("citarok"))
        assertEquals(listOf(27), count("citaroc2k"))
        assertEquals(listOf(32), count("bydb12"))
    }

    private fun profile(id: String, vararg aliases: String) = VehicleModelProfile(
        id, id, aliases.toList(), "", emptyList(), emptyList(), emptyList())

    @Test fun normalizesProviderTypographyWithoutGuessingVariants() {
        val profiles = listOf(profile("short", "Credo Econell 12"),
            profile("next", "Credo Econell 12 Next", "Credobus Econell 12 Next"),
            profile("long", "Credobus Econell 18 Next"), profile("man", "MAN Lion’s City A21"))
        assertEquals("next", findVehicleModel(profiles, " CREDOBUS Econell 12 NEXT ")?.id)
        assertEquals("man", findVehicleModel(profiles, "MAN Lion's City A21")?.id)
        assertEquals("long", findVehicleModel(profiles, "Credobus Econell 18 Next")?.id)
        assertEquals("short", findVehicleModel(profiles, "Credo Econell 12")?.id)
        assertNull(findVehicleModel(profiles, "Credo Econell"))
        assertNull(findVehicleModel(profiles, "Credobus Econell 13 Next"))
        assertNull(findVehicleModel(profiles, ""))
    }

    @Test fun localBusPhotoPrefersExactAgencyAndKeepsRailGallery() {
        fun photo(asset: String, vararg agencies: String) = ModelPhoto(asset, "", "", "", "", "", agencies.toList())
        val general = photo("general.jpg")
        val budapest = photo("budapest.jpg", "BKK")
        val fehervar = photo("fehervar.jpg", "MÁV Személyszállítási Zrt. - Székesfehérvár")
        val p = profile("test", "Test").copy(photos = listOf(general, budapest, fehervar))
        assertEquals(listOf(fehervar), vehicleModelForContext(p, "BUS", "MÁV Személyszállítási Zrt. - Székesfehérvár").photos)
        assertEquals(listOf(budapest), vehicleModelForContext(p, "BUS", "BKK").photos)
        assertEquals(listOf(budapest), vehicleModelForContext(p, "BUS", "Unknown").photos)
        assertEquals(p.photos, vehicleModelForContext(p, "RAIL", "BKK").photos)
        assertTrue(vehicleModelForContext(p.copy(photos = emptyList()), "BUS", "BKK").photos.isEmpty())
    }

    @Test fun explicitOperatorCanResolveAnOtherwiseAmbiguousDriveVariant() {
        val family = profile("urbino12", "Solaris Urbino 12").copy(agencyProfiles = mapOf("PAKS" to "electric"))
        val electric = profile("electric")
        val profiles = listOf(family, electric)
        assertEquals(electric, findVehicleModel(profiles, "Solaris Urbino 12", "PAKS"))
        assertEquals(family, findVehicleModel(profiles, "Solaris Urbino 12", "Unknown"))
        assertNull(findVehicleModel(profiles, "Solaris Urbino 15", "PAKS"))
    }

    @Test fun catalogHasUnambiguousAliasesAndCompleteOfflineAttributionForWebPhotos() {
        val assets = File("src/main/assets/vehicle_models")
        @Suppress("UNCHECKED_CAST")
        val rows = Moshi.Builder().build().adapter(List::class.java)
            .fromJson(File(assets, "catalog.json").readText()) as List<Map<String, Any>>
        assertTrue("Expanded current fleet catalog expected", rows.size >= 120)
        @Suppress("UNCHECKED_CAST")
        val backlog = Moshi.Builder().build().adapter(List::class.java)
            .fromJson(File("../research/fleet/backlog.json").readText()) as List<Map<String, Any>>
        val researchAliases = backlog.flatMap { it["aliases"] as List<*> }
            .map { normalizedVehicleModel(it as String) }.toSet()
        val aliases = mutableSetOf<String>()
        for (row in rows) {
            val id = row["id"] as String
            assertTrue("$id has a description", (row["description"] as String).isNotBlank())
            assertTrue("$id has fun facts", (row["facts"] as List<*>).isNotEmpty())
            (row["aliases"] as List<*>).forEach { alias ->
                assertTrue("Ambiguous alias $alias", aliases.add(normalizedVehicleModel(alias as String)))
            }
            @Suppress("UNCHECKED_CAST")
            val photos = row["photos"] as List<Map<String, String>>
            assertEquals("$id must have exactly one curated photo", 1, photos.size)
            @Suppress("UNCHECKED_CAST")
            val specifications = row["specifications"] as List<Map<String, String>>
            assertTrue("$id needs a basic fact sheet", specifications.size >= 3)
            specifications.forEach { spec ->
                assertFalse("$id has a blank fact label", spec["label"].isNullOrBlank())
                assertFalse("$id has a blank fact value", spec["value"].isNullOrBlank())
            }
            photos.forEach { p ->
                assertTrue("$id photo must use a trusted HTTPS Commons image URL", isFleetPhotoUrl(p.getValue("url")))
                assertFalse("Photos must no longer be bundled", p.containsKey("asset"))
                listOf("caption", "author", "source", "license", "licenseUrl").forEach { key ->
                    assertTrue("$id photo missing $key", !p[key].isNullOrBlank())
                }
                assertTrue(p.getValue("source").startsWith("https://commons.wikimedia.org/"))
                assertTrue(p.getValue("licenseUrl").startsWith("https://"))
                assertTrue("$id must use a free photo license", p.getValue("license")
                    .matches(Regex("CC BY(?:-SA)? [234]\\.0|Public domain|CC0")))
            }
        }
        listOf("Luganszki Mozdonygyár (SZU)-SZERGEJ", "GANZ-MÁVAG-PAPAGÁJ", "Setra S417UL",
            "Setra S419UL", "Siemens-VECTRON", "Alstom-ASTRIDE", "MAN 18C", "HÉV MX", "HÉV MXA",
            "Ikarus C56", "Ikarus C80", "Bbdpmz", "Bybdtee")
            .forEach { assertTrue("Researched fleet model missing: $it", normalizedVehicleModel(it) in aliases) }
        val reported = listOf("vehicle-models-2026-10-24.txt", "local-bus-models-2026-10-25.txt")
            .flatMap { File("src/test/resources/$it").readLines() }.filter { it.isNotBlank() }
        reported.forEach { assertTrue("Reported model needs a profile or explicit research backlog entry: $it",
            normalizedVehicleModel(it) in aliases || normalizedVehicleModel(it) in researchAliases) }
        val local = File("src/test/resources/local-bus-models-2026-10-25.txt").readLines().filter { it.isNotBlank() }
        local.forEach { name ->
            val row = rows.first { row -> (row["aliases"] as List<*>).any { normalizedVehicleModel(it as String) == normalizedVehicleModel(name) } }
            assertTrue("Local model needs a freely licensed photo: $name", (row["photos"] as List<*>).isNotEmpty())
        }
    }
}
