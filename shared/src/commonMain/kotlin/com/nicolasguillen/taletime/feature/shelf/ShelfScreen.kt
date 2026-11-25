package com.nicolasguillen.taletime.feature.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nicolasguillen.taletime.designsystem.ImageKind
import com.nicolasguillen.taletime.designsystem.RoundButton
import com.nicolasguillen.taletime.designsystem.StoryImage
import com.nicolasguillen.taletime.designsystem.Tale
import com.nicolasguillen.taletime.designsystem.TaleColors
import com.nicolasguillen.taletime.designsystem.TaleIcons
import com.nicolasguillen.taletime.domain.Book
import com.nicolasguillen.taletime.domain.Download
import com.nicolasguillen.taletime.feature.parents.ParentalGate
import com.nicolasguillen.taletime.i18n.LocalLanguage
import com.nicolasguillen.taletime.i18n.LocalStrings
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ShelfRoute(
    onOpenBook: (String) -> Unit,
    onOpenParents: () -> Unit,
    viewModel: ShelfViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ShelfScreen(state, onOpenBook, onOpenParents)
}

@Composable
fun ShelfScreen(
    state: ShelfUiState,
    onOpenBook: (String) -> Unit,
    onOpenParents: () -> Unit,
) {
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
                    contentDescription = LocalStrings.current.forGrownUps,
                    onClick = { gate = true },
                    size = 48.dp,
                    container = TaleColors.Cream,
                    content = TaleColors.InkSoft,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.books, key = { it.id }) { book -> BookOnShelf(book, onClick = { onOpenBook(book.id) }) }
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

@Composable
private fun BookOnShelf(
    book: Book,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BookCover(book, Modifier.fillMaxWidth())
        // Endings found, once there's a story to find them in. The row keeps its height either way.
        Row(
            Modifier.size(width = 120.dp, height = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (book.downloaded) {
                Icon(TaleIcons.Star, contentDescription = null, tint = TaleColors.LanternGold, modifier = Modifier.size(22.dp))
                Text(
                    "${book.endingsFound.size}/${book.endings.size}",
                    style = Tale.type.label,
                    color = TaleColors.InkSoft,
                )
            }
        }
    }
}

/**
 * A cover with its title drawn over the calm bottom quarter (covers have no text in them), and a badge in its corner:
 * a lock while the book is paid and not bought, a download arrow until it's downloaded, and a ring that fills while it
 * downloads. The title grows with the cover, so it reads as the book's title on the big cover too.
 */
@Composable
fun BookCover(
    book: Book,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val strings = LocalStrings.current
    BoxWithConstraints(modifier.aspectRatio(ImageKind.COVER.aspect).shadow(6.dp, shape).clip(shape)) {
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
                Box(corner.background(TaleColors.NightBlue), contentAlignment = Alignment.Center) {
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
                Box(
                    corner.background(TaleColors.Cream).semantics { contentDescription = strings.downloading },
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { download.fraction },
                        color = TaleColors.NightBlue,
                        trackColor = TaleColors.Line,
                        strokeWidth = 5.dp,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                    )
                }
            }

            !book.downloaded -> {
                Box(corner.background(TaleColors.NightBlue), contentAlignment = Alignment.Center) {
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
