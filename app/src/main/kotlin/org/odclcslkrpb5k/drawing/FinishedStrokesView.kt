package org.odclcslkrpb5k.drawing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.Log
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.ink.geometry.Box
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import org.odclcslkrpb5k.drawing.adapters.InkStrokes
import org.odclcslkrpb5k.drawing.core.Argb
import org.odclcslkrpb5k.drawing.core.BrushKind
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.Page
import org.odclcslkrpb5k.drawing.core.StrokeData

/**
 * Draws the finished strokes, from the app's own model, into a page-sized bitmap, transparent where
 * nothing is drawn so a coloring page's fills show through. A new stroke is drawn into the bitmap
 * once; the whole page is redrawn only when the strokes are replaced (load, undo). Each frame only
 * copies the bitmap.
 */
class FinishedStrokesView(context: Context) : View(context) {
    private val renderer = CanvasStrokeRenderer.create()
    private val page = createBitmap(Page.WIDTH.toInt(), Page.HEIGHT.toInt())
    private val pageCanvas = Canvas(page)
    private val identity = Matrix()
    private val scaled = Paint(Paint.FILTER_BITMAP_FLAG)
    private val cutOut = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }

    /** Page-to-view transform, set by the drawing surface from its size. */
    val pageToView = Matrix()

    init {
        page.eraseColor(Color.TRANSPARENT)
    }

    /** Draws one more stroke on top of the page. */
    fun add(data: StrokeData) {
        draw(data)
        invalidate()
    }

    /** Replaces every stroke on the page and redraws it. */
    fun show(strokes: List<StrokeData>) {
        page.eraseColor(Color.TRANSPARENT)
        strokes.forEach(::draw)
        invalidate()
    }

    /** Draws a stroke; an eraser stroke instead cuts its shape out of the strokes drawn so far. */
    private fun draw(data: StrokeData) {
        // The cut removes as much as the stroke is opaque, so an eraser is always cut opaque,
        // whatever color it was drawn in while the finger was down.
        val drawn =
            if (data.brush.kind == BrushKind.ERASER) {
                data.copy(brush = data.brush.copy(color = Argb(Color.BLACK)))
            } else {
                data
            }
        when (val stroke = InkStrokes.stroke(drawn)) {
            is Outcome.Ok ->
                if (data.brush.kind == BrushKind.ERASER) erase(stroke.value, data.brush.size)
                else renderer.draw(pageCanvas, stroke.value, identity)
            is Outcome.Err -> Log.w(TAG, "stroke not rendered: ${stroke.error}")
        }
    }

    /**
     * Cuts the eraser stroke's shape out of the strokes drawn so far. The layer covers only the
     * stroke's box (padded by its width), not the page: undo replays every eraser stroke.
     */
    private fun erase(stroke: Stroke, size: Float) {
        val box: Box = stroke.shape.computeBoundingBox() ?: return
        val layer =
            pageCanvas.saveLayer(
                box.xMin - size,
                box.yMin - size,
                box.xMax + size,
                box.yMax + size,
                cutOut,
            )
        renderer.draw(pageCanvas, stroke, identity)
        pageCanvas.restoreToCount(layer)
    }

    /** Draws the page, in page units, onto [canvas] (a thumbnail's). */
    fun drawPage(canvas: Canvas) {
        canvas.drawBitmap(page, 0f, 0f, scaled)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(page, pageToView, scaled)
    }

    private companion object {
        const val TAG = "FinishedStrokes"
    }
}
