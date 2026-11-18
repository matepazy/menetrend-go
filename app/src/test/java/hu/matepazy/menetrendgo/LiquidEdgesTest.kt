package hu.matepazy.menetrendgo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidEdgesTest {
    private fun run(edges: LiquidEdges, goal: Pair<Float, Float>, frames: Int, each: (LiquidEdges) -> Unit = {}) =
        repeat(frames) { edges.step(goal.first, goal.second, 1 / 60f); each(edges) }

    @Test fun tapMoveLeadsWithTheFrontEdgeThenSettles() {
        val edges = LiquidEdges().apply { snap(0f, 100f) }
        var maxLength = 0f
        run(edges, 300f to 400f, 6) { maxLength = maxOf(maxLength, it.end - it.start) }
        assertTrue("front edge runs ahead", edges.end - 400f > edges.start - 300f - 100f)
        assertTrue("the pill stretches in transit", maxLength > 130f)
        run(edges, 300f to 400f, 120)
        assertTrue(edges.settled(300f, 400f))
        assertEquals(300f, edges.start, .5f)
        assertEquals(400f, edges.end, .5f)
    }

    @Test fun movingBackwardsSwapsWhichEdgeLeads() {
        val edges = LiquidEdges().apply { snap(300f, 400f) }
        run(edges, 0f to 100f, 6)
        assertTrue("start edge leads leftwards", 300f - edges.start > 400f - edges.end)
        assertTrue(!edges.forward)
    }

    @Test fun followingAMovingGoalStretchesWithSpeed() {
        fun stretchAt(speed: Float): Float {
            val edges = LiquidEdges().apply { snap(0f, 100f) }
            var x = 0f
            repeat(20) { x += speed / 60f; edges.step(x, x + 100f, 1 / 60f) }
            return edges.end - edges.start
        }
        assertTrue(stretchAt(1200f) > stretchAt(300f))
        assertTrue(stretchAt(300f) > 100f)
    }

    @Test fun largeFrameGapsStayStable() {
        val edges = LiquidEdges().apply { snap(0f, 100f) }
        run(edges, 900f to 1000f, 40) { edges.step(900f, 1000f, .05f) }
        assertTrue(edges.settled(900f, 1000f))
    }
}
