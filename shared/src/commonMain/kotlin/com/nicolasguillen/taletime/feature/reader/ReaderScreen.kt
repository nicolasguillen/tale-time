package com.nicolasguillen.taletime.feature.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nicolasguillen.taletime.designsystem.EndingBadge
import com.nicolasguillen.taletime.designsystem.HomeButton
import com.nicolasguillen.taletime.designsystem.ImageKind
import com.nicolasguillen.taletime.designsystem.PillButton
import com.nicolasguillen.taletime.designsystem.StoryImage
import com.nicolasguillen.taletime.designsystem.Tale
import com.nicolasguillen.taletime.designsystem.TaleColors
import com.nicolasguillen.taletime.designsystem.TaleIcons
import com.nicolasguillen.taletime.designsystem.squish
import com.nicolasguillen.taletime.i18n.LocalStrings
import com.nicolasguillen.taletime.platform.AgentFlags
import com.nicolasguillen.taletime.platform.reduceMotion
import com.nicolasguillen.taletime.story.TimedWord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.pow
import kotlin.math.roundToInt

/** A breath before the first word, while the page fades in (a narration clip starts with 0.3 s of silence, too). */
private const val FIRST_WORD_DELAY_MILLIS = 700L

/** How long a word takes to fade in once its time comes. */
private const val WORD_FADE_MILLIS = 300L

/** The reading clock never jumps more than this in one frame, so it pauses while the app is in the background. */
private const val MAX_FRAME_STEP_MILLIS = 100L

/**
 * The narrowest a page picture is ever shown, as width / height: its 4:3 cropped to the middle 80% of its width,
 * where the house style keeps the main action (content/style/style.json, "composition").
 */
private const val NARROWEST_PICTURE = 4f / 3f * 0.8f

/**
 * Beside the text, the picture may be cropped a little further, to 70% of its width, so it fills the screen's height
 * on every tablet in landscape (74% on a 13-inch iPad), edge to edge, instead of leaving paper above and below it.
 */
private const val NARROWEST_BESIDE = 4f / 3f * 0.7f

/** How far the picture's inner edge melts into the paper, so the picture and the text read as one page. */
private val MELT = 36.dp

/** The fewest lines the text window shows, however little room the picture leaves… */
private const val FEWEST_LINES = 3

/** …and the most, however much room there is: a page never looks like a wall of words, and longer ones slide up. */
private const val MOST_LINES = 10

/** The least room the text and the folded corner keep under the picture on a tall screen. */
private val TEXT_ROOM_TALL = 240.dp

/**
 * The room kept empty at the bottom of the text (the arrows' row once, and the gap above it), so no word ever sits
 * under the folded corner.
 */
private val CORNER_ROOM_PHONE = 68.dp
private val CORNER_ROOM_TABLET = 80.dp
private val CORNER_ROOM_GAP = 8.dp

/** The dog-ear's crease meets the bottom edge this far from the corner and the right edge this far up it. */
private val CREASE_PHONE = 118.dp to 96.dp
private val CREASE_TABLET = 150.dp to 122.dp

/** Where a finger takes the corner: a square at the screen's bottom-right corner. */
private val CORNER_PHONE = 132.dp
private val CORNER_TABLET = 160.dp

/** After a fork's or an ending's text is read, a pause before its cards or "The End" rise on the same page. */
private const val OPEN_PAUSE_MILLIS = 900L

/** The picked card lights up this long, its glow drawn as this many rings; the rest steps back this long. */
private const val GLOW_MILLIS = 550
private const val GLOW_RINGS = 8
private const val STEP_BACK_MILLIS = 350

/** Sparkles burst from the picked card, then come off its edge, two a frame, for this long. */
private const val BURST = 36
private const val EDGE_PER_FRAME = 2
private const val EDGE_MILLIS = 700L

/**
 * The book riffles by itself this long after the tap: the fork page turns, then [LEAVES] blank leaves, faster each
 * time, the last one uncovering the new page. A turn's length in [RIFFLE_MILLIS].
 */
private const val RIFFLE_FROM_MILLIS = 700L
private const val LEAVES = 3
private val RIFFLE_MILLIS = listOf(620L, 420L, 330L, 380L)
private const val DUST_PER_FRAME = 3

/** As the new page lands, sparkles drift down over its picture: the top part of the screen. */
private const val LANDING_SPARKLES = 28
private const val LANDING_AREA = 0.45f

/**
 * A page's picture comes in once the page is in front and lying flat, never beneath a turning page, which would have to
 * draw it on every frame of the turn: it fades in over this long once it's loaded.
 */
private const val PICTURE_FADE_MILLIS = 500

/** With [AgentFlags.autoTurn], the reader waits this long before it turns a page, picks a card or goes back… */
private const val AUTO_TURN_MILLIS = 1_500L

/** …and prints how many frames it drew and missed this often. */
private const val FRAME_REPORT_MILLIS = 10_000L

