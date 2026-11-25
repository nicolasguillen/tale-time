package com.nicolasguillen.taletime.feature.reader

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nicolasguillen.taletime.designsystem.EndingBadge
import com.nicolasguillen.taletime.designsystem.HomeButton
import com.nicolasguillen.taletime.designsystem.ImageKind
import com.nicolasguillen.taletime.designsystem.PillButton
import com.nicolasguillen.taletime.designsystem.RoundButton
import com.nicolasguillen.taletime.designsystem.StoryImage
import com.nicolasguillen.taletime.designsystem.Tale
import com.nicolasguillen.taletime.designsystem.TaleColors
import com.nicolasguillen.taletime.designsystem.TaleIcons
import com.nicolasguillen.taletime.designsystem.squish
import com.nicolasguillen.taletime.i18n.LocalStrings
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
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

/** The page text sits in a window this many lines tall: the picture is the page, the text its caption. */
private const val TEXT_LINES_TALL = 4
private const val TEXT_LINES_WIDE = 6

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
    ReaderScreen(state, viewModel::onEvent, onHome, viewModel::narrationMillis)
}

/**
 * @param narrationMillis the reading clock of a page while its text is read aloud
 *   ([ReaderViewModel.narrationMillis]); a page keeps its own clock while it's null.
 */
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onEvent: (ReaderEvent) -> Unit,
    onHome: () -> Unit,
    narrationMillis: (nodeId: String) -> Long? = { null },
) {
    Box(Modifier.fillMaxSize().background(TaleColors.Paper)) {
        if (state.page == null) {
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
        val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight && maxWidth >= 600.dp
            val tablet = minOf(maxWidth, maxHeight) >= 600.dp
            val screenWidth = maxWidth
            val screenHeight = maxHeight
            AnimatedContent(
                targetState = state,
                contentKey = { it.page?.nodeId },
                transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(250)) },
                label = "page",
            ) { s ->
                val shown = s.page ?: return@AnimatedContent
                // Kept above the layouts, so turning a tablet doesn't start the page over.
                val clock = rememberReadingClock(shown, narrationMillis)
                if (wide) {
                    // The picture fills the height on the left, the text and the arrows sit beside it.
                    val pictureWidth = minOf(screenWidth - if (tablet) 360.dp else 300.dp, screenHeight * ImageKind.PAGE.aspect)
                    val pictureHeight = minOf(screenHeight, pictureWidth / NARROWEST_PICTURE)
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.width(pictureWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Picture(shown, Modifier.fillMaxWidth().height(pictureHeight))
                        }
                        Panel(
                            s,
                            clock,
                            onEvent,
                            onHome,
                            tablet,
                            TEXT_LINES_WIDE,
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)),
                        )
                    }
                } else {
                    // The picture across the top, as tall as its main action allows; the text below it.
                    val pictureHeight = minOf(screenWidth / NARROWEST_PICTURE, screenHeight - topInset - 220.dp)
                    Column(Modifier.fillMaxSize().padding(top = topInset)) {
                        Picture(shown, Modifier.fillMaxWidth().height(pictureHeight))
                        Panel(
                            s,
                            clock,
                            onEvent,
                            onHome,
                            tablet,
                            TEXT_LINES_TALL,
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                                ),
                        )
                    }
                }
            }
            HomeButton(onClick = onHome, modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp))
        }
    }
}

/**
 * The page's reading clock: milliseconds since its first word (from the start of its clip when it's narrated),
 * negative during the breath before it. While the page is read aloud, it's where the narrator is in the clip
 * ([narrationMillis]), so the words come as they're said and wait while the narration pauses (plan.md §7.4).
 * Otherwise it runs on frames, so it stops while the app is in the background. It's saved, so turning the device
 * doesn't start the page over.
 */
@Composable
private fun rememberReadingClock(
    page: ReaderPage,
    narrationMillis: (nodeId: String) -> Long?,
): State<Long> {
    val clock: MutableLongState = rememberSaveable(page.nodeId) { mutableLongStateOf(-FIRST_WORD_DELAY_MILLIS) }
    val end = page.readMillis
    val narrator by rememberUpdatedState(narrationMillis)
    LaunchedEffect(page.nodeId, end) {
        var last = withFrameMillis { it }
        while (clock.longValue < end) {
            val now = withFrameMillis { it }
            clock.longValue = narrator(page.nodeId) ?: (clock.longValue + (now - last).coerceAtMost(MAX_FRAME_STEP_MILLIS))
            last = now
        }
    }
    return clock
}

/** When the page is read: its last word has faded in, and the narrator has said it. */
private val ReaderPage.readMillis: Long
    get() = maxOf((words.lastOrNull()?.atMillis ?: 0L) + WORD_FADE_MILLIS, spokenUntilMillis ?: 0L)

/** The page's illustration, cropped to fill its box, with a slow zoom so it feels alive (plan.md §3.2). */
@Composable
private fun Picture(
    page: ReaderPage,
    modifier: Modifier,
) {
    val drift = rememberInfiniteTransition(label = "drift")
    val zoom by drift.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(18_000), RepeatMode.Reverse),
        label = "zoom",
    )
    Box(modifier.background(TaleColors.PictureMat)) {
        StoryImage(
            page.image,
            ImageKind.PAGE,
            contentDescription = null,
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                },
        )
    }
}

