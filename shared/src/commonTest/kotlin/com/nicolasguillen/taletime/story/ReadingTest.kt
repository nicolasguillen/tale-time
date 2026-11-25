package com.nicolasguillen.taletime.story

import com.nicolasguillen.taletime.fakes.TINY
import com.nicolasguillen.taletime.fixtures.CONTENT_STORIES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadingTest {
    private val tiny = StoryParser.parse(TINY)

    private fun Reading.ids() = path.map { it.node }

    @Test
    fun readsAPathToAnEnding() {
        val start = Reading.start(tiny)
        assertEquals("p1", start.current.id)
        assertFalse(start.canGoBack)
        val atFork = start.next()
        assertEquals("f1", atFork.current.id)
        val end = atFork.choose(1).next()
        assertEquals(listOf("p1", "f1", "p2", "e2"), end.ids())
        assertEquals(listOf(Step("p1"), Step("f1", 1), Step("p2"), Step("e2")), end.path)
        assertTrue(end.atEnding)
        assertEquals(listOf(Step("f1", 1)), end.choices)
    }

    @Test
    fun movesOnlyTheWayTheNodeAllows() {
        val start = Reading.start(tiny)
        assertFailsWith<IllegalStateException> { start.choose(0) }
        assertFailsWith<IllegalStateException> { start.next().next() }
        assertFailsWith<IllegalStateException> { start.next().choose(2) }
    }

    @Test
    fun backRewindsToTheLastForkWithItsQuestionOpen() {
        val atP2 = Reading.start(tiny).next().choose(1)
        val back = atP2.back()
        assertEquals(listOf(Step("p1"), Step("f1")), back.path)
        // From the fork itself, there's no earlier fork: back to the first page.
        assertEquals(listOf(Step("p1")), back.back().path)
        assertEquals(Reading.start(tiny).path, Reading.start(tiny).back().path)
    }

    @Test
    fun previousPagesBackOneStepAndReopensAForkItCrosses() {
        val atP2 = Reading.start(tiny).next().choose(1)
        val atFork = atP2.previous()
        assertEquals(listOf(Step("p1"), Step("f1")), atFork.path)
        assertEquals(listOf(Step("p1")), atFork.previous().path)
        assertEquals(Reading.start(tiny).path, Reading.start(tiny).previous().path)
        assertEquals("e1", atFork.choose(0).current.id)
    }

    @Test
    fun tryAnotherPathFromAnEnding() {
        val ending = Reading.start(tiny).next().choose(0)
        assertTrue(ending.atEnding)
        val other = ending.back().choose(1)
        assertEquals("p2", other.current.id)
    }

    @Test
    fun resumesASavedPath() {
        val saved =
            Reading
                .start(tiny)
                .next()
                .choose(1)
                .path
        assertEquals(saved, Reading.resume(tiny, saved)?.path)
    }

    @Test
    fun refusesAPathTheStoryNoLongerHas() {
        assertNull(Reading.resume(tiny, emptyList()))
        assertNull(Reading.resume(tiny, listOf(Step("p2"))))
        assertNull(Reading.resume(tiny, listOf(Step("p1"), Step("p2"))))
        assertNull(Reading.resume(tiny, listOf(Step("p1"), Step("f1", 0), Step("p2"))))
        assertNull(Reading.resume(tiny, listOf(Step("p1"), Step("f1"), Step("e1"))))
        assertNull(Reading.resume(tiny, listOf(Step("p1"), Step("f1", 1))))
        assertNull(Reading.resume(tiny, listOf(Step("p1"), Step("gone"))))
    }

    /** Every play-through of every real story ends, and back always lands on a fork or the first page. */
    @Test
    fun everyPathOfEveryRealStoryReachesAnEnding() {
        for ((id, json) in CONTENT_STORIES) {
            val story = StoryParser.parse(json)
            var paths = 0

            fun walk(reading: Reading) {
                assertTrue(reading.path.size < 100, "$id: runaway path")
                when (val node = reading.current) {
                    is Ending -> {
                        paths++
                        val back = reading.back()
                        assertTrue(back.current is Fork || back.path.size == 1, "$id: back from ${node.id}")
                    }

                    is Page -> {
                        walk(reading.next())
                    }

                    is Fork -> {
                        node.options.indices.forEach { walk(reading.choose(it)) }
                    }
                }
            }
            walk(Reading.start(story))
            assertTrue(paths >= story.endings.size, id)
        }
    }
}
