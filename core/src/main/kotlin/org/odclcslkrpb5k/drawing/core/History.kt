package org.odclcslkrpb5k.drawing.core

/** One thing the child did to a page, which undo takes back. */
sealed interface Op {
    /** A finished stroke. */
    data class AddStroke(val stroke: StrokeData) : Op

    /** A stamp placed on the page. */
    data class AddStamp(val stamp: StampData) : Op
}

/**
 * A page's edit history. The page is exactly the operations in [done], oldest first; undo moves the
 * newest to [undone], redo moves it back, and a new operation clears [undone].
 */
data class History(val done: List<Op> = emptyList(), val undone: List<Op> = emptyList()) {
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

    /** The stamps on the page, oldest first; they are drawn above every stroke. */
    val stamps: List<StampData>
        get() = done.filterIsInstance<Op.AddStamp>().map { it.stamp }

    /**
     * Moves the stamp placed last: a stamp still being moved or sized is one operation with its
     * placing, so one undo takes it away. Only when the newest operation is exactly [from] is it
     * replaced; anything else (a stroke landed since, an undo) leaves the page unchanged, so a move
     * can never overwrite another operation.
     */
    fun moveStamp(from: StampData, to: StampData): History =
        if (done.lastOrNull() == Op.AddStamp(from))
            History(done.dropLast(1) + Op.AddStamp(to), emptyList())
        else this

    fun apply(op: Op): History = History(done + op, emptyList())

    fun undo(): History =
        if (done.isEmpty()) this else History(done.dropLast(1), undone + done.last())

    fun redo(): History =
        if (undone.isEmpty()) this else History(done + undone.last(), undone.dropLast(1))

    companion object {
        /** The most operations one page may hold. */
        const val MAX_OPS: Int = 100_000
    }
}
