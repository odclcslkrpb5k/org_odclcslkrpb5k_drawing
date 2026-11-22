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
        background =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(2), BUTTON_EDGE)
            }
        setOnClickListener { onTap() }
    }

/** Hides the system bars; a swipe from the edge shows them only briefly. */
fun Activity.immersive() {
    window.insetsController?.let {
        it.hide(WindowInsets.Type.systemBars())
        it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

private const val BUTTON_EDGE = 0xFFD9CCF5.toInt()
