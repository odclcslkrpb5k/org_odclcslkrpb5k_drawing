package org.odclcslkrpb5k.drawing

import android.graphics.Matrix
import android.view.MotionEvent
import kotlin.math.hypot
import org.odclcslkrpb5k.drawing.core.Stamp
import org.odclcslkrpb5k.drawing.core.StampData
import org.odclcslkrpb5k.drawing.core.isSaveable

/**
 * The fingers on stamps. After a face is chosen ([arm]), the next touch places it where the finger
 * lands, selected, and the same touch can slide it. While it is selected, one finger on it moves it
 * and two fingers size it; the move is shown live and committed once, when the last finger lifts,
 * as a move from the stamp as it was grabbed ([commit]). A cancelled touch commits no move (a face
 * it placed stays where it landed). A touch anywhere else lets the stamp go and is left to drawing.
 */
class StampGestures(
    private val view: StampsView,
    private val viewToPage: Matrix,
    private val place: (StampData) -> Boolean,
    private val commit: (from: StampData, to: StampData) -> Unit,
    private val armedChanged: (Stamp?) -> Unit,
) {
    private var armed: Stamp? = null
        set(value) {
            field = value
            armedChanged(value)
        }

    /** The stamp as the finger took hold of it; a move is always from this. */
    private var grabbed: StampData? = null

    /** The stamp as it was when the current finger arrangement began. */
    private var start: StampData? = null
    private var downX = 0f
    private var downY = 0f
    private var spread = 0f
    private val point = FloatArray(2)

    /** The next touch on the page places [face]. */
    fun arm(face: Stamp) {
        release()
        armed = face
    }

    /**
     * Lets the selected stamp go and drops any move in progress; with [disarm], also forgets a
     * chosen face (a stroke handed back meanwhile must not, or the child's next touch would draw).
     */
    fun release(disarm: Boolean = true) {
        view.stampSelected = false
        view.preview = null
        grabbed = null
        start = null
        if (disarm && armed != null) armed = null
    }

    /** Whether a face is chosen and waiting to be placed. */
    val isArmed: Boolean
        get() = armed != null

    /** Handles a touch that is about stamps; false leaves it to drawing. */
    fun handle(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val (x, y) = page(event.x, event.y)
            return armed?.let { placeAt(it, x, y) } ?: grab(x, y)
        }
        if (grabbed == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> rebase(event, 0)
            MotionEvent.ACTION_MOVE -> moved(event)
            MotionEvent.ACTION_POINTER_UP -> rebase(event, if (event.actionIndex == 0) 1 else 0)
            MotionEvent.ACTION_UP -> lifted()
            MotionEvent.ACTION_CANCEL -> view.preview = null.also { grabbed = null }
            else -> Unit
        }
        return true
    }

    /** The chosen face lands under the finger, selected and already held. */
    private fun placeAt(face: Stamp, x: Float, y: Float): Boolean {
        armed = null
        val placed = StampData(face, x, y, StampData.DEFAULT_SIZE)
        val kept = placed.isSaveable() && place(placed)
        view.stampSelected = kept
        if (kept) hold(placed, x, y)
        return true
    }

    /** A finger on the selected stamp takes hold of it; anywhere else lets it go. */
    private fun grab(x: Float, y: Float): Boolean {
        val stamp = view.stamps.lastOrNull()
        if (stamp == null || !view.onSelected(x, y)) {
            view.stampSelected = false
            return false
        }
        hold(stamp, x, y)
        return true
    }

    private fun hold(stamp: StampData, x: Float, y: Float) {
        grabbed = stamp
        start = stamp
        downX = x
        downY = y
        spread = 0f
    }

    /**
     * A finger joined or left: the gesture starts again from the stamp as it is now, held by the
     * pointer at [anchor]; with two fingers down, their spread is the size reference.
     */
    private fun rebase(event: MotionEvent, anchor: Int) {
        start = view.preview ?: start
        val (x, y) = page(event.getX(anchor), event.getY(anchor))
        downX = x
        downY = y
        spread = remainingSpread(event)
    }

    private fun moved(event: MotionEvent) {
        val s = start ?: return
        val (x, y) = page(event.getX(0), event.getY(0))
        val scale =
            if (event.pointerCount > 1 && spread > 0f) remainingSpread(event) / spread else 1f
        val size = (s.size * scale).coerceIn(StampData.MIN_SIZE, StampData.MAX_SIZE)
        val moved = s.copy(x = s.x + x - downX, y = s.y + y - downY, size = size)
        if (moved.isSaveable()) view.preview = moved
    }

    private fun lifted() {
        val from = grabbed
        val to = view.preview
        grabbed = null
        view.preview = null
        if (from != null && to != null && to != from) commit(from, to)
    }

    /** The spread of the first two fingers that stay down (a lifting finger does not count). */
    private fun remainingSpread(event: MotionEvent): Float {
        val leaving =
            if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1
        val staying = (0 until event.pointerCount).filter { it != leaving }
        if (staying.size < 2) return 0f
        val (x0, y0) = page(event.getX(staying[0]), event.getY(staying[0]))
        val (x1, y1) = page(event.getX(staying[1]), event.getY(staying[1]))
        return hypot(x1 - x0, y1 - y0)
    }

    private fun page(x: Float, y: Float): Pair<Float, Float> {
        point[0] = x
        point[1] = y
        viewToPage.mapPoints(point)
        return point[0] to point[1]
    }
}
