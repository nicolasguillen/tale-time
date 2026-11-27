package com.nicolasguillen.taletime.feature.cover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nicolasguillen.taletime.designsystem.EndingBadge
import com.nicolasguillen.taletime.designsystem.HomeButton
import com.nicolasguillen.taletime.designsystem.PillButton
import com.nicolasguillen.taletime.designsystem.RoundButton
import com.nicolasguillen.taletime.designsystem.Tale
import com.nicolasguillen.taletime.designsystem.TaleColors
import com.nicolasguillen.taletime.designsystem.TaleIcons
import com.nicolasguillen.taletime.designsystem.squish
import com.nicolasguillen.taletime.domain.Book
import com.nicolasguillen.taletime.domain.Download
import com.nicolasguillen.taletime.domain.Language
import com.nicolasguillen.taletime.feature.parents.ParentalGate
import com.nicolasguillen.taletime.feature.shelf.BookCover
import com.nicolasguillen.taletime.feature.shelf.Price
import com.nicolasguillen.taletime.i18n.LocalLanguage
import com.nicolasguillen.taletime.i18n.LocalStrings
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CoverRoute(
    storyId: String,
    onRead: (restart: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: CoverViewModel = koinViewModel(key = storyId) { parametersOf(storyId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Only a cover on screen reads its title.
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(CoverEvent.Shown)
        onStopOrDispose { viewModel.onEvent(CoverEvent.Hidden) }
    }
    CoverScreen(
        state,
        viewModel::onEvent,
        onRead = { restart ->
            // The title stops before the first page is read aloud.
            viewModel.onEvent(CoverEvent.OpenBook)
            onRead(restart)
        },
        onBack,
    )
}

@Composable
fun CoverScreen(
    state: CoverUiState,
    onEvent: (CoverEvent) -> Unit,
    onRead: (restart: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var gate by remember { mutableStateOf(false) }
    var languages by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(TaleColors.Paper)) {
        val book = state.book
        if (book != null) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val wide = maxWidth > maxHeight && maxWidth >= 600.dp
                // The language it's read in first: the app's own, when the story is published in it.
                val first = book.entry.firstChoice(LocalLanguage.current.code)
                val download = first?.let { { onEvent(CoverEvent.Download(it)) } }
                // Tapping the cover opens the book, like the big button: it continues a story that's under way. Before
                // the book is downloaded, it downloads it.
                val open =
                    when {
                        !book.open || book.downloading -> null
                        book.downloaded -> ({ onRead(!book.inProgress) })
                        else -> download
                    }
                val actions: @Composable (Modifier) -> Unit = { modifier ->
                    Actions(book, onRead, onEvent, download, onBuy = { gate = true }, modifier)
                }
                if (wide) {
                    // The cover on the left; on the right its caption at the top and the actions at the bottom.
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(48.dp),
                    ) {
                        TappableCover(book, open, Modifier.fillMaxHeight())
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            Caption(book, state.price, centered = false, Modifier.weight(1f).verticalScroll(rememberScrollState()))
                            actions(Modifier.widthIn(max = 560.dp).fillMaxWidth())
                        }
                    }
                } else {
                    // The cover and its caption scroll; the actions stay at the bottom of the screen.
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            TappableCover(book, open, Modifier.padding(top = 64.dp).widthIn(max = 320.dp).fillMaxWidth(0.72f))
                            Caption(book, state.price, centered = true)
                        }
                        actions(
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .widthIn(max = 608.dp)
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                        )
                    }
                }
                HomeButton(onClick = onBack, modifier = Modifier.padding(12.dp))
                // Once it's downloaded, every language it's published in is on the device: the cog switches between them.
                if (book.downloaded && book.languages.size > 1) {
                    RoundButton(
                        TaleIcons.Cog,
                        contentDescription = LocalStrings.current.storyLanguage,
                        onClick = { languages = true },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                        size = 60.dp,
                        container = TaleColors.Cream,
                        content = TaleColors.Ink,
                    )
                }
            }
        } else if (state.missing) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(LocalStrings.current.storyMissing, style = Tale.type.heading)
                PillButton(LocalStrings.current.backToShelf, onClick = onBack, icon = TaleIcons.Home)
            }
        }
        state.unlock?.let { unlock ->
            if (book != null) UnlockSheet(book, unlock, onEvent)
        }
        if (languages && book != null) {
            LanguageSheet(
                book,
                onPick = { code ->
                    languages = false
                    onEvent(CoverEvent.Download(code))
                },
                onDismiss = { languages = false },
            )
        }
        if (gate) {
            ParentalGate(
                onPassed = {
                    gate = false
                    onEvent(CoverEvent.OpenUnlock)
                },
                onDismiss = { gate = false },
            )
        }
    }
}

