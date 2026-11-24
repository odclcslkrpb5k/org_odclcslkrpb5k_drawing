package org.odclcslkrpb5k.drawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokesView
import org.odclcslkrpb5k.drawing.adapters.Coloring
import org.odclcslkrpb5k.drawing.core.Argb
import org.odclcslkrpb5k.drawing.core.BrushKind
import org.odclcslkrpb5k.drawing.core.BrushSpec
import org.odclcslkrpb5k.drawing.core.History
import org.odclcslkrpb5k.drawing.core.Op
import org.odclcslkrpb5k.drawing.core.Page
import org.odclcslkrpb5k.drawing.core.Stamp
import org.odclcslkrpb5k.drawing.core.StrokeData
import org.odclcslkrpb5k.drawing.core.isSaveable

/**
 * The page, bottom to top: a photo and a coloring page's fills, the strokes and stamps in the order
 * she made them (later ones over earlier ones; the eraser rubs out both, and fills made before it),
 * the coloring page's line art (so the lines stay crisp over scribbles), a stamp still selected,
 * then the stroke in progress on Ink's front buffer. It owns the page's history; every change goes
 * through [onChange].
 */
class DrawingSurface(context: Context) : FrameLayout(context) {
    private val finished = FinishedStrokesView(context)
    private val fills = FillsView(context)
    private val lines = LinesView(context)
    private val stampsView = StampsView(context)
    private val ink = InProgressStrokesView(context)
    private val viewToPage = Matrix()
    private val strokes = FingerStrokes(ink, viewToPage, ::strokeFinished)
    private val stampGestures =
        StampGestures(
            stampsView,
            viewToPage,
            place = { add(Op.AddStamp(it)) },
            commit = { from, to -> change(history.moveStamp(from, to)) },
            armedChanged = { onArmed(it) },
        )
    private val fillGestures =
        FillGestures(viewToPage) { region ->
            // Filling a region that already shows the color whole is no change: nothing to undo.
            if (!history.showsFill(region, color) && !add(Op.Fill(region, color))) {
                Log.w(TAG, "fill not kept")
            }
        }
    private var history = History()

    /** The newest stamp is selected and not yet drawn with the strokes. */
    private var stampPending = false

    /** On a coloring page, a tap fills the region under the finger instead of drawing. */
    var filling: Boolean
        get() = fillGestures.on
        set(value) {
            fillGestures.on = value
            if (fillGestures.on) stampGestures.release()
        }

    /** Called on the main thread after every change to the page. */
    var onChange: (History) -> Unit = {}

    /** Called when a face is chosen to be placed (or no longer is). */
    var onArmed: (Stamp?) -> Unit = {}

    /** Called when a finger first lands on the page (the screen closes any open tray). */
    var onTouched: () -> Unit = {}

    /** What the next stroke is drawn with. */
    var tool: Tool = Tool.MEDIUM

    /** The color the next stroke is drawn in; choosing one lets a selected stamp go. */
    var color: Argb = Argb(DEFAULT_COLOR)
        set(value) {
            field = value
            stampGestures.release()
        }

    init {
        setBackgroundColor(Color.WHITE)
        stampsView.pageToView = finished.pageToView
        // A stamp let go joins the strokes in turn: later strokes go over it, the eraser takes it.
        stampsView.onReleased = {
            val newest = history.done.lastOrNull()
            when {
                !stampPending -> Unit
                newest is Op.AddStamp -> finished.addStamp(newest.stamp)
                // Every change lets the stamp go first, so it is the newest; if not, it is drawn
                // at the next full redraw, and this says so.
                else -> Log.w(TAG, "a released stamp is not the newest change")
            }
            stampPending = false
        }
        fills.pageToView = finished.pageToView
        lines.pageToView = finished.pageToView
        addView(fills)
        addView(finished)
        addView(lines)
        addView(stampsView)
        addView(ink)
    }

    /**
     * Shows a page read from disk, replacing whatever is drawn, over [coloring] when it was started
     * from a coloring page (null if that page could not be read: the drawing still opens).
     */
    fun show(page: History, coloring: Coloring?, photo: Bitmap? = null) {
        history = page
        fills.photo = photo
        fillGestures.regions = coloring?.regions
        fills.coloring = coloring
        lines.lines = coloring?.lines
        stampPending = false
        fills.show(page.done)
        finished.show(page.done)
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
     * Then Ink's layer is hidden and [block] waits one more frame, so no frame of that layer is
     * still being drawn when the screen goes: a rare crash in the system's render thread while
     * leaving under load (emulator, 2026-11-24) is the reason; it could not be made to happen on
     * demand, so this is a precaution, checked on the tablet (device.md).
     */
    fun leaving(block: () -> Unit) {
        strokes.cancel()
        settled(
            {
                ink.visibility = INVISIBLE
                postOnAnimation { block() }
            },
            0,
        )
    }

    /**
     * The page flattened, one bitmap pixel per [scale] page units: [THUMB_SCALE] for home's
     * thumbnails, 1 for the tablet's gallery.
     */
    fun picture(scale: Float): Bitmap = flatten(fills, finished, lines, stampsView, scale)

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
            // An eraser rubs out fills as well as strokes and stamps (never a photo or a line).
            if (data.brush.kind == BrushKind.ERASER) fills.erase(data)
            onChange(history)
        } else {
            Log.w(TAG, "stroke not kept: not saveable or page full")
        }
    }

    /** A new stamp or fill, if the page has room (the same rule as for strokes). */
    private fun add(op: Op): Boolean {
        if (history.isFull) {
            Log.w(TAG, "not kept: page full")
            return false
        }
        // Painted before the change is reported: the thumbnail taken for it must show the fill.
        if (op is Op.Fill) fills.fill(op.region, op.color)
        // A stamp just placed is selected: drawn on top until let go, then with the strokes.
        if (op is Op.AddStamp) stampPending = true
        change(history.apply(op))
        return true
    }

    /** A new or moved stamp, or a new fill. */
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
        stampPending = false
        fills.show(next.done)
        finished.show(next.done)
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
        fills.invalidate()
        finished.invalidate()
        lines.invalidate()
        stampsView.invalidate()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onTouched()
        // Stamps first, unless a stroke is under way: a stroke is never interrupted by a stamp.
        // Stamps, then filling, take whole gestures; neither ever interrupts a stroke under way.
        val taken = !strokes.drawing && (stampGestures.handle(event) || fillGestures.handle(event))
        if (taken) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(event)
                strokes.start(event, BrushSpec(tool.kind, inkFor(tool, color), tool.size))
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

        const val DEFAULT_COLOR = 0xFF7A4BE0.toInt()
    }
}

/**
 * The color a stroke is drawn in. The highlighter is see-through, whatever the swatch. The eraser
 * shows a faint grey trail while the finger is down (solid white would seem to wipe the photo and
 * the lines, which it leaves alone); finished, it is a cut-out and its color is never drawn.
 */
private fun inkFor(tool: Tool, color: Argb): Argb =
    when (tool) {
        Tool.ERASER -> Argb(ERASER_TRAIL)
        Tool.HIGHLIGHTER -> Argb((color.value and RGB) or HIGHLIGHT_ALPHA)
        Tool.THIN,
        Tool.MEDIUM,
        Tool.THICK -> color
    }

private const val ERASER_TRAIL = 0x55888888
private const val RGB = 0x00FFFFFF
private const val HIGHLIGHT_ALPHA = 0x66000000
