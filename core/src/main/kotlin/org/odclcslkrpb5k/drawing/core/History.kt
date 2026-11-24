package org.odclcslkrpb5k.drawing.core

/** One thing the child did to a page, which undo takes back. */
sealed interface Op {
    /** A finished stroke. */
    data class AddStroke(val stroke: StrokeData) : Op

    /** A stamp placed on the page. */
    data class AddStamp(val stamp: StampData) : Op

    /** A coloring page's region filled with a color; only a page with a template has regions. */
    data class Fill(val region: Int, val color: Argb) : Op
}

/**
 * A page's edit history. The page is exactly the operations in [done], oldest first, drawn over the
 * coloring page named [template] if it has one; undo moves the newest to [undone], redo moves it
 * back, and a new operation clears [undone].
 */
data class History(
    val done: List<Op> = emptyList(),
    val undone: List<Op> = emptyList(),
    val template: String? = null,
) {
    init {
        require(template == null || Templates.isName(template)) { "not a coloring page name" }
        // A fill needs regions, which only a coloring page has; the codec refuses such a page, so
        // one could never be saved.
        require(template != null || (done + undone).none { it is Op.Fill }) {
            "a fill on a page with no coloring page"
        }
    }

    /** No more operations fit; the page codec reads no more than [MAX_OPS]. */
    val isFull: Boolean
        get() = done.size >= MAX_OPS

    val canUndo: Boolean
        get() = done.isNotEmpty()

    val canRedo: Boolean
        get() = undone.isNotEmpty()

    /** The strokes on the page, oldest first. */
    val strokes: List<StrokeData>
        get() = done.filterIsInstance<Op.AddStroke>().map { it.stroke }

    /**
     * The stamps on the page, oldest first; drawn in turn with the strokes, so later ones cover
     * them.
     */
    val stamps: List<StampData>
        get() = done.filterIsInstance<Op.AddStamp>().map { it.stamp }

    /** Each filled region's color; a region filled again shows its newest color. */
    val fills: Map<Int, Argb>
        get() = done.filterIsInstance<Op.Fill>().associate { it.region to it.color }

    /**
     * Whether [region] shows [color] whole: its newest fill is that color and no eraser stroke has
     * been drawn since (an eraser may have cut into it, so filling it again is a change).
     */
    fun showsFill(region: Int, color: Argb): Boolean {
        val last = done.lastOrNull {
            (it is Op.Fill && it.region == region) ||
                (it is Op.AddStroke && it.stroke.brush.kind == BrushKind.ERASER)
        }
        return last is Op.Fill && last.color == color
    }

    /** The page as saved: everything done, nothing that could be redone. */
    fun saved(): History = copy(undone = emptyList())

    /**
     * Moves the stamp placed last: a stamp still being moved or sized is one operation with its
     * placing, so one undo takes it away. Only when the newest operation is exactly [from] is it
     * replaced; anything else (a stroke landed since, an undo) leaves the page unchanged, so a move
     * can never overwrite another operation.
     */
    fun moveStamp(from: StampData, to: StampData): History =
        if (done.lastOrNull() == Op.AddStamp(from))
            copy(done = done.dropLast(1) + Op.AddStamp(to), undone = emptyList())
        else this

    fun apply(op: Op): History = copy(done = done + op, undone = emptyList())

    fun undo(): History =
        if (done.isEmpty()) this else copy(done = done.dropLast(1), undone = undone + done.last())

    fun redo(): History =
        if (undone.isEmpty()) this
        else copy(done = done + undone.last(), undone = undone.dropLast(1))

    companion object {
        /** The most operations one page may hold. */
        const val MAX_OPS: Int = 100_000
    }
}
