package com.nicolasguillen.taletime.feature.reader

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A page folded flat over a straight crease (docs/page-turn.md §3): its bottom-right corner C, where it lies flat
 * ([home]), has moved to [corner]. The crease is the line halfway between them, at right angles to C → [corner]. Pure
 * geometry, in any unit (the screen draws it in pixels, the tests check it in dp).
 */
class PageFold(
    val width: Float,
    val height: Float,
    val corner: Offset,
) {
    /** Where the corner lies when the page is flat. */
    val home = Offset(width, height)

    /** How far the corner has moved from [home]. */
    val len: Float = (home - corner).getDistance()

    /** Whether the page is folded at all. */
    val folded: Boolean = len > FLAT

    /** At right angles to the crease, toward the part that's folded over: (C − P) / |C − P|. */
    val n: Offset = if (folded) (home - corner) / len else Offset(1f, 0f)

    /** The middle of the crease: halfway between the corner and its home. */
    val m: Offset = (home + corner) / 2f

    // The reflection across the crease: X' = R X + t, with R = [[a, b], [b, −a]].
    private val a = n.y * n.y - n.x * n.x
    private val b = -2f * n.x * n.y
    private val t = Offset(m.x - (a * m.x + b * m.y), m.y - (b * m.x - a * m.y))

    /** The part of the page that stays: `(X − M) · n ≤ 0`. Empty once the whole page has folded over. */
    val keep: List<Offset> = if (folded) clip(keepSide = true) else page()

    /** The part that's folded away, where it was: `(X − M) · n ≥ 0`. The next page shows there. */
    val cut: List<Offset> = if (folded) clip(keepSide = false) else emptyList()

    /** The flap: [cut] mirrored across the crease, lying over the page with its back up. */
    val flap: List<Offset> = cut.map(::reflect)

    /**
     * How dark the uncovered part of the next page is, past the shadow along the crease: in clear shade while the
     * corner is folded up, lighter as the page lifts higher.
     */
    val tint: Float = 0.2f * (1f - len / (1.5f * width)).coerceIn(0f, 1f)

    /** How much of the shading is left: all of it until the last 35% of a turn, then it fades out. */
    val shading: Float = ((corner.x + width) / (0.35f * width)).coerceIn(0f, 1f)

    /** Which side of the crease [x] is on: negative on the page, positive in the part that's folded away. */
    fun side(x: Offset): Float = (x - m).dot(n)

    /** [x] mirrored across the crease. */
    fun reflect(x: Offset): Offset = Offset(a * x.x + b * x.y + t.x, b * x.x - a * x.y + t.y)

    /**
     * The reflection as a 4×4 column-major matrix (Compose's `Matrix.values`), to draw the page's back on the flap.
     */
    fun mirror(): FloatArray =
        FloatArray(16).also { v ->
            v[0] = a
            v[1] = b
            v[4] = b
            v[5] = -a
            v[10] = 1f
            v[12] = t.x
            v[13] = t.y
            v[15] = 1f
        }

    /** Where the arrow sits on the flap: the incenter of a triangle, else the middle of its corners. */
    fun flapCenter(): Offset? =
        when {
            flap.size == 3 -> incenter(flap[0], flap[1], flap[2])
            flap.isNotEmpty() -> flap.reduce { acc, o -> acc + o } / flap.size.toFloat()
            else -> null
        }

    /** The crease's two ends on the page's edges, or null when it isn't folded. */
    fun crease(): Pair<Offset, Offset>? {
        val ends = cut.filter { abs(side(it)) < 1e-3f * maxOf(width, height) }
        return if (ends.size >= 2) ends.first() to ends.last() else null
    }

    private fun page() = listOf(Offset(0f, 0f), Offset(width, 0f), Offset(width, height), Offset(0f, height))

    /** The page cut by the crease (Sutherland–Hodgman against one half-plane), its corners in the page's order. */
    private fun clip(keepSide: Boolean): List<Offset> {
        val corners = page()
        val inside = { x: Offset -> if (keepSide) side(x) <= 0f else side(x) >= 0f }
        val out = ArrayList<Offset>(5)
        var s = corners.last()
        for (e in corners) {
            if (inside(e)) {
                if (!inside(s)) out += cross(s, e)
                out += e
            } else if (inside(s)) {
                out += cross(s, e)
            }
            s = e
        }
        // A sliver with no area (the crease along an edge) is nothing.
        return if (area(out) > 1e-6f * width * height) out else emptyList()
    }

    /** Where the edge s → e crosses the crease. */
    private fun cross(
        s: Offset,
        e: Offset,
    ): Offset {
        val ds = side(s)
        val k = ds / (ds - side(e))
        return s + (e - s) * k
    }

    companion object {
        /** Closer than this, the corner is home and the page lies flat. */
        private const val FLAT = 0.01f

        /**
         * Where the corner goes for a dog-ear whose crease meets the bottom edge [alongBottom] from the corner and
         * the right edge [upRight] up from it: the corner mirrored across that crease.
         */
        fun dogEar(
            width: Float,
            height: Float,
            alongBottom: Float,
            upRight: Float,
        ): Offset {
            val home = Offset(width, height)
            val bottom = Offset(width - alongBottom, height)
            val d = Offset(alongBottom, -upRight)
            val k = (home - bottom).dot(d) / d.dot(d)
            val foot = bottom + d * k
            return foot * 2f - home
        }

        /**
         * Keeps a corner a finger pulls on the page, like paper bound at its left edge: within one page width of the
         * bottom-left corner and one page diagonal of the top-left one, never below the bottom edge or right of the
         * right edge.
         */
        fun bound(
            corner: Offset,
            width: Float,
            height: Float,
        ): Offset {
            var c = Offset(min(corner.x, width), min(corner.y, height))
            val spine = Offset(0f, height)
            if ((c - spine).getDistance() > width) c = spine + (c - spine) * (width / (c - spine).getDistance())
            val diagonal = sqrt(width * width + height * height)
            if (c.getDistance() > diagonal) c *= diagonal / c.getDistance()
            return c
        }

        private fun area(polygon: List<Offset>): Float {
            var twice = 0f
            for (i in polygon.indices) {
                val p = polygon[i]
                val q = polygon[(i + 1) % polygon.size]
                twice += p.x * q.y - q.x * p.y
            }
            return abs(twice) / 2f
        }

        private fun incenter(
            p: Offset,
            q: Offset,
            r: Offset,
        ): Offset {
            val lp = (q - r).getDistance()
            val lq = (r - p).getDistance()
            val lr = (p - q).getDistance()
            val sum = lp + lq + lr
            return if (sum > 0f) (p * lp + q * lq + r * lr) / sum else p
        }
    }
}