/** A page that doesn't turn (a cross-fade) comes in over this long while the one before goes in [FADE_OUT_MILLIS]. */
private const val FADE_IN_MILLIS = 450f
private const val FADE_OUT_MILLIS = 250f

@Composable
fun ReaderRoute(
    storyId: String,
    restart: Boolean,
    onHome: () -> Unit,
    viewModel: ReaderViewModel = koinViewModel(key = "$storyId/$restart") { parametersOf(storyId, restart) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The narration pauses while the app is in the background, and stops when the reader is left.
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(ReaderEvent.Shown)
        onStopOrDispose { viewModel.onEvent(ReaderEvent.Hidden) }
    }
    val lessMotion = remember { reduceMotion() }
    val agent = koinInject<AgentFlags>()
    ReaderScreen(state, viewModel::onEvent, onHome, viewModel::narrationMillis, lessMotion, agent.autoTurn)
}

/**
 * The open book: the page in front, the next one beneath its folded corner, and how one turns into the other
 * (docs/page-turn.md).
 *
 * @param narrationMillis the reading clock of a page while its text is read aloud
 *   ([ReaderViewModel.narrationMillis]); a page keeps its own clock while it's null.
 * @param reduceMotion the device asks for less motion: pages cross-fade instead of turning, and a choice only glows.
 * @param autoTurn an agent asked the reader to turn its pages by itself ([AgentFlags.autoTurn]).
 */
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onEvent: (ReaderEvent) -> Unit,
    onHome: () -> Unit,
    narrationMillis: (nodeId: String) -> Long? = { null },
    reduceMotion: Boolean = false,
    autoTurn: Boolean = false,
) {
    Box(Modifier.fillMaxSize().background(TaleColors.Paper)) {
        val page = state.page
        if (page == null) {
            if (state.failed) {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(LocalStrings.current.storyCantOpen, style = Tale.type.heading)
                    PillButton(LocalStrings.current.backToShelf, onClick = onHome, icon = TaleIcons.Home)
                }
            }
            return@Box
        }
        val book = remember { Book() }
        book.arrive(state)
        val send by rememberUpdatedState(onEvent)
        val scope = rememberCoroutineScope()
        val turn = remember { PageTurn(scope) }
        val sparkles = remember { Sparkles() }
        val where = remember { Where() }
        val magic = book.magic
        val fade = book.fade

        // A page's words and narration start once it's in front and lying flat, never beneath a turning page.
        val clock = rememberReadingClock(page, narrationMillis, running = !state.landing && magic == null)
        val read by remember(page.nodeId) { derivedStateOf { clock.value >= page.readMillis } }

        // Once a plain page is read, its corner folds up; it lies flat again on the next page.
        val cornerUp = page.plain && read && state.upcoming != null && magic == null && !state.landing
        LaunchedEffect(page.nodeId, cornerUp) {
            if (cornerUp) {
                turn.foldUp(page.nodeId, reduceMotion)
            } else if (book.magic == null) {
                turn.lie()
            }
        }
        // A fork's cards and an ending's "The End" rise on the same page once its text is read.
        val opens = !page.plain && state.phase == Phase.TEXT && read && magic == null && !state.landing
        LaunchedEffect(page.nodeId, opens) {
            if (opens) {
                delay(OPEN_PAUSE_MILLIS)
                send(ReaderEvent.Next)
            }
        }
        // Back during a choice's riffle (the screen was made again, the device turned): the new page lands at once.
        LaunchedEffect(state.landing, magic) {
            if (state.landing && magic == null) send(ReaderEvent.Landed)
        }
        LaunchedEffect(fade) {
            if (fade == null) return@LaunchedEffect
            val start = withFrameMillis { it }
            while (fade.millis < FADE_IN_MILLIS) withFrameMillis { fade.millis = (it - start).toFloat() }
            if (book.fade === fade) book.fade = null
        }

        /** The corner was tapped or let go far enough: the page turns, and the one beneath is the page. */
        fun turnPage(flick: Boolean) {
            val next = state.upcoming ?: return
            if (reduceMotion) {
                turn.lie()
                send(ReaderEvent.Next)
                return
            }
            book.turningTo = next.nodeId
            turn.turn(page.nodeId, if (flick) Ease::outCubic else Ease::inOutSine) { send(ReaderEvent.Next) }
        }

        /** One tap on a card: it lights up, sparkles burst from it, and the book flips by itself to the new page. */
        fun pick(index: Int) {
            if (book.magic != null || state.phase != Phase.CHOICE) return
            val chosen = Magic(state.copy(readingChoice = null), index, riffles = !reduceMotion)
            book.magic = chosen
            turn.lie()
            send(ReaderEvent.TapChoice(index))
            scope.launch {
                if (!chosen.riffles) {
                    delay(GLOW_MILLIS.toLong())
                    book.fade = Fade(chosen.fork, chosen.index)
                    book.magic = null
                    send(ReaderEvent.Landed)
                    return@launch
                }
                where.cards[index]?.translate(-where.origin)?.let { card ->
                    sparkles.burst(card.center, BURST)
                    launch {
                        val start = withFrameMillis { it }
                        while (withFrameMillis { it } - start < EDGE_MILLIS) sparkles.edge(card, EDGE_PER_FRAME)
                    }
                }
                delay(RIFFLE_FROM_MILLIS)
                val order = listOf<Any>(chosen.fork.key) + (1..LEAVES).map { Sheet.Leaf(it) }
                RIFFLE_MILLIS.forEachIndexed { i, millis ->
                    turn.turnNow(order[i], Ease::inOutSine, millis) { fold ->
                        // Gold dust flies off the crease, the way the flap goes.
                        fold.crease()?.let { (a, b) -> sparkles.dust(a, b, -fold.n, DUST_PER_FRAME) }
                    }
                    chosen.turned = i + 1
                    turn.lie()
                }
                book.magic = null
                send(ReaderEvent.Landed)
                sparkles.drift(Rect(0f, 0f, turn.width, turn.height * LANDING_AREA), LANDING_SPARKLES)
            }
        }

        if (autoTurn) {
            var picks by remember { mutableIntStateOf(0) }
            LaunchedEffect(page.nodeId, cornerUp, state.phase, magic, state.landing) {
                delay(AUTO_TURN_MILLIS)
                when {
                    cornerUp && !turn.turning -> turnPage(flick = false)
                    state.phase == Phase.CHOICE && magic == null && !state.landing -> pick(picks++ % 2)
                    state.phase == Phase.END -> send(ReaderEvent.TryAnotherPath)
                }
            }
            // How smooth it was: frames missed while a page turns or the book flips, and the rest of the time.
            val turning by rememberUpdatedState(turn.turning || magic != null)
            LaunchedEffect(Unit) { countFrames { turning } }
        }

        val sheets = sheets(state, magic, fade)
        BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { where.origin = it.positionInRoot() }) {
            val tablet = minOf(maxWidth, maxHeight) >= 600.dp
            val density = LocalDensity.current
            val crease = if (tablet) CREASE_TABLET else CREASE_PHONE
            turn.width = constraints.maxWidth.toFloat()
            turn.height = constraints.maxHeight.toFloat()
            turn.crease = with(density) { Offset(crease.first.toPx(), crease.second.toPx()) }
            val layout = PageLayout(wide = maxWidth > maxHeight, tablet = tablet, width = maxWidth, height = maxHeight)
            PageStack(
                sheets,
                keyOf = { it.key },
                turn = turn,
                frontAlpha = { fade?.inAlpha ?: 1f },
                underAlpha = { fade?.outAlpha ?: 1f },
                accessible = { it is Sheet.Story && it.role == Shown.LIVE },
                modifier = Modifier.fillMaxSize(),
            ) { sheet ->
                when (sheet) {
                    is Sheet.Leaf -> {
                        Leaf()
                    }

                    is Sheet.Story -> {
                        PageContent(
                            sheet.ui,
                            clock =
                                when (sheet.role) {
                                    Shown.LIVE -> clock
                                    Shown.NEXT -> NotYet
                                    Shown.HELD -> AllRead
                                },
                            layout = layout,
                            // Not beneath the folded corner, nor under the leaves while the book flips by itself.
                            picture = sheet.role == Shown.HELD || (sheet.role == Shown.LIVE && magic == null),
                            onEvent = onEvent,
                            onHome = onHome,
                            chosen = if (sheet.role == Shown.HELD) magic?.chosenOn(sheet) ?: fade?.chosenOn(sheet) else null,
                            onPick = ::pick,
                            where = where,
                        )
                    }
                }
            }
            SparkleLayer(sparkles, Modifier.fillMaxSize())
            if (cornerUp && !turn.turning) {
                FoldedCorner(
                    turn,
                    side = if (tablet) CORNER_TABLET else CORNER_PHONE,
                    label = LocalStrings.current.nextPage,
                    reduceMotion = reduceMotion,
                    onTurn = ::turnPage,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
            // While a page turns, nothing takes a touch but the home button.
            if (turn.turning || magic != null) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
            }
            HomeButton(onClick = onHome, quiet = true, modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp))
        }
    }
}

