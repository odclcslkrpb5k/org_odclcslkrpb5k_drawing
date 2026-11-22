package org.odclcslkrpb5k.drawing

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.strokes.Stroke
import org.odclcslkrpb5k.drawing.adapters.InkStrokes
import org.odclcslkrpb5k.drawing.core.Argb
import org.odclcslkrpb5k.drawing.core.BrushKind
import org.odclcslkrpb5k.drawing.core.BrushSpec
import org.odclcslkrpb5k.drawing.core.History
import org.odclcslkrpb5k.drawing.core.Op
import org.odclcslkrpb5k.drawing.core.Page
import org.odclcslkrpb5k.drawing.core.isSaveable

private const val BRUSH_SIZE = 18f

/**
 * The page and the finger on it. One finger draws at a time: the first to touch owns the stroke,
 * and other fingers are ignored until it lifts. Ink draws the stroke in progress on the front
 * buffer; when Ink hands a finished stroke back, the app keeps its own copy, draws it on the
 * finished layer, and Ink lets go of it.
 */
class DrawingSurface(context: Context) : FrameLayout(context), InProgressStrokesFinishedListener {
    private val finished = FinishedStrokesView(context)
    private val inProgress = InProgressStrokesView(context)
    private var history = History()

    /** Called on the main thread after every change to the page. */
    var onChange: (History) -> Unit = {}

    /** The color the next stroke is drawn in. */
    var color: Argb = Argb(DEFAULT_COLOR)
    private val viewToPage = Matrix()

    /** Strokes Ink has been asked to finish but has not handed back yet, by Ink's id. */
    private val pending = mutableMapOf<InProgressStrokeId, BrushSpec>()
    private var active: Active? = null

    private class Active(val pointerId: Int, val id: InProgressStrokeId, val brush: BrushSpec)

    /** Shows a page read from disk, replacing whatever is drawn. */
    fun show(page: History) {
        history = page
        finished.show(page.strokes)
    }

    /**
     * The child is leaving: the stroke still under a finger (or a resting palm) is cancelled, then
     * [block] runs once Ink holds no unfinished stroke, so a stroke lifted as the child leaves is
     * handed back and kept, and Ink's renderer is idle before the screen is torn down (tearing it
     * down mid-frame aborted the app). After [LEAVE_FRAMES] frames it runs anyway, and says so.
     */
    fun leaving(block: () -> Unit) {
        cancel()
        settled(block, 0)
    }

    private fun settled(block: () -> Unit, frames: Int) {
        when {
            !inProgress.hasUnfinishedStrokes() -> block()
            frames >= LEAVE_FRAMES -> {
                Log.e(TAG, "Ink did not settle within $LEAVE_FRAMES frames")
                block()
            }
            else -> postOnAnimation { settled(block, frames + 1) }
        }
    }

    fun undo() = replace(history.undo())

    fun redo() = replace(history.redo())

    /** A copy of the page, small, for the gallery. */
    fun thumbnail(): android.graphics.Bitmap = finished.thumbnail()

    private fun replace(next: History) {
        if (next == history) return
        history = next
        finished.show(next.strokes)
        onChange(next)
    }

    private companion object {
        const val TAG = "DrawingSurface"
        const val LEAVE_FRAMES = 180
        const val DEFAULT_COLOR = 0xFF7A4BE0.toInt()
    }

    init {
        setBackgroundColor(Color.WHITE)
        addView(finished)
        addView(inProgress)
        inProgress.addFinishedStrokesListener(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val scale = minOf(w / Page.WIDTH, h / Page.HEIGHT)
        finished.pageToView.setScale(scale, scale)
        finished.pageToView.postTranslate(
            (w - Page.WIDTH * scale) / 2,
            (h - Page.HEIGHT * scale) / 2,
        )
        finished.pageToView.invert(viewToPage)
        finished.invalidate()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> start(event)
            MotionEvent.ACTION_MOVE -> move(event)
            MotionEvent.ACTION_POINTER_UP -> lifted(event, event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_UP -> {
                lifted(event, event.getPointerId(0))
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> cancel()
            else -> Unit
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun start(event: MotionEvent) {
        requestUnbufferedDispatch(event)
        val pointerId = event.getPointerId(0)
        val brush = BrushSpec(BrushKind.PEN, color, BRUSH_SIZE)
        val id = inProgress.startStroke(event, pointerId, InkStrokes.brush(brush), viewToPage)
        active = Active(pointerId, id, brush)
    }

    private fun move(event: MotionEvent) {
        val a = active ?: return
        if (event.findPointerIndex(a.pointerId) < 0) return
        inProgress.addToStroke(event, a.pointerId, a.id)
    }

    private fun lifted(event: MotionEvent, pointerId: Int) {
        val a = active ?: return
        if (pointerId != a.pointerId) return
        inProgress.finishStroke(event, a.pointerId, a.id)
        pending[a.id] = a.brush
        active = null
    }

    private fun cancel() {
        active?.let { inProgress.cancelStroke(it.id) }
        active = null
    }

    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        for ((id, stroke) in strokes) {
            val brush = pending.remove(id) ?: continue
            val data = InkStrokes.data(brush, stroke)
            if (data.isSaveable() && !history.isFull) {
                history = history.apply(Op.AddStroke(data))
                finished.add(data)
                onChange(history)
            } else {
                Log.w(TAG, "stroke not kept: not saveable or page full")
            }
        }
        inProgress.removeFinishedStrokes(strokes.keys)
    }
}
