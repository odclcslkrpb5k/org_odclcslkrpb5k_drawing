package org.odclcslkrpb5k.drawing.core

/** One thing the child did to a page, which undo takes back. */
sealed interface Op {
    /** A finished stroke. */
    data class AddStroke(val stroke: StrokeData) : Op
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
        get() = done.map { op ->
            when (op) {
                is Op.AddStroke -> op.stroke
            }
        }

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