/** The cover, with its title drawn on it as on the shelf. [onOpen] is null while the book is locked. */
@Composable
private fun TappableCover(
    book: Book,
    onOpen: (() -> Unit)?,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    BookCover(
        book,
        if (onOpen == null) {
            modifier
        } else {
            modifier
                .squish(interaction)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen)
        },
    )
}

/**
 * What the book is, under its cover like a caption, one "key: value" line each: the ages it's for, its languages'
 * flags, and until it's downloaded how many endings it has and its price ([price], null once bought).
 */
@Composable
private fun Caption(
    book: Book,
    price: Price?,
    centered: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Column(
        modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (book.entry.ageBand.isNotEmpty()) CaptionLine(strings.agesKey, strings.ageRange(book.entry.ageBand))
        // Once it's downloaded, the endings found say how many there are.
        val endings = book.endingCount
        if (!book.downloaded && endings != null) CaptionLine(strings.endingsKey, endings.toString())
        if (book.languages.isNotEmpty()) {
            val languages = book.languages.map { code -> code to Language.of(code) }
            CaptionLine(
                strings.languagesKey,
                languages.joinToString(" ") { (code, language) -> language?.flag ?: code.uppercase() },
                spoken = languages.joinToString(", ") { (code, language) -> language?.nativeName ?: code },
                valueSize = 20.sp,
            )
        }
        if (!book.downloaded && price != null) {
            CaptionLine(
                strings.priceKey,
                when (price) {
                    Price.Free -> strings.free
                    is Price.Paid -> price.price
                },
            )
        }
    }
}

/** "Key: value", the key quiet and the value in bold. [spoken] is what a screen reader says for the value, if not it. */
@Composable
private fun CaptionLine(
    key: String,
    value: String,
    spoken: String? = null,
    valueSize: TextUnit = TextUnit.Unspecified,
) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = TaleColors.InkSoft)) { append("$key: ") }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = valueSize)) { append(value) }
        },
        style = Tale.type.body,
        modifier = if (spoken == null) Modifier else Modifier.clearAndSetSemantics { contentDescription = "$key: $spoken" },
    )
}

