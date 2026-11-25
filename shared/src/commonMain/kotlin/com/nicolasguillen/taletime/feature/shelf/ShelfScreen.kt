package com.nicolasguillen.taletime.feature.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nicolasguillen.taletime.designsystem.ImageKind
import com.nicolasguillen.taletime.designsystem.PillButton
import com.nicolasguillen.taletime.designsystem.PillTab
import com.nicolasguillen.taletime.designsystem.PillTabs
import com.nicolasguillen.taletime.designsystem.RoundButton
import com.nicolasguillen.taletime.designsystem.StoryImage
import com.nicolasguillen.taletime.designsystem.Tale
import com.nicolasguillen.taletime.designsystem.TaleColors
import com.nicolasguillen.taletime.designsystem.TaleIcons
import com.nicolasguillen.taletime.designsystem.squish
import com.nicolasguillen.taletime.domain.Book
import com.nicolasguillen.taletime.domain.Download
import com.nicolasguillen.taletime.feature.parents.ParentalGate
import com.nicolasguillen.taletime.i18n.LocalLanguage
import com.nicolasguillen.taletime.i18n.LocalStrings
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun ShelfRoute(
    onOpenBook: (String) -> Unit,
    onOpenParents: () -> Unit,
    viewModel: ShelfViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShelfScreen(state, viewModel::onEvent, onOpenBook, onOpenParents)
}

