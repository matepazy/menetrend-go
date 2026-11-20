package hu.matepazy.menetrendgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavLabelFitTest {
    @Test fun labelsThatFitKeepFullSize() {
        assertEquals(1f, NavLabelFit.scale(80f, listOf(40f, 72f)), 0f)
    }

    @Test fun widestLabelSetsOneSharedScale() {
        val scale = NavLabelFit.scale(57f, listOf(50f, 36f, 52f, 56f, 66f))
        assertEquals(57f / 66f, scale, 1e-4f)
        assertTrue(NavLabelFit.fits(12f, scale)) // ~10.4sp
    }

    @Test fun shrinkingPastTheFloorFallsBack() {
        // ~72dp "Beállítások" in a ~57dp slot would need ~9.5sp.
        assertFalse(NavLabelFit.fits(12f, NavLabelFit.scale(57f, listOf(72f))))
    }

    @Test fun tooNarrowFallsBackToIcons() {
        // Large font: 12sp label would need to shrink below the 10sp floor.
        assertFalse(NavLabelFit.fits(12f, NavLabelFit.scale(57f, listOf(95f))))
        assertTrue(NavLabelFit.fits(12f, 10f / 12f))
    }

    @Test fun unmeasuredInputsDoNotShrink() {
        assertEquals(1f, NavLabelFit.scale(0f, listOf(72f)), 0f)
        assertEquals(1f, NavLabelFit.scale(57f, emptyList()), 0f)
    }
}
