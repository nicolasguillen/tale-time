package com.nicolasguillen.taletime.domain

import com.nicolasguillen.taletime.story.Story
import com.nicolasguillen.taletime.story.WordTime
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * A downloaded story, ready to read: its text in the languages on the device, and where its files are.
 *
 * @property images image id → a path Coil can load. An image that isn't here hasn't been delivered yet, and the
 *   app shows the static placeholder for its kind instead.
 * @property audio language → clip id → a path to the clip (docs/story-format.md, "Narration clips"), for the clips
 *   delivered so far. A text without its clip reads without narration.
 * @property timings language → page id → when each word of the page is said in its clip.
 * @property reading the language this story is read in: the one picked last.
 */
class StoryBundle(
    val story: Story,
    val images: Map<String, String>,
    val audio: Map<String, Map<String, String>> = emptyMap(),
    val timings: Map<String, Map<String, List<WordTime>>> = emptyMap(),
    val reading: String = story.languages.first(),
) {
    fun image(imageId: String): String? = images[imageId]

    /** The clip that reads [clipId] aloud in the language the story is read in, if it's delivered. */
    fun clip(clipId: String): String? = audio[reading]?.get(clipId)

    /** When each word of page [nodeId] is said in its clip, in the language the story is read in. */
    fun timings(nodeId: String): List<WordTime>? = timings[reading]?.get(nodeId)
}

/**
 * A story on the device.
 *
 * @property manifest the manifest its files match (a file name in its folder).
 * @property languages the languages downloaded: each one's text and narration.
 * @property reading the language it's read in, the one picked last.
 */
@Serializable
data class Installed(
    val manifest: String,
    val languages: List<String>,
    val reading: String,
)

/** A download a reader started, while it runs or after it failed. */
sealed interface Download {
    val language: String

    /** @property fraction how much is done, 0 to 1. */
    data class Running(
        override val language: String,
        val fraction: Float = 0f,
    ) : Download

    /** It stopped: offline, or the host didn't answer. Picking a language again retries. */
    data class Failed(
        override val language: String,
    ) : Download
}

/**
 * Everything the shelf and the covers need to know about the stories.
 *
 * @property ready the catalog saved on the device (or the one that ships inside the app) has been read.
 * @property stories the shelf, in order: the last catalog fetched, or the copy inside the app until the first launch
 *   online.
 * @property covers story id → its cover's path on the device, once downloaded.
 * @property installed story id → what's downloaded.
 * @property downloads story id → the download running or failed.
 */
data class Library(
    val ready: Boolean = false,
    val stories: List<CatalogEntry> = emptyList(),
    val covers: Map<String, String> = emptyMap(),
    val installed: Map<String, Installed> = emptyMap(),
    val downloads: Map<String, Download> = emptyMap(),
)

/**
 * Where the stories come from: the content host. The shelf lists every story; a story is downloaded when a reader
 * picks a language for it, kept on the device to read offline, and brought up to date by [refresh].
 */
interface StoryLibrary {
    val state: StateFlow<Library>

    /**
     * Fetches the catalog and brings every downloaded story up to date: when a story's manifest changed, downloads the
     * files it doesn't have yet. Offline, it does nothing and the stories on the device stay as they are.
     */
    suspend fun refresh()

    /**
     * Downloads [storyId] in [language] in the background ([state] shows how far it is) and reads it in that language
     * from then on. A language already downloaded is only switched to.
     */
    fun download(
        storyId: String,
        language: String,
    )

    /** A downloaded story, from the device. Throws if it isn't downloaded or can't be read. */
    suspend fun load(storyId: String): StoryBundle

    /** The space the downloaded stories take, in bytes. */
    suspend fun bytesOnDevice(): Long

    /** Deletes every downloaded story (the parents area). The shelf and the covers stay. */
    suspend fun deleteAll()
}
