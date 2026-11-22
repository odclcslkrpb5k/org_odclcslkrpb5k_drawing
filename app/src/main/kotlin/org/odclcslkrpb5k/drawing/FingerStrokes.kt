package org.odclcslkrpb5k.drawing

import android.graphics.Matrix
import android.util.Log
import android.view.MotionEvent
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.strokes.Stroke
import org.odclcslkrpb5k.drawing.adapters.InkStrokes
import org.odclcslkrpb5k.drawing.core.BrushSpec
import org.odclcslkrpb5k.drawing.core.StrokeData

/**
 * One finger drawing with Ink. The first finger to touch owns the stroke, and other fingers are
 * ignored until it lifts. Ink draws the stroke in progress on the front buffer; when Ink hands a
 * finished stroke back, [finished] receives the app's own copy and Ink lets go of it.
 */
class FingerStrokes(
    private val ink: InProgressStrokesView,
    private val viewToPage: Matrix,
    private val finished: (StrokeData) -> Unit,
) : InProgressStrokesFinishedListener {
    /** Strokes Ink has been asked to finish but has not handed back yet, by Ink's id. */
    private val pending = mutableMapOf<InProgressStrokeId, BrushSpec>()
    private var active: Active? = null

    private class Active(val pointerId: Int, val id: InProgressStrokeId, val brush: BrushSpec)

    init {
        ink.addFinishedStrokesListener(this)
    }

    val drawing: Boolean
        get() = active != null

    /** Whether Ink still holds a stroke it has not handed back. */
    val unsettled: Boolean
        get() = ink.hasUnfinishedStrokes()

    fun start(event: MotionEvent, brush: BrushSpec) {
        val pointerId = event.getPointerId(0)
        val id = ink.startStroke(event, pointerId, InkStrokes.brush(brush), viewToPage)
        active = Active(pointerId, id, brush)
    }

    fun move(event: MotionEvent) {
        val a = active ?: return
        if (event.findPointerIndex(a.pointerId) < 0) return
        ink.addToStroke(event, a.pointerId, a.id)
    }

    fun lifted(event: MotionEvent, pointerId: Int) {
        val a = active ?: return
        if (pointerId != a.pointerId) return
        ink.finishStroke(event, a.pointerId, a.id)
        pending[a.id] = a.brush
        active = null
    }

    fun cancel() {
        active?.let { ink.cancelStroke(it.id) }
        active = null
    }

    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        for ((id, stroke) in strokes) {
            val brush = pending.remove(id)
            if (brush == null) Log.w(TAG, "stroke handed back that was not pending")
            else finished(InkStrokes.data(brush, stroke))
        }
        ink.removeFinishedStrokes(strokes.keys)
    }

    private companion object {
        const val TAG = "FingerStrokes"
    }
}
