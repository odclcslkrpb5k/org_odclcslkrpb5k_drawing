package org.odclcslkrpb5k.drawing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.view.View
import androidx.core.graphics.createBitmap
import org.odclcslkrpb5k.drawing.core.Op
import org.odclcslkrpb5k.drawing.core.Page
import org.odclcslkrpb5k.drawing.core.StampData
import org.odclcslkrpb5k.drawing.core.StrokeData

/**
 * What she drew and stamped, in the order she did it, in one page-sized bitmap: a stroke drawn
 * after a stamp goes over it, and an eraser stroke rubs out strokes and stamps alike. Transparent
 * where nothing is drawn, so a coloring page's fills and a photo show through. A new mark is drawn
 * into the bitmap once; the whole page is redrawn only when the marks are replaced (load, undo).
 * Each frame only copies the bitmap.
 */
class FinishedStrokesView(context: Context) : View(context) {
    private val marks = Marks()
    private val art = StampArt(context)
    private val page = createBitmap(Page.WIDTH.toInt(), Page.HEIGHT.toInt())
    private val pageCanvas = Canvas(page)
    private val scaled = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Page-to-view transform, set by the drawing surface from its size. */
    val pageToView = Matrix()

    init {
        page.eraseColor(Color.TRANSPARENT)
    }

    /** Draws one more stroke on top of the page (an eraser cuts instead). */
    fun add(data: StrokeData) {
        marks.stroke(pageCanvas, data)
        invalidate()
    }

    /** Draws one more stamp on top of the page. */
    fun addStamp(stamp: StampData) {
        art.draw(pageCanvas, stamp)
        invalidate()
    }

    /**
     * Replaces the page with these operations' strokes and stamps, in order; with
     * [withoutNewestStamp], a stamp that is the newest operation is left out (it is still selected,
     * and the stamps view draws it on top until it is let go).
     */
    fun show(ops: List<Op>, withoutNewestStamp: Boolean = false) {
        page.eraseColor(Color.TRANSPARENT)
        val drawn =
            if (withoutNewestStamp && ops.lastOrNull() is Op.AddStamp) ops.dropLast(1) else ops
        for (op in drawn) {
            when (op) {
                is Op.AddStroke -> marks.stroke(pageCanvas, op.stroke)
                is Op.AddStamp -> art.draw(pageCanvas, op.stamp)
                is Op.Fill -> Unit
            }
        }
        invalidate()
    }

    /** Draws the page, in page units, onto [canvas] (a thumbnail's). */
    fun drawPage(canvas: Canvas) {
        canvas.drawBitmap(page, 0f, 0f, scaled)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(page, pageToView, scaled)
    }
}
