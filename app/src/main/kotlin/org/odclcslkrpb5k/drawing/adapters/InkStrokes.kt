package org.odclcslkrpb5k.drawing.adapters

import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import org.odclcslkrpb5k.drawing.core.BrushKind
import org.odclcslkrpb5k.drawing.core.BrushSpec
import org.odclcslkrpb5k.drawing.core.InputPoint
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.StrokeData

/** Why a stroke could not be rebuilt for rendering. */
enum class InkError {
    INVALID_INPUT
}

private const val EPSILON = 0.1f

/** Converts the app's strokes into Ink's, for rendering. */
object InkStrokes {
    /** The Ink brush for a brush spec. */
    fun brush(spec: BrushSpec): Brush =
        Brush.createWithColorIntArgb(
            when (spec.kind) {
                BrushKind.PEN -> StockBrushes.pressurePen()
                BrushKind.MARKER -> StockBrushes.marker()
                BrushKind.HIGHLIGHTER -> StockBrushes.highlighter()
            },
            spec.color.value,
            spec.size,
            EPSILON,
        )

    /**
     * Rebuilds an Ink stroke from the app's own stroke. Ink validates its inputs by throwing (for
     * instance on time going backwards), so this is the one place that meets that (ER-3).
     */
    fun stroke(data: StrokeData): Outcome<Stroke, InkError> =
        try {
            val batch = MutableStrokeInputBatch()
            for (p in data.points) {
                batch.add(
                    InputToolType.TOUCH,
                    p.x,
                    p.y,
                    p.elapsedMillis,
                    StrokeInput.NO_STROKE_UNIT_LENGTH,
                    p.pressure,
                )
            }
            Outcome.Ok(Stroke(brush(data.brush), batch))
        } catch (_: IllegalArgumentException) {
            Outcome.Err(InkError.INVALID_INPUT)
        }

    /**
     * The app's copy of a stroke Ink has finished, from the inputs Ink kept (already validated by
     * Ink, in page units because strokes start with the view-to-page transform).
     */
    fun data(brush: BrushSpec, stroke: Stroke): StrokeData {
        val inputs = stroke.inputs
        val points =
            List(inputs.size) { i ->
                val input = inputs.get(i)
                InputPoint(input.x, input.y, input.elapsedTimeMillis, input.pressure)
            }
        return StrokeData(brush, points)
    }
}
