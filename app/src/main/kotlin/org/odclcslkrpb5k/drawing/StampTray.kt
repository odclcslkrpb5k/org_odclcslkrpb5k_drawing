package org.odclcslkrpb5k.drawing

import android.content.Context
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import org.odclcslkrpb5k.drawing.core.Stamp

/**
 * The stamps button and its tray of faces and clip art. The button opens and closes the tray; a
 * stamp chosen in the tray is handed to [arm] and the tray closes; while a stamp is chosen, the
 * button shows it, filled and ringed, and tapping the button puts the stamp back. Any other tool
 * closes the tray; [onOpen] closes any other tray first.
 */
class StampTray(
    private val context: Context,
    private val onOpen: () -> Unit,
    private val arm: (Stamp?) -> Unit,
) {
    private var armed: Stamp? = null

    /**
     * Four across: the faces in the first two rows, clip art in the next two; touches in its gaps
     * never fall through to the page.
     */
    val tray: View by lazy {
        context.trayPanel(columns = COLUMNS).apply {
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
                    else -> {
                        onOpen()
                        tray.visibility = View.VISIBLE
                    }
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
        button.background = context.toolShape(active = face != null)
    }

    private companion object {
        const val PAD = 12
        const val FACE = 112
        const val COLUMNS = 4
    }
}
