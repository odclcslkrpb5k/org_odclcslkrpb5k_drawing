package org.odclcslkrpb5k.drawing.core

/**
 * What a deliberate hold is: exactly [fingers] fingers, each kept within [slop] of where it landed,
 * for [holdMs]. A child's palm (more fingers), a wiggle or a quick tap does not count.
 */
data class HoldRule(val fingers: Int, val holdMs: Long, val slop: Float) {
    init {
        require(fingers >= 1) { "fingers" }
        require(holdMs > 0) { "holdMs" }
        require(slop >= 0f) { "slop" }
    }
}

/** Where a finger first landed. */
data class Spot(val x: Float, val y: Float)

/**
 * One touch on a hold control, from the first finger down until every finger is up, as a value:
 * each event gives the next [Hold]. A touch that breaks the [rule] (a finger too many, one that
 * moves past the slop, or one lifted early) is spoiled until every finger is up, so it can never
 * open by fingers being added and removed until the count happens to be right. A touch opens at
 * most once.
 */
data class Hold(
    val rule: HoldRule,
    val starts: Map<Int, Spot> = emptyMap(),
    /** When the rule's fingers were all down, still and unspoiled; null while not holding. */
    val since: Long? = null,
    val spoiled: Boolean = false,
    val opened: Boolean = false,
) {
    /** A finger landed at ([x], [y]) at [t] (milliseconds, any monotonic clock). */
    fun down(id: Int, x: Float, y: Float, t: Long): Hold {
        val next = copy(starts = starts + (id to Spot(x, y)))
        return when {
            spoiled || opened -> next
            next.starts.size == rule.fingers -> next.copy(since = t)
            next.starts.size > rule.fingers -> next.spoil()
            else -> next
        }
    }

    /**
     * A finger is now at ([x], [y]); past the slop from where it landed, the touch is spoiled. A
     * position not shown to be within the slop (not a number) spoils it too.
     */
    fun move(id: Int, x: Float, y: Float): Hold {
        val start = starts[id] ?: return this
        val dx = x - start.x
        val dy = y - start.y
        val within = dx * dx + dy * dy <= rule.slop * rule.slop
        return if (within) this else spoil()
    }

    /** A finger lifted; with none left the next touch starts fresh. */
    fun up(id: Int): Hold {
        val left = starts - id
        return if (left.isEmpty()) Hold(rule) else copy(starts = left).spoil()
    }

    /** The system took the touch away. */
    fun cancel(): Hold = Hold(rule)

    /** How far the hold has come at [t], from 0 (not holding) to 1. */
    fun progress(t: Long): Float {
        val start = since ?: return 0f
        return ((t - start).toFloat() / rule.holdMs).coerceIn(0f, 1f)
    }

    /** The touch at [t], and whether it opens now: true once per touch, when the hold is done. */
    fun tick(t: Long): Pair<Hold, Boolean> {
        val start = since ?: return this to false
        return if (!opened && t - start >= rule.holdMs) {
            copy(since = null, opened = true) to true
        } else {
            this to false
        }
    }

    private fun spoil() = copy(since = null, spoiled = true)
}
