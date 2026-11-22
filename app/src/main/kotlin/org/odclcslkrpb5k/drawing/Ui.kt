package org.odclcslkrpb5k.drawing

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageButton

/** Every touch target is at least this big: a six-year-old's finger, not a stylus. */
const val TARGET_DP = 88

fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        )
        .toInt()

/** A round, icon-only button; there is no text anywhere a child must read. */
fun Context.iconButton(icon: Int, description: String, onTap: () -> Unit): ImageButton =
    ImageButton(this).apply {
        setImageResource(icon)
        contentDescription = description
        background = toolShape(active = false)
        setOnClickListener { onTap() }
    }

/** A tool button's round background; [active] fills and rings it, so a chosen tool shows. */
fun Context.toolShape(active: Boolean): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (active) ACTIVE_FILL else Color.WHITE)
        setStroke(dp(if (active) ACTIVE_RING else 2), if (active) ACTIVE else BUTTON_EDGE)
    }

/** Hides the system bars; a swipe from the edge shows them only briefly. */
fun Activity.immersive() {
    window.insetsController?.let {
        it.hide(WindowInsets.Type.systemBars())
        it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

private const val BUTTON_EDGE = 0xFFD9CCF5.toInt()
private const val ACTIVE_RING = 5
private const val ACTIVE = 0xFF7A4BE0.toInt()
private const val ACTIVE_FILL = 0xFFE6DAFF.toInt()

/** A round color swatch; the chosen one has a thick black ring. */
fun Context.swatchShape(color: Int, selected: Boolean) =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(
            dp(if (selected) SWATCH_SELECTED_RING else 1),
            if (selected) Color.BLACK else SWATCH_EDGE,
        )
    }

private const val SWATCH_SELECTED_RING = 5
private const val SWATCH_EDGE = 0xFFBDBDBD.toInt()
