package org.odclcslkrpb5k.drawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.view.View
import androidx.core.graphics.createBitmap
import org.odclcslkrpb5k.drawing.adapters.Coloring
import org.odclcslkrpb5k.drawing.core.Argb
import org.odclcslkrpb5k.drawing.core.Page

/**
 * The page's bottom: a photo she took (the selfie stretch), then a coloring page's filled regions,
 * under the strokes, in a page-sized bitmap. A fill paints the region's precomputed row runs, so it
 * never searches the page; undo repaints every fill.
 */
class FillsView(context: Context) : View(context) {
    private val page = createBitmap(Page.WIDTH.toInt(), Page.HEIGHT.toInt())
    private val pageCanvas = Canvas(page)
    private val paint = Paint()
    private val scaled = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Page-to-view transform, shared with the other page layers. */
    var pageToView = Matrix()

    /** A photo to draw on, the page's size; null on a page without one. */
    var photo: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    /** The coloring page being filled; null on a blank page, where nothing is drawn. */
    var coloring: Coloring? = null

    /** Paints one region; regions the page does not have are ignored. */
    fun fill(region: Int, color: Argb) {
        paintRegion(region, color)
        invalidate()
    }

    /** Repaints every fill (open, undo, redo). */
    fun show(fills: Map<Int, Argb>) {
        page.eraseColor(Color.TRANSPARENT)
        for ((region, color) in fills) paintRegion(region, color)
        invalidate()
    }

    private fun paintRegion(region: Int, color: Argb) {
        val runs = coloring?.regions?.runs(region) ?: return
        paint.color = color.value
        for (i in runs.indices step RUN) {
            val y = runs[i].toFloat()
            val x = runs[i + 1].toFloat()
            pageCanvas.drawRect(x, y, x + runs[i + 2], y + 1, paint)
        }
    }

    /** Draws the fills, in page units, onto [canvas] (a thumbnail's). */
    fun drawPage(canvas: Canvas) {
        photo?.let { canvas.drawBitmap(it, 0f, 0f, scaled) }
        if (coloring != null) canvas.drawBitmap(page, 0f, 0f, scaled)
    }

    override fun onDraw(canvas: Canvas) {
        photo?.let { canvas.drawBitmap(it, pageToView, scaled) }
        if (coloring != null) canvas.drawBitmap(page, pageToView, scaled)
    }

    private companion object {
        /** Ints per run: y, first x, length (Regions.runs). */
        const val RUN = 3
    }
}

/** A coloring page's line art, over the strokes, so scribbling past a line never hides it. */
class LinesView(context: Context) : View(context) {
    private val scaled = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Page-to-view transform, shared with the other page layers. */
    var pageToView = Matrix()

    var lines: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Draws the lines, in page units, onto [canvas] (a thumbnail's). */
    fun drawPage(canvas: Canvas) {
        lines?.let { canvas.drawBitmap(it, 0f, 0f, scaled) }
    }

    override fun onDraw(canvas: Canvas) {
        lines?.let { canvas.drawBitmap(it, pageToView, scaled) }
    }
}
