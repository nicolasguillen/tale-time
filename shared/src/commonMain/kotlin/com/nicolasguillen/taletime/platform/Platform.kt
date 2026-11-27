package com.nicolasguillen.taletime.platform

import okio.Path
import org.koin.core.module.Module

/**
 * Facts about the running build, bound in [platformModule].
 *
 * @property debug a debug build: a debuggable APK on Android, a debug Kotlin framework (Xcode's Debug configuration)
 *   on iOS.
 * @property version the version parents see, with the build number: "1.0 (6)".
 */
class BuildInfo(
    val debug: Boolean,
    val version: String,
)

/**
 * Switches for an agent testing a debug build, given when it launches the app; a release build, or an app opened from
 * its icon, never has any. Bound in [platformModule]. On iOS they're launch arguments (`-skipReading`, `-openStory
 * <id>`), on Android intent extras (`--ez skipReading true`, `--es openStory <id>`). For trying and measuring the page
 * turn without a UI test, whose accessibility queries cost the app frames (docs/page-turn.md §11), and for trying an
 * ending without reading the whole story.
 *
 * @property skipReading every page shows all its words at once and nothing is read aloud, so a plain page's corner
 *   folds up about a second after the page is shown, and a fork's cards rise right away.
 * @property openStory the app opens this downloaded story's reader from its first page, over the shelf and its cover.
 * @property openEnding with [openStory], the reader opens on this page instead (an ending's id, or any page's), along
 *   the first path that leads there: the ending's text, then its closing, at once.
 * @property autoTurn the reader turns its pages by itself: a plain page from its corner (the tap turn) a moment after
 *   the corner is up, a fork by picking a card (the one that shows the way, else each one in turn), an ending by
 *   tapping its first "?" badge, or "Try another path" once every ending is found. It never stops.
 */
class AgentFlags(
    val skipReading: Boolean = false,
    val openStory: String? = null,
    val autoTurn: Boolean = false,
    val openEnding: String? = null,
)

/**
 * Where downloaded stories are kept (data/HostedStoryLibrary.kt): private to the app, never emptied by the system
 * (not a cache folder), and left out of device backups, since they download again.
 */
class LibraryDirectory(
    val path: Path,
)

/**
 * Koin bindings that need the platform: where the settings file, the database and the downloaded stories
 * ([LibraryDirectory]) live, the HTTP client for downloads, the [BuildInfo], and a [NarrationPlayer] for each screen
 * that reads aloud. Everything else here is a process-wide singleton (DataStore and Room both require one instance per
 * file).
 */
expect val platformModule: Module
