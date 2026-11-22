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

private const val MAX_BRUSH_SIZE = 1_000f
private const val MAX_STROKE_MILLIS = 3_600_000L

/** The most points one stroke may hold; the page codec reads no more. */
const val MAX_STROKE_POINTS: Int = 1_000_000

/**
 * Whether a stroke can be saved and read back: a positive, bounded brush size, finite numbers, and
 * times within an hour that never decrease (Ink refuses a stroke whose time runs backwards). The
 * page codec refuses any other stroke, so nothing else may enter a page.
 */
fun StrokeData.isSaveable(): Boolean =
    brush.size > 0f &&
        brush.size <= MAX_BRUSH_SIZE &&
        points.size <= MAX_STROKE_POINTS &&
        points.all { it.isSaveable() } &&
        points.zipWithNext().all { (a, b) -> a.elapsedMillis <= b.elapsedMillis }

private fun InputPoint.isSaveable(): Boolean =
    x.isFinite() && y.isFinite() && pressure.isFinite() && elapsedMillis in 0..MAX_STROKE_MILLIS