/**
 * Counts the frames the screen draws and the ones it misses, while [busy] and the rest of the time, and prints them
 * every [FRAME_REPORT_MILLIS], for an agent measuring the page turn ([AgentFlags.autoTurn]): on iOS `xcrun devicectl
 * device process launch --console …` shows them, on Android logcat. The display's pace is the median frame of each
 * report; a frame that takes n paces missed n - 1 frames.
 */
private suspend fun countFrames(busy: () -> Boolean) {
    val gaps = LongArray(4_096)
    val during = BooleanArray(gaps.size)
    var n = 0
    var last = withFrameNanos { it }
    var since = last
    while (true) {
        val now = withFrameNanos { it }
        if (n < gaps.size) {
            gaps[n] = now - last
            during[n++] = busy()
        }
        last = now
        if (now - since < FRAME_REPORT_MILLIS * 1_000_000) continue
        val pace = gaps.copyOf(n).sorted()[n / 2].coerceAtLeast(1)
        // [frames, missed] while busy, then the rest of the time; and the longest frame while busy.
        val counts = LongArray(4)
        var worst = 0L
        for (i in 0 until n) {
            val at = if (during[i]) 0 else 2
            counts[at]++
            counts[at + 1] += ((gaps[i] + pace / 2) / pace - 1).coerceAtLeast(0)
            if (during[i]) worst = maxOf(worst, gaps[i])
        }
        println(
            "TaleTime frames: turning ${counts[0]} drawn, ${counts[1]} missed, worst ${worst / 1_000_000} ms; " +
                "otherwise ${counts[2]} drawn, ${counts[3]} missed (pace ${pace / 100_000 / 10f} ms)",
        )
        n = 0
        since = now
    }
}

