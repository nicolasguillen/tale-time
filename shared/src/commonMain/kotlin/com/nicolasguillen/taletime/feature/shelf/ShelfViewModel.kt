package com.nicolasguillen.taletime.feature.shelf

import androidx.lifecycle.viewModelScope
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

data class ShelfUiState(
    val loading: Boolean = true,
    val books: List<Book> = emptyList(),
)

/** The bookshelf: every story in the catalog, downloaded or not, open or locked, with the endings found so far. */
class ShelfViewModel(
    library: StoryLibrary,
    purchases: Purchases,
    progress: ProgressRepository,
) : StateViewModel<ShelfUiState, Nothing>(ShelfUiState()) {
    init {
        viewModelScope.launch {
            val ready = library.state.filter { it.ready }
            combine(ready, library.downloadedBundles(), purchases.owned, progress.all()) { lib, bundles, owned, saved ->
                shelf(lib, bundles, owned, saved)
            }.collect { books -> setState { copy(loading = false, books = books) } }
        }
    }

    override fun onEvent(event: Nothing) = Unit
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
