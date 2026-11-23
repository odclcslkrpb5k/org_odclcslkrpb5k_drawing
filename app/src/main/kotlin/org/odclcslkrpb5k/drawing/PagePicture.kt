package org.odclcslkrpb5k.drawing

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import org.odclcslkrpb5k.drawing.adapters.Coloring
import org.odclcslkrpb5k.drawing.adapters.TemplateAssets
import org.odclcslkrpb5k.drawing.core.History
import org.odclcslkrpb5k.drawing.core.Outcome
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
        return flatten(fills, finished, lines, stamps, THUMB_SCALE)
    }
}

/** The page's layers, bottom to top, one bitmap pixel per page unit over [scale]. */
fun flatten(
    fills: FillsView,
    finished: FinishedStrokesView,
    lines: LinesView,
    stamps: StampsView,
    scale: Float,
): Bitmap {
    val picture = createBitmap((Page.WIDTH / scale).toInt(), (Page.HEIGHT / scale).toInt())
    val canvas = Canvas(picture)
    canvas.scale(1f / scale, 1f / scale)
    canvas.drawColor(Color.WHITE)
    fills.drawPage(canvas)
    finished.drawPage(canvas)
    lines.drawPage(canvas)
    stamps.drawOnPage(canvas)
    return picture
}

/** Page units per thumbnail pixel. */
const val THUMB_SCALE = 6f

/** The page's coloring page, or null when it has none or it cannot be read (disk thread). */
internal fun coloringOf(assets: AssetManager, page: History?): Coloring? =
    page?.template?.let { name -> (TemplateAssets.load(assets, name) as? Outcome.Ok)?.value }
