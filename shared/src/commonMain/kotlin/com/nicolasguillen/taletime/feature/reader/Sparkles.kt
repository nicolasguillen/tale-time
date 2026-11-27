package com.nicolasguillen.taletime.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.nicolasguillen.taletime.designsystem.TaleColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** How long a sparkle takes to fade in. */
private const val SPARKLE_FADE_IN_MILLIS = 80f

/** A sparkle's glow reaches this far past its edge (dp). */
private const val GLOW_DP = 8f

/** Sparkles slow down by this much every 16 ms… */
private const val DRAG_PER_16_MILLIS = 0.985f

/** …and fall a little (dp per ms²). */
private const val GRAVITY = 0.0003f

/** A twinkle is turned by one of these angles (degrees), drawn into its picture once rather than on every frame. */
private val TURNS = listOf(-20f, -10f, 0f, 10f, 20f)

/** One sparkle, in pixels and milliseconds. */
private class Sparkle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val life: Float,
    val size: Float,
    val twinkle: Boolean,
    val color: Color,
    /** Which of [TURNS] a twinkle is turned by. */
    val turn: Int,
    val wave: Float,
    val phase: Float,
) {
    var age = 0f
}

/**
 * The gold sparkles of a choice (docs/page-turn.md §4): a burst from the picked card, dust off the crease of every
 * page the book riffles through, and a few drifting down as the new page lands. UI effects drawn in code, not story
 * art. Positions are in the pixels of the [SparkleLayer] they're drawn on.
 */
@Stable
internal class Sparkles(
    private val random: Random = Random.Default,
) {
    private val alive = ArrayList<Sparkle>()

    /** Bumped on every frame something moves, so the layer draws again. */
    var frame by mutableIntStateOf(0)
        private set

    /** Something is alive: the layer runs its frames. */
    var running by mutableStateOf(false)
        private set

    /** Pixels per dp, set by the layer. */
    var density = 1f

    /** [count] at once from [at], flying out every way. */
    fun burst(
        at: Offset,
        count: Int,
    ) = repeat(count) {
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val speed = between(0.08f, 0.34f)
        add(at, cos(angle) * speed, sin(angle) * speed)
    }

    /** [count] from points around [rect]'s edge, drifting outward. */
    fun edge(
        rect: Rect,
        count: Int,
    ) = repeat(count) {
        // A point along the edge, clockwise from the top-left corner, and the way out from that side.
        val w = rect.width
        val h = rect.height
        val along = random.nextFloat() * 2f * (w + h)
        val (at, out) =
            when {
                along < w -> Offset(rect.left + along, rect.top) to Offset(0f, -1f)
                along < w + h -> Offset(rect.right, rect.top + along - w) to Offset(1f, 0f)
                along < 2 * w + h -> Offset(rect.right - (along - w - h), rect.bottom) to Offset(0f, 1f)
                else -> Offset(rect.left, rect.bottom - (along - 2 * w - h)) to Offset(-1f, 0f)
            }
        val speed = between(0.03f, 0.14f)
        add(at, (out.x + between(-0.5f, 0.5f)) * speed, (out.y + between(-0.5f, 0.5f)) * speed)
    }

    /** [count] from random points between [from] and [to] (a crease), flying toward [toward], give or take 0.7 rad. */
    fun dust(
        from: Offset,
        to: Offset,
        toward: Offset,
        count: Int,
    ) {
        val heading = atan2(toward.y, toward.x)
        repeat(count) {
            val at = from + (to - from) * random.nextFloat()
            val angle = heading + between(-0.7f, 0.7f)
            val speed = between(0.06f, 0.26f)
            add(at, cos(angle) * speed, sin(angle) * speed)
        }
    }

    /** [count] at random over [area], drifting slowly down. */
    fun drift(
        area: Rect,
        count: Int,
    ) = repeat(count) {
        add(
            Offset(between(area.left, area.right), between(area.top, area.bottom)),
            between(-0.02f, 0.02f),
            between(0.005f, 0.03f),
        )
    }

    /** Moves every sparkle [dt] ms on, and lets go of the ones whose life is over. */
    fun step(dt: Float) {
        val drag = DRAG_PER_16_MILLIS.pow(dt / 16f)
        val fall = GRAVITY * density * dt
        val i = alive.iterator()
        while (i.hasNext()) {
            val s = i.next()
            s.age += dt
            if (s.age >= s.life) {
                i.remove()
                continue
            }
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.vx *= drag
            s.vy = s.vy * drag + fall
        }
        if (alive.isEmpty()) running = false
        frame++
    }

    fun clear() {
        alive.clear()
        running = false
        frame++
    }

    /**
     * Draws every sparkle from its [sprites]: a picture of it with its glow and its turn, made once, so a few hundred of
     * them cost a few hundred small images a frame rather than as many shapes to work out again. Each image is placed
     * and sized by its rectangle alone, with no transform to save and restore.
     */
    fun draw(
        scope: DrawScope,
        sprites: SparkleSprites,
    ) = with(scope) {
        for (s in alive) {
            val lived = s.age / s.life
            val shown = (s.age / SPARKLE_FADE_IN_MILLIS).coerceAtMost(1f) * (1f - lived)
            val twinkling = 1f + 0.35f * sin(s.age * s.wave + s.phase)
            val alpha = (shown * twinkling).coerceIn(0f, 1f)
            if (alpha <= 0.01f) continue
            val sprite = sprites.of(s.twinkle, s.color, s.turn)
            // It shrinks by 40% over its life.
            val side = (sprite.width * s.size * (1f - 0.4f * lived) / sprites.size(s.twinkle)).roundToInt()
            if (side <= 0) continue
            drawImage(
                sprite,
                dstOffset = IntOffset((s.x - side / 2f).roundToInt(), (s.y - side / 2f).roundToInt()),
                dstSize = IntSize(side, side),
                alpha = alpha,
            )
        }
    }

    private fun add(
        at: Offset,
        vx: Float,
        vy: Float,
    ) {
        val twinkle = random.nextFloat() < 0.6f
        alive +=
            Sparkle(
                x = at.x,
                y = at.y,
                vx = vx * density,
                vy = vy * density,
                life = between(500f, 1_500f),
                size = (if (twinkle) between(7f, 15f) else between(1.8f, 4.2f)) * density,
                twinkle = twinkle,
                color = COLORS[random.nextInt(COLORS.size)],
                turn = random.nextInt(TURNS.size),
                wave = between(0.018f, 0.032f),
                phase = random.nextFloat() * 2f * PI.toFloat(),
            )
        running = true
    }

    private fun between(
        from: Float,
        to: Float,
    ) = from + (to - from) * random.nextFloat()

    companion object {
        val COLORS = listOf(TaleColors.LanternGold, TaleColors.Amber, TaleColors.Sparkle)
    }
}

