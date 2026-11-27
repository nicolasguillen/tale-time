package com.nicolasguillen.taletime.data

import com.nicolasguillen.taletime.domain.Download
import com.nicolasguillen.taletime.domain.Installed
import com.nicolasguillen.taletime.fakes.TINY
import com.nicolasguillen.taletime.story.WordTime
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * The library against a fake content host, laid out like `story package` writes it, and an in-memory disk. Each
 * [library] call is a new launch of the app on the same device.
 */
class HostedStoryLibraryTest {
    private val disk = FakeFileSystem()
    private val dir = "/app/library".toPath()

    /** What the host serves, by path. Missing paths are 404s; [online] = false fails every request. */
    private val host = mutableMapOf<String, ByteArray>()
    private var online = true
    private val requests = mutableListOf<String>()

    /** Paths whose next request drops, once each. */
    private val dropOnce = mutableSetOf<String>()

    private val shelfInsideTheApp =
        """{ "format": 1, "stories": [ { "id": "tiny", "access": "free", "ageBand": "3-5", "title": { "en": "Tiny" } } ] }"""

    private fun TestScope.library(scope: CoroutineScope = backgroundScope): HostedStoryLibrary {
        val engine =
            MockEngine { request ->
                val path = request.url.encodedPath.removePrefix("/")
                requests += path
                val body = host[path]
                when {
                    !online -> throw IllegalStateException("offline")
                    dropOnce.remove(path) -> throw IllegalStateException("connection dropped")
                    body == null -> respondError(HttpStatusCode.NotFound)
                    else -> respond(body)
                }
            }
        return HostedStoryLibrary(
            http = HttpClient(engine),
            fileSystem = disk,
            directory = dir,
            // The channel's folder is stories/ on the fake host.
            source = { path -> "https://host.test/stories/$path" },
            readBundledShelf = { shelfInsideTheApp.encodeToByteArray() },
            scope = scope,
            // Everything on the test's thread: the fake disk isn't made for threads.
            io = StandardTestDispatcher(testScheduler),
            refreshEvery = Duration.ZERO,
        )
    }

