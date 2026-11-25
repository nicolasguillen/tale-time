package com.nicolasguillen.taletime.feature.reader

import androidx.lifecycle.viewModelScope
import com.nicolasguillen.taletime.domain.ProgressRepository
import com.nicolasguillen.taletime.domain.SettingsRepository
import com.nicolasguillen.taletime.domain.StoryBundle
import com.nicolasguillen.taletime.domain.StoryLibrary
import com.nicolasguillen.taletime.feature.StateViewModel
import com.nicolasguillen.taletime.platform.NarrationPlayer
import com.nicolasguillen.taletime.story.Ending
import com.nicolasguillen.taletime.story.EndingKind
import com.nicolasguillen.taletime.story.Fork
import com.nicolasguillen.taletime.story.Node
import com.nicolasguillen.taletime.story.Pacing
import com.nicolasguillen.taletime.story.Page
import com.nicolasguillen.taletime.story.Reading
import com.nicolasguillen.taletime.story.TimedWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A breath before a page's narration starts, while the page fades in (its clip starts with 0.3 s of silence, too). */
const val NARRATION_BREATH_MILLIS = 400L

/**
 * What's on screen for the current page.
 * - [TEXT]: the picture and the page text, word by word, with the "next" arrow.
 * - [CHOICE]: a fork's question and its two cards (they slide in after its text).
 * - [END]: "The End", the ending's badge and where to go next.
 */
enum class Phase { TEXT, CHOICE, END }

data class Choice(
    val label: String,
    val image: String?,
)

/**
 * @property words when each word of [text] appears: as its clip says it, or at the narrator's pace without one.
 * @property narrated its text is read aloud: its words follow the narrator ([ReaderViewModel.narrationMillis]).
 * @property spokenUntilMillis when the narrator finishes its last word, if its clip's timings say.
 */
data class ReaderPage(
    val nodeId: String,
    val text: String,
    val image: String?,
    val words: List<TimedWord> = emptyList(),
    val narrated: Boolean = false,
    val spokenUntilMillis: Long? = null,
    val question: String? = null,
    val choices: List<Choice> = emptyList(),
    val endingTitle: String? = null,
    val endingKind: EndingKind? = null,
)

data class ReaderUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val title: String = "",
    val page: ReaderPage? = null,
    val phase: Phase = Phase.TEXT,
    /** There's a page (or a fork's text) to go back to with the "back" arrow. */
    val canGoBack: Boolean = false,
    val endingsFound: Int = 0,
    val endingsTotal: Int = 0,
    /** This ending wasn't found before this play-through. */
    val newEnding: Boolean = false,
    val textScale: Float = 1f,
    /** The card whose label the narrator is reading, 0 or 1, at a fork. */
    val readingChoice: Int? = null,
)

sealed interface ReaderEvent {
    /** The "next" arrow: turns a page, opens a fork's cards, or shows an ending's "The End". */
    data object Next : ReaderEvent

    /** One tap on a card picks it. */
    data class TapChoice(
        val index: Int,
    ) : ReaderEvent

    /** The "back" arrow: one page back, like a book. From a fork's cards, back to its text. */
    data object Previous : ReaderEvent

    /** "Try another path" on the ending screen: back to the last fork, with its cards out. */
    data object TryAnotherPath : ReaderEvent

    /** The reader went out of sight (the app went to the background): the narration pauses. */
    data object Hidden : ReaderEvent

    /** In sight again: the narration goes on from where it paused. */
    data object Shown : ReaderEvent
}

/** How far the narration of a page's text is. */
private enum class Voice { WAITING, READING, DONE }

