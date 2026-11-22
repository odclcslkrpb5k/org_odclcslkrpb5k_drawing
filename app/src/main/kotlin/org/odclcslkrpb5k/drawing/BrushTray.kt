package org.odclcslkrpb5k.drawing

import android.content.Context
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView

/**
 * The brush button and its tray: three pen widths, the highlighter and the eraser. The button shows
 * the brush in use and opens and closes the tray; a brush chosen in the tray is handed to [choose]
 * and the tray closes; [onOpen] closes any other tray first.
 */
class BrushTray(
    private val context: Context,
    private val onOpen: () -> Unit,
    private val choose: (Tool) -> Unit,
) {
    /** The brushes in one row, beside the button. */
    val tray: View by lazy {
        context.trayPanel(columns = Tool.entries.size).apply {
            for (tool in Tool.entries) {
                val button =
                    ImageButton(context).apply {
                        setImageResource(tool.icon)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        background = null
                        contentDescription = tool.name.lowercase()
                        setOnClickListener {
                            choose(tool)
                            close()
                        }
                    }
                addView(
                    button,
                    GridLayout.LayoutParams().apply {
                        width = context.dp(SLOT)
                        height = context.dp(SLOT)
                    },
                )
            }
        }
    }

    val button: ImageButton by lazy {
        context
            .iconButton(Tool.MEDIUM.icon, "brushes") {
                if (tray.isShown) {
                    close()
                } else {
                    onOpen()
                    tray.visibility = View.VISIBLE
                }
            }
            .apply {
                setPadding(context.dp(PAD), context.dp(PAD), context.dp(PAD), context.dp(PAD))
            }
    }

    fun close() {
        tray.visibility = View.GONE
    }

    /** The brush in use, shown on the button. */
    fun show(tool: Tool) {
        button.setImageResource(tool.icon)
    }

    private companion object {
        const val PAD = 12
        const val SLOT = 112
    }
}
