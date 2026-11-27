package com.nicolasguillen.taletime.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nicolasguillen.taletime.designsystem.TaleColors
import com.nicolasguillen.taletime.platform.excludeSystemGestures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

/** The folded corner grows in this long, with a small overshoot. */
private const val GROW_MILLIS = 520f

/** While it waits, it grows and shrinks by [BREATH] over [BREATH_MILLIS], inviting a tap without pulsing. */
private const val BREATH_MILLIS = 2_600f
private const val BREATH = 0.05f

/** The arrow fades in once the corner is this much grown. */
private const val ARROW_FROM = 0.6f
private const val ARROW_FADE_MILLIS = 300f

/** The arrow nudges [NUDGE] dp back and forth along the way to pull, every [NUDGE_MILLIS]. */
private const val NUDGE_MILLIS = 1_400f
private const val NUDGE = 4f

/** A finger eases the corner toward it by this much of the gap per 60 Hz frame. */
private const val FOLLOW = 0.45f
private const val FRAME_MILLIS = 1_000f / 60f

/** A corner let go too soon settles back into the fold this fast. */
private const val SETTLE_MILLIS = 280f

/** A touch that moves less than [TAP_DP] and lasts less than [TAP_MILLIS] is a tap. */
private const val TAP_DP = 10f
private const val TAP_MILLIS = 450L

/** Let go of a page with its corner in the left [TURN_FROM] of the screen, or flicked left this fast, and it turns. */
private const val TURN_FROM = 0.55f
private const val FLICK_DP_PER_MILLIS = 0.35f

/** The way to pull, which the arrow points: to the left and a little up. */
private val PULL = Offset(-1f, -0.45f).let { it / it.getDistance() }
private val PULL_DEGREES = (atan2(PULL.y, PULL.x) * 180.0 / PI).toFloat()

/**
 * The fold of the sheet in front ([owner]): where its bottom-right corner is as it folds up into a dog-ear once the
 * page is read, follows a finger, settles back, or turns the page over (docs/page-turn.md §1–§2). Its animations run
 * one at a time in [scope], frame by frame.
 */
