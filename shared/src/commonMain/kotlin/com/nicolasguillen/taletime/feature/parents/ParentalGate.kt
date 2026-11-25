package com.nicolasguillen.taletime.feature.parents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nicolasguillen.taletime.designsystem.RoundButton
import com.nicolasguillen.taletime.designsystem.Tale
import com.nicolasguillen.taletime.designsystem.TaleColors
import com.nicolasguillen.taletime.designsystem.TaleIcons
import com.nicolasguillen.taletime.i18n.LocalStrings

/**
 * Stands in front of everything a child shouldn't open alone: the parents area and purchases (plan.md §9).
 * A full-screen overlay, not a system dialog.
 */
@Composable
fun ParentalGate(
    onPassed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    var state by remember { mutableStateOf(GateState(GateQuestion.random())) }
    LaunchedEffect(state.passed) { if (state.passed) onPassed() }
    Box(
        Modifier
            .fillMaxSize()
            .background(TaleColors.Scrim)
            // Taps outside the card do nothing (no accidental dismissals): only the close button leaves.
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(strings.forGrownUps, style = Tale.type.heading, modifier = Modifier.weight(1f))
                RoundButton(
                    TaleIcons.Close,
                    contentDescription = strings.close,
                    onClick = onDismiss,
                    size = 44.dp,
                    container = TaleColors.Paper,
                    content = TaleColors.InkSoft,
                )
            }
            Text(
                if (state.wrongTries == 0) strings.gateAsk else strings.gateRetry,
                style = Tale.type.body,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(strings.times(state.question.a, state.question.b), style = Tale.type.heading, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(state.question.answer.length) { i ->
                    Box(
                        Modifier
                            .size(width = 52.dp, height = 60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TaleColors.Paper)
                            .border(2.dp, TaleColors.Line, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(state.typed.getOrNull(i)?.toString() ?: "", style = Tale.type.heading)
                    }
                }
            }
            NumberPad(
                onDigit = { state = state.type(it) },
                onErase = { state = state.erase() },
            )
        }
    }
}

@Composable
private fun NumberPad(
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(null, 0, -1))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (key in row) {
                    when (key) {
                        null -> {
                            Box(Modifier.size(64.dp))
                        }

                        -1 -> {
                            Key(onClick = onErase, description = LocalStrings.current.erase) {
                                Icon(TaleIcons.Erase, contentDescription = null, tint = TaleColors.InkSoft, modifier = Modifier.size(28.dp))
                            }
                        }

                        else -> {
                            Key(
                                onClick = { onDigit(key) },
                                description = key.toString(),
                            ) { Text(key.toString(), style = Tale.type.heading) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(
    onClick: () -> Unit,
    description: String,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(TaleColors.Paper)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}