/** How a story page is on the stack. */
private enum class Shown {
    /** The view model's page now. */
    LIVE,

    /** The next page, beneath the folded corner: its words haven't come yet. */
    NEXT,

    /** A page held as it was: the fork while the book flips after a choice, or the page fading out. */
    HELD,
}

/** A sheet of the book on screen: a story page as [Story.ui] shows it, or one of the blank leaves of a riffle. */
private sealed interface Sheet {
    val key: Any

    data class Story(
        val ui: ReaderUiState,
        val role: Shown,
    ) : Sheet {
        override val key: Any get() = ui.key
    }

    data class Leaf(
        val n: Int,
    ) : Sheet {
        override val key: Any get() = this
    }
}

private val ReaderUiState.key: String get() = page?.nodeId.orEmpty()

/**
 * The sheets on screen, bottom to top: the next page beneath the page now; during a choice's magic the new page at
 * the bottom, the leaves still to turn, and the fork on top; during a cross-fade the page fading out under the one
 * fading in. A key shows once, the topmost.
 */
private fun sheets(
    state: ReaderUiState,
    magic: Magic?,
    fade: Fade?,
): List<Sheet> {
    val live = Sheet.Story(state, Shown.LIVE)
    val all =
        buildList {
            if (magic == null) {
                state.upcoming?.let { next ->
                    add(Sheet.Story(ReaderUiState(page = next, textScale = state.textScale, title = state.title), Shown.NEXT))
                }
            }
            when {
                magic == null -> {
                    if (fade != null) add(Sheet.Story(fade.from, Shown.HELD))
                    add(live)
                }

                magic.riffles -> {
                    add(live)
                    for (n in LEAVES downTo maxOf(1, magic.turned)) add(Sheet.Leaf(n))
                    if (magic.turned == 0) add(Sheet.Story(magic.fork, Shown.HELD))
                }

                else -> {
                    add(live)
                    add(Sheet.Story(magic.fork, Shown.HELD))
                }
            }
        }
    return all.asReversed().distinctBy { it.key }.asReversed()
}

/**
 * A card was picked (docs/page-turn.md §4): the fork page as it was is held in front, its card [index] glowing, then
 * (when it [riffles]) the book flips through a few leaves by itself to the new page.
 */
private class Magic(
    val fork: ReaderUiState,
    val index: Int,
    val riffles: Boolean,
) {
    /** How many sheets have turned over: 0 while the fork is in front, then one more for each leaf. */
    var turned by mutableIntStateOf(0)

    fun chosenOn(sheet: Sheet.Story): Int? = index.takeIf { sheet.key == fork.key }
}

/** A page change that isn't a turn: the page [from] fades out under the one fading in (docs/page-turn.md §5). */
private class Fade(
    val from: ReaderUiState,
    val chosen: Int? = null,
) {
    var millis by mutableFloatStateOf(0f)

    val inAlpha: Float get() = FastOutSlowInEasing.transform((millis / FADE_IN_MILLIS).coerceIn(0f, 1f))
    val outAlpha: Float get() = 1f - FastOutSlowInEasing.transform((millis / FADE_OUT_MILLIS).coerceIn(0f, 1f))

    fun chosenOn(sheet: Sheet.Story): Int? = chosen?.takeIf { sheet.key == from.key }
}

/** How the page in front came to be there: a turn, a choice's magic, or a cross-fade. */
@Stable
private class Book {
    var magic by mutableStateOf<Magic?>(null)
    var fade by mutableStateOf<Fade?>(null)

    /** The page a corner turn is bringing in front: it comes without a cross-fade. */
    var turningTo: String? = null

    private var shown: ReaderUiState? = null

    /**
     * Sees every state the screen shows, before anything reads [fade]: a page that came any other way than a turn
     * or a choice (opening a story is no change) cross-fades in over the one before.
     */
    fun arrive(state: ReaderUiState) {
        val before = shown
        shown = state
        val now = state.page?.nodeId ?: return
        val was = before?.page?.nodeId ?: return
        if (now == was) return
        val expected = now == turningTo || (state.landing && magic != null)
        turningTo = null
        if (!expected) fade = Fade(before)
    }
}

