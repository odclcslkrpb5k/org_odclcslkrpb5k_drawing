package org.odclcslkrpb5k.drawing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import org.odclcslkrpb5k.drawing.adapters.InkStrokes
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

    private fun draw(data: StrokeData) {
        when (val stroke = InkStrokes.stroke(data)) {
            is Outcome.Ok -> renderer.draw(pageCanvas, stroke.value, identity)
            is Outcome.Err -> Log.w(TAG, "stroke not rendered: ${stroke.error}")
        }
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