@Stable
internal class PageTurn(
    private val scope: CoroutineScope,
) {
    /** The page's size in pixels, set by the screen as it lays out. */
    var width = 0f
    var height = 0f

    /** Where the dog-ear's crease meets the edges: how far along the bottom one and up the right one, in pixels. */
    var crease = Offset.Zero

    /** The sheet being folded, by its key: the fold shows on it only while it's the one in front. */
    var owner: Any? by mutableStateOf(null)
        private set

    /** The page is turning over: nothing takes a touch but the home button. */
    var turning by mutableStateOf(false)
        private set

    /** The arrow's opacity, and where it is in its nudge (0 to 1). */
    var arrow by mutableFloatStateOf(0f)
        private set
    var nudge by mutableFloatStateOf(0f)
        private set

    /** How grown the dog-ear is: 0 none, 1 its size, a little more while it overshoots or breathes. */
    private var ear by mutableFloatStateOf(0f)

    /** Where the corner is while a finger holds it, it settles back or the page turns. Null: in the dog-ear. */
    private var free by mutableStateOf<Offset?>(null)

    private var job: Job? = null

    /** Where the corner lies when the page is flat. */
    val home: Offset get() = Offset(width, height)

    /** Where the corner is now, or null while the page lies flat. */
    val corner: Offset? get() = free ?: ear.takeIf { it > 0f }?.let(::dogEar)

    /** The corner folds up on [owner] and waits: it grows in (unless [growIn] is false), then breathes. */
    fun foldUp(
        owner: Any,
        reduceMotion: Boolean,
        growIn: Boolean = true,
    ) = run {
        this.owner = owner
        wait(reduceMotion, growIn)
    }

    /** A finger holds the corner: it eases toward [target] every frame, and the arrow fades out. */
    fun hold(target: () -> Offset) =
        run {
            free = corner ?: home
            var last = withFrameMillis { it }
            while (true) {
                withFrameMillis { now ->
                    val dt = (now - last).toFloat()
                    last = now
                    val c = free ?: home
                    free = c + (target() - c) * (1f - (1f - FOLLOW).pow(dt / FRAME_MILLIS))
                    arrow = (arrow - dt / ARROW_FADE_MILLIS).coerceAtLeast(0f)
                }
            }
        }

    /** The corner let go too soon: it settles back into the fold, and the arrow comes back. */
    fun settle() =
        run {
            val from = free ?: home
            val to = dogEar(1f)
            animate(SETTLE_MILLIS) { t -> free = from + (to - from) * Ease.outCubic(t) }
            wait(reduceMotion = false, growIn = false)
        }

    /** Turns the page in front over, then [onDone]: see [turnNow]. */
    fun turn(
        owner: Any,
        easing: (Float) -> Float,
        onDone: () -> Unit,
    ) = run {
        turnNow(owner, easing)
        onDone()
    }

    /**
     * Turns [owner] over: its corner travels the arc of a [TurnPath] from where it is to the far left, in [millis]
     * (by default as long as the path asks), and [onFrame] sees the fold on every frame. The page stays folded away
     * at the end, until [lie].
     */
    suspend fun turnNow(
        owner: Any,
        easing: (Float) -> Float,
        millis: Long? = null,
        onFrame: (PageFold) -> Unit = {},
    ) {
        this.owner = owner
        turning = true
        arrow = 0f
        val path = TurnPath(corner ?: home, width, height)
        animate((millis ?: path.millis).toFloat()) { t ->
            val at = path.at(easing(t))
            free = at
            onFrame(PageFold(width, height, at))
        }
    }

    /**
     * Brings [owner] back over the sheet beneath, the way a turn took it away, as when a reader turns back a page: its
     * corner travels a turn's arc backward, from the far left (the sheet all folded away) to its place, in [millis],
     * and [onFrame] sees the fold on every frame. [owner] must be the sheet in front from the frame this is called,
     * so it never shows unfolded first. It lies flat at the end, still the owner until [lie].
     */
    suspend fun returnNow(
        owner: Any,
        easing: (Float) -> Float,
        millis: Long,
        onFrame: (PageFold) -> Unit = {},
    ) {
        val path = TurnPath(home, width, height)
        this.owner = owner
        turning = true
        arrow = 0f
        free = path.at(1f)
        animate(millis.toFloat()) { t ->
            val at = path.at(1f - easing(t))
            free = at
            onFrame(PageFold(width, height, at))
        }
    }

    /** The page lies flat: no fold, no arrow, nothing running. */
    fun lie() {
        job?.cancel()
        job = null
        owner = null
        free = null
        ear = 0f
        arrow = 0f
        nudge = 0f
        turning = false
    }

    private fun dogEar(scale: Float): Offset = home + (PageFold.dogEar(width, height, crease.x, crease.y) - home) * scale

    /** The dog-ear waits for a finger: it grows in, breathes, and its arrow fades in and nudges. */
    private suspend fun wait(
        reduceMotion: Boolean,
        growIn: Boolean,
    ) {
        free = null
        turning = false
        if (reduceMotion) {
            ear = 1f
            arrow = 1f
            nudge = 0f
            return
        }
        val start = withFrameMillis { it }
        val arrowWas = arrow
        var arrowFrom: Long? = null
        while (true) {
            withFrameMillis { now ->
                val t = (now - start).toFloat()
                val grown = if (growIn) Ease.outBack((t / GROW_MILLIS).coerceAtMost(1f)) else 1f
                val breathing = if (growIn) t - GROW_MILLIS else t
                ear = grown * (1f + if (breathing > 0f) BREATH * (1f - cos(2f * PI.toFloat() * breathing / BREATH_MILLIS)) / 2f else 0f)
                if (arrowFrom == null && grown >= ARROW_FROM) arrowFrom = now
                arrow = arrowFrom?.let { (arrowWas + (now - it) / ARROW_FADE_MILLIS).coerceAtMost(1f) } ?: 0f
                nudge = (1f - cos(2f * PI.toFloat() * t / NUDGE_MILLIS)) / 2f
            }
        }
    }

    /** Calls [frame] with 0 to 1 on every frame for [millis]. */
    private suspend fun animate(
        millis: Float,
        frame: (Float) -> Unit,
    ) {
        val start = withFrameMillis { it }
        var t = 0f
        while (t < 1f) {
            withFrameMillis { now ->
                t = ((now - start) / millis).coerceIn(0f, 1f)
                frame(t)
            }
        }
    }

    private fun run(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }
}

