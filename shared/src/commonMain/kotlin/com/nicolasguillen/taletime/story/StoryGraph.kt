package com.nicolasguillen.taletime.story

/**
 * The graph rules the reader depends on (the pipeline's `story check` has the full set): the start exists, every
 * link points to a node, there are no cycles, every node is reachable, and a fork's two options go different ways.
 * The app refuses a story with problems instead of trapping a child on a page with no way on.
 */
object StoryGraph {
    fun successors(node: Node): List<String> =
        when (node) {
            is Page -> listOf(node.next)
            is Fork -> node.options.map { it.target }
            is Ending -> emptyList()
        }

    fun problems(story: Story): List<String> {
        val nodes = story.nodes
        if (story.start !in nodes) return listOf("The start node '${story.start}' doesn't exist")
        val problems = mutableListOf<String>()
        for (node in nodes.values) {
            for (target in successors(node)) {
                if (target !in nodes) problems += "'${node.id}' points to '$target', which doesn't exist"
            }
            if (node is Fork && node.options[0].target == node.options[1].target) {
                problems += "Both options of '${node.id}' lead to the same node"
            }
        }
        if (problems.isNotEmpty()) return problems
        findCycle(story)?.let { problems += "Cycle: ${it.joinToString(" -> ")}" }
        val unreachable = nodes.keys - reachable(story)
        unreachable.forEach { problems += "No path from the start reaches '$it'" }
        return problems
    }

    /** Every node a reader can get to from [from] (itself included), the start by default. */
    fun reachable(
        story: Story,
        from: String = story.start,
    ): Set<String> {
        val seen = mutableSetOf<String>()
        val stack = ArrayDeque(listOf(from))
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            val node = story.nodes[id] ?: continue
            if (seen.add(id)) stack.addAll(successors(node))
        }
        return seen
    }

    /** A reader at [from] can still get to [to]: it's [from] itself or lies on one of its ways on. */
    fun leadsTo(
        story: Story,
        from: String,
        to: String,
    ): Boolean = to in reachable(story, from)

    /**
     * At [fork], the option that can still lead to [to] when the other one can't (0 = A, 1 = B), or null when both
     * can or neither can: the card that shows the way to an ending a reader is looking for.
     */
    fun wayTo(
        story: Story,
        fork: Fork,
        to: String,
    ): Int? {
        val leads = fork.options.map { leadsTo(story, it.target, to) }
        return leads.indexOf(true).takeIf { leads.count { it } == 1 }
    }

    private fun findCycle(story: Story): List<String>? {
        val done = mutableSetOf<String>()
        val onPath = mutableListOf<String>()

        fun visit(id: String): List<String>? {
            if (id in done) return null
            val at = onPath.indexOf(id)
            if (at >= 0) return onPath.subList(at, onPath.size) + id
            onPath += id
            for (next in successors(story.node(id))) visit(next)?.let { return it }
            onPath.removeAt(onPath.lastIndex)
            done += id
            return null
        }
        return visit(story.start)
    }
}
