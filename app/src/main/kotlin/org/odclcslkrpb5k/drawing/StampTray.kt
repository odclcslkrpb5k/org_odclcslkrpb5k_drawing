package org.odclcslkrpb5k.drawing

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import org.odclcslkrpb5k.drawing.core.Stamp

/**
 * The stamps button and its tray of faces. The button opens and closes the tray; a face chosen in
 * the tray is handed to [arm] and the tray closes; while a face is chosen, the button shows it,
 * filled and ringed, and tapping the button puts the face back. Any other tool closes the tray.
 */
class StampTray(private val context: Context, private val arm: (Stamp?) -> Unit) {
    private var armed: Stamp? = null

    /** The faces, two by four; touches in its gaps never fall through to the page. */
    val tray: View by lazy {
        GridLayout(context).apply {
            columnCount = 2
            visibility = View.GONE
            isClickable = true
            setPadding(context.dp(PAD), context.dp(PAD), context.dp(PAD), context.dp(PAD))
            background =
                GradientDrawable().apply {
                    cornerRadius = context.dp(PAD * 2).toFloat()
                    setColor(Color.WHITE)
                    setStroke(context.dp(2), EDGE)
                }
            for ((face, icon) in STAMP_DRAWABLES) {
                val button =
                    ImageButton(context).apply {
                        setImageResource(icon)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        background = null
                        contentDescription = face.name.lowercase()
                        setOnClickListener {
                            arm(face)
                            close()
                        }
                    }
                addView(
                    button,
                    GridLayout.LayoutParams().apply {
                        width = context.dp(FACE)
                        height = context.dp(FACE)
                    },
                )
            }
        }
    }

    val button: ImageButton by lazy {
        context
            .iconButton(R.drawable.ic_stamps, "stamps") {
                when {
                    // A chosen face is put back: the button is how a child changes their mind.
                    armed != null -> arm(null).also { close() }
                    tray.isShown -> close()
                    else -> tray.visibility = View.VISIBLE
                }
            }
            .apply {
                setPadding(context.dp(PAD), context.dp(PAD), context.dp(PAD), context.dp(PAD))
            }
    }

    fun close() {
        tray.visibility = View.GONE
    }

    /** The surface says which face is chosen (null: none); the button shows it. */
    fun showArmed(face: Stamp?) {
        armed = face
        button.setImageResource(face?.let { STAMP_DRAWABLES.getValue(it) } ?: R.drawable.ic_stamps)
        button.background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (face == null) Color.WHITE else ARMED_FILL)
                setStroke(
                    context.dp(if (face == null) 2 else ARMED_RING),
                    if (face == null) EDGE else ARMED,
                )
            }
    }

    private companion object {
        const val PAD = 12
        const val FACE = 112
        const val ARMED_RING = 5
        const val EDGE = 0xFFBDBDBD.toInt()
        const val ARMED = 0xFF7A4BE0.toInt()
        const val ARMED_FILL = 0xFFE6DAFF.toInt()
    }
}
