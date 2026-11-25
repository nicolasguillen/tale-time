package com.nicolasguillen.taletime.feature.parents

import androidx.lifecycle.viewModelScope
import com.nicolasguillen.taletime.domain.Language
import com.nicolasguillen.taletime.domain.ProgressRepository
import com.nicolasguillen.taletime.domain.SettingsRepository
import com.nicolasguillen.taletime.domain.StoryLibrary
import com.nicolasguillen.taletime.domain.TextSize
import com.nicolasguillen.taletime.feature.StateViewModel
import com.nicolasguillen.taletime.platform.BuildInfo
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class ParentsUiState(
    val language: Language = Language.DEFAULT,
    val textSize: TextSize = TextSize.MEDIUM,
    val version: String = "",
    val confirmingReset: Boolean = false,
    val resetDone: Boolean = false,
    /** The space the downloaded stories take. */
    val downloadedBytes: Long = 0,
    val confirmingDelete: Boolean = false,
    val deleteDone: Boolean = false,
)

sealed interface ParentsEvent {
    /** The app and its stories speak this language from now on. */
    data class SetLanguage(
        val language: Language,
    ) : ParentsEvent

    data class SetTextSize(
        val size: TextSize,
    ) : ParentsEvent

    data object AskReset : ParentsEvent

    data object ConfirmReset : ParentsEvent

    data object CancelReset : ParentsEvent

    data object AskDelete : ParentsEvent

    data object ConfirmDelete : ParentsEvent

    data object CancelDelete : ParentsEvent
}

/**
 * The parents area (behind the gate): language, reading settings, forgetting progress, deleting the downloaded stories,
 * and what Tale Time is made of.
 */
class ParentsViewModel(
    private val settings: SettingsRepository,
    private val progress: ProgressRepository,
    private val library: StoryLibrary,
    buildInfo: BuildInfo,
) : StateViewModel<ParentsUiState, ParentsEvent>(ParentsUiState(version = buildInfo.version)) {
    init {
        viewModelScope.launch {
            settings.settings.collect { s -> setState { copy(language = s.language, textSize = s.textSize) } }
        }
        viewModelScope.launch {
            library.state.map { it.installed }.distinctUntilChanged().collect {
                val bytes = library.bytesOnDevice()
                setState { copy(downloadedBytes = bytes) }
            }
        }
    }

    override fun onEvent(event: ParentsEvent) {
        when (event) {
            is ParentsEvent.SetLanguage -> {
                viewModelScope.launch { settings.update { it.copy(language = event.language) } }
            }

            is ParentsEvent.SetTextSize -> {
                viewModelScope.launch { settings.update { it.copy(textSize = event.size) } }
            }

            ParentsEvent.AskReset -> {
                setState { copy(confirmingReset = true, resetDone = false) }
            }

            ParentsEvent.CancelReset -> {
                setState { copy(confirmingReset = false) }
            }

            ParentsEvent.AskDelete -> {
                setState { copy(confirmingDelete = true, deleteDone = false) }
            }

            ParentsEvent.CancelDelete -> {
                setState { copy(confirmingDelete = false) }
            }

            ParentsEvent.ConfirmDelete -> {
                viewModelScope.launch {
                    library.deleteAll()
                    val bytes = library.bytesOnDevice()
                    setState { copy(confirmingDelete = false, deleteDone = true, downloadedBytes = bytes) }
                }
            }

            ParentsEvent.ConfirmReset -> {
                viewModelScope.launch {
                    progress.resetAll()
                    setState { copy(confirmingReset = false, resetDone = true) }
                }
            }
        }
    }
}
