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

    @Test
    fun anAgentCanGoStraightToAnyPage() {
        assertEquals(listOf(Step("p1"), Step("f1", 1), Step("p2"), Step("e2")), Reading.to(tiny, "e2")?.path)
        assertEquals(listOf(Step("p1"), Step("f1", 0), Step("e1")), Reading.to(tiny, "e1")?.path)
        assertEquals(listOf(Step("p1"), Step("f1")), Reading.to(tiny, "f1")?.path)
        assertNull(Reading.to(tiny, "gone"))
    }

    @Test
    fun towardAnotherEndingRewindsToTheLatestForkThatCanLeadThere() {
        val fox = StoryParser.parse(CONTENT_STORIES.getValue("fox-and-the-lantern"))
        // Hedge, bridge, ask for help, lead the parade: the Sky Parade.
        val parade =
            Reading
                .start(fox)
                .next()
                .next()
                .choose(0)
                .next()
                .next()
                .choose(1)
                .next()
                .next()
                .choose(0)
                .choose(0)
        assertEquals("e2-sky-parade", parade.current.id)
        // Goodnight, Glim is the other card at the last fork.
        assertEquals(parade.path.take(9) + Step("p10a-friends"), parade.toward("e3-goodnight-glim")?.path)
        // The Croakiest Song is the other way at the willow, two forks back.
        assertEquals("p09-willow", parade.toward("e4-frog-chorus")?.current?.id)
        // The Big Splash is on the stones, the other way at the brook.
        assertEquals("p06-brook", parade.toward("e1-big-splash")?.current?.id)
        // Not another ending: nowhere to go.
        assertNull(parade.toward("e2-sky-parade"))
        assertNull(parade.toward("p01-meadow"))
        assertNull(parade.toward("gone"))
    }

    /**
     * From every ending of every real story, a rewind toward any other ending goes back only to a fork this path read,
     * and from there the cards marked with the way ([StoryGraph.wayTo]) always lead to it.
     */
    @Test
    fun theWayToEveryOtherEndingOnlyGoesBackToPagesRead() {
        for ((id, json) in CONTENT_STORIES) {
            val story = StoryParser.parse(json)

            fun follow(
                from: Reading,
                target: String,
            ): Reading {
                var at = from
                while (at.current !is Ending) {
                    at =
                        when (val node = at.current) {
                            is Page -> at.next()
                            is Fork -> at.choose(StoryGraph.wayTo(story, node, target) ?: 0)
                            is Ending -> at
                        }
                }
                return at
            }

            fun walk(reading: Reading) {
                when (val node = reading.current) {
                    is Ending -> {
                        for (other in story.endings.filter { it.id != node.id }) {
                            val back = reading.toward(other.id)
                            assertTrue(back != null, "$id: no way from ${node.id} to ${other.id}")
                            assertTrue(back.current is Fork, "$id: ${node.id} → ${other.id}")
                            assertEquals(
                                reading.path.map { it.node }.take(back.path.size),
                                back.path.map { it.node },
                                "$id: ${node.id} → ${other.id}",
                            )
                            assertEquals(other.id, follow(back, other.id).current.id, "$id: ${node.id} → ${other.id}")
                        }
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
        }
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
