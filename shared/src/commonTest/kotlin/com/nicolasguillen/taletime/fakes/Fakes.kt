package com.nicolasguillen.taletime.fakes

import com.nicolasguillen.taletime.domain.Access
import com.nicolasguillen.taletime.domain.CatalogEntry
import com.nicolasguillen.taletime.domain.Download
import com.nicolasguillen.taletime.domain.HostedFile
import com.nicolasguillen.taletime.domain.Installed
import com.nicolasguillen.taletime.domain.LanguageOffer
import com.nicolasguillen.taletime.domain.Library
import com.nicolasguillen.taletime.domain.Product
import com.nicolasguillen.taletime.domain.ProgressRepository
import com.nicolasguillen.taletime.domain.PurchaseResult
import com.nicolasguillen.taletime.domain.Purchases
import com.nicolasguillen.taletime.domain.Settings
import com.nicolasguillen.taletime.domain.SettingsRepository
import com.nicolasguillen.taletime.domain.StoryBundle
import com.nicolasguillen.taletime.domain.StoryLibrary
import com.nicolasguillen.taletime.domain.StoryProgress
import com.nicolasguillen.taletime.fixtures.SCHEMA_FIXTURES
import com.nicolasguillen.taletime.platform.NarrationPlayer
import com.nicolasguillen.taletime.story.Step
import com.nicolasguillen.taletime.story.StoryFormatException
import com.nicolasguillen.taletime.story.StoryGraph
import com.nicolasguillen.taletime.story.StoryParser
import com.nicolasguillen.taletime.story.WordTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The tiny fixture: language → its `story/<lang>.json`. */
val TINY: Map<String, String> = SCHEMA_FIXTURES.getValue("tiny")

/** The tiny fixture on the shelf, in English and Spanish. */
fun tinyEntry(access: String = "free"): CatalogEntry =
    CatalogEntry(
        id = "tiny",
        access = if (access == "paid") Access.PAID else Access.FREE,
        product = if (access == "paid") "story.tiny" else null,
        ageBand = "3-5",
        title = mapOf("en" to "Tiny", "es" to "Chiquito"),
        languages = mapOf("en" to LanguageOffer(1_200_000), "es" to LanguageOffer(2_400_000, narration = true)),
        manifest = HostedFile("manifest-0123456789.json", 100, "0".repeat(64)),
    )

/**
 * A [StoryLibrary] in memory: [download] finishes at once (or fails, with [failDownloads]), and [load] reads the
 * story in the languages "downloaded".
 *
 * @param stories story id → its files (language → the text of its `story/<lang>.json`).
 * @param images story id → (image id → path) of its delivered images.
 * @param audio story id → language → (clip id → path) of its delivered clips.
 * @param timings story id → language → page id → when its words are said.
 */
class FakeStoryLibrary(
    entries: List<CatalogEntry>,
    private val stories: Map<String, Map<String, String>>,
    private val images: Map<String, Map<String, String>> = emptyMap(),
    installed: Map<String, Installed> = emptyMap(),
    private val audio: Map<String, Map<String, Map<String, String>>> = emptyMap(),
    private val timings: Map<String, Map<String, Map<String, List<WordTime>>>> = emptyMap(),
) : StoryLibrary {
    override val state = MutableStateFlow(Library(ready = true, stories = entries, installed = installed))
    var refreshed = 0
    var failDownloads = false
    val downloaded = mutableListOf<Pair<String, String>>()

    override suspend fun refresh() {
        refreshed++
    }

    override fun download(
        storyId: String,
        language: String,
    ) {
        downloaded += storyId to language
        if (failDownloads) {
            state.update { it.copy(downloads = it.downloads + (storyId to Download.Failed(language))) }
            return
        }
        state.update {
            val before = it.installed[storyId]?.languages.orEmpty()
            val now = Installed("manifest-0123456789.json", (before + language).distinct(), language)
            it.copy(installed = it.installed + (storyId to now), downloads = it.downloads - storyId)
        }
    }

    override suspend fun load(storyId: String): StoryBundle {
        val installed = state.value.installed[storyId] ?: throw IllegalStateException("'$storyId' isn't downloaded")
        val files = stories[storyId] ?: throw IllegalStateException("No story '$storyId'")
        val story = StoryParser.parse(files.filterKeys { it in installed.languages })
        if (StoryGraph.problems(story).isNotEmpty()) throw StoryFormatException("broken")
        return StoryBundle(
            story,
            images[storyId].orEmpty(),
            audio = audio[storyId].orEmpty().filterKeys { it in installed.languages },
            timings = timings[storyId].orEmpty().filterKeys { it in installed.languages },
            reading = story.languageFor(installed.reading),
        )
    }

    override suspend fun bytesOnDevice(): Long = state.value.installed.size * 1_000_000L

    override suspend fun deleteAll() = state.update { it.copy(installed = emptyMap()) }
}

