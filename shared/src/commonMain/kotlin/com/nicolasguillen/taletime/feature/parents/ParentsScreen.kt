package com.nicolasguillen.taletime.feature.parents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nicolasguillen.taletime.designsystem.HomeButton
import com.nicolasguillen.taletime.designsystem.PillButton
import com.nicolasguillen.taletime.designsystem.Tale
import com.nicolasguillen.taletime.designsystem.TaleColors
import com.nicolasguillen.taletime.designsystem.TaleIcons
import com.nicolasguillen.taletime.domain.Language
import com.nicolasguillen.taletime.domain.TextSize
import com.nicolasguillen.taletime.i18n.LocalStrings
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ParentsRoute(
    onBack: () -> Unit,
    viewModel: ParentsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ParentsScreen(state, viewModel::onEvent, onBack)
}

@Composable
fun ParentsScreen(
    state: ParentsUiState,
    onEvent: (ParentsEvent) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    Box(Modifier.fillMaxSize().background(TaleColors.Paper), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeButton(onClick = onBack)
                Text(strings.forGrownUps, style = Tale.type.title)
            }

            Section(strings.language) {
                Text(strings.languageExplained, style = Tale.type.body)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (language in Language.entries) {
                        // Each language in its own words, so a parent finds theirs whatever the app speaks now.
                        Choice(language == state.language, { onEvent(ParentsEvent.SetLanguage(language)) }, Modifier.weight(1f)) { color ->
                            Text(language.nativeName, style = Tale.type.label, color = color)
                        }
                    }
                }
            }

            Section(strings.textSize) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (size in TextSize.entries) {
                        Choice(size == state.textSize, { onEvent(ParentsEvent.SetTextSize(size)) }, Modifier.weight(1f)) { color ->
                            Text("Aa", style = Tale.type.label.copy(fontSize = (22 * size.scale).sp), color = color)
                        }
                    }
                }
            }

            Section(strings.progress) {
                Text(strings.progressExplained, style = Tale.type.body)
                when {
                    state.confirmingReset -> {
                        Text(strings.forgetEverything, style = Tale.type.label)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PillButton(strings.yesForget, onClick = { onEvent(ParentsEvent.ConfirmReset) })
                            PillButton(strings.keep, onClick = { onEvent(ParentsEvent.CancelReset) }, primary = false)
                        }
                    }

                    else -> {
                        PillButton(
                            strings.resetProgress,
                            onClick = { onEvent(ParentsEvent.AskReset) },
                            icon = TaleIcons.Restart,
                            primary = false,
                        )
                        if (state.resetDone) Text(strings.resetDone, style = Tale.type.small)
                    }
                }
            }

            Section(strings.downloads) {
                Text(strings.downloadsExplained(strings.megabytes(state.downloadedBytes)), style = Tale.type.body)
                when {
                    state.confirmingDelete -> {
                        Text(strings.deleteDownloadsAsk, style = Tale.type.label)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PillButton(strings.yesDelete, onClick = { onEvent(ParentsEvent.ConfirmDelete) })
                            PillButton(strings.keep, onClick = { onEvent(ParentsEvent.CancelDelete) }, primary = false)
                        }
                    }

                    else -> {
                        PillButton(
                            strings.deleteDownloads,
                            onClick = { onEvent(ParentsEvent.AskDelete) },
                            icon = TaleIcons.Erase,
                            primary = false,
                            enabled = state.downloadedBytes > 0,
                        )
                        if (state.deleteDone) Text(strings.downloadsDeleted, style = Tale.type.small)
                    }
                }
            }

            Section(strings.aboutTaleTime) {
                Text(strings.aboutStories, style = Tale.type.body)
                Text(strings.aboutPrivacy, style = Tale.type.body)
                Text(strings.version(state.version), style = Tale.type.small)
            }
        }
    }
}

/** One option of a row of choices (a language, a text size): a big tile, filled in when it's the one picked. */
@Composable
private fun Choice(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (color: Color) -> Unit,
) {
    Box(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) TaleColors.NightBlue else TaleColors.Paper)
            .border(2.dp, if (selected) TaleColors.NightBlue else TaleColors.Line, RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(if (selected) TaleColors.Cream else TaleColors.Ink)
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(TaleColors.Cream)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = Tale.type.heading)
        content()
    }
}
