package com.nicolasguillen.taletime.domain

import com.nicolasguillen.taletime.story.Step
import kotlinx.coroutines.flow.Flow

/**
 * What a child has done in one story, kept on the device only. Keyed by node ids, which never change once a story
 * is published, so progress survives new versions of a story.
 *
 * @property path the play-through to pick up again, or null if there's none (never started, or reset).
 * @property visited every page seen on any path.
 * @property endingsFound the endings reached, by node id.
 */
data class StoryProgress(
    val path: List<Step>? = null,
    val visited: Set<String> = emptySet(),
    val endingsFound: Set<String> = emptySet(),
)

interface ProgressRepository {
    fun progress(storyId: String): Flow<StoryProgress>

    /** Every story with any progress, by story id. */
    fun all(): Flow<Map<String, StoryProgress>>

    /** Saves the current play-through and marks its pages visited. */
    suspend fun save(
        storyId: String,
        path: List<Step>,
    )

    suspend fun markEndingFound(
        storyId: String,
        endingId: String,
    )

    /** Forgets every story's progress (the parents area). */
    suspend fun resetAll()
}