/** Where a sheet lies in the stack. */
private enum class Place { FRONT, UNDER, DEEP }

/**
 * The book on screen: [sheets] lie one on another, bottom to top, the last one in front. Only the one in front takes
 * touches, and only an [accessible] one speaks to a screen reader. While it's folded (it's the [PageTurn.owner]), it's
 * drawn with its corner cut away and the flap over it, and the sheet under it shows through, shaded; while it fades in
 * ([frontAlpha] under 1), the one under it shows through, fading out ([underAlpha]). The sheets further down are
 * composed, so their pictures load, but not drawn.
 *
 * Each sheet is composed once for as long as it's in the stack ([keyOf]): the page beneath becomes the page in front
 * without being composed again, so its picture doesn't reload.
 */
@Composable
internal fun <T : Any> PageStack(
    sheets: List<T>,
    keyOf: (T) -> Any,
    turn: PageTurn,
    frontAlpha: () -> Float,
    underAlpha: () -> Float,
    modifier: Modifier = Modifier,
    accessible: (T) -> Boolean = { true },
    content: @Composable (T) -> Unit,
) {
    val density = LocalDensity.current
    val ink = remember(density) { FoldInk(density) }
    val front = sheets.lastOrNull()?.let(keyOf)
    val folds = remember { FoldCache() }
    // Worked out once a frame, for the page in front and the one under it.
    val fold = { folds.of(if (front != null && turn.owner == front) turn.corner else null, turn.width, turn.height) }
    Box(modifier) {
        sheets.forEachIndexed { i, sheet ->
            key(keyOf(sheet)) {
                val layer = rememberGraphicsLayer()
                val place =
                    when (i) {
                        sheets.lastIndex -> Place.FRONT
                        sheets.lastIndex - 1 -> Place.UNDER
                        else -> Place.DEEP
                    }
                val drawn =
                    when (place) {
                        Place.FRONT -> {
                            Modifier
                                .graphicsLayer { alpha = frontAlpha() }
                                .then(if (accessible(sheet)) Modifier else Modifier.clearAndSetSemantics {})
                                // Something takes every touch on the page in front, so the sheets under it never do.
                                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                                .drawWithContent { drawFront(fold(), layer, ink, turn.arrow, turn.nudge) }
                                // The page's own drawing, kept as it is: a frame of the fold only draws it again.
                                .graphicsLayer()
                        }

                        Place.UNDER -> {
                            Modifier
                                .clearAndSetSemantics {}
                                .graphicsLayer { alpha = underAlpha() }
                                .drawWithContent {
                                    val folded = fold()
                                    // Hidden under the page in front, unless it's folded or fading in.
                                    if (folded == null && frontAlpha() >= 1f) return@drawWithContent
                                    drawContent()
                                    if (folded != null) drawUncovered(folded, ink)
                                }.graphicsLayer()
                        }

                        Place.DEEP -> {
                            Modifier.clearAndSetSemantics {}.drawWithContent {}
                        }
                    }
                Box(Modifier.fillMaxSize().then(drawn)) { content(sheet) }
            }
        }
    }
}