@Composable
fun ShelfScreen(
    state: ShelfUiState,
    onEvent: (ShelfEvent) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenParents: () -> Unit,
) {
    val strings = LocalStrings.current
    var gate by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(TaleColors.Paper)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tale Time", style = Tale.type.title, modifier = Modifier.weight(1f))
                RoundButton(
                    icon = TaleIcons.Parents,
                    contentDescription = strings.forGrownUps,
                    onClick = { gate = true },
                    size = 48.dp,
                    container = TaleColors.Cream,
                    content = TaleColors.InkSoft,
                )
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                PillTabs(
                    tabs = listOf(PillTab(strings.myBooks, TaleIcons.Books), PillTab(strings.store, TaleIcons.Store)),
                    selected = state.tab?.ordinal ?: -1,
                    onSelect = { onEvent(ShelfEvent.Pick(ShelfTab.entries[it])) },
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                )
            }
            // Each tab is its own shelf, scrolled to the top when it's picked.
            key(state.tab) {
                Bookshelf(
                    books = state.shown,
                    onOpenBook = onOpenBook,
                    // In the Store, what each book costs, on a tag under it.
                    tag =
                        if (state.tab == ShelfTab.STORE) {
                            { book -> state.price(book)?.let { PriceTag(it) } }
                        } else {
                            null
                        },
                    empty = {
                        if (!state.loading) {
                            when (state.tab) {
                                ShelfTab.MY_BOOKS -> {
                                    EmptyShelf(strings.noBooksYet, strings.store, TaleIcons.Store) {
                                        onEvent(ShelfEvent.Pick(ShelfTab.STORE))
                                    }
                                }

                                ShelfTab.STORE -> {
                                    EmptyShelf(strings.everyBookIsYours, strings.myBooks, TaleIcons.Books) {
                                        onEvent(ShelfEvent.Pick(ShelfTab.MY_BOOKS))
                                    }
                                }

                                null -> {}
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
        if (gate) {
            ParentalGate(
                onPassed = {
                    gate = false
                    onOpenParents()
                },
                onDismiss = { gate = false },
            )
        }
    }
}

/** Room above the books on each shelf. */
private val Headroom = 28.dp

/** The books' sides to the screen's, and between two books. */
private val ShelfSide = 24.dp
private val BookGap = 20.dp

/**
 * A plank: its top, its front edge (deep enough for the Store's price labels), and its shadow. Books stand a little way
 * into the top, so they look set on it. Every shelf is the same, so switching tabs moves nothing.
 */
private val PlankTop = 10.dp
private val PlankFront = 24.dp
private val PlankShadow = 14.dp
private val BookSink = 5.dp
private val BelowBooks = PlankTop + PlankFront + PlankShadow - BookSink

/**
 * Wooden shelves with the books standing on them, as many to a shelf as fit, each with its [tag] on the shelf's edge
 * under it if there's one. Whole shelves fill the screen, down to its bottom, empty ones too; more books than that
 * scroll. With no books, [empty] stands on the first shelf.
 */
@Composable
private fun Bookshelf(
    books: List<Book>,
    onOpenBook: (String) -> Unit,
    tag: (@Composable (Book) -> Unit)?,
    empty: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val available = maxWidth - ShelfSide * 2
        val smallest = if (maxWidth >= 600.dp) 200.dp else 140.dp
        val fit = ((available + BookGap) / (smallest + BookGap)).toInt().coerceAtLeast(2)
        val natural = (available - BookGap * (fit - 1)) / fit / BOOK_ASPECT
        // The nearest whole number of shelves to the screen's height: the books a little shorter to fit one more, or
        // the shelves a little farther apart to fill it.
        val onScreen = (maxHeight / (Headroom + natural + BelowBooks)).roundToInt().coerceAtLeast(1)
        val bookHeight = minOf(natural, maxHeight / onScreen - Headroom - BelowBooks).coerceAtLeast(96.dp)
        val shelfHeight = maxOf(maxHeight / onScreen, Headroom + bookHeight + BelowBooks)
        val bookWidth = bookHeight * BOOK_ASPECT
        val columns = ((available + BookGap) / (bookWidth + BookGap)).toInt().coerceAtLeast(1)
        val rows = books.chunked(columns)
        val shelves = maxOf(rows.size, onScreen)
        LazyColumn(Modifier.fillMaxSize()) {
            items(shelves) { index ->
                val row = rows.getOrNull(index)
                Shelf(
                    Modifier.fillMaxWidth().height(shelfHeight),
                    books = {
                        if (row != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(BookGap)) {
                                for (book in row) {
                                    BookOnShelf(book, onClick = { onOpenBook(book.id) }, Modifier.width(bookWidth))
                                }
                            }
                        } else if (index == 0) {
                            Box(Modifier.fillMaxWidth().height(bookHeight), contentAlignment = Alignment.Center) { empty() }
                        }
                    },
                    tags = {
                        if (row != null && tag != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(BookGap), verticalAlignment = Alignment.CenterVertically) {
                                for (book in row) {
                                    Box(Modifier.width(bookWidth), contentAlignment = Alignment.Center) { tag(book) }
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

/** One shelf: a wooden plank across the screen with [books] standing on it, and [tags] on its front edge. */
@Composable
private fun Shelf(
    modifier: Modifier,
    books: @Composable () -> Unit,
    tags: @Composable () -> Unit,
) {
    Box(modifier) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = ShelfSide - 10.dp)) {
            val wood = RoundedCornerShape(4.dp)
            Column(Modifier.fillMaxWidth().clip(wood)) {
                Box(Modifier.fillMaxWidth().height(PlankTop).background(TaleColors.ShelfTop))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(PlankFront)
                        .background(Brush.verticalGradient(listOf(TaleColors.ShelfFront, TaleColors.ShelfFrontShade))),
                )
            }
            Box(
                Modifier
                    .padding(horizontal = 6.dp)
                    .fillMaxWidth()
                    .height(PlankShadow)
                    .background(Brush.verticalGradient(listOf(TaleColors.ShelfShadow, Color.Transparent))),
            )
        }
        Box(Modifier.align(Alignment.BottomStart).padding(start = ShelfSide, end = ShelfSide, bottom = BelowBooks)) {
            books()
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = ShelfSide, end = ShelfSide, bottom = PlankShadow)
                .height(PlankFront),
            contentAlignment = Alignment.CenterStart,
        ) {
            tags()
        }
    }
}

/** A price label on the shelf's front edge, square like the ones in a shop. "FREE" in capitals; a price as the store writes it. */
@Composable
private fun PriceTag(price: Price) {
    Text(
        when (price) {
            Price.Free -> LocalStrings.current.free.uppercase()
            is Price.Paid -> price.price
        },
        style = Tale.type.label.copy(fontSize = 14.sp, lineHeight = 16.sp, letterSpacing = 1.sp),
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier =
            Modifier
                .shadow(1.dp, RectangleShape)
                .background(TaleColors.Cream)
                .border(1.dp, TaleColors.Line, RectangleShape)
                .widthIn(min = 64.dp)
                .padding(horizontal = 10.dp, vertical = 1.dp),
    )
}

@Composable
private fun BookOnShelf(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    BookCover(
        book,
        modifier
            .squish(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        downloadArrow = false,
    )
}

/** What an empty shelf says, with a button to the other tab. */
@Composable
private fun EmptyShelf(
    text: String,
    button: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Column(
        Modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, style = Tale.type.label, color = TaleColors.InkSoft, textAlign = TextAlign.Center)
        PillButton(button, onClick = onClick, icon = icon)
    }
}

/** A book's spine, beside its cover, as a share of the cover's width. */
private const val SPINE = 0.07f

/** A book's width to its height: its cover and its spine. */
val BOOK_ASPECT = ImageKind.COVER.aspect * (1 + SPINE)

/**
 * A book: a hardcover with its spine on the left, beside the cover so the cover's picture stays whole, and its title
 * drawn over the calm bottom quarter of the cover (covers have no text in them). A badge in its corner shows a lock
 * while the book is paid and not bought, a ring that fills while it downloads, and, with [downloadArrow], an arrow until
 * it's downloaded. The title grows with the cover, so it reads as the book's title on the big cover too.
 */
@Composable
fun BookCover(
    book: Book,
    modifier: Modifier = Modifier,
    downloadArrow: Boolean = true,
) {
    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp)
    Row(modifier.aspectRatio(BOOK_ASPECT).shadow(6.dp, shape).clip(shape)) {
        Spine(Modifier.weight(SPINE).fillMaxHeight())
        Cover(book, downloadArrow, Modifier.weight(1f).fillMaxHeight())
    }
}

/**
 * The spine: the same night-blue cloth on every book, like one collection, shaded round, with gilt bands near its ends
 * and the hinge's groove where it meets the cover.
 */
@Composable
private fun Spine(modifier: Modifier) {
    Box(modifier.background(TaleColors.NightBlue)) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to TaleColors.SpineShade,
                    0.45f to TaleColors.SpineSheen,
                    1f to TaleColors.SpineShade,
                ),
            ),
        )
        for (band in listOf(Alignment.TopCenter, Alignment.BottomCenter)) {
            Box(
                Modifier
                    .align(band)
                    .padding(vertical = 14.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(TaleColors.LanternGold),
            )
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(1.5.dp)
                .background(TaleColors.SpineGroove),
        )
    }
}