@Composable
private fun Panel(
    state: ReaderUiState,
    clock: State<Long>,
    onEvent: (ReaderEvent) -> Unit,
    onHome: () -> Unit,
    tablet: Boolean,
    lines: Int,
    modifier: Modifier,
) {
    val page = state.page ?: return
    AnimatedContent(
        targetState = state.phase,
        transitionSpec = { (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 6 }) togetherWith fadeOut(tween(200)) },
        label = "phase",
        modifier = modifier,
    ) { phase ->
        when (phase) {
            Phase.TEXT -> TextPanel(page, clock, state.canGoBack, state.textScale, tablet, lines, onEvent)
            Phase.CHOICE -> ChoicePanel(page, state.textScale, tablet, state.readingChoice, onEvent)
            Phase.END -> EndPanel(state, onEvent, onHome)
        }
    }
}

@Composable
private fun TextPanel(
    page: ReaderPage,
    clock: State<Long>,
    canGoBack: Boolean,
    textScale: Float,
    tablet: Boolean,
    lines: Int,
    onEvent: (ReaderEvent) -> Unit,
) {
    val read by remember(page.nodeId) { derivedStateOf { clock.value >= page.readMillis } }
    val size = (if (tablet) 27f else 23f) * textScale
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            RevealingText(
                page,
                clock,
                Tale.type.story.copy(fontSize = size.sp),
                windowHeight = with(LocalDensity.current) { (size * 1.5f * lines).sp.toDp() },
                scrollable = read,
            )
        }
        PageArrows(canGoBack, tablet, onEvent, showNext = true, nextPulses = read)
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
    // The end of the last word that has started to appear.
    val shownEnd by remember(page.nodeId) {
        derivedStateOf { words.lastOrNull { it.atMillis <= clock.value }?.end ?: 0 }
    }
    val target by remember(page.nodeId) {
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
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val text = layout ?: return@drawWithContent
                        val now = clock.value
                        val waiting = words.indexOfFirst { it.atMillis > now }
                        // Erase every word that hasn't come yet in one go, then fade in the ones arriving.
                        if (waiting >= 0) {
                            drawPath(
                                text.getPathForRange(words[waiting].start, page.text.length),
                                Color.Black,
                                blendMode = BlendMode.DstOut,
                            )
                        }
                        for (i in (if (waiting >= 0) waiting else words.size) - 1 downTo 0) {
                            val shown = (now - words[i].atMillis).toFloat() / WORD_FADE_MILLIS
                            if (shown >= 1f) break
                            val path = text.getPathForRange(words[i].start, words[i].end)
                            drawPath(path, Color.Black, alpha = 1f - shown, blendMode = BlendMode.DstOut)
                        }
                    },
        )
    }
}

/** Fades the top [fade] pixels of a scrolled window, so the oldest line melts away instead of being cut. */
private fun Modifier.fadeTopWhenScrolled(
    scroll: ScrollState,
    fade: Float,
): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (scroll.value > 0) {
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = fade),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

/** Back on the left and next on the right, like the corners of a book's pages. */
@Composable
private fun PageArrows(
    canGoBack: Boolean,
    tablet: Boolean,
    onEvent: (ReaderEvent) -> Unit,
    showNext: Boolean,
    nextPulses: Boolean = false,
) {
    val size = if (tablet) 80.dp else 68.dp
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (canGoBack) {
            RoundButton(
                TaleIcons.Previous,
                contentDescription = LocalStrings.current.previousPage,
                onClick = { onEvent(ReaderEvent.Previous) },
                size = size,
                container = TaleColors.Cream,
                content = TaleColors.NightBlue,
            )
        }
        Spacer(Modifier.weight(1f))
        if (showNext) {
            RoundButton(
                TaleIcons.Next,
                contentDescription = LocalStrings.current.nextPage,
                onClick = { onEvent(ReaderEvent.Next) },
                size = size,
                pulse = nextPulses,
            )
        }
    }
}

/** @param readingChoice the card whose label the narrator is reading: it lifts a little. */
@Composable
private fun ChoicePanel(
    page: ReaderPage,
    textScale: Float,
    tablet: Boolean,
    readingChoice: Int?,
    onEvent: (ReaderEvent) -> Unit,
) {
    val base = if (tablet) 26f else 22f
    val biggest = if (tablet) 300.dp else 220.dp
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
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
                )
                // The cards share the room left under the question, each as big as it allows.
                if (stacked) {
                    page.choices.forEachIndexed { i, choice ->
                        ChoiceCard(choice, biggest, readingChoice == i, Modifier.weight(1f, fill = false)) {
                            onEvent(ReaderEvent.TapChoice(i))
                        }
                    }
                } else {
                    Row(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        page.choices.forEachIndexed { i, choice ->
                            ChoiceCard(choice, biggest, readingChoice == i, Modifier.weight(1f, fill = false)) {
                                onEvent(ReaderEvent.TapChoice(i))
                            }
                        }
                    }
                }
            }
        }
        PageArrows(canGoBack = true, tablet = tablet, onEvent = onEvent, showNext = false)
    }
}

/** A choice card: one tap picks it and the story goes that way. It lifts a little while its label is [read] aloud. */
@Composable
private fun ChoiceCard(
    choice: Choice,
    biggest: Dp,
    read: Boolean,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val interaction = remember { MutableInteractionSource() }
    val lift by animateFloatAsState(if (read) 1.06f else 1f, tween(250), label = "lift")
    Column(
        modifier
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
            }.squish(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .weight(1f, fill = false)
                .sizeIn(maxWidth = biggest, maxHeight = biggest)
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .shadow(4.dp, shape)
                .clip(shape)
                .border(4.dp, TaleColors.LanternGold, shape),
        ) {
            StoryImage(choice.image, ImageKind.CARD, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Text(choice.label, style = Tale.type.label, textAlign = TextAlign.Center)
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
