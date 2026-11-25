package com.nicolasguillen.taletime.feature.shelf

import com.nicolasguillen.taletime.domain.Book
import com.nicolasguillen.taletime.domain.Download
import com.nicolasguillen.taletime.domain.StoryBundle
import com.nicolasguillen.taletime.fakes.TINY
import com.nicolasguillen.taletime.fakes.tinyEntry
import com.nicolasguillen.taletime.story.StoryParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShelfUiStateTest {
    private val bundle = StoryBundle(StoryParser.parse(TINY), emptyMap(), reading = "en")

    private fun book(
        id: String,
        downloaded: Boolean = false,
        download: Download? = null,
    ) = Book(
        entry = tinyEntry().copy(id = id),
        cover = null,
        bundle = bundle.takeIf { downloaded },
        download = download,
        open = true,
        endingsFound = emptySet(),
        inProgress = false,
    )

    @Test
    fun downloadedBooksAreMineAndTheRestAreInTheStore() {
        val state =
            ShelfUiState(
                loading = false,
                books = listOf(book("a", downloaded = true), book("b"), book("c", download = Download.Running("en", 0.5f))),
            )
        assertEquals(listOf("a"), state.myBooks.map { it.id })
        // A download stays in the Store until it's done.
        assertEquals(listOf("b", "c"), state.store.map { it.id })
    }

    @Test
    fun theShelfOpensOnMyBooksOrOnTheStoreWhileMyBooksIsEmpty() {
        val mine = ShelfUiState(loading = false, books = listOf(book("a", downloaded = true), book("b")))
        assertEquals(ShelfTab.MY_BOOKS, mine.tab)
        assertEquals(listOf("a"), mine.shown.map { it.id })

        val none = ShelfUiState(loading = false, books = listOf(book("b")))
        assertEquals(ShelfTab.STORE, none.tab)
        assertEquals(listOf("b"), none.shown.map { it.id })

        // Until the shelf is read, no tab is picked, so it never shows My Books first only to switch to the Store.
        assertNull(ShelfUiState().tab)
        assertEquals(emptyList(), ShelfUiState().shown)
    }

    @Test
    fun aPickedTabStaysPicked() {
        val state = ShelfUiState(loading = false, books = listOf(book("b")), picked = ShelfTab.MY_BOOKS)
        assertEquals(ShelfTab.MY_BOOKS, state.tab)
        assertEquals(emptyList(), state.shown)
        assertEquals(ShelfTab.STORE, state.copy(picked = ShelfTab.STORE).tab)
    }
}
