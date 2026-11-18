package hu.matepazy.menetrendgo

import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidIndicatorTest {
    private val gap = 4f
    private val slot = LiquidIndicator.slotWidth(412f, 4, gap) // 100px slots

    @Test fun slotsShareTheRowEvenly() {
        assertEquals(100f, slot, 0f)
        assertEquals(312f, LiquidIndicator.slotLeft(3, slot, gap), 0f)
    }

    @Test fun releaseSnapsToTheNearestSlotAndClamps() {
        assertEquals(0, LiquidIndicator.nearest(10f, slot, gap, 4))
        assertEquals(1, LiquidIndicator.nearest(160f, slot, gap, 4))
        assertEquals(2, LiquidIndicator.nearest(240f, slot, gap, 4))
        assertEquals(3, LiquidIndicator.nearest(900f, slot, gap, 4))
        assertEquals(0, LiquidIndicator.nearest(-50f, slot, gap, 4))
    }

    @Test fun stretchedPillThinsAndCompressedPillSwells() {
        assertEquals(1f, LiquidIndicator.heightFactor(100f, slot), 1e-4f)
        assertEquals(.92f, LiquidIndicator.heightFactor(150f, slot), 1e-4f)
        assertEquals(.84f, LiquidIndicator.heightFactor(400f, slot), 1e-4f)
        assertEquals(1.06f, LiquidIndicator.heightFactor(40f, slot), 1e-4f)
    }

    @Test fun dragPastTheEndsResistsWithinTheLimit() {
        assertEquals(150f, LiquidIndicator.rubberBand(150f, 50f, 362f, 30f), 0f)
        val pulled = LiquidIndicator.rubberBand(500f, 50f, 362f, 30f)
        assert(pulled > 362f && pulled < 392f)
        val pushed = LiquidIndicator.rubberBand(-400f, 50f, 362f, 30f)
        assert(pushed < 50f && pushed > 20f)
        // Resistance grows: twice the pull moves the pill less than twice as far.
        assert(LiquidIndicator.rubberBand(462f, 50f, 362f, 30f) - 362f > (pulled - 362f) / 2)
    }

    @Test fun coverageFollowsThePill() {
        assertEquals(1f, LiquidIndicator.coverage(104f, slot, 104f, 204f), 1e-4f)
        assertEquals(.5f, LiquidIndicator.coverage(104f, slot, 154f, 254f), 1e-4f)
        assertEquals(0f, LiquidIndicator.coverage(0f, slot, 208f, 308f), 1e-4f)
        // A stretched pill fully colours every slot it spans.
        assertEquals(1f, LiquidIndicator.coverage(104f, slot, 0f, 308f), 1e-4f)
    }
}
