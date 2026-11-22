package org.odclcslkrpb5k.drawing

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.graphics.withMatrix
import kotlin.math.hypot
import org.odclcslkrpb5k.drawing.core.Stamp
import org.odclcslkrpb5k.drawing.core.StampData

/** The drawable for each face, in the order of [Stamp]. */
val STAMP_DRAWABLES: Map<Stamp, Int> =
    mapOf(
        Stamp.HAPPY to R.drawable.stamp_happy,
        Stamp.SAD to R.drawable.stamp_sad,
        Stamp.ANGRY to R.drawable.stamp_angry,
        Stamp.SURPRISED to R.drawable.stamp_surprised,
        Stamp.SILLY to R.drawable.stamp_silly,
        Stamp.LOVE to R.drawable.stamp_love,
        Stamp.SLEEPY to R.drawable.stamp_sleepy,
        Stamp.SCARED to R.drawable.stamp_scared,
    )

/**
 * The stamps, above every stroke. They are drawn from their page position and size, never from
 * pixels, so they stay sharp at any size. The newest stamp may be selected: it is outlined, and
 * while a finger moves it, [preview] stands in for it until the move is committed.
 */
class StampsView(context: Context) : View(context) {
    private val drawables: Map<Stamp, Drawable> = STAMP_DRAWABLES.mapValues { (_, id) ->
        checkNotNull(context.getDrawable(id)) { "stamp drawable" }
    }
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

    /** Whether the newest stamp is selected (it can still be moved and sized). */
    var stampSelected = false
        set(value) {
            field = value
            invalidate()
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

    /** Draws the stamps in page units onto [canvas] (the view, or a thumbnail). */
    fun drawOnPage(canvas: Canvas) {
        for (s in current()) {
            val half = (s.size / 2).toInt()
            drawables.getValue(s.stamp).run {
                setBounds(
                    s.x.toInt() - half,
                    s.y.toInt() - half,
                    s.x.toInt() + half,
                    s.y.toInt() + half,
                )
                draw(canvas)
            }
        }
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
