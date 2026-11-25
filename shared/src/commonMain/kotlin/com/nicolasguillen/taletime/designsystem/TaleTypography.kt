package com.nicolasguillen.taletime.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nicolasguillen.taletime.resources.Res
import com.nicolasguillen.taletime.resources.andika_bold
import com.nicolasguillen.taletime.resources.andika_regular
import org.jetbrains.compose.resources.Font

/**
 * Andika (SIL, OFL), a typeface designed for beginning readers: a single-story "a", distinct b/d/p/q and I/l/1.
 * Used for everything, story text and UI, so the letters a child learns look the same everywhere.
 */
@Immutable
data class TaleTypography(
    val title: TextStyle,
    val heading: TextStyle,
    val story: TextStyle,
    val label: TextStyle,
    val body: TextStyle,
    val small: TextStyle,
)

@Composable
fun andika(): FontFamily =
    FontFamily(
        Font(Res.font.andika_regular, FontWeight.Normal),
        Font(Res.font.andika_bold, FontWeight.Bold),
    )

@Composable
fun taleTypography(): TaleTypography {
    val family = andika()
    val base = TextStyle(fontFamily = family, color = TaleColors.Ink)
    return TaleTypography(
        title = base.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 1.15.em),
        heading = base.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 1.2.em),
        story = base.copy(fontSize = 21.sp, lineHeight = 1.5.em),
        label = base.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 1.2.em),
        body = base.copy(fontSize = 17.sp, lineHeight = 1.4.em),
        small = base.copy(fontSize = 14.sp, color = TaleColors.InkSoft, lineHeight = 1.3.em),
    )
}

val LocalTaleTypography = staticCompositionLocalOf<TaleTypography> { error("No TaleTheme") }
