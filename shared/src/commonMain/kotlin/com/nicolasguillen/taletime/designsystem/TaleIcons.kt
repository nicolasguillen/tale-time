package com.nicolasguillen.taletime.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The few UI icons the app needs, as plain rounded strokes on a 24×24 grid. They're tinted where they're used.
 * UI chrome only: story pictures are never drawn in code.
 */
object TaleIcons {
    /** Turn the page: a chunky arrow, the same as [Previous] the other way. */
    val Next: ImageVector by lazy {
        stroke("next", width = 3f) {
            moveTo(5f, 12f)
            lineTo(18.5f, 12f)
            moveTo(12.5f, 6f)
            lineTo(18.5f, 12f)
            lineTo(12.5f, 18f)
        }
    }

    /** One page back. */
    val Previous: ImageVector by lazy {
        stroke("previous", width = 3f) {
            moveTo(19f, 12f)
            lineTo(5.5f, 12f)
            moveTo(11.5f, 6f)
            lineTo(5.5f, 12f)
            lineTo(11.5f, 18f)
        }
    }

    /** Back to the last fork: a curved "undo" arrow. */
    val Rewind: ImageVector by lazy {
        stroke("rewind") {
            moveTo(9f, 14f)
            lineTo(4f, 9f)
            lineTo(9f, 4f)
            moveTo(4f, 9f)
            lineTo(14f, 9f)
            arcToRelative(5.5f, 5.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 11f)
            lineTo(10f, 20f)
        }
    }

    /** Back to the shelf: a solid little house with a round-topped door, easy to read at a glance. */
    val Home: ImageVector by lazy {
        builder("home")
            .path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.EvenOdd,
            ) {
                moveTo(12f, 3.2f)
                lineTo(21f, 11f)
                lineTo(18.6f, 11f)
                lineTo(18.6f, 20f)
                lineTo(5.4f, 20f)
                lineTo(5.4f, 11f)
                lineTo(3f, 11f)
                close()
                // The door, cut out.
                moveTo(9.6f, 20f)
                lineTo(9.6f, 15.6f)
                arcToRelative(2.4f, 2.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.8f, 0f)
                lineTo(14.4f, 20f)
                close()
            }.build()
    }

    /** The parents area: three sliders. */
    val Parents: ImageVector by lazy {
        stroke("parents") {
            for ((y, knob) in listOf(6f to 15f, 12f to 8f, 18f to 13f)) {
                moveTo(4f, y)
                lineTo(20f, y)
                moveTo(knob + 2f, y)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, -4f, 0f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4f, 0f)
            }
        }
    }

    val Lock: ImageVector by lazy {
        stroke("lock") {
            moveTo(7f, 11f)
            lineTo(17f, 11f)
            arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.5f, 1.5f)
            lineTo(18.5f, 19f)
            arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.5f, 1.5f)
            lineTo(7f, 20.5f)
            arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.5f, -1.5f)
            lineTo(5.5f, 12.5f)
            arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.5f, -1.5f)
            close()
            moveTo(8.5f, 11f)
            lineTo(8.5f, 8f)
            arcToRelative(3.5f, 3.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7f, 0f)
            lineTo(15.5f, 11f)
        }
    }

    /** Not downloaded yet, or download it: an arrow down onto a line. */
    val Download: ImageVector by lazy {
        stroke("download") {
            moveTo(12f, 4f)
            lineTo(12f, 15f)
            moveTo(7f, 10.5f)
            lineTo(12f, 15.5f)
            lineTo(17f, 10.5f)
            moveTo(5f, 19.5f)
            lineTo(19f, 19.5f)
        }
    }

    val Play: ImageVector by lazy {
        fill("play") {
            moveTo(8f, 4.5f)
            lineTo(19.5f, 12f)
            lineTo(8f, 19.5f)
            close()
        }
    }

    /** Start over: a circular arrow. */
    val Restart: ImageVector by lazy {
        stroke("restart") {
            moveTo(4.5f, 12f)
            arcToRelative(7.5f, 7.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 2.2f, -5.3f)
            moveTo(4.5f, 3.5f)
            lineTo(4.5f, 8f)
            lineTo(9f, 8f)
        }
    }

    val Star: ImageVector by lazy {
        fill("star") {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 10f else 4.3f
                val angle = -PI / 2 + i * PI / 5
                val x = 12f + r * cos(angle).toFloat()
                val y = 12.5f + r * sin(angle).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    val Check: ImageVector by lazy {
        stroke("check") {
            moveTo(5f, 12.5f)
            lineTo(10f, 17.5f)
            lineTo(19f, 7f)
        }
    }

    val Close: ImageVector by lazy {
        stroke("close") {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }

    val Erase: ImageVector by lazy {
        stroke("erase") {
            moveTo(9f, 5f)
            lineTo(20f, 5f)
            lineTo(20f, 19f)
            lineTo(9f, 19f)
            lineTo(3f, 12f)
            close()
            moveTo(11.5f, 9.5f)
            lineTo(16.5f, 14.5f)
            moveTo(16.5f, 9.5f)
            lineTo(11.5f, 14.5f)
        }
    }

    private fun stroke(
        name: String,
        width: Float = 2.4f,
        draw: PathBuilder.() -> Unit,
    ): ImageVector =
        builder(name)
            .path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = draw,
            ).build()

    private fun fill(
        name: String,
        draw: PathBuilder.() -> Unit,
    ): ImageVector =
        builder(name)
            .path(fill = SolidColor(Color.Black), strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
            .build()

    private fun builder(name: String) =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
}
