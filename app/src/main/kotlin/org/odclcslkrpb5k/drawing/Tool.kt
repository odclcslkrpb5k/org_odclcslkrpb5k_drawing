package org.odclcslkrpb5k.drawing

import org.odclcslkrpb5k.drawing.core.BrushKind

/** Widths in page pixels (the page is 1920 wide). */
private const val THIN_SIZE = 8f
private const val MEDIUM_SIZE = 18f
private const val THICK_SIZE = 40f
private const val HIGHLIGHTER_SIZE = 36f
private const val ERASER_SIZE = 48f

/**
 * What a finger draws with, as the child chooses it in the brush tray. Pens are Ink's marker, one
 * steady width: a finger's pressure readings are not worth following.
 */
enum class Tool(val kind: BrushKind, val size: Float, val icon: Int) {
    THIN(BrushKind.MARKER, THIN_SIZE, R.drawable.ic_brush_thin),
    MEDIUM(BrushKind.MARKER, MEDIUM_SIZE, R.drawable.ic_brush_medium),
    THICK(BrushKind.MARKER, THICK_SIZE, R.drawable.ic_brush_thick),
    HIGHLIGHTER(BrushKind.HIGHLIGHTER, HIGHLIGHTER_SIZE, R.drawable.ic_highlighter),
    ERASER(BrushKind.ERASER, ERASER_SIZE, R.drawable.ic_eraser),
}
