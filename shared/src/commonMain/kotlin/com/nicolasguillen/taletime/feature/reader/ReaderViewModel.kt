package com.nicolasguillen.taletime.feature.reader

import androidx.lifecycle.viewModelScope
import com.nicolasguillen.taletime.domain.ProgressRepository
import com.nicolasguillen.taletime.domain.SettingsRepository
import com.nicolasguillen.taletime.domain.SharedClip
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
import com.nicolasguillen.taletime.story.StoryGraph
import com.nicolasguillen.taletime.story.TimedWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * When a page's words come when an agent skipped the reading (platform/AgentFlags): long before its clock starts, so
 * every word is there from the start and none fades in.
 */
internal const val SKIPPED_WORD_MILLIS = -60_000L

/** A breath before a page's narration starts, while the page fades in (its clip starts with 0.3 s of silence, too). */
const val NARRATION_BREATH_MILLIS = 400L

/**
 * How long each part of an ending's closing stays when its clip isn't delivered (in this language, or at all): "The
 * End", the badges coming in, and the closing line before the buttons.
 */
internal const val THE_END_MILLIS = 1_600L
internal const val BADGES_MILLIS = 1_300L
internal const val OUTRO_MILLIS = 1_200L

/**
 * What's on screen for the current page.
 * - [TEXT]: the picture and the page text, word by word. Once a plain page is read, its corner folds up to turn it.
 * - [CHOICE]: a fork's question and its two cards (they rise on the same page once its text is read).
 * - [END]: an ending's closing (they rise once the ending's text is read): see [Closing].
 */
enum class Phase { TEXT, CHOICE, END }

/**
 * How far an ending's closing is ([Phase.END]): each part comes after the one before, as the narrator says it, so the
 * story ends slowly instead of all at once.
 */
enum class Closing {
    /** "The End" rises, and the narrator says it. */
    THE_END,

    /** Every ending's badge comes in; a new one turns from "?" into its picture. A "?" can be tapped from now on. */
    BADGES,

    /**
     * The narrator says what's next: there are endings left to find (tap a question mark; the "?" badges wiggle), and
     * more stories to discover; or every one is found, and the narrator hopes they loved them.
     */
    OUTRO,

    /** The buttons: try another path, back to the shelf. */
    DONE,
}

/** @property toward this card shows the way to the ending the reader is looking for (a "?" tapped at an ending). */
data class Choice(
    val label: String,
    val image: String?,
    val toward: Boolean = false,
)

/** An ending's badge on the ending screen: its picture once [found], a "?" until then. */
data class EndingSpot(
    val id: String,
    val title: String,
    val image: String?,
    val found: Boolean,
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
    val endingKind: EndingKind? = null,
) {
    /**
     * A plain page: it turns from its folded corner. On a fork or an ending what comes next rises on the same page
     * (docs/page-turn.md §5).
     */
    val plain: Boolean get() = question == null && endingKind == null
}

data class ReaderUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val title: String = "",
    val page: ReaderPage? = null,
    val phase: Phase = Phase.TEXT,
    /**
     * What the next page shows, laid out beneath the folded corner so turning uncovers it: what [ReaderEvent.Next]
     * would show from a plain page. Null on a fork or an ending.
     */
    val upcoming: ReaderPage? = null,
    /**
     * The book is flipping by itself to the new [page], which waits (no words, nothing read aloud) until the screen
     * says it has landed ([ReaderEvent.Landed]): forward after a card was picked, or back after a rewind
     * ([rewinding]).
     */
    val landing: Boolean = false,
    /**
     * The book is flipping back from an ending to [page], a fork read on the way there, over this many pages read in
     * between ([ReaderEvent.FindEnding], [ReaderEvent.TryAnotherPath]). Null otherwise.
     */
    val rewinding: Int? = null,
    val endingsFound: Int = 0,
    val endingsTotal: Int = 0,
    /** Every ending of the story, in reading order, for its badge on the ending screen. */
    val endings: List<EndingSpot> = emptyList(),
    /** This ending wasn't found before this play-through. */
    val newEnding: Boolean = false,
    /** How far the ending's closing is, in [Phase.END]. */
    val closing: Closing = Closing.THE_END,
    val textScale: Float = 1f,
    /** The card whose label the narrator is reading, 0 or 1, at a fork. */
    val readingChoice: Int? = null,
)

sealed interface ReaderEvent {
    /**
     * A plain page has turned (the screen sends it once the turn is over), or a fork's text or an ending's text is read
     * and its cards or its closing rise.
     */
    data object Next : ReaderEvent

    /** One tap on a card picks it: the path is saved at once, and the new page waits until it has [Landed]. */
    data class TapChoice(
        val index: Int,
    ) : ReaderEvent

    /** The book has flipped to the page a card picked, or back to a fork: its words and narration can start. */
    data object Landed : ReaderEvent

