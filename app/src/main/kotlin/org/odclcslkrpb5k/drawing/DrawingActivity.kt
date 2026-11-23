package org.odclcslkrpb5k.drawing

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.window.OnBackInvokedDispatcher
import org.odclcslkrpb5k.drawing.adapters.Coloring
import org.odclcslkrpb5k.drawing.adapters.Gallery
import org.odclcslkrpb5k.drawing.adapters.InkLoader
import org.odclcslkrpb5k.drawing.adapters.PageStore
import org.odclcslkrpb5k.drawing.adapters.StoreError
import org.odclcslkrpb5k.drawing.adapters.TemplateAssets
import org.odclcslkrpb5k.drawing.core.Argb
import org.odclcslkrpb5k.drawing.core.History
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.PageId
import org.odclcslkrpb5k.drawing.core.Templates

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
 * One page: home, undo and redo on the left, the page in the middle, colors on the right; a page
 * started from a coloring page also has the fill tool. Nothing is shown, and no touch is taken,
 * until the page is read: a stroke can never land on a page that is then replaced. A page that
 * cannot be read is never written over; the screen closes instead. The page is saved after every
 * change and whenever the screen stops being in front.
 */
class DrawingActivity : Activity() {
    private val surface by lazy { DrawingSurface(this) }
    private val store by lazy { PageStore(filesDir) }
    private val id by lazy { intent.getStringExtra(EXTRA_PAGE)?.let(PageId::parse) }
    /** The coloring page a new page starts from; ignored for a page already saved. */
    private val template by lazy {
        intent.getStringExtra(EXTRA_TEMPLATE)?.takeIf { Templates.isName(it) }
    }
    private var opened: PageId? = null

    /** The page changed since its picture was last sent to the tablet's gallery. */
    private var unexported = false
    private val swatches = mutableListOf<View>()
    private val stamps: StampTray by lazy {
        StampTray(this, onOpen = { brushes.close() }) { face ->
            if (face != null) setFilling(false)
            surface.arm(face)
        }
    }
    private val closeTrays: () -> Unit = {
        stamps.close()
        brushes.close()
    }

