package com.nicolasguillen.taletime.feature.shelf

import androidx.lifecycle.viewModelScope
import com.nicolasguillen.taletime.domain.Access
import com.nicolasguillen.taletime.domain.Book
import com.nicolasguillen.taletime.domain.ProgressRepository
import com.nicolasguillen.taletime.domain.Purchases
import com.nicolasguillen.taletime.domain.StoryBundle
import com.nicolasguillen.taletime.domain.StoryLibrary
import com.nicolasguillen.taletime.domain.shelf
import com.nicolasguillen.taletime.feature.StateViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The shelf's two tabs: the books on this device, and the rest of the catalog. */
enum class ShelfTab { MY_BOOKS, STORE }

/**
 * @property picked the tab a reader tapped, or null until they tap one.
 * @property prices product id → the price the store shows, for the paid books it knows.
 */
data class ShelfUiState(
    val loading: Boolean = true,
    val books: List<Book> = emptyList(),
    val picked: ShelfTab? = null,
    val prices: Map<String, String> = emptyMap(),
) {
    /** The downloaded books, ready to read offline. */
    val myBooks: List<Book> get() = books.filter { it.downloaded }

    /** Every other book in the catalog: free ones to download, paid ones to unlock. A download stays here until it's done. */
    val store: List<Book> get() = books.filterNot { it.downloaded }

    /**
     * The tab picked, or until one is: My Books, or the Store while My Books is empty. Null while the shelf is read,
     * so it never shows My Books first only to switch to the Store.
     */
    val tab: ShelfTab?
        get() =
            when {
                picked != null -> picked
                loading -> null
                myBooks.isEmpty() -> ShelfTab.STORE
                else -> ShelfTab.MY_BOOKS
            }

    /** The books on the shelf of [tab]. */
    val shown: List<Book>
        get() =
            when (tab) {
                ShelfTab.MY_BOOKS -> myBooks
                ShelfTab.STORE -> store
                null -> emptyList()
            }

    /** What [book] costs (see [Book.price]). */
    fun price(book: Book): Price? = book.price(prices)
}

/**
 * What this book costs: [Price.Free], its price from the store ([prices], product id → price), or null for a book
 * that's bought or not priced yet.
 */
fun Book.price(prices: Map<String, String>): Price? =
    when {
        entry.access == Access.FREE -> {
            Price.Free
        }

        open -> {
            null
        }

        else -> {
            entry.product
                ?.let { prices[it] }
                ?.let { Price.Paid(it) }
        }
    }

sealed interface Price {
    data object Free : Price

    data class Paid(
        val price: String,
    ) : Price
}

sealed interface ShelfEvent {
    data class Pick(
        val tab: ShelfTab,
    ) : ShelfEvent
}

/** The bookshelf: every story in the catalog, in My Books once downloaded and in the Store until then. */
class ShelfViewModel(
    library: StoryLibrary,
    purchases: Purchases,
    progress: ProgressRepository,
) : StateViewModel<ShelfUiState, ShelfEvent>(ShelfUiState()) {
    init {
        viewModelScope.launch {
            val ready = library.state.filter { it.ready }
            combine(ready, library.downloadedBundles(), purchases.owned, progress.all()) { lib, bundles, owned, saved ->
                shelf(lib, bundles, owned, saved)
            }.collect { books ->
                setState {
                    // Once the last book is deleted, the shelf goes back to opening on the Store.
                    val emptied = myBooks.isNotEmpty() && books.none { it.downloaded }
                    copy(loading = false, books = books, picked = picked.takeUnless { emptied })
                }
            }
        }
        viewModelScope.launch {
            library.state
                .filter { it.ready }
                .map { lib -> lib.stories.mapNotNull { it.product } }
                .distinctUntilChanged()
                .collect { ids ->
                    val prices = if (ids.isEmpty()) emptyMap() else purchases.products(ids).associate { it.id to it.price }
                    setState { copy(prices = prices) }
                }
        }
    }

    override fun onEvent(event: ShelfEvent) {
        when (event) {
            is ShelfEvent.Pick -> setState { copy(picked = event.tab) }
        }
    }
}

/**
 * The downloaded stories, read again whenever what's downloaded changes. A story that can't be read is left out, so
 * it shows as not downloaded rather than breaking the shelf.
 */
fun StoryLibrary.downloadedBundles(): Flow<Map<String, StoryBundle>> =
    state
        .filter { it.ready }
        .map { it.installed }
        .distinctUntilChanged()
        .map { installed -> installed.keys.mapNotNull { id -> runCatching { id to load(id) }.getOrNull() }.toMap() }
