package com.nicolasguillen.taletime.story

/**
 * One page of a play-through: the node, and for a fork that's been answered, which option was taken (0 = A, 1 = B).
 */
data class Step(
    val node: String,
    val choice: Int? = null,
)

/**
 * A play-through in progress: the path from the start to the current page. Immutable: every move returns the next
 * [Reading]. The reader saves [path] so a child can come back to the same page later.
 */
class Reading private constructor(
    val story: Story,
    val path: List<Step>,
) {
    val current: Node get() = story.node(path.last().node)

    val atEnding: Boolean get() = current is Ending

    /** There's somewhere for "Try another path" to go back to: an earlier fork, or the first page. */
    val canGoBack: Boolean get() = path.size > 1

    /** Turns a plain page. */
    fun next(): Reading {
        val page = current as? Page ?: error("'${current.id}' isn't a plain page")
        return Reading(story, path + Step(page.next))
    }

    /** Takes option [index] (0 = A, 1 = B) at the current fork. */
    fun choose(index: Int): Reading {
        val fork = current as? Fork ?: error("'${current.id}' isn't a fork")
        val option = fork.options.getOrNull(index) ?: error("'${fork.id}' has no option $index")
        return Reading(story, path.dropLast(1) + Step(fork.id, index) + Step(option.target))
    }

    /**
     * Rewinds to the last fork before the current page, with its question open again ("Try another path" on an
     * ending). Before the first fork it goes back to the first page. Like [toward], only to a page this path has read.
     */
    fun back(): Reading {
        if (!canGoBack) return this
        val fork = path.dropLast(1).indexOfLast { story.node(it.node) is Fork }
        val keep = if (fork >= 0) fork else 0
        return Reading(story, path.take(keep) + Step(path[keep].node))
    }

    /**
     * Rewinds toward [endingId], an ending this path didn't reach: to the latest fork on the path whose other option
     * can still lead there, with its question open again. There's always one, since every ending can be reached from
     * the start, so the rewind only ever goes back to a page this path has read. Null when [endingId] isn't an ending
     * of the story or is the one this path reached.
     */
    fun toward(endingId: String): Reading? {
        if (story.nodes[endingId] !is Ending || current.id == endingId) return null
        for (i in path.indices.reversed()) {
            val fork = story.node(path[i].node) as? Fork ?: continue
            val taken = path[i].choice ?: continue
            val other = fork.options[1 - taken].target
            if (StoryGraph.leadsTo(story, other, endingId)) return Reading(story, path.take(i) + Step(fork.id))
        }
        return null
    }

    /** The forks answered so far, in order: the choices that make this path. */
    val choices: List<Step> get() = path.filter { it.choice != null }

    companion object {
        fun start(story: Story): Reading = Reading(story, listOf(Step(story.start)))

        /**
         * The first play-through that reaches [nodeId] (option A before B at every fork), stopped there: for an agent
         * that wants to try the end of a story without reading all of it (platform/AgentFlags). Null if no node has
         * that id.
         */
        fun to(
            story: Story,
            nodeId: String,
        ): Reading? {
            if (nodeId !in story.nodes) return null

            fun walk(reading: Reading): Reading? {
                val node = reading.current
                if (node.id == nodeId) return reading
                if (!StoryGraph.leadsTo(story, node.id, nodeId)) return null
                return when (node) {
                    is Page -> walk(reading.next())
                    is Fork -> node.options.indices.firstNotNullOfOrNull { walk(reading.choose(it)) }
                    is Ending -> null
                }
            }
            return walk(start(story))
        }

        /**
         * Picks up a saved path, or returns null if it doesn't fit the story anymore (a new version changed the
         * structure): then the child starts again from the first page.
         */
        fun resume(
            story: Story,
            path: List<Step>,
        ): Reading? {
            if (path.isEmpty() || path.first().node != story.start) return null
            for ((i, step) in path.withIndex()) {
                val node = story.nodes[step.node] ?: return null
                val last = i == path.lastIndex
                val expected =
                    when (node) {
                        is Page -> if (last) null else node.next
                        is Fork -> step.choice?.let { node.options.getOrNull(it)?.target ?: return null }
                        is Ending -> null
                    }
                if (!last && (expected == null || path[i + 1].node != expected)) return null
                if (last && step.choice != null) return null
            }
            return Reading(story, path)
        }
    }
}
