package org.odclcslkrpb5k.drawing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.odclcslkrpb5k.drawing.core.Hold
import org.odclcslkrpb5k.drawing.core.HoldRule

/**
 * A control that acts only when held by the parent's rule (core [Hold]): the gate's faint dot on
 * home, and the parent screen's "empty the trash" (with an [icon]). While the hold is good a ring
 * fills around it, so the parent knows to keep holding; [onHeld] runs once per touch when the ring
 * is full. An accessibility click does nothing: only the hold acts.
 */
class HoldButton(context: Context) : View(context) {
    private val rule = context.parentHold()
    private var hold = Hold(rule)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DOT_COLOR }
    private val ring =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dp(RING_DP).toFloat()
            strokeCap = Paint.Cap.ROUND
            color = RING_COLOR
        }
    private val arc = RectF()
    private val ticking = Runnable {
        if (tick()) {
            performClick()
            onHeld()
        }
    }

    var onHeld: () -> Unit = {}

    /** Drawn in a larger pale face; without one the control is only a faint dot. */
    var icon: Drawable? = null
        set(value) {
            field = value
            dot.color = if (value == null) DOT_COLOR else FACE_COLOR
            invalidate()
        }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val i = event.actionIndex
        val id = event.getPointerId(i)
        hold =
            when (event.actionMasked) {
                // A first finger starts a fresh touch, whatever a lost UP or CANCEL left behind; a
                // scrolling parent may not take the touch over a small drift.
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    Hold(rule).down(id, event.getX(i), event.getY(i), event.eventTime)
                }
                MotionEvent.ACTION_POINTER_DOWN ->
                    hold.down(id, event.getX(i), event.getY(i), event.eventTime)
                MotionEvent.ACTION_MOVE -> moved(event)
                MotionEvent.ACTION_POINTER_UP,
                MotionEvent.ACTION_UP -> hold.up(id)
                MotionEvent.ACTION_CANCEL -> hold.cancel()
                else -> hold
            }
        if (tick()) {
            performClick()
            onHeld()
        }
        return true
    }

    private fun moved(event: MotionEvent): Hold {
        var next = hold
        for (p in 0 until event.pointerCount) {
            next = next.move(event.getPointerId(p), event.getX(p), event.getY(p))
        }
        return next
    }

    /**
     * Checks the hold now, and again next frame for as long as it is being held; true when it is
     * done now (the caller acts: lint wants the click performed where the touch is handled).
     */
    private fun tick(): Boolean {
        removeCallbacks(ticking)
        val (next, held) = hold.tick(SystemClock.uptimeMillis())
        hold = next
        if (hold.since != null) postOnAnimation(ticking)
        invalidate()
        return held
    }

    /** Announces the hold to accessibility; the action itself is [onHeld], run only by a hold. */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticking)
        hold = hold.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = context.dp(if (icon == null) DOT_DP else FACE_DP).toFloat()
        canvas.drawCircle(cx, cy, r, dot)
        icon?.let {
            val half = (r * ICON_FRACTION).toInt()
            it.setBounds(cx.toInt() - half, cy.toInt() - half, cx.toInt() + half, cy.toInt() + half)
            it.draw(canvas)
        }
        val progress = hold.progress(SystemClock.uptimeMillis())
        if (progress > 0f) {
            val ringR = r + context.dp(RING_GAP_DP)
            arc.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
            canvas.drawArc(arc, START_ANGLE, FULL_TURN * progress, false, ring)
        }
    }

    private companion object {
        const val DOT_DP = 10
        const val FACE_DP = 40
        const val RING_DP = 6
        const val RING_GAP_DP = 8
        const val ICON_FRACTION = 0.6f
        const val START_ANGLE = -90f
        const val FULL_TURN = 360f
        const val DOT_COLOR = 0x337A4BE0
        const val FACE_COLOR = 0xFFFFE0E0.toInt()
        const val RING_COLOR = 0xFF7A4BE0.toInt()
    }
}

/** Two fingers held still for three seconds: what a parent does on purpose and a child rarely. */
fun Context.parentHold(): HoldRule =
    HoldRule(fingers = 2, holdMs = PARENT_HOLD_MS, slop = dp(PARENT_SLOP_DP).toFloat())

private const val PARENT_HOLD_MS = 3_000L
private const val PARENT_SLOP_DP = 24