    /**
     * A "?" badge on the ending screen: the book flips back to the latest fork read on the way here whose other card
     * can lead to [endingId], reads that page again, and the cards on the way to it show the way.
     */
    data class FindEnding(
        val endingId: String,
    ) : ReaderEvent

    /** "Try another path" on the ending screen: the book flips back to the last fork and reads it again. */
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
 * same page. There's no going back a page: forward, and back to a fork from an ending (a "?" badge, or "Try another
 * path"), only ever to a page this path has read.
 *
 * Everything with a delivered clip is read aloud (plan.md §3.2): a page's text, as its words appear; a fork's
 * question, then its two labels, as its cards come out; an ending's closing ([Closing]): "The End" and the closing
 * line, which every story shares (the catalog's, [StoryLibrary]). The page's words come as
 * the narrator says them, from its clip's timings. A text without its clip (narration isn't delivered yet, in this
 * language) reads without narration: its words come at the narrator's pace for the age band. Going to another page
 * stops the clip, and the narration pauses while the app is out of sight.
 *
 * The story is read in the language it was downloaded in, the one picked last on its cover. Progress is kept by node
 * id, the same in every language, so picking another language keeps the place.
 *
 * @param restart start from the first page even if there's a saved path ("Read" / "Start over" on the cover).
 * @param at start on this page instead, along the first path there: an agent asked for it (platform/AgentFlags), to
 *   try an ending without reading the whole story.
 * @param skipReading an agent testing the page turn asked for it (platform/AgentFlags): every page has all its words
 *   at once and nothing is read aloud.
 */
class ReaderViewModel(
    private val storyId: String,
    private val restart: Boolean,
    private val library: StoryLibrary,
    private val progress: ProgressRepository,
    private val settings: SettingsRepository,
    private val player: NarrationPlayer,
    private val skipReading: Boolean = false,
    private val at: String? = null,
) : StateViewModel<ReaderUiState, ReaderEvent>(ReaderUiState()) {
    private var bundle: StoryBundle? = null
    private var reading: Reading? = null
    private var found: Set<String> = emptySet()
    private var narration: Job? = null

    /** The ending a "?" badge asked for, while the reader is on the way to it: the cards that lead there show it. */
    private var seeking: String? = null

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
            val asked = at?.let { Reading.to(loaded.story, it) }
            show(asked ?: resumed ?: Reading.start(loaded.story), Phase.TEXT)
        }
    }

    /**
     * The reading clock of page [nodeId] while its text is read aloud: how far into its clip the narrator is, 0 before
     * the clip starts and [Long.MAX_VALUE] once it's over. Null when the page keeps its own clock: it isn't being read
     * aloud (no clip, or its clip couldn't play), or it's no longer the page on screen.
     */
    fun narrationMillis(nodeId: String): Long? {
        // The page the book is flipping to waits.
        if (currentState.landing && nodeId == currentState.page?.nodeId) return 0
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
                        if (currentState.phase == Phase.TEXT && !currentState.landing) {
                            setState { copy(phase = Phase.CHOICE) }
                            narrate(node, Phase.CHOICE)
                        }
                    }

                    is Ending -> {
                        if (currentState.phase == Phase.TEXT && !currentState.landing) {
                            setState { copy(phase = Phase.END, closing = Closing.THE_END) }
                            close()
                        }
                    }
                }
            }

            is ReaderEvent.TapChoice -> {
                if (reading.current !is Fork || currentState.phase != Phase.CHOICE) return
                val next = reading.choose(event.index)
                // Picked the other way: the way to the ending looked for is gone, and so are its marks.
                seeking = seeking?.takeIf { StoryGraph.leadsTo(next.story, next.current.id, it) }
                show(next, Phase.TEXT, landing = true)
            }

            ReaderEvent.Landed -> {
                if (!currentState.landing) return
                setState { copy(landing = false, rewinding = null) }
                narrate(reading.current, currentState.phase)
            }

            is ReaderEvent.FindEnding -> {
                if (currentState.phase != Phase.END || currentState.landing) return
                val back = reading.toward(event.endingId) ?: return
                seeking = event.endingId
                rewind(reading, back)
            }

            ReaderEvent.TryAnotherPath -> {
                if (currentState.phase != Phase.END || currentState.landing || !reading.canGoBack) return
                seeking = null
                rewind(reading, reading.back())
            }

            ReaderEvent.Hidden, ReaderEvent.Shown -> {} // handled above, with or without a story
        }
    }

    override fun onCleared() {
        narration?.cancel()
        player.release()
    }

    /** The book flips back from [from]'s ending to [to]'s page, which it then reads again: its text, then its cards. */
    private fun rewind(
        from: Reading,
        to: Reading,
    ) = show(to, Phase.TEXT, landing = true, rewinding = (from.path.size - to.path.size - 1).coerceAtLeast(0))

    /** Shows [next]'s page. While it's [landing], it waits: nothing is read aloud until [ReaderEvent.Landed]. */
    private fun show(
        next: Reading,
        phase: Phase,
        landing: Boolean = false,
        rewinding: Int? = null,
    ) {
        val bundle = bundle ?: return
        reading = next
        val story = bundle.story
        val node = next.current
        val firstTime = node is Ending && node.id !in found
        if (node is Ending) {
            found = found + node.id
            seeking = null
        }
        val endingIds = story.endings.map { it.id }.toSet()
        setState {
            copy(
                loading = false,
                title = story.title[bundle.reading],
                page = page(bundle, node),
                phase = phase,
                upcoming = (node as? Page)?.let { page(bundle, story.node(it.next)) },
                landing = landing,
                rewinding = rewinding,
                // Endings a new version removed don't count.
                endingsFound = (found intersect endingIds).size,
                endingsTotal = story.endings.size,
                endings = story.endings.map { EndingSpot(it.id, it.title[bundle.reading], bundle.image(it.image), it.id in found) },
                newEnding = firstTime,
                closing = Closing.THE_END,
            )
        }
        if (landing) hush() else narrate(node, phase)
        viewModelScope.launch {
            progress.save(storyId, next.path)
            if (node is Ending) progress.markEndingFound(storyId, node.id)
        }
    }

    /**
     * Reads aloud what [phase] shows of [node], with every clip that's delivered: its text, or a fork's question and
     * labels. An ending's closing is [close]. Stops whatever was being read.
     */
    private fun narrate(
        node: Node,
        phase: Phase,
    ) {
        if (phase == Phase.END && node is Ending) return close()
        hush()
        if (skipReading) return
        val bundle = bundle ?: return
        val parts =
            when (phase) {
                Phase.TEXT -> listOf(node.id)
                Phase.CHOICE -> listOf("${node.id}-q", "${node.id}-a", "${node.id}-b")
                Phase.END -> emptyList()
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
                    val played = play(path)
                    if (text) voice = if (played) Voice.DONE else null
                }
                setState { copy(readingChoice = null) }
            }
    }

    /**
     * An ending's closing, one part after the other ([Closing]): "The End", the badges, the closing line, the buttons.
     * Each part waits for its clip, or a moment when it has none. The ending's name isn't shown or said: it's only
     * its badge's label for a screen reader.
     */
    private fun close() {
        hush()
        if (skipReading) {
            setState { copy(closing = Closing.DONE) }
            return
        }
        val bundle = bundle ?: return
        val shared =
            library.state.value.narration[bundle.reading]
                .orEmpty()
        narration =
            viewModelScope.launch {
                delay(NARRATION_BREATH_MILLIS)
                say(shared[SharedClip.THE_END], orWait = THE_END_MILLIS)
                setState { copy(closing = Closing.BADGES) }
                delay(BADGES_MILLIS)
                setState { copy(closing = Closing.OUTRO) }
                val more = currentState.endings.any { !it.found }
                say(shared[if (more) SharedClip.MORE_ENDINGS else SharedClip.ALL_ENDINGS], orWait = OUTRO_MILLIS)
                setState { copy(closing = Closing.DONE) }
            }
    }

    /** Plays [clip], or waits [orWait] when there's none or it can't play. */
    private suspend fun say(
        clip: String?,
        orWait: Long,
    ) {
        if (clip == null || !play(clip)) delay(orWait)
    }

    /** Plays [path] to its end; false if it can't play. */
    private suspend fun play(path: String): Boolean =
        try {
            player.play(path)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false // a clip that can't play: that part reads without narration
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
        val narrated = bundle.clip(node.id) != null && !skipReading
        val times = bundle.timings(node.id)
        val words =
            when {
                skipReading -> Pacing.paced(text, pace).map { it.copy(atMillis = SKIPPED_WORD_MILLIS) }
                narrated -> Pacing.narrated(text, times, pace)
                else -> Pacing.paced(text, pace)
            }
        val way = (node as? Fork)?.let { fork -> seeking?.let { StoryGraph.wayTo(story, fork, it) } }
        return ReaderPage(
            nodeId = node.id,
            text = text,
            image = bundle.image(node.image),
            words = words,
            narrated = narrated,
            spokenUntilMillis = times?.takeIf { narrated && it.size == words.size }?.lastOrNull()?.endMillis,
            question = (node as? Fork)?.question?.get(lang),
            choices =
                (node as? Fork)
                    ?.options
                    ?.mapIndexed { i, it -> Choice(it.label[lang], bundle.image(it.image), toward = i == way) }
                    .orEmpty(),
            endingKind = (node as? Ending)?.kind,
        )
    }
}
