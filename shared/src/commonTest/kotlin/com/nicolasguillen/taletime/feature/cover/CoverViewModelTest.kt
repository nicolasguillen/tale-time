package com.nicolasguillen.taletime.feature.cover

import com.nicolasguillen.taletime.domain.Download
import com.nicolasguillen.taletime.domain.PurchaseResult
import com.nicolasguillen.taletime.domain.StoryProgress
import com.nicolasguillen.taletime.domain.UnavailablePurchases
import com.nicolasguillen.taletime.fakes.FakeNarrationPlayer
import com.nicolasguillen.taletime.fakes.FakeProgress
import com.nicolasguillen.taletime.fakes.FakePurchases
import com.nicolasguillen.taletime.fakes.clipPath
import com.nicolasguillen.taletime.fakes.tinyLibrary
import com.nicolasguillen.taletime.feature.shelf.Price
import com.nicolasguillen.taletime.story.Step
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
class CoverViewModelTest {
    private val progress = FakeProgress()
    private val main = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aFreeStoryIsOpenAndShowsContinueOnceStarted() {
        val vm = CoverViewModel("tiny", tinyLibrary(), UnavailablePurchases(), progress, FakeNarrationPlayer())
        val book = vm.state.value.book!!
        assertTrue(book.open)
        assertFalse(book.inProgress)
        progress.edit("tiny") { StoryProgress(path = listOf(Step("p1"), Step("f1"))) }
        assertTrue(
            vm.state.value.book!!
                .inProgress,
        )
    }

    @Test
    fun aPaidStoryUnlocksWhenAGrownUpBuysIt() {
        val purchases = FakePurchases(prices = mapOf("story.tiny" to "CHF 3.00"))
        val vm = CoverViewModel("tiny", tinyLibrary(access = "paid"), purchases, progress, FakeNarrationPlayer())
        assertFalse(
            vm.state.value.book!!
                .open,
        )

        vm.onEvent(CoverEvent.OpenUnlock)
        assertEquals(
            "CHF 3.00",
            vm.state.value.unlock
                ?.price,
        )
        vm.onEvent(CoverEvent.Buy)
        assertTrue(
            vm.state.value.book!!
                .open,
        )
        assertNull(vm.state.value.unlock, "the sheet closes once the story is open")
    }

    @Test
    fun withoutAStoreThePaidStoryStaysLockedAndSaysWhy() {
        val vm = CoverViewModel("tiny", tinyLibrary(access = "paid"), UnavailablePurchases(), progress, FakeNarrationPlayer())
        vm.onEvent(CoverEvent.OpenUnlock)
        assertNull(
            vm.state.value.unlock
                ?.price,
        )
        assertTrue(
            vm.state.value.unlock!!
                .storeUnavailable,
            "the sheet says so, in the app's language",
        )
        assertNull(
            vm.state.value.unlock!!
                .message,
        )
        assertFalse(
            vm.state.value.book!!
                .open,
        )
    }

    @Test
    fun aCancelledPurchaseKeepsTheSheetOpen() {
        val purchases = FakePurchases(prices = mapOf("story.tiny" to "$2.99"), result = PurchaseResult.Cancelled)
        val vm = CoverViewModel("tiny", tinyLibrary(access = "paid"), purchases, progress, FakeNarrationPlayer())
        vm.onEvent(CoverEvent.OpenUnlock)
        vm.onEvent(CoverEvent.Buy)
        assertFalse(
            vm.state.value.book!!
                .open,
        )
        assertEquals(
            false,
            vm.state.value.unlock
                ?.busy,
        )
        vm.onEvent(CoverEvent.Restore)
        assertEquals(1, purchases.restored)
        vm.onEvent(CoverEvent.CloseUnlock)
        assertNull(vm.state.value.unlock)
    }

    @Test
    fun anUnknownStoryIsMissing() {
        val vm = CoverViewModel("nope", tinyLibrary(), UnavailablePurchases(), progress, FakeNarrationPlayer())
        assertTrue(vm.state.value.missing)
    }

    @Test
    fun aBookNotDownloadedYetDownloadsEveryLanguageAndReadsTheOnePicked() {
        val library = tinyLibrary(downloaded = emptyList())
        val vm = CoverViewModel("tiny", library, UnavailablePurchases(), progress, FakeNarrationPlayer())
        val before = vm.state.value.book!!
        assertFalse(before.downloaded)
        assertEquals(2, before.endingCount, "the catalog says how many endings, before it's downloaded")
        assertEquals(listOf("en", "es"), before.languages)
        vm.onEvent(CoverEvent.Download("es"))
        assertEquals(listOf("tiny" to "es"), library.downloaded)
        val book = vm.state.value.book!!
        assertTrue(book.downloaded)
        assertEquals(listOf("en", "es"), book.bundle!!.story.languages, "one download for every language")
        assertEquals("es", book.bundle?.reading)
        assertEquals("Chiquito", book.title("en"), "read in the language picked, whatever the app speaks")
    }

    @Test
    fun theCogReadsADownloadedBookInAnotherOfItsLanguages() {
        val library = tinyLibrary(downloaded = listOf("en", "es"))
        val vm = CoverViewModel("tiny", library, UnavailablePurchases(), progress, FakeNarrationPlayer())
        vm.onEvent(CoverEvent.Download("es"))
        assertEquals(
            "es",
            vm.state.value.book!!
                .bundle!!
                .reading,
        )
    }