/**
 * Reads one story: the path so far lives in a [Reading], and every move is saved so the child can come back to the
 * same page.
 *
 * Everything with a delivered clip is read aloud (plan.md §3.2): a page's text, as its words appear; a fork's
 * question, then its two labels, as its cards come out; an ending's title under "The End". The page's words come as
 * the narrator says them, from its clip's timings. A text without its clip (narration isn't delivered yet, in this
 * language) reads without narration: its words come at the narrator's pace for the age band. Going to another page
 * stops the clip, and the narration pauses while the app is out of sight.
 *
 * The story is read in the language it was downloaded in, the one picked last on its cover. Progress is kept by node
 * id, the same in every language, so picking another language keeps the place.
 *
 * @param restart start from the first page even if there's a saved path ("Read" / "Start over" on the cover).
 */
class ReaderViewModel(
    private val storyId: String,
    private val restart: Boolean,
    private val library: StoryLibrary,
    private val progress: ProgressRepository,
    private val settings: SettingsRepository,
    private val player: NarrationPlayer,
) : StateViewModel<ReaderUiState, ReaderEvent>(ReaderUiState()) {
    private var bundle: StoryBundle? = null
    private var reading: Reading? = null
    private var found: Set<String> = emptySet()
    private var narration: Job? = null

    /** The page whose text is being read aloud, and how far. */
    private var voiceNode: String? = null
    private var voice: Voice? = null

    init {
        viewModelScope.launch {
            settings.settings.collect { s -> setState { copy(textScale = s.textSize.scale) } }
        }
        viewModelScope.launch {
            val loaded = runCatching { library.load(storyId) }.getOrNull()
            if (loaded == null) {
                setState { copy(loading = false, failed = true) }
                return@launch
            }
            bundle = loaded
            val saved = progress.progress(storyId).first()
            found = saved.endingsFound
            val resumed =
                saved.path
                    ?.takeUnless { restart }
                    ?.let { Reading.resume(loaded.story, it) }
                    ?.takeUnless { it.atEnding }
            show(resumed ?: Reading.start(loaded.story), Phase.TEXT)
        }
    }

    /**
     * The reading clock of page [nodeId] while its text is read aloud: how far into its clip the narrator is, 0 before
     * the clip starts and [Long.MAX_VALUE] once it's over. Null when the page keeps its own clock: it isn't being read
     * aloud (no clip, or its clip couldn't play), or it's no longer the page on screen.
     */
    fun narrationMillis(nodeId: String): Long? {
        if (nodeId != voiceNode) return null
        return when (voice) {
            Voice.WAITING -> 0
            Voice.READING -> player.positionMillis() ?: 0
            Voice.DONE -> Long.MAX_VALUE
            null -> null
        }
    }

    override fun onEvent(event: ReaderEvent) {
        when (event) {
            ReaderEvent.Hidden -> player.pause()
            ReaderEvent.Shown -> player.resume()
            else -> Unit
        }
        val reading = reading ?: return
        when (event) {
            ReaderEvent.Next -> {
                when (val node = reading.current) {
                    is Page -> {
                        show(reading.next(), Phase.TEXT)
                    }

                    is Fork -> {
                        if (currentState.phase == Phase.TEXT) {
                            setState { copy(phase = Phase.CHOICE, canGoBack = true) }
                            narrate(node, Phase.CHOICE)
                        }
                    }

                    is Ending -> {
                        if (currentState.phase == Phase.TEXT) {
                            setState { copy(phase = Phase.END) }
                            narrate(node, Phase.END)
                        }
                    }
                }
            }

            is ReaderEvent.TapChoice -> {
                if (reading.current !is Fork || currentState.phase != Phase.CHOICE) return
                show(reading.choose(event.index), Phase.TEXT)
            }

            ReaderEvent.Previous -> {
                when {
                    // Back to the text, all there already: it isn't read again.
                    currentState.phase != Phase.TEXT -> {
                        setState { copy(phase = Phase.TEXT, canGoBack = reading.canGoBack) }
                        hush()
                    }

                    reading.canGoBack -> {
                        reading.previous().let { show(it, if (it.current is Fork) Phase.CHOICE else Phase.TEXT) }
                    }
                }
            }

            ReaderEvent.TryAnotherPath -> {
                if (!reading.canGoBack) return
                val back = reading.back()
                show(back, if (back.current is Fork) Phase.CHOICE else Phase.TEXT)
            }

            ReaderEvent.Hidden, ReaderEvent.Shown -> {} // handled above, with or without a story
        }
    }

    override fun onCleared() {
        narration?.cancel()
        player.release()
    }

    private fun show(
        next: Reading,
        phase: Phase,
    ) {
        val bundle = bundle ?: return
        reading = next
        val story = bundle.story
        val node = next.current
        val firstTime = node is Ending && node.id !in found
        if (node is Ending) found = found + node.id
        val endingIds = story.endings.map { it.id }.toSet()
        setState {
            copy(
                loading = false,
                title = story.title[bundle.reading],
                page = page(bundle, node),
                phase = phase,
                canGoBack = next.canGoBack || phase == Phase.CHOICE,
                // Endings a new version removed don't count.
                endingsFound = (found intersect endingIds).size,
                endingsTotal = story.endings.size,
                newEnding = firstTime,
            )
        }
        narrate(node, phase)
        viewModelScope.launch {
            progress.save(storyId, next.path)
            if (node is Ending) progress.markEndingFound(storyId, node.id)
        }
    }

    /**
     * Reads aloud what [phase] shows of [node], with every clip that's delivered: its text, a fork's question and
     * labels, or an ending's title. Stops whatever was being read.
     */
    private fun narrate(
        node: Node,
        phase: Phase,
    ) {
        hush()
        val bundle = bundle ?: return
        val parts =
            when (phase) {
                Phase.TEXT -> listOf(node.id)
                Phase.CHOICE -> listOf("${node.id}-q", "${node.id}-a", "${node.id}-b")
                Phase.END -> listOf("${node.id}-end")
            }
        val clips = parts.mapNotNull { id -> bundle.clip(id)?.let { id to it } }
        if (clips.isEmpty()) return
        if (phase == Phase.TEXT) {
            voiceNode = node.id
            voice = Voice.WAITING
        }
        narration =
            viewModelScope.launch {
                delay(NARRATION_BREATH_MILLIS)
                for ((id, path) in clips) {
                    val text = id == node.id
                    val card =
                        when (id) {
                            "${node.id}-a" -> 0
                            "${node.id}-b" -> 1
                            else -> null
                        }
                    setState { copy(readingChoice = card) }
                    if (text) voice = Voice.READING
                    val played =
                        try {
                            player.play(path)
                            true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            false // a clip that can't play: that part reads without narration
                        }
                    if (text) voice = if (played) Voice.DONE else null
                }
                setState { copy(readingChoice = null) }
            }
    }

    /** Stops reading aloud. */
    private fun hush() {
        narration?.cancel()
        narration = null
        voiceNode = null
        voice = null
        if (currentState.readingChoice != null) setState { copy(readingChoice = null) }
    }

    private fun page(
        bundle: StoryBundle,
        node: Node,
    ): ReaderPage {
        val story = bundle.story
        val lang = bundle.reading
        val text = node.text[lang]
        val pace = Pacing.wordsPerMinute(story.ageBand)
        val narrated = bundle.clip(node.id) != null
        val times = bundle.timings(node.id)
        val words = if (narrated) Pacing.narrated(text, times, pace) else Pacing.paced(text, pace)
        return ReaderPage(
            nodeId = node.id,
            text = text,
            image = bundle.image(node.image),
            words = words,
            narrated = narrated,
            spokenUntilMillis = times?.takeIf { narrated && it.size == words.size }?.lastOrNull()?.endMillis,
            question = (node as? Fork)?.question?.get(lang),
            choices = (node as? Fork)?.options?.map { Choice(it.label[lang], bundle.image(it.image)) }.orEmpty(),
            endingTitle = (node as? Ending)?.title?.get(lang),
            endingKind = (node as? Ending)?.kind,
        )
    }
}
