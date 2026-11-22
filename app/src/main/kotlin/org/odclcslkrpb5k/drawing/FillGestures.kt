package org.odclcslkrpb5k.drawing

import android.graphics.Matrix
import android.view.MotionEvent
import org.odclcslkrpb5k.drawing.core.Regions

/**
 * The fill tool on a coloring page. While [on], each gesture is one fill: the first finger down
 * fills the region under it, or the nearest within [NEAR] page pixels when it lands on a line (a
 * child aims at the shape, not between the lines). The rest of the gesture is taken and ignored, so
 * a fill never also draws.
 */
class FillGestures(private val viewToPage: Matrix, private val fill: (region: Int) -> Unit) {
    /** The page's regions; null on a blank page, where the tool cannot be on. */
    var regions: Regions? = null
        set(value) {
            field = value
            if (value == null) on = false
        }

    var on: Boolean = false
        set(value) {
            field = value && regions != null
        }

    /** True when the event belongs to the fill tool. */
    fun handle(event: MotionEvent): Boolean {
        val page = regions
        if (!on || page == null) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val at = floatArrayOf(event.x, event.y)
            viewToPage.mapPoints(at)
            val region = page.near(at[0].toInt(), at[1].toInt(), NEAR)
            if (region != Regions.LINE) fill(region)
        }
        return true
    }

    private companion object {
        const val NEAR = 24
    }
}
