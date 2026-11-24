package org.odclcslkrpb5k.drawing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.ink.geometry.Box
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import org.odclcslkrpb5k.drawing.adapters.InkStrokes
import org.odclcslkrpb5k.drawing.core.Argb
import org.odclcslkrpb5k.drawing.core.BrushKind
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.Stamp
import org.odclcslkrpb5k.drawing.core.StampData
import org.odclcslkrpb5k.drawing.core.StrokeData

/**
 * Draws what she adds to a page, in page units, onto a page-sized canvas: strokes through Ink, and
 * eraser strokes as a cut that takes away whatever that canvas holds under them (strokes and stamps
 * on one layer, fills on another). Main thread (Ink's renderer).
 */
class Marks {
    private val renderer = CanvasStrokeRenderer.create()
    private val identity = Matrix()
    private val cutOut = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }

    /** Draws a stroke; an eraser stroke instead cuts its shape out of what [canvas] holds. */
    fun stroke(canvas: Canvas, data: StrokeData) {
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
                if (data.brush.kind == BrushKind.ERASER) cut(canvas, stroke.value, data.brush.size)
                else renderer.draw(canvas, stroke.value, identity)
            is Outcome.Err -> Log.w(TAG, "stroke not rendered: ${stroke.error}")
        }
    }

    /**
     * Cuts the eraser stroke's shape out of what [canvas] holds. The layer covers only the stroke's
     * box (padded by its width), not the page: undo replays every eraser stroke.
     */
    private fun cut(canvas: Canvas, stroke: Stroke, size: Float) {
        val box: Box = stroke.shape.computeBoundingBox() ?: return
        val layer =
            canvas.saveLayer(
                box.xMin - size,
                box.yMin - size,
                box.xMax + size,
                box.yMax + size,
                cutOut,
            )
        renderer.draw(canvas, stroke, identity)
        canvas.restoreToCount(layer)
    }

    private companion object {
        const val TAG = "Marks"
    }
}

/** The stamps' pictures, drawn at a stamp's place and size. */
class StampArt(context: Context) {
    private val drawables: Map<Stamp, Drawable> = STAMP_DRAWABLES.mapValues { (_, id) ->
        checkNotNull(context.getDrawable(id)) { "stamp drawable" }
    }

    fun draw(canvas: Canvas, s: StampData) {
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
