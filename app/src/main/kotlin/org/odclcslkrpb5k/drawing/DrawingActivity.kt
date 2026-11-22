package org.odclcslkrpb5k.drawing

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.window.OnBackInvokedDispatcher
import org.odclcslkrpb5k.drawing.adapters.InkLoader
import org.odclcslkrpb5k.drawing.adapters.PageStore
import org.odclcslkrpb5k.drawing.adapters.StoreError
import org.odclcslkrpb5k.drawing.core.Argb
import org.odclcslkrpb5k.drawing.core.History
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.PageId

/** The colors on the swatch rail, brightest first. */
private val SWATCHES =
    listOf(
        0xFFE53935,
        0xFFFF8F00,
        0xFFFDD835,
        0xFF43A047,
        0xFF00ACC1,
        0xFF1E88E5,
        0xFF7A4BE0,
        0xFFE0409A,
        0xFFFF80AB,
        0xFF8D6E63,
        0xFF212121,
        0xFFFFFFFF,
    )

/**
 * One page: home, undo and redo on the left, the page in the middle, colors on the right. Nothing
 * is shown, and no touch is taken, until the page is read: a stroke can never land on a page that
 * is then replaced. A page that cannot be read is never written over; the screen closes instead.
 * The page is saved after every change and whenever the screen stops being in front.
 */
class DrawingActivity : Activity() {
    private val surface by lazy { DrawingSurface(this) }
    private val store by lazy { PageStore(filesDir) }
    private val id by lazy { intent.getStringExtra(EXTRA_PAGE)?.let(PageId::parse) }
    private var opened: PageId? = null
    private val swatches = mutableListOf<View>()
    private val stamps by lazy { StampTray(this) { face -> surface.arm(face) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = id
        if (page == null) {
            Log.e(TAG, "no page id")
            finish()
            return
        }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT
        ) {
            leave()
        }
        // Ink's classes may be touched only once its native library is in (InkLoader).
        InkLoader.whenLoaded { if (!gone()) load(page) }
    }

    private fun gone() = isFinishing || isDestroyed

    /** Reads the page on the disk thread, after any save still queued for it, then opens it. */
    private fun load(page: PageId) {
        PageDisk.run(
            {
                when (val has = store.hasVersions(page)) {
                    is Outcome.Ok -> if (has.value) store.load(page) else Outcome.Ok(History())
                    is Outcome.Err -> has
                }
            },
            failed = Outcome.Err(StoreError.IO),
        ) { loaded ->
            // A change not yet on disk is newer than anything the load could read.
            val unsaved = PageSaves.unsaved(page)
            when {
                gone() -> Unit
                unsaved != null -> open(page, unsaved)
                loaded is Outcome.Ok -> open(page, loaded.value)
                loaded is Outcome.Err -> {
                    Log.e(TAG, "page not opened: ${loaded.error}")
                    finish()
                }
            }
        }
    }

    private fun open(page: PageId, history: History) {
        setContentView(layout())
        surface.show(history)
        opened = page
        PageSaves.open(page, this)
        surface.onChange = { changed ->
            PageSaves.changed(store, page, this, changed, surface.thumbnail())
            // A stroke Ink hands back as the screen leaves is saved at once, not after the pause.
            if (isFinishing) PageSaves.flush(store, page)
        }
    }

    /** Home at the top; stamps, undo and redo at the bottom, under the child's hand. */
    private fun toolsRail(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(PAD), dp(PAD), dp(PAD), dp(PAD))
            addView(
                iconButton(R.drawable.ic_home, "home") { stamps.close().also { leave() } },
                square(),
            )
            addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
            addView(stamps.button, square())
            addView(
                iconButton(R.drawable.ic_undo, "undo") { stamps.close().also { surface.undo() } },
                square(),
            )
            addView(
                iconButton(R.drawable.ic_redo, "redo") { stamps.close().also { surface.redo() } },
                square(),
            )
        }

    /** One swatch per color, filling the right edge from top to bottom. */
    private fun colorsRail(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(PAD), dp(PAD), dp(PAD), dp(PAD))
            for (argb in SWATCHES) {
                val swatch = View(context)
                swatch.background = swatchShape(argb.toInt(), selected = false)
                swatch.contentDescription = "color"
                swatch.setOnClickListener { pick(swatch, Argb(argb.toInt())) }
                swatches += swatch
                addView(swatch, LinearLayout.LayoutParams(dp(SWATCH), 0, 1f))
            }
        }

    private fun layout(): View {
        val tools = toolsRail()
        surface.onArmed = { face -> stamps.showArmed(face) }
        surface.onTouched = { stamps.close() }
        val colors = colorsRail()
        pick(swatches[SWATCHES.indexOf(DEFAULT)], Argb(DEFAULT.toInt()))
        val row =
            LinearLayout(this).apply {
                setBackgroundColor(BACKGROUND)
                addView(
                    tools,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, MATCH),
                )
                addView(surface, LinearLayout.LayoutParams(0, MATCH, 1f))
                addView(
                    colors,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, MATCH),
                )
            }
        return FrameLayout(this).apply {
            addView(row)
            addView(
                stamps.tray,
                FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START or Gravity.CENTER_VERTICAL)
                    .apply {
                        marginStart = dp(TARGET_DP + 2 * PAD)
                    },
            )
        }
    }

    private fun square() =
        LinearLayout.LayoutParams(dp(TARGET_DP), dp(TARGET_DP)).apply {
            setMargins(0, dp(PAD), 0, dp(PAD))
        }

    private fun pick(swatch: View, color: Argb) {
        stamps.close()
        surface.color = color
        swatches.forEachIndexed { i, v ->
            v.background = swatchShape(SWATCHES[i].toInt(), v === swatch)
        }
    }

    private fun swatchShape(color: Int, selected: Boolean) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(if (selected) SELECTED_RING else 1), if (selected) Color.BLACK else EDGE)
        }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    /** Back to home, once Ink has handed back what it holds. */
    private fun leave() {
        // Before the page is open Ink must not be touched (InkLoader), and nothing is drawn yet.
        if (opened == null) finish() else surface.leaving { finish() }
    }

    override fun onPause() {
        super.onPause()
        opened?.let { PageSaves.flush(store, it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        opened?.let { PageSaves.close(it, this) }
    }

    companion object {
        const val EXTRA_PAGE = "page"
        private const val TAG = "DrawingActivity"
        private const val PAD = 12
        private const val SWATCH = 72
        private const val SELECTED_RING = 5
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        private const val BACKGROUND = 0xFFF4EEFF.toInt()
        private const val EDGE = 0xFFBDBDBD.toInt()
        private const val DEFAULT = 0xFF7A4BE0
    }
}