    /**
     * Publishes the tiny story like `story package` does, and returns its manifest's name.
     *
     * @param timings language → its timings file, for the languages with narrated pages.
     */
    private fun publish(
        pictures: Map<String, String> = mapOf("cover" to "a cover", "p1" to "page one"),
        clips: Map<String, Map<String, String>> = mapOf("es" to mapOf("title" to "título", "p1" to "página uno")),
        timings: Map<String, String> = emptyMap(),
    ): String {
        val files = mutableMapOf<String, ByteArray>()

        fun put(
            stem: String,
            ext: String,
            data: ByteArray,
        ): String {
            val name = "$stem-${data.toByteString().sha256().hex().take(10)}.$ext"
            files[name] = data
            host["stories/tiny/$name"] = data
            return name
        }

        fun info(name: String) = files.getValue(name).let { """{"bytes":${it.size},"sha256":"${it.toByteString().sha256().hex()}"}""" }

        val images = pictures.mapValues { (id, text) -> put(id, "webp", text.encodeToByteArray()) }
        val languages =
            TINY.mapValues { (lang, json) ->
                put("story-$lang", "json", json.encodeToByteArray()) to
                    clips[lang].orEmpty().mapValues { (clip, text) -> put("$lang-$clip", "m4a", text.encodeToByteArray()) }
            }
        val timed = timings.mapValues { (lang, json) -> put("timings-$lang", "json", json.encodeToByteArray()) }
        val manifest =
            buildString {
                append("""{"format":1,"id":"tiny","images":{""")
                append(images.entries.joinToString(",") { (id, name) -> "\"$id\":\"$name\"" })
                append("""},"languages":{""")
                append(
                    languages.entries.joinToString(",") { (lang, files) ->
                        val audio = files.second.entries.joinToString(",") { (clip, name) -> "\"$clip\":\"$name\"" }
                        val timing = timed[lang]?.let { ""","timings":"$it"""" }.orEmpty()
                        """"$lang":{"story":"${files.first}","audio":{$audio}$timing}"""
                    },
                )
                append("""},"files":{""")
                append(files.keys.joinToString(",") { "\"$it\":${info(it)}" })
                append("}}")
            }.encodeToByteArray()
        val manifestName = put("manifest", "json", manifest)
        host["stories/catalog.json"] =
            """{ "format": 1, "stories": [ {
                "id": "tiny", "access": "free", "ageBand": "3-5",
                "title": { "en": "Tiny", "es": "Chiquito" },
                "languages": { "en": { "bytes": 100, "narration": false }, "es": { "bytes": 200, "narration": true } },
                "cover": { "file": "${images.getValue("cover")}", ${info(images.getValue("cover")).removeSurrounding("{", "}")} },
                "manifest": { "file": "$manifestName", ${info(manifestName).removeSurrounding("{", "}")} }
            } ] }""".encodeToByteArray()
        return manifestName
    }

    /**
     * Publishes the clips every story shares like `story package` does (in stories/app/, listed in the catalog's
     * `narration`), on an empty shelf: language → clip id → its sound.
     */
    private fun publishShared(clips: Map<String, Map<String, String>>) {
        val narration =
            clips.entries.joinToString(",") { (lang, byId) ->
                val entries =
                    byId.entries.joinToString(",") { (id, text) ->
                        val data = text.encodeToByteArray()
                        val sha = data.toByteString().sha256().hex()
                        val name = "$lang-$id-${sha.take(10)}.m4a"
                        host["stories/app/$name"] = data
                        """"$id":{"file":"$name","bytes":${data.size},"sha256":"$sha"}"""
                    }
                """"$lang":{$entries}"""
            }
        host["stories/catalog.json"] = """{ "format": 1, "narration": {$narration}, "stories": [] }""".encodeToByteArray()
    }

    private suspend fun HostedStoryLibrary.downloaded(storyId: String): Installed =
        state.first { storyId in it.installed && storyId !in it.downloads }.installed.getValue(storyId)

    private fun storyFiles() = disk.list(dir / "stories" / "tiny").map { it.name }.toSet()

    @Test
    fun showsTheShelfInsideTheAppUntilItHasBeenOnline() =
        runTest {
            online = false
            val library = library()
            library.refresh()
            val shelf = library.state.value
            assertTrue(shelf.ready)
            assertEquals(listOf("Tiny"), shelf.stories.map { it.title("en") })
            assertFalse(shelf.stories.single().downloadable)

            online = true
            publish()
            library.refresh()
            val entry =
                library.state.value.stories
                    .single()
            assertTrue(entry.downloadable)
            assertEquals("Chiquito", entry.title("es"))
            assertEquals(
                (dir / "stories" / "tiny" / entry.cover!!.file).toString(),
                library.state.value.covers["tiny"],
                "the cover is downloaded for the shelf",
            )
            assertEquals(emptyMap(), library.state.value.installed, "nothing else is downloaded")
        }

    @Test
    fun downloadsEveryLanguageAndReadsItOfflineLater() =
        runTest {
            val manifest = publish()
            val library = library()
            library.refresh()
            library.download("tiny", "es")
            assertEquals(Installed(manifest, listOf("en", "es"), "es"), library.downloaded("tiny"))
            assertTrue(requests.any { it.contains("story-en") }, "English too, to switch to it offline")

            val bundle = library.load("tiny")
            assertEquals(listOf("en", "es"), bundle.story.languages)
            assertEquals("es", bundle.reading)
            assertContentEquals("page one".encodeToByteArray(), disk.read(bundle.image("p1")!!.toPath()) { readByteArray() })
            assertEquals(setOf("title", "p1"), bundle.audio.getValue("es").keys)

            // The next launch, offline: the story is still there.
            online = false
            val later = library()
            later.refresh()
            assertEquals(listOf("Chiquito"), listOf(later.load("tiny").story.title["es"]))
            assertTrue(
                later.state.value.stories
                    .single()
                    .downloadable,
                "the last catalog was kept",
            )
        }

    @Test
    fun aNarratedLanguageComesWithWhenEachWordIsSaid() =
        runTest {
            publish(timings = mapOf("es" to """{"format":1,"clips":{"p1":[[300,800],[800,1350],[1400,2000]]}}"""))
            val library = library()
            library.refresh()
            library.download("tiny", "es")
            library.downloaded("tiny")
            assertTrue(requests.any { it.startsWith("stories/tiny/timings-es-") })
            val bundle = library.load("tiny")
            assertEquals(listOf(WordTime(300, 800), WordTime(800, 1_350), WordTime(1_400, 2_000)), bundle.timings("p1"))
            assertNull(bundle.timings("f1"), "a page without its clip has no timings")
            assertTrue(bundle.clip("p1")!!.endsWith(".m4a"))
            assertNull(bundle.clip("f1"))
        }

    @Test
    fun aDownloadShowsHowFarItIs() =
        runTest {
            publish()
            val library = library()
            library.refresh()
            library.download("tiny", "es")
            assertTrue(library.state.value.downloads["tiny"] is Download.Running, "running at once, before any file")
            library.downloaded("tiny")
            assertNull(library.state.value.downloads["tiny"])
        }

    @Test
    fun aFileThatDoesntMatchItsChecksumFailsTheDownload() =
        runTest {
            publish()
            val page = host.keys.first { it.startsWith("stories/tiny/p1-") }
            host[page] = "something else".encodeToByteArray()
            val library = library()
            library.refresh()
            library.download("tiny", "en")
            val failed = library.state.first { it.downloads["tiny"] is Download.Failed }
            assertEquals(Download.Failed("en"), failed.downloads["tiny"])
            assertEquals(emptyMap(), failed.installed)
        }

    @Test
    fun aDroppedConnectionIsTriedAgain() =
        runTest {
            publish()
            dropOnce += host.keys.filter { it.startsWith("stories/tiny/p1-") || it.startsWith("stories/tiny/story-en-") }
            val library = library()
            library.refresh()
            library.download("tiny", "en")
            assertEquals(listOf("en", "es"), library.downloaded("tiny").languages)
            assertTrue(dropOnce.isEmpty())
        }

    @Test
    fun offlineTheDownloadFailsAndTryingAgainOnlineWorks() =
        runTest {
            publish()
            online = false
            val library = library()
            library.refresh()
            library.download("tiny", "en")
            library.state.first { it.downloads["tiny"] is Download.Failed }
            online = true
            library.download("tiny", "en")
            assertEquals(listOf("en", "es"), library.downloaded("tiny").languages)
        }

    @Test
    fun switchingLanguageOnlySwitches() =
        runTest {
            publish()
            val library = library()
            library.refresh()
            library.download("tiny", "en")
            library.downloaded("tiny")

            requests.clear()
            library.download("tiny", "es")
            assertNull(library.state.value.downloads["tiny"], "no download to show")
            library.state.first { it.installed["tiny"]?.reading == "es" }
            assertEquals("es", library.load("tiny").reading)
            assertEquals(emptyList(), requests, "nothing to download")

            online = false
            library.download("tiny", "en")
            library.state.first { it.installed["tiny"]?.reading == "en" }
            assertNull(library.state.value.downloads["tiny"], "offline too")
        }

    @Test
    fun aStoryDownloadedInOneLanguageGetsTheOthersAtTheNextLaunch() =
        runTest {
            val manifest = publish()
            val first = library()
            first.refresh()
            first.download("tiny", "es")
            first.downloaded("tiny")
            // As an older version of the app left it: Spanish only.
            disk.delete(dir / "stories" / "tiny" / storyFiles().first { it.startsWith("story-en-") })
            disk.write(dir / "installed.json") {
                writeUtf8("""{"tiny":{"manifest":"$manifest","languages":["es"],"reading":"es"}}""")
            }

            requests.clear()
            val next = library()
            next.refresh()
            assertEquals(Installed(manifest, listOf("en", "es"), "es"), next.downloaded("tiny"))
            assertEquals(
                listOf("story-en"),
                requests.filter { it.startsWith("stories/tiny/") }.map { it.substringAfterLast('/').substringBeforeLast('-') },
                "only what's missing",
            )
        }

    @Test
    fun aChangedStoryUpdatesAtTheNextLaunchWithOnlyTheFilesThatChanged() =
        runTest {
            publish()
            val first = library()
            first.refresh()
            first.download("tiny", "es")
            first.downloaded("tiny")
            val oldPage = storyFiles().first { it.startsWith("p1-") }

            // A new picture and a re-recorded clip are published.
            val manifest =
                publish(
                    pictures = mapOf("cover" to "a cover", "p1" to "a better page one"),
                    clips =
                        mapOf("es" to mapOf("title" to "título", "p1" to "otra toma")),
                )
            requests.clear()
            val next = library()
            next.refresh()
            assertEquals(manifest, next.downloaded("tiny").manifest)
            val fetched =
                requests
                    .filter {
                        it.startsWith(
                            "stories/tiny/",
                        )
                    }.map { it.substringAfterLast('/').substringBeforeLast('-') }
                    .sorted()
            assertEquals(listOf("es-p1", "manifest", "p1"), fetched)
            val bundle = next.load("tiny")
            assertContentEquals("a better page one".encodeToByteArray(), disk.read(bundle.image("p1")!!.toPath()) { readByteArray() })
            assertTrue(oldPage in storyFiles(), "the old version stays until the next launch, in case it's being read")

            library().refresh()
            assertFalse(oldPage in storyFiles(), "then it's deleted")
        }

    @Test
    fun aStoryOffTheShelfLeavesTheDevice() =
        runTest {
            publish()
            val library = library()
            library.refresh()
            library.download("tiny", "en")
            library.downloaded("tiny")
            host["stories/catalog.json"] = """{ "format": 1, "stories": [] }""".encodeToByteArray()
            library().refresh()
            assertFalse(disk.exists(dir / "stories" / "tiny") && storyFiles().isNotEmpty())
        }

    @Test
    fun deletingTheDownloadsKeepsTheShelfAndItsCovers() =
        runTest {
            publish()
            val library = library()
            library.refresh()
            library.download("tiny", "es")
            library.downloaded("tiny")
            assertTrue(library.bytesOnDevice() > 0)
            library.deleteAll()
            assertEquals(emptyMap(), library.state.value.installed)
            assertEquals(0L, library.bytesOnDevice())
            assertEquals(
                setOf(
                    library.state.value.stories
                        .single()
                        .cover!!
                        .file,
                ),
                storyFiles(),
            )
            assertTrue("tiny" in library.state.value.covers)
        }

    @Test
    fun theClipsEveryStorySharesComeWithTheCatalog() =
        runTest {
            publishShared(mapOf("es" to mapOf("the-end" to "fin", "more-endings" to "más finales"), "en" to mapOf("the-end" to "the end")))
            val library = library()
            library.refresh()
            val narration = library.state.value.narration
            assertEquals(setOf("the-end", "more-endings"), narration.getValue("es").keys)
            assertContentEquals(
                "fin".encodeToByteArray(),
                disk.read(narration.getValue("es").getValue("the-end").toPath()) { readByteArray() },
            )
            assertEquals(setOf("the-end"), narration.getValue("en").keys)

            // The next launch, offline: they're still there.
            online = false
            val later = library()
            later.refresh()
            assertEquals(narration, later.state.value.narration)

            // A clip made again: the new one comes, the old one goes at the launch after.
            online = true
            val old = narration.getValue("es").getValue("the-end").toPath()
            publishShared(mapOf("es" to mapOf("the-end" to "fin, otra vez")))
            val next = library()
            next.refresh()
            val now = next.state.value.narration
            assertEquals(mapOf("es" to setOf("the-end")), now.mapValues { it.value.keys })
            assertContentEquals(
                "fin, otra vez".encodeToByteArray(),
                disk.read(now.getValue("es").getValue("the-end").toPath()) { readByteArray() },
            )
            assertTrue(disk.exists(old), "the old clip stays until the next launch")
            library().refresh()
            assertFalse(disk.exists(old), "then it's deleted")
        }

    @Test
    fun aSharedClipThatCantBeFetchedIsLeftOut() =
        runTest {
            publishShared(mapOf("es" to mapOf("the-end" to "fin", "all-endings" to "todos")))
            host.remove(host.keys.first { it.contains("all-endings") })
            val library = library()
            library.refresh()
            assertEquals(
                setOf("the-end"),
                library.state.value.narration["es"]
                    ?.keys,
            )
        }
}