    @Test
    fun theCoverShowsWhatTheBookCostsUntilItIsBought() {
        assertEquals(
            Price.Free,
            CoverViewModel("tiny", tinyLibrary(downloaded = emptyList()), UnavailablePurchases(), progress, FakeNarrationPlayer())
                .state.value.price,
        )
        val purchases = FakePurchases(prices = mapOf("story.tiny" to "CHF 3.00"))
        val paid = tinyLibrary(access = "paid", downloaded = emptyList())
        val vm = CoverViewModel("tiny", paid, purchases, progress, FakeNarrationPlayer())
        assertEquals(Price.Paid("CHF 3.00"), vm.state.value.price)
        vm.onEvent(CoverEvent.OpenUnlock)
        vm.onEvent(CoverEvent.Buy)
        assertNull(vm.state.value.price, "bought: nothing to pay")
        assertNull(
            CoverViewModel("tiny", paid, UnavailablePurchases(), progress, FakeNarrationPlayer()).state.value.price,
            "no price without the store",
        )
    }

    @Test
    fun aFailedDownloadShowsOnTheBook() {
        val library = tinyLibrary(downloaded = emptyList()).apply { failDownloads = true }
        val vm = CoverViewModel("tiny", library, UnavailablePurchases(), progress, FakeNarrationPlayer())
        vm.onEvent(CoverEvent.Download("en"))
        assertTrue(
            vm.state.value.book!!
                .download is Download.Failed,
        )
        assertFalse(
            vm.state.value.book!!
                .downloaded,
        )
    }

    @Test
    fun aLockedBookDoesntDownload() {
        val library = tinyLibrary(access = "paid", downloaded = emptyList())
        val vm = CoverViewModel("tiny", library, UnavailablePurchases(), progress, FakeNarrationPlayer())
        vm.onEvent(CoverEvent.Download("en"))
        assertEquals(emptyList(), library.downloaded)
    }

    @Test
    fun tryingAgainOfflineFetchesTheShelf() {
        val library = tinyLibrary(downloaded = emptyList())
        val vm = CoverViewModel("tiny", library, UnavailablePurchases(), progress, FakeNarrationPlayer())
        vm.onEvent(CoverEvent.Retry)
        assertEquals(1, library.refreshed)
    }

    @Test
    fun theNarratorReadsTheTitleOnceAndAgainInAnotherLanguage() {
        val player = FakeNarrationPlayer()
        val library = tinyLibrary(clips = mapOf("en" to listOf("title"), "es" to listOf("title")))
        val vm = CoverViewModel("tiny", library, UnavailablePurchases(), progress, player)
        main.scheduler.advanceUntilIdle()
        assertEquals(listOf(clipPath("en", "title")), player.played)
        player.finish()

        vm.onEvent(CoverEvent.Download("es"))
        main.scheduler.advanceUntilIdle()
        assertEquals(listOf(clipPath("en", "title"), clipPath("es", "title")), player.played)
        player.finish()

        // Picking the language it is read in already reads nothing.
        vm.onEvent(CoverEvent.Download("es"))
        main.scheduler.advanceUntilIdle()
        assertEquals(2, player.played.size)
    }

    @Test
    fun theTitleStopsWhenTheBookOpens() {
        val player = FakeNarrationPlayer()
        val vm = CoverViewModel("tiny", tinyLibrary(clips = mapOf("en" to listOf("title"))), UnavailablePurchases(), progress, player)
        main.scheduler.advanceUntilIdle()
        assertEquals(clipPath("en", "title"), player.playing)
        vm.onEvent(CoverEvent.OpenBook)
        vm.onEvent(CoverEvent.Hidden)
        assertNull(player.playing)
        // Back from the book: the title isn't read again.
        vm.onEvent(CoverEvent.Shown)
        main.scheduler.advanceUntilIdle()
        assertNull(player.playing)
        assertEquals(1, player.played.size)
    }

    @Test
    fun theTitleGoesOnAfterTheScreenTurns() {
        val player = FakeNarrationPlayer()
        val vm = CoverViewModel("tiny", tinyLibrary(clips = mapOf("en" to listOf("title"))), UnavailablePurchases(), progress, player)
        main.scheduler.advanceUntilIdle()
        // Turning the screen hides the cover for a moment: the title pauses where it is, then goes on.
        vm.onEvent(CoverEvent.Hidden)
        assertTrue(player.paused)
        assertEquals(clipPath("en", "title"), player.playing)
        vm.onEvent(CoverEvent.Shown)
        main.scheduler.advanceUntilIdle()
        assertFalse(player.paused)
        assertEquals(clipPath("en", "title"), player.playing)
        assertEquals(1, player.played.size, "the title goes on, it doesn't start over")
    }

    @Test
    fun aCoverOutOfSightReadsItsTitleOnlyOnceItIsBack() {
        val player = FakeNarrationPlayer()
        val library = tinyLibrary(downloaded = emptyList(), clips = mapOf("en" to listOf("title")))
        val vm = CoverViewModel("tiny", library, UnavailablePurchases(), progress, player)
        vm.onEvent(CoverEvent.Shown)
        vm.onEvent(CoverEvent.Hidden)
        // The download finishes while another screen (or another copy of the app's screen) is in front.
        library.download("tiny", "en")
        main.scheduler.advanceUntilIdle()
        assertNull(player.playing, "nobody hears a cover that isn't on screen")
        vm.onEvent(CoverEvent.Shown)
        assertEquals(clipPath("en", "title"), player.playing)
    }

    @Test
    fun aBookNotDownloadedOrWithoutItsTitleClipIsQuiet() {
        val player = FakeNarrationPlayer()
        CoverViewModel("tiny", tinyLibrary(downloaded = emptyList()), UnavailablePurchases(), progress, player)
        CoverViewModel("tiny", tinyLibrary(clips = mapOf("es" to listOf("title"))), UnavailablePurchases(), progress, player)
        main.scheduler.advanceUntilIdle()
        assertTrue(player.played.isEmpty())
    }
}