/** Where the cards are on screen, so sparkles can burst from them. */
private class Where {
    var origin = Offset.Zero
    val cards = arrayOfNulls<Rect>(2)
}

/** A clock that doesn't run. */
private class Still(
    override val value: Long,
) : State<Long>

/** The page beneath: none of its words have come yet. */
private val NotYet = Still(-FIRST_WORD_DELAY_MILLIS)

/** A page held as it was: all its words are there. */
private val AllRead = Still(Long.MAX_VALUE)

/** What fits on the screen: the picture beside the text when it's [wide], above it when it's tall. */
private class PageLayout(
    val wide: Boolean,
    val tablet: Boolean,
    val width: Dp,
    val height: Dp,
)

/**
 * The page's reading clock: milliseconds since its first word (from the start of its clip when it's narrated),
 * negative during the breath before it. While the page is read aloud, it's where the narrator is in the clip
 * ([narrationMillis]), so the words come as they're said and wait while the narration pauses (plan.md §7.4).
 * Otherwise it runs on frames, so it stops while the app is in the background. It runs only while the page is
 * [running]: in front and lying flat. It's saved, so turning the device doesn't start the page over.
 */
@Composable
private fun rememberReadingClock(
    page: ReaderPage,
    narrationMillis: (nodeId: String) -> Long?,
    running: Boolean,
): State<Long> {
    val clock: MutableLongState = rememberSaveable(page.nodeId) { mutableLongStateOf(-FIRST_WORD_DELAY_MILLIS) }
    val end = page.readMillis
    val narrator by rememberUpdatedState(narrationMillis)
    LaunchedEffect(page.nodeId, end, running) {
        if (!running) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (clock.longValue < end) {
            val now = withFrameMillis { it }
            clock.longValue = narrator(page.nodeId) ?: (clock.longValue + (now - last).coerceAtMost(MAX_FRAME_STEP_MILLIS))
            last = now
        }
    }
    return clock
}

/** Erases the text from [start] to [end] (left to right), line by line, by [alpha]. */
private fun DrawScope.erase(
    text: TextLayoutResult,
    start: Int,
    end: Int,
    alpha: Float,
) {
    val first = text.getLineForOffset(start)
    val last = text.getLineForOffset((end - 1).coerceAtLeast(start))
    for (line in first..last) {
        val left = if (line == first) text.getHorizontalPosition(start, usePrimaryDirection = true) else 0f
        val right = if (line == last && end < text.layoutInput.text.length) text.getHorizontalPosition(end, true) else size.width
        val top = text.getLineTop(line)
        drawRect(
            Color.Black,
            topLeft = Offset(left, top),
            size = Size(right - left, text.getLineBottom(line) - top),
            alpha = alpha,
            blendMode = BlendMode.DstOut,
        )
    }
}

/** At [now], some words have come and some are still coming or fading in. */
private fun List<TimedWord>.revealing(now: Long): Boolean =
    isNotEmpty() && now >= first().atMillis && now < last().atMillis + WORD_FADE_MILLIS

/** When the page is read: its last word has faded in, and the narrator has said it. */
private val ReaderPage.readMillis: Long
    get() = maxOf((words.lastOrNull()?.atMillis ?: 0L) + WORD_FADE_MILLIS, spokenUntilMillis ?: 0L)

/**
 * One story page across the whole screen, the picture and the text together, so they turn as one sheet of paper.
 *
 * @param picture its picture shows: it's in front and lying flat, or held as it was. Until then the picture's place is
 *   bare, and the picture fades in once it's loaded.
 * @param chosen the card picked on this fork, glowing while the book flips to its page; the rest steps back.
 */
@Composable
private fun PageContent(
    ui: ReaderUiState,
    clock: State<Long>,
    layout: PageLayout,
    picture: Boolean,
    onEvent: (ReaderEvent) -> Unit,
    onHome: () -> Unit,
    chosen: Int?,
    onPick: (Int) -> Unit,
    where: Where,
) {
    val shown = ui.page ?: return
    // The picture runs to the screen's edges, under the system bars, and melts into the paper where the text is.
    Box(Modifier.fillMaxSize().background(TaleColors.Paper)) {
        if (layout.wide) {
            // The picture fills the height on the left, edge to edge, the text sits beside it.
            val pictureWidth = minOf(layout.width - if (layout.tablet) 360.dp else 300.dp, layout.height * ImageKind.PAGE.aspect)
            val pictureHeight = minOf(layout.height, pictureWidth / NARROWEST_BESIDE)
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(pictureWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Picture(shown, picture, meltsDown = false, Modifier.fillMaxWidth().height(pictureHeight))
                }
                Panel(
                    ui,
                    clock,
                    onEvent,
                    onHome,
                    layout.tablet,
                    chosen,
                    onPick,
                    where,
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)),
                )
            }
        } else {
            // The picture across the top, from the very top, as tall as its main action allows; the text below it.
            val pictureHeight = minOf(layout.width / NARROWEST_PICTURE, layout.height - TEXT_ROOM_TALL)
            Column(Modifier.fillMaxSize()) {
                Picture(shown, picture, meltsDown = true, Modifier.fillMaxWidth().height(pictureHeight))
                Panel(
                    ui,
                    clock,
                    onEvent,
                    onHome,
                    layout.tablet,
                    chosen,
                    onPick,
                    where,
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                )
            }
        }
    }
}