/**
 * The bottom of the cover. From the Store (not downloaded): one big button, Download, or Buy forever for a paid book
 * not bought yet (behind the parental gate), and how far the download is. Once downloaded: the endings found so far,
 * then the big button to read it (Continue, with Start over beside or under it, once a reading is under way).
 *
 * @param download downloads it, in every language, or null when it can't be downloaded yet (the shelf inside the app).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(
    book: Book,
    onRead: (restart: Boolean) -> Unit,
    onEvent: (CoverEvent) -> Unit,
    download: (() -> Unit)?,
    onBuy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val state = book.download
    BoxWithConstraints(modifier) {
        val side = maxWidth >= 460.dp
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                book.downloaded -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(strings.endingsFoundOf(book.endingsFound.size, book.endings.size), style = Tale.type.label)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (ending in book.endings) {
                                EndingBadge(book.bundle?.image(ending.image), found = ending.id in book.endingsFound)
                            }
                        }
                    }
                    if (state is Download.Failed) Text(strings.downloadFailed, style = Tale.type.body)
                    if (state is Download.Running) {
                        DownloadBar(state.fraction)
                    } else if (!book.inProgress) {
                        BigButton(strings.read, icon = TaleIcons.Play, onClick = { onRead(true) })
                    } else if (side) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            StartOver(onRead)
                            BigButton(strings.continueReading, onClick = { onRead(false) }, Modifier.weight(1f), icon = TaleIcons.Play)
                        }
                    } else {
                        BigButton(strings.continueReading, icon = TaleIcons.Play, onClick = { onRead(false) })
                        StartOver(onRead, Modifier.fillMaxWidth())
                    }
                }

                state is Download.Running -> {
                    DownloadBar(state.fraction)
                }

                book.open && download == null -> {
                    // The shelf that ships inside the app, before the first launch online.
                    Text(strings.needsInternet, style = Tale.type.body)
                    BigButton(strings.tryAgain, icon = TaleIcons.Restart, onClick = { onEvent(CoverEvent.Retry) })
                }

                else -> {
                    if (state is Download.Failed) Text(strings.downloadFailed, style = Tale.type.body)
                    if (book.open) {
                        BigButton(strings.download, onClick = { download?.invoke() })
                    } else {
                        BigButton(strings.buyForever, icon = TaleIcons.Lock, onClick = onBuy)
                    }
                }
            }
        }
    }
}

/** The cover's main action, across the bottom. */
@Composable
private fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    icon: ImageVector? = null,
) {
    PillButton(text, onClick = onClick, modifier = modifier, icon = icon, large = true)
}

@Composable
private fun StartOver(
    onRead: (restart: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    PillButton(LocalStrings.current.startOver, onClick = { onRead(true) }, modifier = modifier, icon = TaleIcons.Restart, primary = false)
}

/** Every language the book is published in, each with its flag and in its own words; the one it's read in is picked. */
@Composable
private fun LanguageSheet(
    book: Book,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val reading = book.bundle?.reading
    Sheet(onDismiss = onDismiss) {
        Text(strings.storyLanguage, style = Tale.type.heading)
        for (code in book.languages) {
            val language = Language.of(code)
            val name = language?.let { "${it.flag}  ${it.nativeName}" } ?: code.uppercase()
            PillButton(
                name,
                onClick = { onPick(code) },
                icon = if (code == reading) TaleIcons.Check else null,
                primary = code == reading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PillButton(strings.close, onClick = onDismiss, primary = false, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * A card over the cover, on a dimmed background. A tap on the background closes it through [onDismiss]; without one,
 * it's ignored.
 */
@Composable
private fun Sheet(
    onDismiss: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(TaleColors.Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onDismiss?.invoke() }),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(TaleColors.Cream)
                // A tap on the card itself doesn't close it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** How far the download is: a thick bar that fills, big enough for a child to watch, where the big button was. */
@Composable
private fun DownloadBar(fraction: Float) {
    val strings = LocalStrings.current
    val shape = RoundedCornerShape(50)
    Box(Modifier.fillMaxWidth().heightIn(min = 68.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(shape)
                .background(TaleColors.Line)
                .semantics { contentDescription = strings.downloading },
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                    .clip(shape)
                    .background(TaleColors.NightBlue),
            )
        }
    }
}

@Composable
private fun UnlockSheet(
    book: Book,
    unlock: UnlockUiState,
    onEvent: (CoverEvent) -> Unit,
) {
    val strings = LocalStrings.current
    Sheet {
        Text(strings.unlock(book.title(LocalLanguage.current.code)), style = Tale.type.heading)
        Text(strings.unlockExplained, style = Tale.type.body)
        (unlock.message ?: strings.storeUnavailable.takeIf { unlock.storeUnavailable })?.let {
            Text(it, style = Tale.type.label, color = TaleColors.EarthBrown)
        }
        PillButton(
            unlock.price?.let(strings.buyFor) ?: strings.buy,
            onClick = { onEvent(CoverEvent.Buy) },
            enabled = unlock.price != null && !unlock.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        PillButton(
            strings.restorePurchases,
            onClick = { onEvent(CoverEvent.Restore) },
            primary = false,
            enabled = !unlock.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        PillButton(strings.notNow, onClick = { onEvent(CoverEvent.CloseUnlock) }, primary = false, modifier = Modifier.fillMaxWidth())
    }
}