@Composable
private fun Cover(
    book: Book,
    downloadArrow: Boolean,
    modifier: Modifier,
) {
    val strings = LocalStrings.current
    BoxWithConstraints(modifier) {
        val titleSize = (maxWidth.value * 0.075f).coerceIn(19f, 34f).sp
        val badge = (maxWidth.value * 0.2f).coerceIn(44f, 64f).dp
        StoryImage(book.cover, ImageKind.COVER, contentDescription = null, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(TaleColors.Cream.copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                book.title(LocalLanguage.current.code),
                style = Tale.type.label.copy(fontSize = titleSize),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val download = book.download
        val corner =
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(badge)
                .clip(CircleShape)
        when {
            !book.open -> {
                Badge(corner, TaleColors.NightBlue) {
                    Icon(
                        TaleIcons.Lock,
                        contentDescription = strings.locked,
                        tint = TaleColors.Cream,
                        modifier =
                            Modifier.size(badge * 0.55f),
                    )
                }
            }

            download is Download.Running -> {
                Badge(corner.semantics { contentDescription = strings.downloading }, TaleColors.Cream) {
                    CircularProgressIndicator(
                        progress = { download.fraction },
                        color = TaleColors.NightBlue,
                        trackColor = TaleColors.Line,
                        strokeWidth = 5.dp,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                    )
                }
            }

            downloadArrow && !book.downloaded -> {
                Badge(corner, TaleColors.NightBlue) {
                    Icon(
                        TaleIcons.Download,
                        contentDescription = strings.notDownloaded,
                        tint = TaleColors.Cream,
                        modifier = Modifier.size(badge * 0.55f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(
    modifier: Modifier,
    color: Color,
    content: @Composable () -> Unit,
) {
    Box(modifier.background(color), contentAlignment = Alignment.Center) { content() }
}