/**
 * Where a finger takes the folded corner: a square [side] wide at the page's bottom-right corner, over the flap and
 * the uncovered corner. A tap turns the page; a drag lifts it, and letting go turns it if the corner is far enough to
 * the left or flicked, else it settles back. To a screen reader it's the [label] button. [onTurn] says whether it was
 * flicked.
 */
@Composable
internal fun FoldedCorner(
    turn: PageTurn,
    side: Dp,
    label: String,
    reduceMotion: Boolean,
    onTurn: (flick: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val turnPage by rememberUpdatedState(onTurn)
    Box(
        modifier
            .size(side)
            .excludeSystemGestures()
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick {
                    turnPage(false)
                    true
                }
            }.pointerInput(turn, reduceMotion) { pullCorner(turn, reduceMotion) { turnPage(it) } },
    )
}

private suspend fun PointerInputScope.pullCorner(
    turn: PageTurn,
    reduceMotion: Boolean,
    onTurn: (flick: Boolean) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown()
    down.consume()
    if (reduceMotion) {
        // The page doesn't follow the finger: letting go turns it (a cross-fade).
        waitForUpOrCancellation()?.let {
            it.consume()
            onTurn(false)
        }
        return@awaitEachGesture
    }
    // The square sits at the page's bottom-right corner.
    val origin = Offset(turn.width - size.width, turn.height - size.height)
    // The corner keeps its offset from the finger, so the fold never jumps.
    val offset = (turn.corner ?: turn.home) - (origin + down.position)
    var finger = down.position
    turn.hold { PageFold.bound(origin + finger + offset, turn.width, turn.height) }
    // Every move counts, with the ones the system batched in between.
    val flick = Flick().apply { add(down.uptimeMillis, down.position.x) }
    var farthest = 0f
    var up: PointerInputChange? = null
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
        change.historical.forEach { flick.add(it.uptimeMillis, it.position.x) }
        flick.add(change.uptimeMillis, change.position.x)
        farthest = max(farthest, (change.position - down.position).getDistance())
        if (change.changedToUpIgnoreConsumed()) {
            up = change
            break
        }
        finger = change.position
        change.consume()
    }
    up?.consume()
    val tapped = up != null && farthest < TAP_DP * density && up.uptimeMillis - down.uptimeMillis < TAP_MILLIS
    // Pixels per ms to dp per ms, over the last moves.
    val flicked = flick.velocity() / density < -FLICK_DP_PER_MILLIS
    val corner = turn.corner
    when {
        tapped -> onTurn(false)
        up != null && corner != null && (corner.x < TURN_FROM * turn.width || flicked) -> onTurn(flicked)
        else -> turn.settle()
    }
}

/** A blank leaf the book riffles through after a choice: paper with a soft gold glow in the middle. */
@Composable
internal fun Leaf() {
    Box(
        Modifier.fillMaxSize().background(TaleColors.Paper).drawBehind {
            drawRect(
                Brush.radialGradient(
                    0f to TaleColors.LanternGold.copy(alpha = 0.3f),
                    0.62f to Color.Transparent,
                    center = center,
                    radius = hypot(size.width, size.height) / 2f,
                ),
            )
        },
    )
}

