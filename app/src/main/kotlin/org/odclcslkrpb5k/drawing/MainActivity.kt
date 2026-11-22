package org.odclcslkrpb5k.drawing

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.ScrollView
import org.odclcslkrpb5k.drawing.adapters.PageStore
import org.odclcslkrpb5k.drawing.core.PageId

/** Home: a big "new page" tile, then every saved page, newest first. A tap opens a page. */
class MainActivity : Activity() {
    private val store by lazy { PageStore(filesDir) }
    private val grid by lazy { GridLayout(this).apply { columnCount = COLUMNS } }
    private var created = 0

    /**
     * A page is opening; further taps are ignored until home is back in front (no double open). No
     * scenario proves this: injected taps never land inside that window on the emulator.
     */
    private var opening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(BACKGROUND)
                setPadding(dp(PAD), dp(PAD), dp(PAD), dp(PAD))
                addView(grid)
            }
        )
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
        for ((id, thumb) in pages) {
            val tile =
                ImageView(this).apply {
                    background = tileShape()
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(TILE_PAD), dp(TILE_PAD), dp(TILE_PAD), dp(TILE_PAD))
                    contentDescription = "drawing"
                    if (thumb != null) setImageBitmap(thumb)
                    setOnClickListener { open(id) }
                }
            grid.addView(tile, tileParams())
        }
    }

    private fun open(id: PageId) {
        if (opening) return
        opening = true
        startActivity(
            Intent(this, DrawingActivity::class.java).putExtra(DrawingActivity.EXTRA_PAGE, id.value)
        )
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
        const val COLUMNS = 4
        const val PAD = 12
        const val TILE_PAD = 8
        const val TILE_W = 280
        const val TILE_H = 175
        const val CORNER = 24
        const val BACKGROUND = 0xFFF4EEFF.toInt()
        const val TILE = 0xFFFFFFFF.toInt()
        const val TILE_EDGE = 0xFFD9CCF5.toInt()
    }
}
