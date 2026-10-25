package hu.matepazy.menetrendgo

import com.squareup.moshi.Moshi
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class VehicleModelTest {
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

    @Test fun bundledCatalogHasUnambiguousAliasesAndCompleteOfflineAttribution() {
        val assets = File("src/main/assets/vehicle_models")
        @Suppress("UNCHECKED_CAST")
        val rows = Moshi.Builder().build().adapter(List::class.java)
            .fromJson(File(assets, "catalog.json").readText()) as List<Map<String, Any>>
        assertTrue("Full live-feed catalog expected", rows.size >= 76)
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
            photos.forEach { p ->
                assertTrue("$id photo bundled", File(assets, p.getValue("asset")).length() > 1000)
                listOf("caption", "author", "source", "license", "licenseUrl").forEach { key ->
                    assertTrue("$id photo missing $key", !p[key].isNullOrBlank())
                }
                assertTrue(p.getValue("source").startsWith("https://commons.wikimedia.org/"))
                assertTrue(p.getValue("licenseUrl").startsWith("https://"))
                assertTrue("$id must use a free photo license", p.getValue("license")
                    .matches(Regex("CC BY(?:-SA)? [234]\\.0|Public domain|CC0")))
            }
        }
        val reported = listOf("vehicle-models-2026-10-24.txt", "local-bus-models-2026-10-25.txt")
            .flatMap { File("src/test/resources/$it").readLines() }.filter { it.isNotBlank() }
        reported.forEach { assertTrue("Live model not covered: $it", normalizedVehicleModel(it) in aliases) }
        val local = File("src/test/resources/local-bus-models-2026-10-25.txt").readLines().filter { it.isNotBlank() }
        local.forEach { name ->
            val row = rows.first { row -> (row["aliases"] as List<*>).any { normalizedVehicleModel(it as String) == normalizedVehicleModel(name) } }
            assertTrue("Local model needs a freely licensed photo: $name", (row["photos"] as List<*>).isNotEmpty())
        }
    }
}
