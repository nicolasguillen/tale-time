package com.nicolasguillen.taletime.feature.cover

import androidx.lifecycle.viewModelScope
import com.nicolasguillen.taletime.domain.Book
import com.nicolasguillen.taletime.domain.ProgressRepository
import com.nicolasguillen.taletime.domain.PurchaseResult
import com.nicolasguillen.taletime.domain.Purchases
import com.nicolasguillen.taletime.domain.StoryLibrary
import com.nicolasguillen.taletime.domain.shelf
import com.nicolasguillen.taletime.feature.StateViewModel
import com.nicolasguillen.taletime.feature.reader.NARRATION_BREATH_MILLIS
import com.nicolasguillen.taletime.feature.shelf.downloadedBundles
import com.nicolasguillen.taletime.platform.NarrationPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * The unlock sheet for a paid story, shown once a grown-up passed the gate.
 *
 * @property price as the store shows it, or null while it's loading or when the store has no price.
 * @property storeUnavailable the store has no price for it: the sheet says the store isn't available, in the app's
 *   language.
 * @property message what the store said (in the device's language), when a purchase didn't go through.
 */
data class UnlockUiState(
    val price: String? = null,
    val busy: Boolean = false,
    val storeUnavailable: Boolean = false,
    val message: String? = null,
)

data class CoverUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val book: Book? = null,
    val unlock: UnlockUiState? = null,
)

sealed interface CoverEvent {
    /** Download the story in this language, or read it in this language if it's downloaded already. */
    data class Download(
        val language: String,
    ) : CoverEvent

    /** Offline at the first launch: try to fetch the shelf again. */
    data object Retry : CoverEvent

    /** The gate was passed: show the price and the buy button. */
    data object OpenUnlock : CoverEvent

    data object Buy : CoverEvent

    data object Restore : CoverEvent

    data object CloseUnlock : CoverEvent

    /**
     * The cover went out of sight (the app went to the background, or the book opened): the title stops, and a title
     * due meanwhile (a download finishing) waits until the cover is back.
     */
    data object Hidden : CoverEvent

    /** In sight again. */
    data object Shown : CoverEvent
}

/**
 * A book's cover: read or continue it once it's downloaded; before that, the languages it can be downloaded in, and how
 * far a download is. A paid book asks for a grown-up first. Once it's downloaded, the narrator reads its title, if
 * that clip is delivered (plan.md §3.1): when the cover opens, and again when the book switches to another language.
 */
class CoverViewModel(
    private val storyId: String,
    private val library: StoryLibrary,
    private val purchases: Purchases,
    progress: ProgressRepository,
    private val player: NarrationPlayer,
) : StateViewModel<CoverUiState, CoverEvent>(CoverUiState()) {
    private var title: Job? = null

    /** The language the title was last read in. */
    private var titleReadIn: String? = null

    init {
        viewModelScope.launch {
            val ready = library.state.filter { it.ready }
            combine(ready, library.downloadedBundles(), purchases.owned, progress.all()) { lib, bundles, owned, saved ->
                shelf(lib, bundles, owned, saved).firstOrNull { it.id == storyId }
            }.collect { book ->
                if (book == null) {
                    setState { copy(loading = false, missing = true, book = null) }
                } else {
                    setState { copy(loading = false, missing = false, book = book, unlock = unlock.takeIf { !book.open }) }
                    readTitle(book)
                }
            }
        }
    }

    private fun readTitle(book: Book) {
        val bundle = book.bundle ?: return
        if (bundle.reading == titleReadIn) return
        titleReadIn = bundle.reading
        val clip = bundle.clip("title") ?: return
        title?.cancel()
        title =
            viewModelScope.launch {
                delay(NARRATION_BREATH_MILLIS)
                try {
                    player.play(clip)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The title is on the cover anyway.
                }
            }
    }

    override fun onCleared() {
        title?.cancel()
        player.release()
    }

    override fun onEvent(event: CoverEvent) {
        val product = currentState.book?.entry?.product
        when (event) {
            is CoverEvent.Download -> {
                if (currentState.book?.open == true) library.download(storyId, event.language)
            }

            CoverEvent.Retry -> {
                viewModelScope.launch { library.refresh() }
            }

            CoverEvent.OpenUnlock -> {
                if (product == null) return
                setState { copy(unlock = UnlockUiState()) }
                viewModelScope.launch {
                    val price = purchases.products(listOf(product)).firstOrNull()?.price
                    setState { copy(unlock = unlock?.copy(price = price, storeUnavailable = price == null)) }
                }
            }

            CoverEvent.Buy -> {
                if (product == null) return
                setState { copy(unlock = unlock?.copy(busy = true, message = null, storeUnavailable = false)) }
                viewModelScope.launch {
                    val result = purchases.buy(product)
                    val unavailable = result as? PurchaseResult.Unavailable
                    setState {
                        copy(
                            unlock =
                                unlock?.copy(
                                    busy = false,
                                    message = unavailable?.reason,
                                    storeUnavailable = unavailable != null && unavailable.reason == null,
                                ),
                        )
                    }
                }
            }

            CoverEvent.Restore -> {
                setState { copy(unlock = unlock?.copy(busy = true, message = null, storeUnavailable = false)) }
                viewModelScope.launch {
                    purchases.restore()
                    setState { copy(unlock = unlock?.copy(busy = false)) }
                }
            }

            CoverEvent.CloseUnlock -> {
                setState { copy(unlock = null) }
            }

            CoverEvent.Hidden -> {
                title?.cancel()
                player.pause()
            }

            CoverEvent.Shown -> {
                player.resume()
            }
        }
    }
}
