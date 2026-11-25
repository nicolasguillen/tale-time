package com.nicolasguillen.taletime.feature.cover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nicolasguillen.taletime.designsystem.EndingBadge
import com.nicolasguillen.taletime.designsystem.HomeButton
import com.nicolasguillen.taletime.designsystem.PillButton
import com.nicolasguillen.taletime.designsystem.Tale
import com.nicolasguillen.taletime.designsystem.TaleColors
import com.nicolasguillen.taletime.designsystem.TaleIcons
import com.nicolasguillen.taletime.designsystem.squish
import com.nicolasguillen.taletime.domain.Book
import com.nicolasguillen.taletime.domain.Download
import com.nicolasguillen.taletime.domain.Language
import com.nicolasguillen.taletime.feature.parents.ParentalGate
import com.nicolasguillen.taletime.feature.shelf.BookCover
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
            viewModel.onEvent(CoverEvent.Hidden)
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
    Box(Modifier.fillMaxSize().background(TaleColors.Paper)) {
        val book = state.book
        if (book != null) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val wide = maxWidth > maxHeight && maxWidth >= 600.dp
                // Tapping the cover opens the book, like the big button: it continues a story that's under way. Before
                // the book is downloaded, it downloads it in the language offered first.
                val language = LocalLanguage.current.code
                val open =
                    when {
                        !book.open || book.downloading -> null
                        book.downloaded -> ({ onRead(!book.inProgress) })
                        else -> book.entry.firstChoice(language)?.let { first -> { onEvent(CoverEvent.Download(first)) } }
                    }
                if (wide) {
                    Row(
                        Modifier.fillMaxSize().padding(start = 96.dp, end = 32.dp, top = 24.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TappableCover(book, open, Modifier.fillMaxHeight())
                        Details(book, onRead, onEvent, onAskGrownUp = { gate = true }, Modifier.weight(1f))
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        TappableCover(book, open, Modifier.padding(top = 64.dp).widthIn(max = 320.dp).fillMaxWidth(0.72f))
                        Details(book, onRead, onEvent, onAskGrownUp = { gate = true }, Modifier.fillMaxWidth())
                    }
                }
                HomeButton(onClick = onBack, modifier = Modifier.padding(12.dp))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(
    book: Book,
    onRead: (restart: Boolean) -> Unit,
    onEvent: (CoverEvent) -> Unit,
    onAskGrownUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    // The title is on the cover already.
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (book.entry.ageBand.isNotEmpty()) Text(strings.ages(book.entry.ageBand), style = Tale.type.small)
        val download = book.download
        when {
            !book.open -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(TaleIcons.Lock, contentDescription = null, tint = TaleColors.NightBlue, modifier = Modifier.size(28.dp))
                    Text(strings.askAGrownUp, style = Tale.type.body, modifier = Modifier.weight(1f, fill = false))
                }
                PillButton(strings.forGrownUps, onClick = onAskGrownUp, icon = TaleIcons.Lock, primary = false)
            }

            download is Download.Running -> {
                DownloadBar(download.fraction)
            }

            !book.downloaded -> {
                if (download is Download.Failed) Text(strings.downloadFailed, style = Tale.type.body)
                if (book.entry.downloadable) {
                    LanguageChoices(book, onEvent, primaryFirst = true)
                } else {
                    // The shelf that ships inside the app, before the first launch online.
                    Text(strings.needsInternet, style = Tale.type.body)
                    PillButton(strings.tryAgain, onClick = { onEvent(CoverEvent.Retry) }, icon = TaleIcons.Restart)
                }
            }

            else -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (book.inProgress) {
                        PillButton(strings.continueReading, onClick = { onRead(false) }, icon = TaleIcons.Play)
                        PillButton(strings.startOver, onClick = { onRead(true) }, icon = TaleIcons.Restart, primary = false)
                    } else {
                        PillButton(strings.read, onClick = { onRead(true) }, icon = TaleIcons.Play)
                    }
                }
                if (download is Download.Failed) Text(strings.downloadFailed, style = Tale.type.body)
                // Its other languages: one tap reads it in one of them (downloading it first if needed).
                LanguageChoices(book, onEvent, primaryFirst = false)
            }
        }
        if (book.downloaded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.endingsFoundOf(book.endingsFound.size, book.endings.size), style = Tale.type.label)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (ending in book.endings) {
                        EndingBadge(book.bundle?.image(ending.image), found = ending.id in book.endingsFound)
                    }
                }
            }
        }
    }
}

/**
 * One button per language the book can be read in, each in its own words. Before it's downloaded ([primaryFirst]),
 * the app's language comes first and stands out, with the size of each download; once it's downloaded, the others,
 * quieter, to switch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChoices(
    book: Book,
    onEvent: (CoverEvent) -> Unit,
    primaryFirst: Boolean,
) {
    val strings = LocalStrings.current
    val appLanguage = LocalLanguage.current.code
    val first = book.entry.firstChoice(appLanguage)
    val reading = book.bundle?.reading
    val offered =
        book.entry.languages.keys
            .sortedBy { if (it == first) 0 else 1 }
            .filter { it != reading }
    if (offered.isEmpty()) return
    val installed =
        book.bundle
            ?.story
            ?.languages
            .orEmpty()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (code in offered) {
            val name = Language.of(code)?.nativeName ?: code.uppercase()
            val size = book.entry.languages[code]?.bytes
            val label = if (code in installed || size == null) name else "$name · ${strings.megabytes(size)}"
            PillButton(
                label,
                onClick = { onEvent(CoverEvent.Download(code)) },
                icon = if (code in installed) null else TaleIcons.Download,
                primary = primaryFirst && code == first,
            )
        }
    }
}

/** How far the download is: a thick bar that fills, big enough for a child to watch. */
@Composable
private fun DownloadBar(fraction: Float) {
    val strings = LocalStrings.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
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

@Composable
private fun UnlockSheet(
    book: Book,
    unlock: UnlockUiState,
    onEvent: (CoverEvent) -> Unit,
) {
    val strings = LocalStrings.current
    Box(
        Modifier
            .fillMaxSize()
            .background(TaleColors.Scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(TaleColors.Cream)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
}
