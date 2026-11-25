package com.nicolasguillen.taletime.data

import com.nicolasguillen.taletime.domain.CatalogEntry
import com.nicolasguillen.taletime.domain.CatalogParser
import com.nicolasguillen.taletime.domain.Download
import com.nicolasguillen.taletime.domain.Installed
import com.nicolasguillen.taletime.domain.Library
import com.nicolasguillen.taletime.domain.StoryBundle
import com.nicolasguillen.taletime.domain.StoryLibrary
import com.nicolasguillen.taletime.story.StoryFormatException
import com.nicolasguillen.taletime.story.StoryGraph
import com.nicolasguillen.taletime.story.StoryParser
import com.nicolasguillen.taletime.story.WordTime
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** The catalog's path in a channel's folder. */
const val CATALOG_PATH = "catalog.json"

/**
 * Where the stories are downloaded from: a path in one channel's folder on the content host (`catalog.json`,
 * `<id>/<file>`) → its URL. The files are written by `story package` and uploaded by `story publish <channel>`
 * (tools/storytool/package.py, publish.py).
 */
fun interface ContentSource {
    fun url(path: String): String

    companion object {
        const val BUCKET = "tale-time-b537d.firebasestorage.app"

        /** The finished stories, for release builds: `stories/production/`. */
        val Production = firebase("production")

        /**
         * Every story as it is, for debug builds: `stories/draft/`, with draft translations and whatever pictures and
         * clips are delivered so far.
         */
        val Draft = firebase("draft")

        /**
         * A channel's folder on Firebase Storage: plain HTTPS, no Firebase SDK. backend/storage.rules lets anyone read
         * single files under stories/. Paths are `[a-z0-9-./]`, so only the slashes need escaping.
         */
        private fun firebase(channel: String) =
            ContentSource { path ->
                "https://firebasestorage.googleapis.com/v0/b/$BUCKET/o/${"stories/$channel/$path".replace("/", "%2F")}?alt=media"
            }
    }
}

/** `stories/<id>/manifest-<hash>.json` (schema/manifest.schema.json): every file of one version of a story. */
@Serializable
internal data class Manifest(
    val id: String,
    val images: Map<String, String>,
    val languages: Map<String, Language>,
    val files: Map<String, FileInfo>,
) {
    /** @property timings when each word of its narrated pages is said ([Timings]), once a page's clip is here. */
    @Serializable
    data class Language(
        val story: String,
        val audio: Map<String, String> = emptyMap(),
        val timings: String? = null,
    )

    @Serializable
    data class FileInfo(
        val bytes: Long,
        val sha256: String,
    )

    /** The files a reader needs to read it in [languages]: the pictures, and each language's text, clips and timings. */
    fun filesFor(languages: Collection<String>): List<String> =
        (
            images.values +
                languages.flatMap { lang ->
                    this.languages[lang]?.let { listOfNotNull(it.story, it.timings) + it.audio.values }.orEmpty()
                }
        ).distinct()
}

/**
 * `timings-<lang>-<hash>.json`: page id → `[start, end]` of each of its words in its clip, in milliseconds (measured by
 * `story package`, tools/storytool/timings.py).
 */
@Serializable
internal data class Timings(
    val clips: Map<String, List<List<Long>>> = emptyMap(),
) {
    fun words(): Map<String, List<WordTime>> =
        clips.mapValues { (_, words) -> words.filter { it.size == 2 }.map { (start, end) -> WordTime(start, end) } }
}

/**
 * The stories from the content host ([source]), kept in [directory] to read offline:
 *
 * ```
 * catalog.json            the last catalog fetched (the copy that ships inside the app stands in until then)
 * installed.json          story id → its manifest, the languages downloaded and the one it's read in
 * stories/<id>/<file>     its files, named after their contents: cover, manifests, texts, pictures, clips
 * ```
 *
 * A file only counts once its size and SHA-256 match its manifest, and it's written next to its final name and then
 * renamed, so a half-written file is never taken for a whole one. A story switches to a new version only once every
 * file of that version is on the device; the files of the old one are deleted at the next launch, so a story being
 * read is never pulled from under the reader. One download, update or delete runs at a time.
 *
 * @param readBundledShelf the shelf that ships inside the app (`files/shelf.json`): only titles, until the first
 *   launch online.
 * @param scope where downloads run: they outlive the screen that started them.
 * @param refreshEvery how often [refresh] goes online: once per launch, not again when the app comes back to the
 *   front a moment later.
 */
