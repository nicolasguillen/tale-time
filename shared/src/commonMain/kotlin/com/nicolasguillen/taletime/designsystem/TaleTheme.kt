package com.nicolasguillen.taletime.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** One light, warm theme. Bedtime mode (warmer, dimmer) comes later (plan.md §3.4). */
@Composable
fun TaleTheme(content: @Composable () -> Unit) {
    val typography = taleTypography()
    val colors =
        lightColorScheme(
            primary = TaleColors.NightBlue,
            onPrimary = TaleColors.Cream,
            secondary = TaleColors.LanternGold,
            onSecondary = TaleColors.Ink,
            background = TaleColors.Paper,
            onBackground = TaleColors.Ink,
            surface = TaleColors.Cream,
            onSurface = TaleColors.Ink,
            outline = TaleColors.Line,
        )
    CompositionLocalProvider(LocalTaleTypography provides typography) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

object Tale {
    val type: TaleTypography
        @Composable @ReadOnlyComposable
        get() = LocalTaleTypography.current
}
