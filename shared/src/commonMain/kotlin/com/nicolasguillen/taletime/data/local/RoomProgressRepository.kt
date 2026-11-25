package com.nicolasguillen.taletime.data.local

import com.nicolasguillen.taletime.domain.ProgressRepository
import com.nicolasguillen.taletime.domain.StoryProgress
import com.nicolasguillen.taletime.story.Step
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomProgressRepository(
    private val dao: ProgressDao,
) : ProgressRepository {
    override fun all(): Flow<Map<String, StoryProgress>> =
        combine(dao.paths(), dao.visited(), dao.endings()) { paths, visited, endings ->
            val ids = paths.map { it.storyId } + visited.map { it.storyId } + endings.map { it.storyId }
            ids.toSet().associateWith { id ->
                StoryProgress(
                    path = paths.firstOrNull { it.storyId == id }?.path?.let(::decodePath),
                    visited = visited.filter { it.storyId == id }.map { it.nodeId }.toSet(),
                    endingsFound = endings.filter { it.storyId == id }.map { it.endingId }.toSet(),
                )
            }
        }.distinctUntilChanged()

    override fun progress(storyId: String): Flow<StoryProgress> = all().map { it[storyId] ?: StoryProgress() }.distinctUntilChanged()

    override suspend fun save(
        storyId: String,
        path: List<Step>,
    ) {
        dao.savePath(ReadingPathEntity(storyId, encodePath(path)))
        dao.visit(path.map { VisitedPageEntity(storyId, it.node) })
    }

    override suspend fun markEndingFound(
        storyId: String,
        endingId: String,
    ) = dao.found(EndingFoundEntity(storyId, endingId))

    override suspend fun resetAll() {
        dao.deletePaths()
        dao.deleteVisited()
        dao.deleteEndings()
    }
}

internal fun encodePath(path: List<Step>): String = path.joinToString(",") { step -> step.choice?.let { "${step.node}:$it" } ?: step.node }

internal fun decodePath(text: String): List<Step>? =
    if (text.isEmpty()) {
        null
    } else {
        text.split(",").map { part ->
            val node = part.substringBefore(":")
            val choice = part.substringAfter(":", "").takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: return null }
            Step(node, choice)
        }
    }