class HostedStoryLibrary(
    private val http: HttpClient,
    private val fileSystem: FileSystem,
    private val directory: Path,
    private val source: ContentSource,
    private val readBundledShelf: suspend () -> ByteArray,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val refreshEvery: Duration = 1.minutes,
    private val clock: TimeSource = TimeSource.Monotonic,
) : StoryLibrary {
    private val json = Json { ignoreUnknownKeys = true }
    private val catalogFile = directory / "catalog.json"
    private val installedFile = directory / "installed.json"
    private val storiesDir = directory / "stories"

    private val mutableState = MutableStateFlow(Library())
    override val state: StateFlow<Library> = mutableState.asStateFlow()

    /** One refresh, download or delete at a time. */
    private val jobs = Mutex()

    /** When the catalog was last fetched in this run. */
    private var fetchedAt: TimeMark? = null

    /** The files the last run left behind (an old version, a half download) were deleted. */
    private var cleaned = false

    private val bundleLock = Mutex()
    private val bundles = mutableMapOf<String, Pair<Installed, StoryBundle>>()

    init {
        scope.launch { jobs.withLock { if (!state.value.ready) readSaved() } }
    }

    override suspend fun refresh() {
        jobs.withLock {
            if (!state.value.ready) readSaved()
            if (!cleaned) {
                cleaned = true
                withContext(io) { cleanUp() }
            }
            if (fetchedAt?.let { it.elapsedNow() < refreshEvery } == true) return
            val stories = fetchCatalog() ?: return
            for (entry in stories) {
                val cover = entry.cover ?: continue
                runCatching {
                    ensure(
                        entry.id,
                        cover.file,
                        cover.bytes,
                        cover.sha256,
                    )
                }.onFailure { if (it is CancellationException) throw it }
            }
            val covers = withContext(io) { covers(stories) }
            mutableState.update { it.copy(covers = covers) }
            val shelf = stories.associateBy { it.id }
            for ((id, installed) in state.value.installed) {
                val entry = shelf[id]
                when {
                    // Off the shelf: it goes from the device too.
                    entry == null -> {
                        forget(id)
                    }

                    entry.manifest != null && entry.manifest.file != installed.manifest -> {
                        try {
                            install(entry, installed.languages, installed.reading) {}
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // The old version stays; the next launch tries again.
                        }
                    }
                }
            }
        }
    }

    override fun download(
        storyId: String,
        language: String,
    ) {
        if (state.value.downloads[storyId] is Download.Running) return
        setDownload(storyId, Download.Running(language))
        scope.launch {
            val done =
                try {
                    jobs.withLock { downloadOrSwitch(storyId, language) }
                    true
                } catch (e: CancellationException) {
                    setDownload(storyId, null)
                    throw e
                } catch (_: Exception) {
                    false
                }
            setDownload(storyId, if (done) null else Download.Failed(language))
        }
    }

    override suspend fun load(storyId: String): StoryBundle {
        val installed = state.first { it.ready }.installed[storyId] ?: throw IllegalStateException("'$storyId' isn't downloaded")
        return bundleLock.withLock {
            bundles[storyId]?.takeIf { it.first == installed }?.second
                ?: withContext(io) { readBundle(storyId, installed) }.also { bundles[storyId] = installed to it }
        }
    }

    /** Every file of the downloaded stories; the covers, which the shelf keeps, don't count. */
    override suspend fun bytesOnDevice(): Long =
        withContext(io) {
            val covers =
                state.value.stories
                    .mapNotNull { entry -> entry.cover?.let { storiesDir / entry.id / it.file } }
                    .toSet()
            fileSystem
                .listRecursivelyOrEmpty(storiesDir)
                .filter { it !in covers }
                .sumOf { fileSystem.metadataOrNull(it)?.takeIf { m -> m.isRegularFile }?.size ?: 0L }
        }

    override suspend fun deleteAll() {
        jobs.withLock {
            for (id in state.value.installed.keys) forget(id)
        }
    }

    // Catalog ---------------------------------------------------------------------------------------------------------

    /** The catalog and what's downloaded, as saved on the device. */
    private suspend fun readSaved() {
        val saved =
            withContext(io) {
                val stories =
                    readText(catalogFile)?.let { runCatching { CatalogParser.parse(it) }.getOrNull() }
                        ?: runCatching { CatalogParser.parse(readBundledShelf().decodeToString()) }.getOrDefault(emptyList())
                val installed =
                    readText(installedFile)
                        ?.let { runCatching { json.decodeFromString<Map<String, Installed>>(it) }.getOrNull() }
                        .orEmpty()
                        .filter { (id, story) -> fileSystem.exists(storiesDir / id / story.manifest) }
                Library(ready = true, stories = stories, covers = covers(stories), installed = installed)
            }
        mutableState.update { saved.copy(downloads = it.downloads) }
    }

    /** The catalog from the host, saved for offline launches, or null when it can't be fetched. */
    private suspend fun fetchCatalog(): List<CatalogEntry>? {
        val bytes =
            try {
                get(CATALOG_PATH, fresh = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return null
            }
        val stories = runCatching { CatalogParser.parse(bytes.decodeToString()) }.getOrNull() ?: return null
        withContext(io) { write(catalogFile, bytes) }
        fetchedAt = clock.markNow()
        mutableState.update { it.copy(stories = stories) }
        return stories
    }

    private fun covers(stories: List<CatalogEntry>): Map<String, String> =
        stories
            .mapNotNull { entry ->
                entry.cover?.takeIf { has(entry.id, it.file, it.bytes) }?.let { entry.id to (storiesDir / entry.id / it.file).toString() }
            }.toMap()

    // Downloads -------------------------------------------------------------------------------------------------------

    private suspend fun downloadOrSwitch(
        storyId: String,
        language: String,
    ) {
        if (!state.value.ready) readSaved()
        val installed = state.value.installed[storyId]
        if (installed != null && language in installed.languages) {
            saveInstalled(storyId, installed.copy(reading = language))
            return
        }
        // The catalog inside the app can't download anything, and a story must come from this run's catalog.
        if (fetchedAt == null) fetchCatalog() ?: throw IllegalStateException("Offline")
        val entry = state.value.stories.firstOrNull { it.id == storyId } ?: throw IllegalStateException("'$storyId' isn't on the shelf")
        install(entry, installed?.languages.orEmpty() + language, language) { fraction ->
            setDownload(storyId, Download.Running(language, fraction))
        }
    }

    /**
     * Brings [entry] to its current manifest in [languages] (the ones it doesn't publish any more are dropped): fetches
     * the files the device doesn't have, a few at a time, then switches to it.
     */
    private suspend fun install(
        entry: CatalogEntry,
        languages: List<String>,
        reading: String,
        progress: (Float) -> Unit,
    ) {
        val ref = entry.manifest ?: throw IllegalStateException("'${entry.id}' can't be downloaded yet")
        ensure(entry.id, ref.file, ref.bytes, ref.sha256)
        val manifest = withContext(io) { readManifest(entry.id, ref.file) }
        val langs = languages.distinct().filter { it in manifest.languages }.ifEmpty { listOf(manifest.languages.keys.first()) }
        val missing =
            withContext(io) {
                manifest.filesFor(langs).filter { name ->
                    val info = manifest.files[name] ?: throw StoryFormatException("The manifest doesn't describe '$name'")
                    !has(entry.id, name, info.bytes)
                }
            }
        val total = missing.sumOf { manifest.files.getValue(it).bytes }.coerceAtLeast(1)
        val done = MutableStateFlow(0L)
        val permits = Semaphore(PARALLEL_DOWNLOADS)
        progress(0f)
        coroutineScope {
            missing
                .map { name ->
                    async {
                        permits.withPermit {
                            val info = manifest.files.getValue(name)
                            ensure(entry.id, name, info.bytes, info.sha256)
                            progress(done.updateAndGet { it + info.bytes }.toFloat() / total)
                        }
                    }
                }.awaitAll()
        }
        saveInstalled(entry.id, Installed(ref.file, langs, reading.takeIf { it in langs } ?: langs.first()))
    }

    /** Downloads one file of a story unless it's on the device already, and checks its size and SHA-256. */
    private suspend fun ensure(
        storyId: String,
        name: String,
        bytes: Long,
        sha256: String,
    ) {
        if (withContext(io) { has(storyId, name, bytes) }) return
        // A dropped connection or a garbled file is tried again a couple of times before the whole download gives up.
        var attempt = 1
        while (true) {
            try {
                val data = get("$storyId/$name")
                if (data.size.toLong() != bytes || data.toByteString().sha256().hex() != sha256.lowercase()) {
                    throw IllegalStateException("$storyId/$name doesn't match its checksum")
                }
                withContext(io) { write(storiesDir / storyId / name, data) }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == ATTEMPTS) throw e
                delay(RETRY_AFTER * attempt++)
            }
        }
    }

    private suspend fun get(
        path: String,
        fresh: Boolean = false,
    ): ByteArray {
        val response = http.get(source.url(path)) { if (fresh) header(HttpHeaders.CacheControl, "no-cache") }
        check(response.status.isSuccess()) { "$path: HTTP ${response.status}" }
        return response.bodyAsBytes()
    }

    private fun setDownload(
        storyId: String,
        download: Download?,
    ) = mutableState.update { it.copy(downloads = if (download == null) it.downloads - storyId else it.downloads + (storyId to download)) }

    // On the device ---------------------------------------------------------------------------------------------------

    private suspend fun saveInstalled(
        storyId: String,
        installed: Installed?,
    ) {
        val all = state.value.installed.let { if (installed == null) it - storyId else it + (storyId to installed) }
        withContext(io) { write(installedFile, json.encodeToString(all).encodeToByteArray()) }
        mutableState.update { it.copy(installed = all) }
    }

    /** Takes a story off the device. Its cover stays, for the shelf. */
    private suspend fun forget(storyId: String) {
        saveInstalled(storyId, null)
        withContext(io) {
            val cover =
                state.value.stories
                    .firstOrNull { it.id == storyId }
                    ?.cover
                    ?.file
            fileSystem
                .listOrNull(storiesDir / storyId)
                .orEmpty()
                .filter { it.name != cover }
                .forEach { fileSystem.delete(it) }
        }
        bundleLock.withLock { bundles.remove(storyId) }
    }

    /** Deletes what no downloaded story and no cover on the shelf uses: old versions, half downloads, old stories. */
    private fun cleanUp() {
        val shelf = state.value.stories.associateBy { it.id }
        val installed = state.value.installed
        for (folder in fileSystem.listOrNull(storiesDir).orEmpty()) {
            val id = folder.name
            val keep = mutableSetOf<String>()
            shelf[id]?.cover?.let { keep += it.file }
            installed[id]?.let { story ->
                keep += story.manifest
                runCatching { readManifest(id, story.manifest) }.getOrNull()?.let { keep += it.filesFor(story.languages) }
            }
            if (keep.isEmpty()) {
                fileSystem.deleteRecursively(folder)
            } else {
                fileSystem
                    .listOrNull(folder)
                    .orEmpty()
                    .filter { it.name !in keep }
                    .forEach { fileSystem.delete(it) }
            }
        }
    }

    private fun readBundle(
        storyId: String,
        installed: Installed,
    ): StoryBundle {
        val folder = storiesDir / storyId
        val manifest = readManifest(storyId, installed.manifest)
        val texts =
            installed.languages.associateWith { lang ->
                val name = manifest.languages[lang]?.story ?: throw StoryFormatException("'$storyId' has no '$lang'")
                readText(folder / name) ?: throw StoryFormatException("'$storyId' is missing $name")
            }
        val story = StoryParser.parse(texts)
        val problems = StoryGraph.problems(story)
        if (story.id != storyId || problems.isNotEmpty()) {
            throw StoryFormatException("Story '$storyId' can't be read: ${problems.ifEmpty { listOf("its id is '${story.id}'") }}")
        }
        return StoryBundle(
            story = story,
            images = manifest.images.mapValues { (_, name) -> (folder / name).toString() },
            audio =
                installed.languages.associateWith { lang ->
                    manifest.languages[lang]
                        ?.audio
                        .orEmpty()
                        .mapValues { (_, name) -> (folder / name).toString() }
                },
            // Without its timings, a narrated page still plays: its words come at the narrator's pace.
            timings =
                installed.languages.associateWith { lang ->
                    manifest.languages[lang]
                        ?.timings
                        ?.let { name -> readText(folder / name) }
                        ?.let { text -> runCatching { json.decodeFromString<Timings>(text).words() }.getOrNull() }
                        .orEmpty()
                },
            reading = story.languageFor(installed.reading),
        )
    }

    private fun readManifest(
        storyId: String,
        name: String,
    ): Manifest =
        json.decodeFromString(
            readText(storiesDir / storyId / name) ?: throw StoryFormatException("'$storyId' is missing its manifest $name"),
        )

    private fun has(
        storyId: String,
        name: String,
        bytes: Long,
    ): Boolean = fileSystem.metadataOrNull(storiesDir / storyId / name)?.size == bytes

    private fun readText(path: Path): String? = runCatching { fileSystem.read(path) { readUtf8() } }.getOrNull()

    /** Writes next to [path], then renames, so a reader never sees half a file. */
    private fun write(
        path: Path,
        data: ByteArray,
    ) {
        path.parent?.let { fileSystem.createDirectories(it) }
        val partial = path.parent!! / "${path.name}.part"
        fileSystem.write(partial) { write(data) }
        fileSystem.atomicMove(partial, path)
    }

    private fun FileSystem.listRecursivelyOrEmpty(dir: Path): List<Path> = if (exists(dir)) listRecursively(dir).toList() else emptyList()

    private companion object {
        /** Files downloaded at the same time. */
        const val PARALLEL_DOWNLOADS = 4

        /** Tries per file, and the wait before the next one (longer each time). */
        const val ATTEMPTS = 3
        val RETRY_AFTER = 1.seconds
    }
}