@Composable
private fun Panel(
    state: ReaderUiState,
    clock: State<Long>,
    onEvent: (ReaderEvent) -> Unit,
    onHome: () -> Unit,
    tablet: Boolean,
    chosen: Int?,
    onPick: (Int) -> Unit,
    where: Where,
    modifier: Modifier,
) {
    val page = state.page ?: return
    // A fork's cards and an ending's "The End" rise on the same page once its text is read.
    AnimatedContent(
        targetState = state.phase,
        transitionSpec = { (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 6 }) togetherWith fadeOut(tween(200)) },
        label = "phase",
        modifier = modifier,
    ) { phase ->
        when (phase) {
            Phase.TEXT -> TextPanel(page, clock, state.textScale, tablet)
            Phase.CHOICE -> ChoicePanel(page, state.textScale, tablet, state.readingChoice, chosen, onPick, where)
            Phase.END -> EndPanel(state, onEvent, onHome)
        }
    }
}

@Composable
private fun TextPanel(
    page: ReaderPage,
    clock: State<Long>,
    textScale: Float,
    tablet: Boolean,
) {
    // Keyed on the clock too, as in RevealingText.
    val read by remember(page, clock) { derivedStateOf { clock.value >= page.readMillis } }
    val size = (if (tablet) 27f else 23f) * textScale
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // The window is as many whole lines tall as the room allows, up to ten, so most pages show whole.
            val line = with(LocalDensity.current) { (size * 1.5f).sp.toDp() }
            val lines = (maxHeight / line).toInt().coerceIn(FEWEST_LINES, MOST_LINES)
            RevealingText(page, clock, Tale.type.story.copy(fontSize = size.sp), windowHeight = line * lines, scrollable = read)
        }
        // Room for the folded corner: no word ever sits under it.
        Spacer(Modifier.height(CORNER_ROOM_GAP + if (tablet) CORNER_ROOM_TABLET else CORNER_ROOM_PHONE))
    }
}

/**
 * A fork's question and its two cards.
 *
 * @param readingChoice the card whose label the narrator is reading: it lifts a little.
 * @param chosen the card picked: it lights up, and the other card and the question step back.
 */
@Composable
private fun ChoicePanel(
    page: ReaderPage,
    textScale: Float,
    tablet: Boolean,
    readingChoice: Int?,
    chosen: Int?,
    onPick: (Int) -> Unit,
    where: Where,
) {
    val base = if (tablet) 26f else 22f
    val biggest = if (tablet) 300.dp else 220.dp
    val back by animateFloatAsState(if (chosen != null) 1f else 0f, tween(STEP_BACK_MILLIS), label = "back")
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Side by side under a wide picture; one above the other in a tall, narrow column beside it.
            val stacked = maxHeight > maxWidth * 1.4f
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(
                    page.question.orEmpty(),
                    style = Tale.type.heading.copy(fontSize = (base * textScale).sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.stepBack(back),
                )
                // The cards share the room left under the question, each as big as it allows.
                val cards: @Composable (Modifier) -> Unit = { weight ->
                    page.choices.forEachIndexed { i, choice ->
                        ChoiceCard(
                            choice,
                            i,
                            biggest,
                            read = readingChoice == i,
                            picked = chosen == i,
                            modifier = weight.stepBack(if (chosen != null && chosen != i) back else 0f),
                            onPlaced = { where.cards[i] = it },
                        ) { onPick(i) }
                    }
                }
                if (stacked) {
                    cards(Modifier.weight(1f, fill = false))
                } else {
                    Row(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        cards(Modifier.weight(1f, fill = false))
                    }
                }
            }
        }
    }
}

/** Fades out and shrinks to 90% as [back] goes from 0 to 1: what wasn't picked steps back. */
private fun Modifier.stepBack(back: Float): Modifier =
    graphicsLayer {
        alpha = 1f - back
        scaleX = 1f - 0.1f * back
        scaleY = 1f - 0.1f * back
    }

/**
 * A choice card: one tap picks it and the story goes that way. It lifts a little while its label is [read] aloud.
 * Once [picked], it lights up with a gold glow (docs/page-turn.md §4). [onPlaced] gets its picture's bounds on screen.
 */
