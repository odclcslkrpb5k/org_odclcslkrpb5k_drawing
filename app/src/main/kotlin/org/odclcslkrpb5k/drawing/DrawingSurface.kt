package org.odclcslkrpb5k.drawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.core.graphics.createBitmap
import androidx.ink.authoring.InProgressStrokesView
import org.odclcslkrpb5k.drawing.core.Argb
import org.odclcslkrpb5k.drawing.core.BrushKind
import org.odclcslkrpb5k.drawing.core.BrushSpec
import org.odclcslkrpb5k.drawing.core.History
import org.odclcslkrpb5k.drawing.core.Op
import org.odclcslkrpb5k.drawing.core.Page
import org.odclcslkrpb5k.drawing.core.Stamp
import org.odclcslkrpb5k.drawing.core.StampData
import org.odclcslkrpb5k.drawing.core.StrokeData
import org.odclcslkrpb5k.drawing.core.isSaveable

private const val BRUSH_SIZE = 18f

/**
 * The page: finished strokes, then stamps above them, then the stroke in progress on Ink's front
 * buffer. It owns the page's history; every change goes through [onChange].
 */
class DrawingSurface(context: Context) : FrameLayout(context) {
    private val finished = FinishedStrokesView(context)
    private val stampsView = StampsView(context)
    private val ink = InProgressStrokesView(context)
    private val viewToPage = Matrix()
    private val strokes = FingerStrokes(ink, viewToPage, ::strokeFinished)
    private val stampGestures =
        StampGestures(
            stampsView,
            viewToPage,
            place = ::placeStamp,
            commit = { from, to -> change(history.moveStamp(from, to)) },
            armedChanged = { onArmed(it) },
        )
    private var history = History()

    /** Called on the main thread after every change to the page. */
    var onChange: (History) -> Unit = {}

    /** Called when a face is chosen to be placed (or no longer is). */
    var onArmed: (Stamp?) -> Unit = {}

    /** Called when a finger first lands on the page (the screen closes any open tray). */
    var onTouched: () -> Unit = {}

    /** The color the next stroke is drawn in; choosing one lets a selected stamp go. */
    var color: Argb = Argb(DEFAULT_COLOR)
        set(value) {
            field = value
            stampGestures.release()
        }

    init {
        setBackgroundColor(Color.WHITE)
        stampsView.pageToView = finished.pageToView
        addView(finished)
        addView(stampsView)
        addView(ink)
    }

    /** Shows a page read from disk, replacing whatever is drawn. */
    fun show(page: History) {
        history = page
        finished.show(page.strokes)
        stampsView.stamps = page.stamps
    }

    /** The next touch on the page places [face]; null forgets a chosen face. */
    fun arm(face: Stamp?) = if (face == null) stampGestures.release() else stampGestures.arm(face)

    val isArmed: Boolean
        get() = stampGestures.isArmed

    fun undo() = replace(history.undo())

    fun redo() = replace(history.redo())

    /**
     * The child is leaving: the stroke still under a finger (or a resting palm) is cancelled, then
     * [block] runs once Ink holds no unfinished stroke, so a stroke lifted as the child leaves is
     * handed back and kept, and Ink's renderer is idle before the screen is torn down (tearing it
     * down mid-frame aborted the app). After [LEAVE_FRAMES] frames it runs anyway, and says so.
     */
    fun leaving(block: () -> Unit) {
        strokes.cancel()
        settled(block, 0)
    }

    /** The page, strokes and stamps, small, for the gallery. */
    fun thumbnail(): Bitmap {
        val thumb =
            createBitmap((Page.WIDTH / THUMB_SCALE).toInt(), (Page.HEIGHT / THUMB_SCALE).toInt())
        val canvas = Canvas(thumb)
        canvas.scale(1f / THUMB_SCALE, 1f / THUMB_SCALE)
        finished.drawPage(canvas)
        stampsView.drawOnPage(canvas)
        return thumb
    }

    private fun settled(block: () -> Unit, frames: Int) {
        when {
            !strokes.unsettled -> block()
            frames >= LEAVE_FRAMES -> {
                Log.e(TAG, "Ink did not settle within $LEAVE_FRAMES frames")
                block()
            }
            else -> postOnAnimation { settled(block, frames + 1) }
        }
    }

    private fun strokeFinished(data: StrokeData) {
        // A stroke now follows any stamp just placed, so that stamp can no longer be moved; a face
        // the child has chosen meanwhile stays chosen.
        stampGestures.release(disarm = false)
        if (data.isSaveable() && !history.isFull) {
            history = history.apply(Op.AddStroke(data))
            finished.add(data)
            onChange(history)
        } else {
            Log.w(TAG, "stroke not kept: not saveable or page full")
        }
    }

    /** A new stamp, if the page has room (the same rule as for strokes). */
    private fun placeStamp(stamp: StampData): Boolean {
        if (history.isFull) {
            Log.w(TAG, "stamp not kept: page full")
            return false
        }
        change(history.apply(Op.AddStamp(stamp)))
        return true
    }

    /** A new or moved stamp. */
    private fun change(next: History) {
        if (next == history) return
        history = next
        stampsView.stamps = next.stamps
        onChange(next)
    }

    /** Undo or redo: everything is redrawn, and no stamp stays selected. */
    private fun replace(next: History) {
        stampGestures.release()
        if (next == history) return
        history = next
        finished.show(next.strokes)
        stampsView.stamps = next.stamps
        onChange(next)
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
        stampsView.invalidate()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onTouched()
        // Stamps first, unless a stroke is under way: a stroke is never interrupted by a stamp.
        if (!strokes.drawing && stampGestures.handle(event)) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(event)
                strokes.start(event, BrushSpec(BrushKind.PEN, color, BRUSH_SIZE))
            }
            MotionEvent.ACTION_MOVE -> strokes.move(event)
            MotionEvent.ACTION_POINTER_UP ->
                strokes.lifted(event, event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_UP -> {
                strokes.lifted(event, event.getPointerId(0))
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> strokes.cancel()
            else -> Unit
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private companion object {
        const val TAG = "DrawingSurface"
        const val LEAVE_FRAMES = 180
        const val THUMB_SCALE = 6f
        const val DEFAULT_COLOR = 0xFF7A4BE0.toInt()
    }
}
