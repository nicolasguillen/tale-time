package com.nicolasguillen.taletime.domain

import com.nicolasguillen.taletime.story.Ending
import com.nicolasguillen.taletime.story.Reading

/**
 * A book as the shelf and its cover show it.
 *
 * @property cover the cover picture on the device, or null for the placeholder.
 * @property bundle the story, once it's downloaded.
 * @property download a download running or failed.
 * @property open free, or bought: a closed one shows a lock and asks for a grown-up.
 * @property inProgress a play-through stopped before its ending, which "Continue" picks up.
 */
data class Book(
    val entry: CatalogEntry,
    val cover: String?,
    val bundle: StoryBundle?,
    val download: Download?,
    val open: Boolean,
    val endingsFound: Set<String>,
    val inProgress: Boolean,
) {
    val id: String get() = entry.id
    val downloaded: Boolean get() = bundle != null
    val downloading: Boolean get() = download is Download.Running
    val endings: List<Ending> get() = bundle?.story?.endings.orEmpty()

    /** The title in the language the book is read in once downloaded; before that, in [appLanguage] if it has it. */
    fun title(appLanguage: String): String {
        val bundle = bundle ?: return entry.title(appLanguage)
        return bundle.story.title[bundle.reading]
    }
}

/**
 * Puts the catalog, what's downloaded, what this account owns and the saved progress together, in shelf order.
 *
 * @param bundles story id → the downloaded story, for the ones the device has.
 */
fun shelf(
    library: Library,
    bundles: Map<String, StoryBundle>,
    owned: Set<String>,
    progress: Map<String, StoryProgress>,
): List<Book> =
    library.stories.map { entry ->
        val bundle = bundles[entry.id]
        val saved = progress[entry.id] ?: StoryProgress()
        val story = bundle?.story
        val reading = story?.let { s -> saved.path?.let { Reading.resume(s, it) } }
        Book(
            entry = entry,
            cover = bundle?.image(bundle.story.cover) ?: library.covers[entry.id],
            bundle = bundle,
            download = library.downloads[entry.id],
            open = entry.isOpen(owned),
            // Endings a new version removed don't count any more.
            endingsFound = story?.let { s -> saved.endingsFound intersect s.endings.map { it.id }.toSet() } ?: saved.endingsFound,
            inProgress = reading != null && !reading.atEnding && reading.path.size > 1,
        )
    }