    /** The pen to go back to when a color is chosen while erasing. */
    private var lastPen = Tool.MEDIUM
    private val brushes: BrushTray by lazy {
        BrushTray(this, onOpen = { stamps.close() }) { tool ->
            stamps.close()
            setFilling(false)
            surface.arm(null)
            surface.tool = tool
            if (tool != Tool.ERASER) lastPen = tool
            brushes.show(tool)
        }
    }
    private val bucket by lazy {
        iconButton(R.drawable.ic_fill, "fill") {
            stamps.close()
            brushes.close()
            surface.arm(null)
            setFilling(!surface.filling)
        }
    }

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
                    is Outcome.Ok ->
                        if (has.value) store.load(page)
                        else Outcome.Ok(History(template = template))
                    is Outcome.Err -> has
                }
            },
            failed = Outcome.Err(StoreError.IO),
        ) { loaded ->
            // A change not yet on disk is newer than anything the load could read.
            val unsaved = PageSaves.unsaved(page)
            when {
                gone() -> Unit
                unsaved != null -> withColoring(page, unsaved)
                loaded is Outcome.Ok -> withColoring(page, loaded.value)
                loaded is Outcome.Err -> {
                    Log.e(TAG, "page not opened: ${loaded.error}")
                    finish()
                }
            }
        }
    }

    /**
     * Opens the page once its coloring page, if it has one, is read on the disk thread. A coloring
     * page that cannot be read (say, one a later version dropped) leaves the drawing to open plain.
     */
    private fun withColoring(page: PageId, history: History) {
        val name = history.template
        if (name == null) {
            open(page, history, null)
            return
        }
        PageDisk.run(
            { TemplateAssets.load(assets, name) },
            failed = Outcome.Err("unexpected failure"),
        ) { loaded ->
            val coloring =
                when (loaded) {
                    is Outcome.Ok -> loaded.value
                    is Outcome.Err -> null.also { Log.e(TAG, "coloring page: ${loaded.error}") }
                }
            // The closing screen may have handed over a change while the coloring page was read.
            if (!gone()) open(page, PageSaves.unsaved(page) ?: history, coloring)
        }
    }

    private fun open(page: PageId, history: History, coloring: Coloring?) {
        setContentView(layout(coloring != null))
        surface.show(history, coloring)
        opened = page
        PageSaves.open(page, this)
        surface.onChange = { changed ->
            unexported = true
            PageSaves.changed(store, page, this, changed, surface.picture(THUMB_SCALE))
            // A stroke Ink hands back as the screen leaves is saved at once, not after the pause.
            if (isFinishing) PageSaves.flush(store, page)
        }
    }

    /** Turns the fill tool on or off; the button shows which. */
    private fun setFilling(on: Boolean) {
        surface.filling = on
        bucket.background = toolShape(active = surface.filling)
    }

    /**
     * Home at the top; the fill tool (on a coloring page), stamps, undo and redo at the bottom,
     * under the child's hand.
     */
    private fun toolsRail(coloring: Boolean): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(PAD), dp(PAD), dp(PAD), dp(PAD))
            addView(
                iconButton(R.drawable.ic_home, "home") {
                    closeTrays()
                    leave()
                },
                square(),
            )
            addView(brushes.button, square())
            addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
            if (coloring) addView(bucket, square())
            addView(stamps.button, square())
            addView(
                iconButton(R.drawable.ic_undo, "undo") {
                    closeTrays()
                    surface.undo()
                },
                square(),
            )
            addView(
                iconButton(R.drawable.ic_redo, "redo") {
                    closeTrays()
                    surface.redo()
                },
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

    private fun layout(coloring: Boolean): View {
        val tools = toolsRail(coloring)
        surface.onArmed = { face -> stamps.showArmed(face) }
        surface.onTouched = closeTrays
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
                brushes.tray,
                FrameLayout.LayoutParams(WRAP, WRAP, Gravity.START or Gravity.TOP).apply {
                    marginStart = dp(TARGET_DP + 2 * PAD)
                    topMargin = dp(TARGET_DP + 2 * PAD)
                },
            )
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
        closeTrays()
        surface.color = color
        // A color chosen while erasing means drawing again, with the last pen used.
        if (surface.tool == Tool.ERASER) {
            surface.tool = lastPen
            brushes.show(lastPen)
        }
        swatches.forEachIndexed { i, v ->
            v.background = swatchShape(SWATCHES[i].toInt(), v === swatch)
        }
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

    /**
     * The page is saved now, and, if it changed, its picture goes to the tablet's gallery: once per
     * visit rather than per stroke, as a full-size picture is far larger than the page. The export
     * is queued behind the save (which [PageSaves.flush] posts to this thread first), so the page
     * reaches the disk before its slower copy does. A failed export is tried at the next pause.
     */
    override fun onPause() {
        super.onPause()
        val page = opened ?: return
        PageSaves.flush(store, page)
        if (unexported) {
            unexported = false
            val picture = surface.picture(1f)
            window.decorView.post {
                PageDisk.run({ Gallery.export(contentResolver, page, picture) }, failed = null) {
                    if (it !is Outcome.Ok) {
                        Log.w(
                            TAG,
                            "picture not exported: ${(it as? Outcome.Err)?.error ?: "unexpected"}",
                        )
                        unexported = true
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        opened?.let { PageSaves.close(it, this) }
    }

    companion object {
        const val EXTRA_PAGE = "page"
        const val EXTRA_TEMPLATE = "template"
        private const val TAG = "DrawingActivity"
        private const val PAD = 12
        private const val SWATCH = 72
        private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        private const val BACKGROUND = 0xFFF4EEFF.toInt()
        private const val DEFAULT = 0xFF7A4BE0
    }
}
