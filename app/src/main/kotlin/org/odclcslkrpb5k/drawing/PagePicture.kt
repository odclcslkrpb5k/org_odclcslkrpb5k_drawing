package org.odclcslkrpb5k.drawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import org.odclcslkrpb5k.drawing.adapters.Coloring
import org.odclcslkrpb5k.drawing.core.History
import org.odclcslkrpb5k.drawing.core.Page

/**
 * Draws saved pages small, off screen, through the same layers the drawing screen shows: the parent
 * screen's previews of older versions, and the gallery picture of a page rolled back. One instance
 * reuses its page-sized layers for every page it draws; main thread only.
 */
class PagePicture(context: Context) {
    private val fills = FillsView(context)
    private val finished = FinishedStrokesView(context)
    private val lines = LinesView(context)
    private val stamps = StampsView(context)

    fun draw(page: History, coloring: Coloring?): Bitmap {
        fills.coloring = coloring
        lines.lines = coloring?.lines
        fills.show(page.fills)
        finished.show(page.strokes)
        stamps.stamps = page.stamps
        return thumbnail(fills, finished, lines, stamps)
    }
}

/** The page's layers, bottom to top, small, for the gallery. */
fun thumbnail(
    fills: FillsView,
    finished: FinishedStrokesView,
    lines: LinesView,
    stamps: StampsView,
): Bitmap {
    val thumb =
        createBitmap((Page.WIDTH / THUMB_SCALE).toInt(), (Page.HEIGHT / THUMB_SCALE).toInt())
    val canvas = Canvas(thumb)
    canvas.scale(1f / THUMB_SCALE, 1f / THUMB_SCALE)
    canvas.drawColor(Color.WHITE)
    fills.drawPage(canvas)
    finished.drawPage(canvas)
    lines.drawPage(canvas)
    stamps.drawOnPage(canvas)
    return thumb
}

private const val THUMB_SCALE = 6f