/**
 * The arc a turning page's corner travels (docs/page-turn.md §2): a quadratic curve from [from] to (−width, height),
 * where the whole page has folded over to the left, lifting in an arc on the way.
 */
class TurnPath(
    val from: Offset,
    width: Float,
    height: Float,
) {
    val to = Offset(-width, height)

    /** How much of the way there is to go: 1 from the bottom-right corner, 0 at the end. */
    val span: Float = ((from.x + width) / (2f * width)).coerceIn(0f, 1f)

    /** Halfway along in x, and above the higher end, more so the farther there is to go. */
    val control = Offset((from.x + to.x) / 2f, min(from.y, to.y) - 0.3f * min(width, height) * span)

    /** How long the turn takes from [from]: about 0.7 s from the folded corner. */
    val millis: Long = (300f + 450f * span).toLong()

    /** Where the corner is at [t], 0 to 1 along the curve. */
    fun at(t: Float): Offset {
        val u = 1f - t
        return from * (u * u) + control * (2f * u * t) + to * (t * t)
    }
}

/**
 * How fast a finger moves sideways as it lets go of a page: its distance over the time of its last moves, the ones
 * within [windowMillis] of the latest (docs/page-turn.md §2), in units per millisecond. Negative is to the left.
 */
class Flick(
    private val windowMillis: Long = 80,
) {
    private val times = ArrayDeque<Long>()
    private val xs = ArrayDeque<Float>()

    fun add(
        timeMillis: Long,
        x: Float,
    ) {
        times.addLast(timeMillis)
        xs.addLast(x)
        while (times.size > 2 && timeMillis - times.first() > windowMillis) {
            times.removeFirst()
            xs.removeFirst()
        }
    }

    /** 0 until two moves at least a few ms apart. */
    fun velocity(): Float {
        if (times.size < 2) return 0f
        val dt = times.last() - times.first()
        return if (dt >= 4) (xs.last() - xs.first()) / dt else 0f
    }
}

/** The easing curves of the page turn, as plain functions of 0..1. */
object Ease {
    fun inOutSine(t: Float): Float = (-(cos(PI * t) - 1.0) / 2.0).toFloat()

    fun outCubic(t: Float): Float {
        val u = 1f - t
        return 1f - u * u * u
    }

    /** Overshoots a little past 1 before it settles, more with a bigger [overshoot]. */
    fun outBack(
        t: Float,
        overshoot: Float = 1.5f,
    ): Float {
        val u = t - 1f
        return 1f + (overshoot + 1f) * u * u * u + overshoot * u * u
    }
}

internal fun Offset.dot(o: Offset): Float = x * o.x + y * o.y