/**
 * The sparkles' pictures, drawn once in code for this [density]: a four-pointed twinkle (turned by each of [TURNS]) and
 * a dot in each color, each with its amber glow around it (blur 8 dp, 90%). UI effects, not story art.
 */
internal class SparkleSprites(
    private val density: Float,
) {
    private val twinkleSize = TWINKLE_DP * density
    private val dotSize = DOT_DP * density
    private val glow = GLOW_DP * density
    private val twinkles = Sparkles.COLORS.associateWith { color -> TURNS.map { sprite(twinkleSize, color, twinkle = true, it) } }
    private val dots = Sparkles.COLORS.associateWith { sprite(dotSize, it, twinkle = false) }

    fun of(
        twinkle: Boolean,
        color: Color,
        turn: Int,
    ): ImageBitmap = if (twinkle) twinkles.getValue(color)[turn] else dots.getValue(color)

    /** How big a sparkle of each kind is in its picture, in pixels (tip to tip, or across). */
    fun size(twinkle: Boolean): Float = if (twinkle) twinkleSize else dotSize

    private fun sprite(
        size: Float,
        color: Color,
        twinkle: Boolean,
        degrees: Float = 0f,
    ): ImageBitmap {
        val radius = size / 2f
        val side = ceil(2f * (radius + glow)).toInt()
        val image = ImageBitmap(side, side)
        val center = Offset(side / 2f, side / 2f)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), Size(side.toFloat(), side.toFloat())) {
            drawCircle(
                Brush.radialGradient(
                    0f to TaleColors.Amber.copy(alpha = 0.9f),
                    radius / (radius + glow) to TaleColors.Amber.copy(alpha = 0.45f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius + glow,
                ),
                radius = radius + glow,
                center = center,
            )
            if (twinkle) {
                // Four quadratic curves through the center.
                val star =
                    Path().apply {
                        moveTo(center.x, center.y - radius)
                        quadraticTo(center.x, center.y, center.x + radius, center.y)
                        quadraticTo(center.x, center.y, center.x, center.y + radius)
                        quadraticTo(center.x, center.y, center.x - radius, center.y)
                        quadraticTo(center.x, center.y, center.x, center.y - radius)
                        close()
                    }
                rotate(degrees, pivot = center) { drawPath(star, color) }
            } else {
                drawCircle(color, radius = radius, center = center)
            }
        }
        return image
    }

    private companion object {
        /** The biggest twinkle and dot, which every sparkle is scaled down from. */
        const val TWINKLE_DP = 15f
        const val DOT_DP = 4.2f
    }
}

/** Draws [sparkles] and runs their frames while any is alive. */
@Composable
internal fun SparkleLayer(
    sparkles: Sparkles,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    sparkles.density = density
    LaunchedEffect(sparkles, sparkles.running) {
        if (!sparkles.running) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (sparkles.running) {
            val now = withFrameMillis { it }
            // At most 100 ms at once, so they don't leap after a pause.
            sparkles.step((now - last).coerceAtMost(100L).toFloat())
            last = now
        }
    }
    val sprites = remember(density) { SparkleSprites(density) }
    Canvas(modifier) {
        // Reading the frame count makes the canvas draw again on every step.
        if (sparkles.frame >= 0) sparkles.draw(this, sprites)
    }
}
