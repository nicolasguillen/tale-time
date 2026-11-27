package com.nicolasguillen.taletime.feature.reader

import com.nicolasguillen.taletime.domain.Installed
import com.nicolasguillen.taletime.domain.Language
import com.nicolasguillen.taletime.domain.Settings
import com.nicolasguillen.taletime.domain.StoryProgress
import com.nicolasguillen.taletime.domain.TextSize
import com.nicolasguillen.taletime.fakes.FakeNarrationPlayer
import com.nicolasguillen.taletime.fakes.FakeProgress
import com.nicolasguillen.taletime.fakes.FakeSettings
import com.nicolasguillen.taletime.fakes.FakeStoryLibrary
import com.nicolasguillen.taletime.fakes.TINY
import com.nicolasguillen.taletime.fakes.clipPath
import com.nicolasguillen.taletime.fakes.tinyEntry
import com.nicolasguillen.taletime.fakes.tinyLibrary
import com.nicolasguillen.taletime.story.EndingKind
import com.nicolasguillen.taletime.story.Pacing
import com.nicolasguillen.taletime.story.Step
import com.nicolasguillen.taletime.story.WordTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private val progress = FakeProgress()
    private val settings = FakeSettings()
    private val main = UnconfinedTestDispatcher()
    private val player = FakeNarrationPlayer()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    /**
     * The tiny story read aloud in English: its first page with its timings, the fork's question and labels, the page
     * behind the blue door and the silly ending's title. The fork's own text has no clip yet.
     */
    private fun narrated(timings: Boolean = true) =
        ReaderViewModel(
            "tiny",
            true,
            tinyLibrary(
                clips = mapOf("en" to listOf("title", "p1", "f1-q", "f1-a", "f1-b", "p2", "e1-end")),
                timings =
                    if (timings) {
                        mapOf("en" to mapOf("p1" to listOf(WordTime(310, 640), WordTime(650, 890), WordTime(900, 1_300))))
                    } else {
                        emptyMap()
                    },
            ),
            progress,
            settings,
            player,
        )

    /** The breath before a clip passes. */
    private fun breathe() = main.scheduler.advanceUntilIdle()

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun reader(restart: Boolean = true) = ReaderViewModel("tiny", restart, tinyLibrary(), progress, settings, FakeNarrationPlayer())

    private val ReaderViewModel.page get() = state.value.page!!

    @Test
    fun opensOnTheFirstPageAndTurnsPages() {
        val vm = reader()
        assertEquals("p1", vm.page.nodeId)
        assertEquals("Pip woke up.", vm.page.text)
        assertEquals("/library/stories/tiny/p1-0123456789.webp", vm.page.image)
        assertEquals("Tiny", vm.state.value.title)
        vm.onEvent(ReaderEvent.Next)
        assertEquals("f1", vm.page.nodeId)
        assertNull(vm.page.image, "not delivered: the placeholder shows")
        assertEquals(listOf(Step("p1"), Step("f1")), progress.state.value["tiny"]?.path)
    }

    @Test
    fun eachPageComesWordByWordAtTheNarratorsPace() {
        val page = reader().page
        assertEquals(listOf("Pip", "woke", "up."), page.words.map { page.text.substring(it.start, it.end) })
        // The tiny story is for 3–5: 115 words a minute.
        assertEquals(listOf(0L, 521, 1_042), page.words.map { it.atMillis })
    }

    @Test
    fun aForkShowsItsTextThenItsCardsAndOneTapPicks() {
        val vm = reader()
        vm.onEvent(ReaderEvent.Next)
        assertEquals(Phase.TEXT, vm.state.value.phase)
        assertEquals("The red door or the blue door?", vm.page.question)
        assertEquals(listOf("Red door", "Blue door"), vm.page.choices.map { it.label })
        vm.onEvent(ReaderEvent.TapChoice(0)) // ignored until the cards are out
        assertEquals("f1", vm.page.nodeId)

        vm.onEvent(ReaderEvent.Next)
        assertEquals(Phase.CHOICE, vm.state.value.phase)
        vm.onEvent(ReaderEvent.TapChoice(1))
        assertEquals("p2", vm.page.nodeId)
        assertEquals(Phase.TEXT, vm.state.value.phase)
        vm.onEvent(ReaderEvent.TapChoice(0)) // a second tap on the way out does nothing
        assertEquals("p2", vm.page.nodeId)
    }

    @Test
    fun skippingTheReadingShowsEveryWordAtOnceAndReadsNothingAloud() {
        val vm =
            ReaderViewModel(
                "tiny",
                true,
                tinyLibrary(clips = mapOf("en" to listOf("title", "p1", "f1-q", "f1-a", "f1-b", "p2", "e1-end"))),
                progress,
                settings,
                player,
                skipReading = true,
            )
        breathe()
        assertEquals(List(3) { SKIPPED_WORD_MILLIS }, vm.page.words.map { it.atMillis })
        assertFalse(vm.page.narrated)
        assertNull(vm.page.spokenUntilMillis)
        assertNull(vm.narrationMillis("p1"), "the page keeps its own clock")
        val upcoming = vm.state.value.upcoming!!
        assertTrue(upcoming.words.all { it.atMillis == SKIPPED_WORD_MILLIS })
        vm.onEvent(ReaderEvent.Next)
        vm.onEvent(ReaderEvent.Next)
        breathe()
        assertEquals(Phase.CHOICE, vm.state.value.phase)
        assertNull(vm.state.value.readingChoice)
        assertEquals(emptyList(), player.played)
    }

    @Test
    fun theNextPageIsLaidOutBeneathAPlainPageOnly() {
        val vm = reader()
        val upcoming = vm.state.value.upcoming!!
        assertEquals("f1", upcoming.nodeId)
        assertEquals("Pip saw two doors.", upcoming.text)
        assertEquals(listOf("Pip", "saw", "two", "doors."), upcoming.words.map { upcoming.text.substring(it.start, it.end) })
        assertTrue(vm.page.plain)

        vm.onEvent(ReaderEvent.Next)
        assertEquals(upcoming, vm.page, "the page beneath is the page now, as it was laid out")
        assertFalse(vm.page.plain)
        assertNull(vm.state.value.upcoming, "a fork's cards rise on the same page")
        vm.onEvent(ReaderEvent.Next)
        vm.onEvent(ReaderEvent.TapChoice(1))
        assertEquals(
            "e2",
            vm.state.value.upcoming!!
                .nodeId,
        )
        vm.onEvent(ReaderEvent.Landed)
        vm.onEvent(ReaderEvent.Next)
        assertEquals("e2", vm.page.nodeId)
        assertFalse(vm.page.plain)
        assertNull(vm.state.value.upcoming, "nothing beneath an ending")
    }

    @Test
    fun aChoiceIsSavedAtOnceButNothingIsReadUntilTheNewPageHasLanded() {
        val vm = narrated()
        vm.onEvent(ReaderEvent.Next)
        vm.onEvent(ReaderEvent.Next)
        breathe()
        assertEquals(clipPath("en", "f1-q"), player.playing)
        assertFalse(vm.state.value.landing)

        vm.onEvent(ReaderEvent.TapChoice(1))
        assertEquals("p2", vm.page.nodeId)
        assertTrue(vm.state.value.landing)
        assertEquals(listOf(Step("p1"), Step("f1", 1), Step("p2")), progress.state.value["tiny"]?.path, "saved at once")
        breathe()
        assertNull(player.playing, "the question stops, and the new page waits")
        assertEquals(0L, vm.narrationMillis("p2"), "its words wait too")
        assertFalse(clipPath("en", "p2") in player.played)

        vm.onEvent(ReaderEvent.Landed)
        assertFalse(vm.state.value.landing)
        breathe()
        assertEquals(clipPath("en", "p2"), player.playing)
        vm.onEvent(ReaderEvent.Landed) // a second one changes nothing
        assertEquals(clipPath("en", "p2"), player.playing)
    }

    @Test
    fun aPageWithoutItsClipWaitsWhileItLandsThenKeepsItsOwnClock() {
        val vm = reader()
        vm.onEvent(ReaderEvent.Next)
        vm.onEvent(ReaderEvent.Next)
        vm.onEvent(ReaderEvent.TapChoice(0))
        assertEquals(0L, vm.narrationMillis("e1"))
        vm.onEvent(ReaderEvent.Landed)
        assertNull(vm.narrationMillis("e1"))
    }

    @Test
    fun anEndingIsFoundOnceAndTryAnotherPathGoesBackToTheFork() {
        val vm = reader()
        vm.onEvent(ReaderEvent.Next)
        vm.onEvent(ReaderEvent.Next)
        vm.onEvent(ReaderEvent.TapChoice(0))
        assertEquals("e1", vm.page.nodeId)
        assertEquals(EndingKind.SILLY, vm.page.endingKind)
        assertTrue(vm.state.value.newEnding)
        assertEquals(1 to 2, vm.state.value.endingsFound to vm.state.value.endingsTotal)
        assertEquals(setOf("e1"), progress.state.value["tiny"]?.endingsFound)

        vm.onEvent(ReaderEvent.Next) // nothing closes while the book is still flipping to the ending
        assertEquals(Phase.TEXT, vm.state.value.phase)
        vm.onEvent(ReaderEvent.Landed)
        vm.onEvent(ReaderEvent.Next)
        assertEquals(Phase.END, vm.state.value.phase)
        vm.onEvent(ReaderEvent.TryAnotherPath)
        // The book flips back to the fork, straight before this ending, and reads it again.
        assertEquals("f1", vm.page.nodeId)
        assertEquals(Phase.TEXT, vm.state.value.phase)
        assertTrue(vm.state.value.landing)
        assertEquals(0, vm.state.value.rewinding)
        assertFalse(vm.page.choices.any { it.toward }, "no way to mark: any other path will do")
        vm.onEvent(ReaderEvent.Landed)
        assertNull(vm.state.value.rewinding)
        vm.onEvent(ReaderEvent.Next)
        assertEquals(Phase.CHOICE, vm.state.value.phase)

        // The same ending again isn't new.
        vm.onEvent(ReaderEvent.TapChoice(0))
        assertFalse(vm.state.value.newEnding)
        assertEquals(1, vm.state.value.endingsFound)
    }

    @Test
    fun continuesASavedPathUnlessAskedToStartOver() {
        progress.edit("tiny") { StoryProgress(path = listOf(Step("p1"), Step("f1", 1), Step("p2"))) }
        assertEquals("p2", reader(restart = false).page.nodeId)
        assertEquals("p1", reader(restart = true).page.nodeId)
    }

    @Test
    fun aFinishedOrOutdatedPathStartsAgain() {
        progress.edit("tiny") { StoryProgress(path = listOf(Step("p1"), Step("f1", 0), Step("e1"))) }
        assertEquals("p1", reader(restart = false).page.nodeId)
        progress.edit("tiny") { StoryProgress(path = listOf(Step("p1"), Step("renamed"))) }
        assertEquals("p1", reader(restart = false).page.nodeId)
    }

    @Test
    fun followsTheTextSizeSetting() =
        kotlinx.coroutines.test.runTest {
            val vm = reader()
            assertEquals(1f, vm.state.value.textScale)
            settings.update { Settings(textSize = TextSize.LARGE) }
            assertEquals(TextSize.LARGE.scale, vm.state.value.textScale)
        }

    @Test
    fun readsTheStoryInTheLanguageItWasDownloadedIn() {
        val vm = ReaderViewModel("tiny", true, tinyLibrary(downloaded = listOf("es")), progress, settings, FakeNarrationPlayer())
        assertEquals("Chiquito", vm.state.value.title)
        assertEquals("Pip se despertó.", vm.page.text)
        assertEquals(listOf("Pip", "se", "despertó."), vm.page.words.map { vm.page.text.substring(it.start, it.end) })
        vm.onEvent(ReaderEvent.Next)
        assertEquals("¿La puerta roja o la puerta azul?", vm.page.question)
        assertEquals(listOf("Puerta roja", "Puerta azul"), vm.page.choices.map { it.label })
    }

    @Test
    fun theAppsLanguageDoesntChangeTheStorysLanguage() =
        kotlinx.coroutines.test.runTest {
            val vm = reader()
            vm.onEvent(ReaderEvent.Next)
            settings.update { it.copy(language = Language.SPANISH) }
            assertEquals("Pip saw two doors.", vm.page.text)
            assertEquals("Tiny", vm.state.value.title)
        }

    @Test
    fun aStoryNotDownloadedCantBeRead() {
        val vm = ReaderViewModel("tiny", true, tinyLibrary(downloaded = emptyList()), progress, settings, FakeNarrationPlayer())
        assertTrue(vm.state.value.failed)
        assertNull(vm.state.value.page)
    }

    @Test
    fun aStoryThatCantBeReadSaysSo() {
        val broken = TINY.mapValues { it.value.replace("\"next\": \"e2\"", "\"next\": \"nowhere\"") }
        val library =
            FakeStoryLibrary(
                entries = listOf(tinyEntry()),
                stories = mapOf("tiny" to broken),
                installed = mapOf("tiny" to Installed("manifest-0123456789.json", listOf("en"), "en")),
            )
        val vm = ReaderViewModel("tiny", true, library, progress, settings, FakeNarrationPlayer())
        assertTrue(vm.state.value.failed)
        assertNull(vm.state.value.page)
    }

    @Test
    fun aNarratedPageIsReadAloudAndItsWordsComeAsTheNarratorSaysThem() {
        val vm = narrated()
        val page = vm.page
        assertTrue(page.narrated)
        assertEquals(listOf(310L, 650, 900), page.words.map { it.atMillis })
        assertEquals(1_300L, page.spokenUntilMillis)
        assertEquals(0L, vm.narrationMillis("p1"), "the words wait for the clip")
        assertTrue(player.played.isEmpty(), "a breath first, while the page fades in")

        breathe()
        assertEquals(listOf(clipPath("en", "p1")), player.played)
        player.position = 700
        assertEquals(700L, vm.narrationMillis("p1"))
        assertNull(vm.narrationMillis("f1"), "only the page being read follows the narrator")
        player.finish()
        assertEquals(Long.MAX_VALUE, vm.narrationMillis("p1"), "every word is there once the clip is over")
    }

    @Test
    fun withoutItsTimingsANarratedPageComesAtTheNarratorsPaceAfterTheClipsLead() {
        val page = narrated(timings = false).page
        val paced = Pacing.paced(page.text, Pacing.wordsPerMinute("3-5"))
        assertEquals(paced.map { it.atMillis + Pacing.CLIP_LEAD_MILLIS }, page.words.map { it.atMillis })
        assertNull(page.spokenUntilMillis)
    }

    @Test
    fun turningThePageStopsTheClipAndATextWithoutOneKeepsItsOwnClock() {
        val vm = narrated()
        breathe()
        assertEquals(clipPath("en", "p1"), player.playing)
        vm.onEvent(ReaderEvent.Next)
        breathe()
        assertNull(player.playing)
        assertEquals("f1", vm.page.nodeId)
        assertFalse(vm.page.narrated)
        assertNull(vm.narrationMillis("f1"))
        assertNull(vm.narrationMillis("p1"))
    }

    @Test
    fun aForkReadsItsQuestionThenEachLabelAsItsCardsComeOut() {
        val vm = narrated()
        vm.onEvent(ReaderEvent.Next)
        vm.onEvent(ReaderEvent.Next)
        assertEquals(Phase.CHOICE, vm.state.value.phase)
        breathe()
        assertEquals(clipPath("en", "f1-q"), player.playing)
        assertNull(vm.state.value.readingChoice)
        player.finish()
        assertEquals(clipPath("en", "f1-a"), player.playing)
        assertEquals(0, vm.state.value.readingChoice)
        player.finish()
        assertEquals(clipPath("en", "f1-b"), player.playing)
        assertEquals(1, vm.state.value.readingChoice)
        player.finish()
        assertNull(player.playing)
        assertNull(vm.state.value.readingChoice)
        // The first page's clip never started: the page was turned during its breath.
        assertEquals(listOf("f1-q", "f1-a", "f1-b").map { clipPath("en", it) }, player.played)
    }

    @Test
    fun anEndingsNameIsNeverSaidEvenWithItsClip() {
        val vm = narrated()
        vm.onEvent(ReaderEvent.Next)
        vm.onEvent(ReaderEvent.Next)
        vm.onEvent(ReaderEvent.TapChoice(0))
        assertEquals("e1", vm.page.nodeId)
        vm.onEvent(ReaderEvent.Landed)
        vm.onEvent(ReaderEvent.Next)
        assertEquals(Phase.END, vm.state.value.phase)
        breathe()
        assertEquals(Closing.DONE, vm.state.value.closing)
        assertFalse(clipPath("en", "e1-end") in player.played)
    }

    @Test
    fun theNarrationPausesWhileTheReaderIsOutOfSight() {
        val vm = narrated()
        breathe()
        vm.onEvent(ReaderEvent.Hidden)
        assertTrue(player.paused)
        vm.onEvent(ReaderEvent.Shown)
        assertFalse(player.paused)
        assertEquals(clipPath("en", "p1"), player.playing, "the same clip goes on")
    }

    @Test
    fun aReaderOutOfSightStartsNoClipUntilItIsBack() {
        val vm = narrated()
        vm.onEvent(ReaderEvent.Hidden)
        breathe()
        assertNull(player.playing)
        assertEquals(0L, vm.narrationMillis("p1"), "the words wait with the narrator")
        vm.onEvent(ReaderEvent.Shown)
        assertEquals(clipPath("en", "p1"), player.playing)
    }

    @Test
    fun aClipThatCantPlayLeavesThePageItsOwnClock() {
        player.failing += clipPath("en", "p1")
        val vm = narrated()
        breathe()
        assertNull(vm.narrationMillis("p1"))
        assertEquals("p1", vm.page.nodeId)
    }

    /** The tiny story read to its silly ending, Cake, through the red door: its text read, the closing not yet. */
    private fun ReaderViewModel.toCake(): ReaderViewModel {
        onEvent(ReaderEvent.Next)
        onEvent(ReaderEvent.Next)
        onEvent(ReaderEvent.TapChoice(0))
        onEvent(ReaderEvent.Landed)
        assertEquals("e1", page.nodeId)
        return this
    }

    @Test
    fun anEndingClosesOnePartAfterTheOtherAsTheNarratorSaysThem() {
        val library = tinyLibrary(clips = mapOf("en" to listOf("e1-end")))
        library.state.value =
            library.state.value.copy(
                narration = mapOf("en" to mapOf("the-end" to "/app/en-the-end.m4a", "more-endings" to "/app/en-more.m4a")),
            )
        val vm = ReaderViewModel("tiny", true, library, progress, settings, player).toCake()
        assertEquals(
            listOf("e1", "e2"),
            vm.state.value.endings
                .map { it.id },
        )
        assertEquals(
            listOf(true, false),
            vm.state.value.endings
                .map { it.found },
        )
        assertEquals(
            "Cake",
            vm.state.value.endings[0]
                .title,
        )

        vm.onEvent(ReaderEvent.Next)
        assertEquals(Closing.THE_END, vm.state.value.closing)
        breathe()
        assertEquals("/app/en-the-end.m4a", player.playing, "the narrator says the story is over")
        player.finish()
        assertEquals(Closing.BADGES, vm.state.value.closing)
        assertNull(player.playing, "the badges come in quietly, and the ending's name isn't said")
        breathe()
        assertEquals(Closing.OUTRO, vm.state.value.closing)
        assertEquals("/app/en-more.m4a", player.playing, "one ending is left: tap a question mark; and more stories")
        player.finish()
        assertEquals(Closing.DONE, vm.state.value.closing)
    }

    @Test
    fun withoutItsClipsAnEndingStillClosesOnePartAfterTheOther() {
        val vm = reader().toCake()
        vm.onEvent(ReaderEvent.Next)
        assertEquals(Closing.THE_END, vm.state.value.closing)
        main.scheduler.advanceTimeBy(NARRATION_BREATH_MILLIS + THE_END_MILLIS + 1)
        assertEquals(Closing.BADGES, vm.state.value.closing)
        breathe()
        assertEquals(Closing.DONE, vm.state.value.closing)
    }

    @Test
    fun onceEveryEndingIsFoundTheNarratorSaysSo() {
        progress.edit("tiny") { StoryProgress(endingsFound = setOf("e2")) }
        val library = tinyLibrary()
        library.state.value =
            library.state.value.copy(
                narration = mapOf("en" to mapOf("more-endings" to "/app/en-more.m4a", "all-endings" to "/app/en-all.m4a")),
            )
        val vm = ReaderViewModel("tiny", true, library, progress, settings, player).toCake()
        assertTrue(
            vm.state.value.endings
                .all { it.found },
        )
        vm.onEvent(ReaderEvent.Next)
        breathe()
        assertEquals("/app/en-all.m4a", player.playing)
    }

    @Test
    fun aQuestionMarkTakesTheBookBackToTheForkAndMarksTheWay() {
        val vm = reader().toCake()
        vm.onEvent(ReaderEvent.FindEnding("e2")) // not before the closing
        assertEquals("e1", vm.page.nodeId)

        vm.onEvent(ReaderEvent.Next)
        vm.onEvent(ReaderEvent.FindEnding("e1")) // this one: nowhere to go
        assertEquals("e1", vm.page.nodeId)
        vm.onEvent(ReaderEvent.FindEnding("e2"))
        assertEquals("f1", vm.page.nodeId)
        assertEquals(Phase.TEXT, vm.state.value.phase, "its page is read again first")
        assertTrue(vm.state.value.landing)
        assertEquals(0, vm.state.value.rewinding)
        assertEquals(listOf(Step("p1"), Step("f1")), progress.state.value["tiny"]?.path)
        assertEquals(listOf(false, true), vm.page.choices.map { it.toward }, "the blue door leads to Flowers")

        vm.onEvent(ReaderEvent.FindEnding("e2")) // a second tap on the way back does nothing
        assertEquals(Phase.TEXT, vm.state.value.phase)
        vm.onEvent(ReaderEvent.Landed)
        vm.onEvent(ReaderEvent.Next)
        assertEquals(Phase.CHOICE, vm.state.value.phase)
        vm.onEvent(ReaderEvent.TapChoice(1))
        vm.onEvent(ReaderEvent.Landed)
        vm.onEvent(ReaderEvent.Next)
        assertEquals("e2", vm.page.nodeId)
        assertTrue(vm.state.value.newEnding)
        assertEquals(2, vm.state.value.endingsFound)
    }

    @Test
    fun pickingTheOtherWayDropsTheMarks() {
        val vm = reader().toCake()
        vm.onEvent(ReaderEvent.Next)
        vm.onEvent(ReaderEvent.FindEnding("e2"))
        vm.onEvent(ReaderEvent.Landed)
        vm.onEvent(ReaderEvent.Next)
        vm.onEvent(ReaderEvent.TapChoice(0))
        vm.onEvent(ReaderEvent.Landed)
        assertEquals("e1", vm.page.nodeId)
        vm.onEvent(ReaderEvent.Next)
        vm.onEvent(ReaderEvent.TryAnotherPath)
        assertFalse(vm.page.choices.any { it.toward }, "Try another path looks for nothing")
    }

    @Test
    fun anAgentCanOpenTheReaderOnAnEnding() {
        val vm = ReaderViewModel("tiny", true, tinyLibrary(), progress, settings, FakeNarrationPlayer(), at = "e2")
        assertEquals("e2", vm.page.nodeId)
        assertTrue(vm.state.value.newEnding)
        assertEquals(listOf(Step("p1"), Step("f1", 1), Step("p2"), Step("e2")), progress.state.value["tiny"]?.path)
        assertEquals("p1", ReaderViewModel("tiny", true, tinyLibrary(), progress, settings, FakeNarrationPlayer(), at = "gone").page.nodeId)
    }
}
