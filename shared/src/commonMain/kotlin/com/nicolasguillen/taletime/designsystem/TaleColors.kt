package com.nicolasguillen.taletime.designsystem

import androidx.compose.ui.graphics.Color

/**
 * The app's quiet UI palette: warm paper and ink, so the illustrations stand out (plan.md §6). Most of it comes from
 * the house style's base palette (content/style/style.json). Features use these, never raw hex values.
 */
object TaleColors {
    val Paper = Color(0xFFF3EAD8)
    val Cream = Color(0xFFFFF6E3)
    val Line = Color(0xFFD8C7A4)
    val Ink = Color(0xFF3A2C24)
    val InkSoft = Color(0xFF7A6552)
    val NightBlue = Color(0xFF2E3F66)
    val LanternGold = Color(0xFFF6C453)
    val MeadowGreen = Color(0xFF7FA65A)
    val Lilac = Color(0xFFB9A3D0)
    val EarthBrown = Color(0xFF8A5A3C)

    /** Behind the page while an illustration loads, and around letterboxed pictures. */
    val PictureMat = Color(0xFFE9DDC4)

    /** Dims the page behind the parental gate and dialogs. */
    val Scrim = Color(0x99231A14)

    /** The bookshelf's wood: the top of a shelf, its front edge (lit to shaded), and the shadow it casts below. */
    val ShelfTop = Color(0xFFDDB17F)
    val ShelfFront = Color(0xFFB98052)
    val ShelfFrontShade = Color(0xFF9A643E)
    val ShelfShadow = Color(0x333A2C24)

    /** A book's spine: the shade at its edges, the light on its rounded middle, and the hinge's groove by the cover. */
    val SpineShade = Color(0x803A2C24)
    val SpineSheen = Color(0x1AFFFFFF)
    val SpineGroove = Color(0x993A2C24)
}
