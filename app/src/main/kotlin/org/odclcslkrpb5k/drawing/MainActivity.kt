package org.odclcslkrpb5k.drawing

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import org.odclcslkrpb5k.drawing.adapters.InkLoader
import org.odclcslkrpb5k.drawing.adapters.PageStore
import org.odclcslkrpb5k.drawing.adapters.PhotoStore
import org.odclcslkrpb5k.drawing.adapters.TemplateAssets
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.PageId

/**
 * Home: big "new page" and "coloring pages" tiles, then every saved page, newest first. A tap opens
 * a page. "Coloring pages" shows the coloring pages instead, with a tile (and Back) to return. A
 * faint dot in the bottom corner, held with two fingers, opens the parent's screen.
 */
class MainActivity : Activity() {
    private val store by lazy { PageStore(filesDir) }
    private val picture by lazy { PagePicture(this) }
    private val photos by lazy { PhotoStore(filesDir) }
    private val selfies by lazy { Selfies(this, { photos }, { store }) { open(it) } }
    private val grid by lazy { GridLayout(this).apply { columnCount = COLUMNS } }
    private var created = 0

    /**
     * A page is opening; further taps are ignored until home is back in front (no double open). No
     * scenario proves this: injected taps never land inside that window on the emulator.
     */
    private var opening = false

