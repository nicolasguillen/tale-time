package com.nicolasguillen.taletime.feature.reader

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The fold of docs/page-turn.md §3, on a 360 × 760 dp phone. */
class PageFoldTest {
    private val w = 360f
    private val h = 760f

    private fun assertNear(
        expected: Offset,
        actual: Offset,
        tolerance: Float = 0.1f,
    ) = assertTrue(
        abs(expected.x - actual.x) <= tolerance && abs(expected.y - actual.y) <= tolerance,
        "expected $expected, got $actual",
    )

    private fun assertPolygon(
        expected: List<Offset>,
        actual: List<Offset>,
    ) {
        assertEquals(expected.size, actual.size, "corners of $actual")
        expected.zip(actual).forEach { (e, a) -> assertNear(e, a) }
    }

    @Test
    fun theDogEarFoldsTheCornerOverItsCrease() {
        val corner = PageFold.dogEar(w, h, alongBottom = 118f, upRight = 96f)
        assertNear(Offset(266.0f, 644.5f), corner)

        val fold = PageFold(w, h, corner)
        assertTrue(fold.folded)
        assertPolygon(listOf(Offset(0f, 0f), Offset(360f, 0f), Offset(360f, 664f), Offset(242f, 760f), Offset(0f, 760f)), fold.keep)
        assertPolygon(listOf(Offset(360f, 664f), Offset(266f, 644.5f), Offset(242f, 760f)), fold.flap)
        // The crease runs between the two edges, and the flap's arrow sits inside it.
        val (a, b) = fold.crease()!!
        assertEquals(setOf(Offset(360f, 664f), Offset(242f, 760f)).map { it.round() }.toSet(), setOf(a.round(), b.round()))
        val arrow = fold.flapCenter()!!
        assertTrue(fold.side(arrow) < 0f, "on the page's side of the crease, where the flap lies")
    }

    @Test
    fun aPageTurnedAllTheWayKeepsNothingAndItsFlapIsOffToTheLeft() {
        val fold = PageFold(w, h, Offset(-w, h))
        assertTrue(fold.keep.isEmpty())
        assertTrue(fold.flap.isNotEmpty())
        assertTrue(fold.flap.all { it.x <= 0.001f }, "flap at ${fold.flap}")
        assertEquals(0f, fold.shading, "the shade is gone at the end of a turn")
    }

    @Test
    fun aCornerAtHomeIsNoFold() {
        val fold = PageFold(w, h, Offset(w, h))
        assertFalse(fold.folded)
        assertTrue(fold.cut.isEmpty())
        assertTrue(fold.flap.isEmpty())
        assertEquals(4, fold.keep.size)
    }

    @Test
    fun theFlapIsTheCutAwayPartMirroredAcrossTheCrease() {
        val fold = PageFold(w, h, Offset(120f, 500f))
        fold.cut.zip(fold.flap).forEach { (c, f) ->
            assertNear(c, fold.reflect(f), 0.01f)
            assertEquals(fold.side(c), -fold.side(f), 0.01f)
        }
        // The matrix the flap is drawn with is the same reflection.
        val v = fold.mirror()
        val p = Offset(200f, 700f)
        assertNear(fold.reflect(p), Offset(v[0] * p.x + v[4] * p.y + v[12], v[1] * p.x + v[5] * p.y + v[13]), 0.01f)
    }

    @Test
    fun aDraggedCornerStaysBoundAtTheSpine() {
        val diagonal = sqrt(w * w + h * h)
        for (r in listOf(10f, 200f, 500f, 900f, 2_000f)) {
            for (step in 0 until 24) {
                val a = step * 15.0 * kotlin.math.PI / 180.0
                val pulled = Offset(w + r * cos(a).toFloat(), h + r * sin(a).toFloat())
                val c = PageFold.bound(pulled, w, h)
                assertTrue((c - Offset(0f, h)).getDistance() <= w + 0.01f, "$pulled → $c")
                assertTrue(c.getDistance() <= diagonal + 0.01f, "$pulled → $c")
                assertTrue(c.x <= w && c.y <= h, "$pulled → $c")
            }
        }
        val inside = Offset(200f, 600f)
        assertEquals(inside, PageFold.bound(inside, w, h))
    }

    @Test
    fun theNextPageIsInShadeWhileTheCornerIsLowAndLighterAsItLifts() {
        val small = PageFold(w, h, Offset(300f, 700f))
        assertEquals(0.2f * (1f - small.len / 540f), small.tint, 1e-4f)
        assertEquals(0f, PageFold(w, h, Offset(w - 540f, h)).tint, 1e-4f)
        assertEquals(0f, PageFold(w, h, Offset(-w, h)).tint, 1e-4f)
        assertTrue(PageFold(w, h, Offset(300f, 700f)).tint > PageFold(w, h, Offset(100f, 500f)).tint)
    }

    @Test
    fun aTurnArcsFromTheCornerToTheFarLeft() {
        val from = PageFold.dogEar(w, h, 118f, 96f)
        val path = TurnPath(from, w, h)
        assertNear(from, path.at(0f))
        assertNear(Offset(-w, h), path.at(1f))
        assertTrue(path.at(0.5f).y < from.y, "the page lifts in an arc")
        // About 0.7 s from the folded corner.
        assertTrue(path.millis in 650L..720L, "${path.millis} ms")
        assertEquals(750L, TurnPath(Offset(w, h), w, h).millis)
    }

    @Test
    fun theEasingsStartAtZeroAndEndAtOne() {
        for (ease in listOf(Ease::inOutSine, Ease::outCubic, { t: Float -> Ease.outBack(t) })) {
            assertEquals(0f, ease(0f), 1e-5f)
            assertEquals(1f, ease(1f), 1e-5f)
        }
        assertTrue((0..100).any { Ease.outBack(it / 100f) > 1f }, "ease-out-back overshoots")
    }

    @Test
    fun aFlickIsTheSpeedOfTheLastMoves() {
        val flick = Flick()
        assertEquals(0f, flick.velocity())
        // Slowly at first, then fast to the left: only the last 80 ms count.
        flick.add(0, 300f)
        flick.add(200, 290f)
        flick.add(240, 260f)
        flick.add(280, 200f)
        assertEquals((200f - 290f) / 80f, flick.velocity(), 1e-4f)
        // Batched moves at the same moment don't divide by zero.
        val still = Flick().apply { add(10, 100f) }
        still.add(10, 50f)
        assertEquals(0f, still.velocity())
    }

    private fun Offset.round() = Offset(kotlin.math.round(x), kotlin.math.round(y))
}
