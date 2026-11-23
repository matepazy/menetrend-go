package hu.matepazy.menetrendgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegalTermsTest {
    @Test fun joinsWrappedLinesButKeepsItemsRulesAndParagraphs() {
        val raw = "Title line\n\n=====\n\nSection 1 -- Definitions.\n\n  a. Adapted Material means\n     material derived from it.\n  b. Licensor means\n     the party.\n\nA paragraph that\nwraps here.\r\n"
        assertEquals("Title line\n\nSection 1 -- Definitions.\n\na. Adapted Material means material derived from it.\nb. Licensor means the party.\n\nA paragraph that wraps here.",
            reflowLegalText(raw))
    }

    @Test fun bundledLicenseReflowsWithoutOrphanedContinuations() {
        val text = reflowLegalText(File("../LICENSE").readText())
        assertEquals("Attribution-NonCommercial-ShareAlike 4.0 International", text.lines().first())
        assertTrue("Section 8 -- Interpretation." in text)
        // Every line is a paragraph start, list item or heading; a wrapped continuation would start lowercase.
        val item = Regex("""^\(?[a-z0-9]{1,4}[.)]\s""")
        assertEquals(emptyList<String>(), text.lines().filter { it.isNotEmpty() && it.first().isLowerCase() && !item.containsMatchIn(it) })
    }
}