@Composable
private fun ChoiceCard(
    choice: Choice,
    index: Int,
    biggest: Dp,
    read: Boolean,
    picked: Boolean,
    modifier: Modifier,
    onPlaced: (Rect) -> Unit,
    onTap: () -> Unit,
) {
    val corner = 22.dp
    val shape = RoundedCornerShape(corner)
    val interaction = remember { MutableInteractionSource() }
    val lift by animateFloatAsState(if (read) 1.06f else 1f, tween(250), label = "lift")
    val glow = remember { Animatable(0f) }
    LaunchedEffect(picked) { if (picked) glow.animateTo(1f, tween(GLOW_MILLIS, easing = LinearEasing)) }
    Column(
        modifier
            .graphicsLayer {
                // It grows to 114%, then settles at 110%.
                val scale = if (glow.value > 0f) rise(glow.value, 1f, 1.14f, 1.1f) else lift
                scaleX = scale
                scaleY = scale
            }.squish(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onTap)
            .testTag("choice-$index"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .weight(1f, fill = false)
                .sizeIn(maxWidth = biggest, maxHeight = biggest)
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .onGloballyPositioned { onPlaced(it.boundsInRoot()) }
                .drawBehind {
                    val g = glow.value
                    if (g <= 0f) return@drawBehind
                    val radius = corner.toPx()
                    // A soft gold glow, 40 dp at 80% settling to 30 dp at 65%: the card's shape ever bigger and fainter
                    // behind it (filled, which draws on the GPU everywhere; wide strokes don't on every device)…
                    val reach = rise(g, 0f, 40.dp.toPx(), 30.dp.toPx())
                    val strength = rise(g, 0f, 0.8f, 0.65f)
                    val each = 1f - (1f - strength / 2f).pow(1f / GLOW_RINGS)
                    for (i in GLOW_RINGS downTo 1) behind(reach * i / GLOW_RINGS, radius, TaleColors.LanternGold, each)
                    // …and a gold ring around the card, 6 dp at 60% settling to 4 dp at 50%.
                    behind(rise(g, 0f, 6.dp.toPx(), 4.dp.toPx()), radius, TaleColors.LanternGold, rise(g, 0f, 0.6f, 0.5f))
                }.shadow(4.dp, shape)
                .clip(shape)
                .border(4.dp, TaleColors.LanternGold, shape),
        ) {
            StoryImage(choice.image, ImageKind.CARD, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Text(choice.label, style = Tale.type.label, textAlign = TextAlign.Center)
    }
}

/** The card's rounded shape, [out] bigger all round, filled behind it: the card covers all but a band [out] wide. */
private fun DrawScope.behind(
    out: Float,
    radius: Float,
    color: Color,
    alpha: Float,
) = drawRoundRect(
    color,
    topLeft = Offset(-out, -out),
    size = Size(size.width + 2f * out, size.height + 2f * out),
    cornerRadius = CornerRadius(radius + out),
    alpha = alpha,
)

/** A value that rises from [from] to [peak] in the first 45% of [t] (0 to 1), then settles to [rest]. */
private fun rise(
    t: Float,
    from: Float,
    peak: Float,
    rest: Float,
): Float =
    if (t < 0.45f) {
        from + (peak - from) * Ease.outCubic(t / 0.45f)
    } else {
        peak + (rest - peak) * Ease.inOutSine((t - 0.45f) / 0.55f)
    }

/**
 * The page's illustration, cropped to fill its box, with a slow zoom so it feels alive (plan.md §3.2). The zoom stays
 * inside the box, and the edge toward the text melts into the paper: the bottom one when [meltsDown], else the end one.
 * Until it's [shown] the box is bare: nothing is loaded, drawn or zoomed. Then it fades in once it's loaded.
 */
@Composable
private fun Picture(
    page: ReaderPage,
    shown: Boolean,
    meltsDown: Boolean,
    modifier: Modifier,
) {
    Box(
        modifier
            .clipToBounds()
            .background(TaleColors.PictureMat)
            .drawWithContent {
                drawContent()
                val melt = MELT.toPx()
                val stops = arrayOf(0f to Color.Transparent, 0.55f to TaleColors.Paper.copy(alpha = 0.7f), 1f to TaleColors.Paper)
                val brush =
                    when {
                        meltsDown -> {
                            Brush.verticalGradient(*stops, startY = size.height - melt, endY = size.height)
                        }

                        layoutDirection == LayoutDirection.Ltr -> {
                            Brush.horizontalGradient(
                                *stops,
                                startX = size.width - melt,
                                endX = size.width,
                            )
                        }

                        else -> {
                            Brush.horizontalGradient(*stops, startX = melt, endX = 0f)
                        }
                    }
                drawRect(brush)
            },
    ) {
        if (shown) {
            val drift = rememberInfiniteTransition(label = "drift")
            val zoom by drift.animateFloat(
                initialValue = 1f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(tween(18_000), RepeatMode.Reverse),
                label = "zoom",
            )
            val appear = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            StoryImage(
                page.image,
                ImageKind.PAGE,
                contentDescription = null,
                modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        // An offscreen layer only while it fades. Not ModulateAlpha: on iOS that bakes the alpha into
                        // the recording, so the picture would stay as faint as on its first frame.
                        alpha = appear.value
                    },
                onLoaded = { scope.launch { appear.animateTo(1f, tween(PICTURE_FADE_MILLIS, easing = LinearEasing)) } },
            )
        }
    }
}

/**
 * The page text, word by word as the clock reaches each one, in a window a few lines tall. When the words reach the
 * line below the window, the text slides up a line and the oldest line fades away at the top. Once every word is
 * there, a child can scroll back up.
 *
 * The whole text is laid out from the start, and the words not read yet are erased while drawing, so no word ever
 * jumps to another line and nothing is measured again on each frame.
 */
@Composable
private fun RevealingText(
    page: ReaderPage,
    clock: State<Long>,
    style: TextStyle,
    windowHeight: Dp,
    scrollable: Boolean,
) {
    var layout by remember(page.nodeId) { mutableStateOf<TextLayoutResult?>(null) }
    val scroll = rememberScrollState()
    val words = page.words
    // The end of the last word that has started to appear. Keyed on the clock, not only the page: the page beneath
    // becomes the page in front without being composed again, and its clock goes from NotYet to the live one. Kept on
    // NotYet, the window would never follow the words down.
    val shownEnd by remember(page, clock) {
        derivedStateOf { words.lastOrNull { it.atMillis <= clock.value }?.end ?: 0 }
    }
    val target by remember(page, clock) {
        derivedStateOf {
            val text = layout ?: return@derivedStateOf 0
            if (shownEnd == 0) return@derivedStateOf 0
            val bottom = text.getLineBottom(text.getLineForOffset(shownEnd - 1))
            (bottom - scroll.viewportSize).roundToInt().coerceAtLeast(0)
        }
    }
    LaunchedEffect(target) { scroll.animateScrollTo(target, tween(600)) }
    val fade = with(LocalDensity.current) { (style.fontSize * 0.9f).toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = windowHeight)
            .fadeTopWhenScrolled(scroll, fade)
            .verticalScroll(scroll, enabled = scrollable),
    ) {
        Text(
            page.text,
            style = style,
            onTextLayout = { layout = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // Erasing words needs a layer of its own, only while some have come and some haven't.
                        compositingStrategy =
                            if (words.revealing(clock.value)) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                    }.drawWithContent {
                        val now = clock.value
                        // None yet (a page beneath the one in front): nothing to draw, nothing to erase.
                        if (words.isNotEmpty() && now < words.first().atMillis) return@drawWithContent
                        drawContent()
                        if (!words.revealing(now)) return@drawWithContent
                        val text = layout ?: return@drawWithContent
                        val waiting = words.indexOfFirst { it.atMillis > now }
                        // Erase every word that hasn't come yet, then fade in the ones arriving. Rectangles over their
                        // lines, not the letters' outlines: those are worked out on the CPU on some devices.
                        if (waiting >= 0) erase(text, words[waiting].start, page.text.length, 1f)
                        for (i in (if (waiting >= 0) waiting else words.size) - 1 downTo 0) {
                            val shown = (now - words[i].atMillis).toFloat() / WORD_FADE_MILLIS
                            if (shown >= 1f) break
                            erase(text, words[i].start, words[i].end, 1f - shown)
                        }
                    },
        )
    }
}

