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
            }
        }
        val reported = File("src/test/resources/vehicle-models-2026-10-24.txt").readLines().filter { it.isNotBlank() }
        reported.forEach { assertTrue("Live model not covered: $it", normalizedVehicleModel(it) in aliases) }
    }
}