    /** While the coloring pages are shown, Back returns to the saved pages. */
    private val backToPages = OnBackInvokedCallback { refresh() }
    private var picking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selfies.restore(savedInstanceState)
        val gate =
            HoldButton(this).apply {
                contentDescription = "parent"
                onHeld = { startActivity(Intent(this@MainActivity, ParentActivity::class.java)) }
            }
        setContentView(
            FrameLayout(this).apply {
                addView(
                    ScrollView(this@MainActivity).apply {
                        setBackgroundColor(BACKGROUND)
                        setPadding(dp(PAD), dp(PAD), dp(PAD), dp(PAD))
                        addView(grid)
                    }
                )
                addView(
                    gate,
                    FrameLayout.LayoutParams(dp(GATE), dp(GATE), Gravity.BOTTOM or Gravity.END),
                )
            }
        )
    }

    @Deprecated("Activity's own result callback; this app uses no AndroidX activity results.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        selfies.answered(requestCode, resultCode)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selfies.save(outState)
    }

    override fun onResume() {
        super.onResume()
        opening = false
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    /**
     * Reads the saved pages and thumbnails on the disk thread, after any queued save, then shows
     * them.
     */
    private fun refresh() {
        stopPicking()
        PageDisk.run({ store.list().map { it to store.thumbnail(it) } }, failed = emptyList()) {
            pages ->
            if (!isDestroyed) show(pages)
        }
    }

    private fun show(pages: List<Pair<PageId, Bitmap?>>) {
        grid.removeAllViews()
        grid.addView(
            iconButton(R.drawable.ic_add, "new page") {
                    open(PageId.next(System.currentTimeMillis(), created++))
                }
                .apply { background = tileShape() },
            tileParams(),
        )
        grid.addView(
            iconButton(R.drawable.ic_coloring, "coloring pages") { pick() }
                .apply { background = tileShape() },
            tileParams(),
        )
        // A page on a photo, when there is a camera app to take it.
        if (selfies.available) {
            grid.addView(
                iconButton(R.drawable.ic_camera, "photo") {
                        if (!opening) {
                            // Like opening a page: further taps wait until home is back in front.
                            opening = true
                            selfies.take(PageId.next(System.currentTimeMillis(), created++))
                        }
                    }
                    .apply { background = tileShape() },
                tileParams(),
            )
        }
        for ((id, thumb) in pages) {
            val tile = tile(thumb, "drawing")
            tile.setOnClickListener { open(id) }
            grid.addView(tile, tileParams())
            if (thumb == null) redraw(id, tile)
        }
    }

    /**
     * Draws a page's missing thumbnail from the page itself and keeps it: a page restored from a
     * backup comes without one (thumbnails are left out of it), as does one whose thumbnail write
     * failed. The page is read on the disk thread; drawing waits for Ink.
     */
    private fun redraw(id: PageId, tile: ImageView) {
        PageDisk.run(
            {
                when (val page = store.load(id)) {
                    is Outcome.Ok -> page.value to backingOf(assets, photos, id, page.value)
                    is Outcome.Err ->
                        null.also { Log.w(TAG, "page for a thumbnail: ${page.error}") }
                }
            },
            failed = null,
        ) { page ->
            if (page != null && !isDestroyed) {
                InkLoader.whenLoaded {
                    val (drawn, backing) = page
                    val thumb = picture.draw(drawn, backing.coloring, backing.photo)
                    if (!isDestroyed) tile.setImageBitmap(thumb)
                    // Kept only while still missing and the page still saved: a drawing screen may
                    // have written a newer one meanwhile, or the parent trashed the page.
                    PageDisk.run(
                        {
                            val saved = (store.hasVersions(id) as? Outcome.Ok)?.value == true
                            if (!saved || store.thumbFile(id).exists()) null
                            else store.saveThumbnail(id, thumb)
                        },
                        failed = null,
                    ) {}
                }
            }
        }
    }

    /** Reads the coloring pages' previews on the disk thread, then shows them to choose from. */
    private fun pick() {
        PageDisk.run(
            { TemplateAssets.names(assets).map { it to TemplateAssets.preview(assets, it) } },
            failed = emptyList(),
        ) { pages ->
            if (!isDestroyed) showColoring(pages)
        }
    }

    private fun showColoring(pages: List<Pair<String, Bitmap?>>) {
        if (!picking) {
            picking = true
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                backToPages,
            )
        }
        grid.removeAllViews()
        grid.addView(
            iconButton(R.drawable.ic_home, "my drawings") { refresh() }
                .apply { background = tileShape() },
            tileParams(),
        )
        for ((name, preview) in pages) {
            val tile = tile(preview, "coloring page")
            tile.setOnClickListener {
                open(PageId.next(System.currentTimeMillis(), created++), template = name)
            }
            grid.addView(tile, tileParams())
        }
    }

    private fun stopPicking() {
        if (!picking) return
        picking = false
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backToPages)
    }

    private fun tile(image: Bitmap?, description: String) =
        ImageView(this).apply {
            background = tileShape()
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(TILE_PAD), dp(TILE_PAD), dp(TILE_PAD), dp(TILE_PAD))
            contentDescription = description
            if (image != null) setImageBitmap(image)
        }

    private fun open(id: PageId, template: String? = null) {
        if (opening) return
        opening = true
        val intent =
            Intent(this, DrawingActivity::class.java).putExtra(DrawingActivity.EXTRA_PAGE, id.value)
        if (template != null) intent.putExtra(DrawingActivity.EXTRA_TEMPLATE, template)
        startActivity(intent)
    }

    private fun tileParams() =
        GridLayout.LayoutParams().apply {
            width = dp(TILE_W)
            height = dp(TILE_H)
            setMargins(dp(PAD), dp(PAD), dp(PAD), dp(PAD))
        }

    private fun tileShape() =
        GradientDrawable().apply {
            cornerRadius = dp(CORNER).toFloat()
            setColor(TILE)
            setStroke(dp(2), TILE_EDGE)
        }

    private companion object {
        const val TAG = "MainActivity"
        const val COLUMNS = 4
        const val PAD = 12
        const val TILE_PAD = 8
        const val TILE_W = 280
        const val TILE_H = 175
        const val CORNER = 24
        const val GATE = 120
        const val BACKGROUND = 0xFFF4EEFF.toInt()
        const val TILE = 0xFFFFFFFF.toInt()
        const val TILE_EDGE = 0xFFD9CCF5.toInt()
    }
}