/** The pieces the fold is drawn with, made once: reusable paths, and the spec's sizes in pixels. */
private class FoldInk(
    density: Density,
) {
    val keep = Path()
    val cut = Path()
    val flap = Path()
    val uncovered = Path()
    val shadow = Path()
    val matrix = Matrix()

    val dp = density.density
    val flapBlur = 7f * dp
    val creaseLight = 9f * dp
    val shadowMin = 16f * dp
    val shadowMax = 90f * dp
    val nudge = NUDGE * dp

    /** The arrow: [com.nicolasguillen.taletime.designsystem.TaleIcons.Next], 40 dp, centered on its middle. */
    private val unit = 40f * dp / 24f
    val arrowStroke = 3.2f * unit
    val arrowNear = 1.5f * dp
    val arrowFar = 6f * dp
    val arrow =
        Path().apply {
            fun at(
                x: Float,
                y: Float,
            ) = Offset((x - 11.75f) * unit, (y - 12f) * unit)
            at(5f, 12f).let { moveTo(it.x, it.y) }
            at(18.5f, 12f).let { lineTo(it.x, it.y) }
            at(12.5f, 6f).let { moveTo(it.x, it.y) }
            at(18.5f, 12f).let { lineTo(it.x, it.y) }
            at(12.5f, 18f).let { lineTo(it.x, it.y) }
        }

    /**
     * The arrow pointing the way to pull, white with its two soft dark shadows, drawn once: the fold draws this picture
     * on every frame rather than eleven strokes.
     */
    val arrowImage: ImageBitmap =
        run {
            val side = ceil(56f * dp).toInt()
            val image = ImageBitmap(side, side)
            val size = Size(side.toFloat(), side.toFloat())
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), size) {
                withTransform({
                    translate(side / 2f, side / 2f + dp)
                    rotate(PULL_DEGREES, Offset.Zero)
                }) { strokeShadow(arrow, arrowStroke, arrowNear, TaleColors.FoldShadow, 0.85f) }
                withTransform({
                    translate(side / 2f, side / 2f)
                    rotate(PULL_DEGREES, Offset.Zero)
                }) {
                    strokeShadow(arrow, arrowStroke, arrowFar, TaleColors.FoldShadow, 0.5f)
                    drawPath(arrow, Color.White, style = Stroke(arrowStroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            image
        }
}

/** The page in front, folded (docs/page-turn.md §3): recorded once, drawn as the page and mirrored into the flap. */
private fun ContentDrawScope.drawFront(
    fold: PageFold?,
    layer: GraphicsLayer,
    ink: FoldInk,
    arrow: Float,
    nudge: Float,
) {
    if (fold == null) {
        drawContent()
        return
    }
    layer.record { this@drawFront.drawContent() }
    // The page, its corner folded away.
    if (fold.keep.isNotEmpty()) clipPath(ink.keep.polygon(fold.keep)) { drawLayer(layer) }
    if (fold.flap.isEmpty()) return
    val flap = ink.flap.polygon(fold.flap)
    // A soft shadow around the flap, onto the page and the one beneath. It goes with the shade at the end of a turn.
    softShadow(fold, ink, 0.4f * fold.shading)
    // The back of the paper, with the page showing through mirrored, like ink through paper.
    fold.mirror().copyInto(ink.matrix.values)
    withTransform({
        transform(ink.matrix)
        clipPath(ink.cut.polygon(fold.cut))
    }) {
        drawLayer(layer)
        drawRect(TaleColors.PageBack, alpha = 0.93f)
    }
    // Shading from the crease to the flap's tip: dark in the crease, a warm light just past it.
    val half = fold.len / 2f
    val light = (ink.creaseLight / half).coerceAtMost(0.2f)
    clipPath(flap) {
        drawRect(
            Brush.linearGradient(
                0f to TaleColors.Ink.copy(alpha = 0.3f),
                light to TaleColors.FoldLight.copy(alpha = 0.55f),
                0.45f to TaleColors.FoldLight.copy(alpha = 0.12f),
                1f to TaleColors.Ink.copy(alpha = 0.1f),
                start = fold.m,
                end = fold.corner,
            ),
        )
    }
    if (arrow > 0f) fold.flapCenter()?.let { drawPullArrow(it, arrow, nudge, ink) }
}

/** The next page where the corner is folded away, in the flap's shade: darkest along the crease. */
private fun DrawScope.drawUncovered(
    fold: PageFold,
    ink: FoldInk,
) {
    if (fold.shading <= 0f || fold.cut.isEmpty()) return
    val reach = (0.35f * fold.len).coerceIn(ink.shadowMin, ink.shadowMax)
    clipPath(ink.uncovered.polygon(fold.cut)) {
        drawRect(
            Brush.linearGradient(
                0f to TaleColors.FoldShadow.copy(alpha = 0.58f * fold.shading),
                1f to TaleColors.FoldShadow.copy(alpha = fold.tint * fold.shading),
                start = fold.m,
                end = fold.m + fold.n * reach,
            ),
        )
    }
}

/** The white arrow on the flap, pointing the way to pull, with two soft dark shadows so it reads on any picture. */
private fun DrawScope.drawPullArrow(
    at: Offset,
    alpha: Float,
    nudge: Float,
    ink: FoldInk,
) {
    val center = at + PULL * (ink.nudge * nudge)
    val image = ink.arrowImage
    drawImage(image, topLeft = Offset(center.x - image.width / 2f, center.y - image.height / 2f), alpha = alpha)
}

/**
 * A soft shadow around the flap, [FoldInk.flapBlur] wide. No blur filter draws on every platform the app runs on, so
 * it's the flap drawn a few times, bigger and fainter: about half its [alpha] at the edge, fading to nothing a blur
 * out, like a blur. The flap itself is drawn over it.
 */
private fun DrawScope.softShadow(
    fold: PageFold,
    ink: FoldInk,
    alpha: Float,
    steps: Int = 5,
) {
    val center = fold.flapCenter() ?: return
    // How far the flap's nearest edge is from its center: scaling by (r + d) / r moves a triangle's edges out by d.
    val r = fold.flap.indices.minOf { i -> distanceToEdge(center, fold.flap[i], fold.flap[(i + 1) % fold.flap.size]) }
    if (alpha <= 0.005f || r <= 0f) return
    val each = 1f - (1f - alpha / 2f).pow(1f / steps)
    for (i in steps downTo 1) {
        val k = (r + ink.flapBlur * i / steps) / r
        // Filled convex shapes draw on the GPU everywhere; stroked outlines don't on every device.
        drawPath(ink.shadow.polygon(fold.flap, center, k), TaleColors.FoldShadow, alpha = each)
    }
}

/** How far [p] is from the line through [a] and [b]. */
private fun distanceToEdge(
    p: Offset,
    a: Offset,
    b: Offset,
): Float {
    val d = b - a
    val len = d.getDistance()
    return if (len > 0f) kotlin.math.abs((p.x - a.x) * d.y - (p.y - a.y) * d.x) / len else (p - a).getDistance()
}

/** A soft shadow of the stroked line [path], [width] wide: [alpha] on the line, fading out [blur] past it. */
private fun DrawScope.strokeShadow(
    path: Path,
    width: Float,
    blur: Float,
    color: Color,
    alpha: Float,
    steps: Int = 4,
) {
    if (alpha <= 0.005f) return
    val each = 1f - (1f - alpha).pow(1f / (steps + 1))
    for (i in steps downTo 0) {
        drawPath(
            path,
            color,
            alpha = each,
            style = Stroke(width + 2f * blur * i / steps, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** [points] as a closed shape, scaled by [scale] around [center]. */
private fun Path.polygon(
    points: List<Offset>,
    center: Offset = Offset.Zero,
    scale: Float = 1f,
): Path {
    rewind()
    for (i in points.indices) {
        val x = center.x + (points[i].x - center.x) * scale
        val y = center.y + (points[i].y - center.y) * scale
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
    return this
}

/** The fold for the corner where it is, worked out once however many sheets draw it in a frame. */
private class FoldCache {
    private var corner: Offset? = null
    private var width = 0f
    private var height = 0f
    private var fold: PageFold? = null

    fun of(
        corner: Offset?,
        width: Float,
        height: Float,
    ): PageFold? {
        if (corner == null) return null
        if (corner != this.corner || width != this.width || height != this.height) {
            this.corner = corner
            this.width = width
            this.height = height
            fold = PageFold(width, height, corner).takeIf { it.folded }
        }
        return fold
    }
}
