package org.odclcslkrpb5k.drawing.core

/** A color as 0xAARRGGBB. */
@JvmInline value class Argb(val value: Int)

/** The brushes a stroke can be drawn with. */
enum class BrushKind {
    PEN,
    MARKER,
    HIGHLIGHTER,
}

/** How a stroke is drawn: brush, color and size in page units. */
data class BrushSpec(val kind: BrushKind, val color: Argb, val size: Float)

/** One sampled input point, in page coordinates, timed from the stroke's start. */
data class InputPoint(val x: Float, val y: Float, val elapsedMillis: Long, val pressure: Float)

/**
 * A finished stroke as the app owns it. Rendering libraries rebuild their own objects from this;
 * nothing but this is saved or undone.
 */
data class StrokeData(val brush: BrushSpec, val points: List<InputPoint>)