/**
 * Fades the top [fade] pixels of a scrolled window, so the oldest line melts away instead of being cut. The fade grows
 * with the first [fade] pixels of scrolling, so a text a hair taller than its window doesn't dim its first line.
 */
private fun Modifier.fadeTopWhenScrolled(
    scroll: ScrollState,
    fade: Float,
): Modifier =
    // The fade needs a layer of its own, only once the text is scrolled.
    graphicsLayer { compositingStrategy = if (scroll.value > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
        .drawWithContent {
            drawContent()
            val strength = (scroll.value / fade).coerceIn(0f, 1f)
            if (strength > 0f) {
                drawRect(
                    Brush.verticalGradient(0f to Color.Black.copy(alpha = 1f - strength), 1f to Color.Black, startY = 0f, endY = fade),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

@Composable
private fun EndPanel(
    state: ReaderUiState,
    onEvent: (ReaderEvent) -> Unit,
    onHome: () -> Unit,
) {
    val page = state.page ?: return
    val strings = LocalStrings.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Text(strings.theEnd, style = Tale.type.title)
        Box {
            EndingBadge(page.image, found = true, size = 112.dp)
            if (state.newEnding) {
                Text(
                    strings.newEnding,
                    style = Tale.type.label,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(50))
                            .background(TaleColors.LanternGold)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Text(page.endingTitle.orEmpty(), style = Tale.type.heading, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(TaleIcons.Star, contentDescription = null, tint = TaleColors.LanternGold, modifier = Modifier.size(26.dp))
            Text(strings.endingsFound(state.endingsFound, state.endingsTotal), style = Tale.type.label)
        }
        PillButton(strings.tryAnotherPath, onClick = { onEvent(ReaderEvent.TryAnotherPath) }, icon = TaleIcons.Rewind)
        PillButton(strings.backToShelf, onClick = onHome, icon = TaleIcons.Home, primary = false)
    }
}