/**
 * The tiny fixture alone on the shelf, free, downloaded in [downloaded] (none: not downloaded yet) and read in the
 * first of them. Its first page's picture is delivered, and the clips in [clips] (language → clip ids), each at
 * `/<lang>-<clip>.m4a`, with [timings] (language → page id → when its words are said).
 */
fun tinyLibrary(
    access: String = "free",
    downloaded: List<String> = listOf("en"),
    clips: Map<String, List<String>> = emptyMap(),
    timings: Map<String, Map<String, List<WordTime>>> = emptyMap(),
): FakeStoryLibrary =
    FakeStoryLibrary(
        entries = listOf(tinyEntry(access)),
        stories = mapOf("tiny" to TINY),
        images = mapOf("tiny" to mapOf("p1" to "/library/stories/tiny/p1-0123456789.webp")),
        installed =
            if (downloaded.isEmpty()) {
                emptyMap()
            } else {
                mapOf("tiny" to Installed("manifest-0123456789.json", downloaded, downloaded.first()))
            },
        audio = mapOf("tiny" to clips.mapValues { (lang, ids) -> ids.associateWith { clipPath(lang, it) } }),
        timings = mapOf("tiny" to timings),
    )

/** Where [tinyLibrary] puts a clip. */
fun clipPath(
    language: String,
    clipId: String,
): String = "/$language-$clipId.m4a"

/**
 * A narrator that makes no sound: each clip "plays" until [finish] ends it, and fails at once if it's in [failing].
 * Like the real players, cancelling the call stops it, and while [paused] a clip waits to start.
 */
class FakeNarrationPlayer : NarrationPlayer {
    /** Every clip asked for, in order. */
    val played = mutableListOf<String>()
    val failing = mutableSetOf<String>()

    /** The clip sounding now. */
    var playing: String? = null
        private set
    var position = 0L
    private val pausedNow = MutableStateFlow(false)
    val paused: Boolean get() = pausedNow.value
    var released = false
        private set
    private var end: CompletableDeferred<Unit>? = null

    override suspend fun play(path: String) {
        played += path
        if (path in failing) throw IllegalStateException("Can't play $path")
        pausedNow.first { !it }
        val done = CompletableDeferred<Unit>()
        end = done
        playing = path
        position = 0
        try {
            done.await()
        } finally {
            if (end === done) {
                end = null
                playing = null
            }
        }
    }

    /** The clip playing reaches its end. */
    fun finish() {
        end?.complete(Unit)
    }

    override fun positionMillis(): Long? = playing?.let { position }

    override fun pause() {
        pausedNow.value = true
    }

    override fun resume() {
        pausedNow.value = false
    }

    override fun release() {
        released = true
    }
}

class FakeProgress : ProgressRepository {
    val state = MutableStateFlow<Map<String, StoryProgress>>(emptyMap())

    override fun progress(storyId: String): Flow<StoryProgress> = state.map { it[storyId] ?: StoryProgress() }

    override fun all(): Flow<Map<String, StoryProgress>> = state

    override suspend fun save(
        storyId: String,
        path: List<Step>,
    ) = edit(storyId) { it.copy(path = path, visited = it.visited + path.map { step -> step.node }) }

    override suspend fun markEndingFound(
        storyId: String,
        endingId: String,
    ) = edit(storyId) { it.copy(endingsFound = it.endingsFound + endingId) }

    override suspend fun resetAll() {
        state.value = emptyMap()
    }

    fun edit(
        storyId: String,
        change: (StoryProgress) -> StoryProgress,
    ) = state.update { it + (storyId to change(it[storyId] ?: StoryProgress())) }
}

class FakeSettings(
    initial: Settings = Settings(),
) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<Settings> = state

    override suspend fun update(transform: (Settings) -> Settings) = state.update(transform)
}

/** A store that sells [prices] and remembers what was bought. */
class FakePurchases(
    private val prices: Map<String, String> = emptyMap(),
    private val result: PurchaseResult = PurchaseResult.Bought,
) : Purchases {
    override val owned = MutableStateFlow(emptySet<String>())
    var restored = 0

    override suspend fun products(ids: List<String>): List<Product> = ids.mapNotNull { id -> prices[id]?.let { Product(id, it) } }

    override suspend fun buy(productId: String): PurchaseResult {
        if (result == PurchaseResult.Bought) owned.update { it + productId }
        return result
    }

    override suspend fun restore() {
        restored++
    }
}
