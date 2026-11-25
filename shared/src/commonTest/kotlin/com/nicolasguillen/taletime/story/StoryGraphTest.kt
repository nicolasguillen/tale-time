package com.nicolasguillen.taletime.story

import com.nicolasguillen.taletime.fakes.TINY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoryGraphTest {
    private val tiny = StoryParser.parse(TINY)

    private fun Story.withNode(node: Node) = copy(nodes = nodes + (node.id to node))

    @Test
    fun theFixtureHasNoProblems() {
        assertEquals(emptyList(), StoryGraph.problems(tiny))
        assertEquals(tiny.nodes.keys, StoryGraph.reachable(tiny))
    }

    @Test
    fun findsADanglingLink() {
        val broken = tiny.withNode((tiny.node("p2") as Page).copy(next = "nowhere"))
        assertEquals(listOf("'p2' points to 'nowhere', which doesn't exist"), StoryGraph.problems(broken))
    }

    @Test
    fun findsACycle() {
        val broken = tiny.withNode((tiny.node("p2") as Page).copy(next = "p1"))
        val problems = StoryGraph.problems(broken)
        assertTrue(problems.any { it.startsWith("Cycle: p1 -> f1 -> p2 -> p1") }, problems.toString())
    }

    @Test
    fun findsAnUnreachableNode() {
        val broken = tiny.withNode(Page("lost", Text(mapOf("en" to "Nobody comes here.")), "lost", "e1"))
        assertEquals(listOf("No path from the start reaches 'lost'"), StoryGraph.problems(broken))
    }

    @Test
    fun findsAForkWhoseOptionsGoTheSameWay() {
        val fork = tiny.node("f1") as Fork
        val broken = tiny.withNode(fork.copy(options = listOf(fork.options[0], fork.options[1].copy(target = "e1"))))
        assertTrue(StoryGraph.problems(broken).contains("Both options of 'f1' lead to the same node"))
    }

    @Test
    fun findsAMissingStart() {
        assertEquals(listOf("The start node 'nope' doesn't exist"), StoryGraph.problems(tiny.copy(start = "nope")))
    }
}
