package org.odclcslkrpb5k.drawing

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.view.View
import androidx.core.graphics.withMatrix
import kotlin.math.hypot
import org.odclcslkrpb5k.drawing.core.Stamp
import org.odclcslkrpb5k.drawing.core.StampData

/** The drawable for each stamp: a `when`, so a stamp added without one does not compile. */
fun stampDrawable(stamp: Stamp): Int =
    when (stamp) {
        Stamp.HAPPY -> R.drawable.stamp_happy
        Stamp.SAD -> R.drawable.stamp_sad
        Stamp.ANGRY -> R.drawable.stamp_angry
        Stamp.SURPRISED -> R.drawable.stamp_surprised
        Stamp.SILLY -> R.drawable.stamp_silly
        Stamp.LOVE -> R.drawable.stamp_love
        Stamp.SLEEPY -> R.drawable.stamp_sleepy
        Stamp.SCARED -> R.drawable.stamp_scared
        Stamp.UNICORN -> R.drawable.stamp_unicorn
        Stamp.CROWN -> R.drawable.stamp_crown
        Stamp.RAINBOW -> R.drawable.stamp_rainbow
        Stamp.STAR -> R.drawable.stamp_star
        Stamp.FLOWER -> R.drawable.stamp_flower
        Stamp.HEART -> R.drawable.stamp_heart
        Stamp.MICROPHONE -> R.drawable.stamp_microphone
        Stamp.BUTTERFLY -> R.drawable.stamp_butterfly
    }

/** Every stamp's drawable, in the order of [Stamp] (the tray's order). */
val STAMP_DRAWABLES: Map<Stamp, Int> = Stamp.entries.associateWith(::stampDrawable)

/**
 * The stamp still selected, above everything: the newest stamp, just placed, outlined while it can
 * be moved or sized, and [preview] standing in for it while a finger moves it. Every other stamp is
 * drawn in turn with the strokes (FinishedStrokesView), so later strokes cover it and the eraser
 * takes it; the surface hands the selected one over there when it is let go ([onReleased]).
 */
class StampsView(context: Context) : View(context) {
    private val art = StampArt(context)
    private val ring =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = RING_WIDTH
            color = RING_COLOR
            pathEffect = DashPathEffect(floatArrayOf(RING_DASH, RING_DASH), 0f)
        }

    /** Page-to-view transform, the same as the strokes'. */
    var pageToView = Matrix()

    var stamps: List<StampData> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    /** Called when the selected stamp is let go: it now belongs with the strokes. */
    var onReleased: () -> Unit = {}

    /** Whether the newest stamp is selected (it can still be moved and sized). */
    var stampSelected = false
        set(value) {
            val released = field && !value
            field = value
            invalidate()
            if (released) onReleased()
        }

    /** Stands in for the selected stamp while a finger moves it. */
    var preview: StampData? = null
        set(value) {
            field = value
            invalidate()
        }

    /** The stamps as they look now, the moving one included. */
    private fun current(): List<StampData> {
        val p = preview ?: return stamps
        return stamps.dropLast(1) + p
    }

    /** Whether a page point is on the selected stamp (a little generous, for small fingers). */
    fun onSelected(x: Float, y: Float): Boolean {
        val s = current().lastOrNull() ?: return false
        return stampSelected && hypot(x - s.x, y - s.y) <= s.size * HIT_SLOP / 2
    }

    /** Draws the selected stamp, if any, in page units onto [canvas] (the view, or a thumbnail). */
    fun drawOnPage(canvas: Canvas) {
        val s = current().lastOrNull()
        if (stampSelected && s != null) art.draw(canvas, s)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.withMatrix(pageToView) {
            drawOnPage(this)
            val s = current().lastOrNull()
            if (stampSelected && s != null) drawCircle(s.x, s.y, s.size * RING_SCALE / 2, ring)
        }
    }

    private companion object {
        const val HIT_SLOP = 1.3f
        const val RING_SCALE = 1.15f
        const val RING_WIDTH = 6f
        const val RING_DASH = 18f
        const val RING_COLOR = 0xFF7A4BE0.toInt()
    }
}
