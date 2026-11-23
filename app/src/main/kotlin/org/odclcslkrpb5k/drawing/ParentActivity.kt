package org.odclcslkrpb5k.drawing

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import org.odclcslkrpb5k.drawing.adapters.InkLoader
import org.odclcslkrpb5k.drawing.adapters.PageStore
import org.odclcslkrpb5k.drawing.adapters.PageTrash
import org.odclcslkrpb5k.drawing.adapters.PhotoStore
import org.odclcslkrpb5k.drawing.adapters.StoreError
import org.odclcslkrpb5k.drawing.core.History
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.PageId

/**
 * The parent's screen, behind the gate on home: every saved page with "trash" and "versions", the
 * versions of one page to roll back to, and the trash, whose pages go back with a tap and which
 * empties only by the same two-finger hold that opened this screen. A page with a save still
 * pending is left alone. A roll back saves one more version, so the page keeps only five, the
 * oldest makes room: one roll back at a time, and none onto the newest (it would only push one
 * out).
 */
class ParentActivity : Activity() {
    private val store by lazy { PageStore(filesDir) }
    private val trash by lazy { PageTrash(filesDir, store) }
    private val picture by lazy { PagePicture(this) }
    private val photos by lazy { PhotoStore(filesDir) }
    private val column by lazy { LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } }

    /** While one page's versions are shown, Back returns to the list. */
    private val backToList = OnBackInvokedCallback { refresh() }
    private var inVersions = false

    /** A roll back is on the disk thread; further taps on versions are ignored until it is done. */
    private var rollingBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(BACKGROUND)
                setPadding(dp(PARENT_PAD), dp(PARENT_PAD), dp(PARENT_PAD), dp(PARENT_PAD))
                addView(column)
            }
        )
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    private fun gone() = isFinishing || isDestroyed

    /**
     * Reads the saved and trashed pages with their pictures on the disk thread, then shows them.
     */
    private fun refresh() {
        rollingBack = false
        leaveVersions()
        PageDisk.run(
            {
                Lists(
                    store.list().map { it to store.thumbnail(it) },
                    trash.list().map { it to trash.thumbnail(it) },
                )
            },
            failed = Lists(emptyList(), emptyList()),
        ) {
            if (!gone()) show(it)
        }
    }

    private class Lists(
        val saved: List<Pair<PageId, Bitmap?>>,
        val trashed: List<Pair<PageId, Bitmap?>>,
    )

    private fun show(lists: Lists) {
        column.removeAllViews()
        column.addView(parentHeader(R.string.parent_drawings) { finish() })
        column.addView(
            parentGrid(lists.saved) { id ->
                listOf(
                    iconButton(R.drawable.ic_trash, "trash") { act(id) { trash.trash(id) } },
                    iconButton(R.drawable.ic_versions, "versions") { versions(id) },
                )
            }
        )
        val empty =
            HoldButton(this).apply {
                icon = getDrawable(R.drawable.ic_trash)
                contentDescription = "empty the trash"
                onHeld = { act(null) { trash.empty() } }
            }
        column.addView(parentLabel(R.string.parent_trash))
        column.addView(empty, LinearLayout.LayoutParams(dp(HOLD_DP), dp(HOLD_DP)))
        column.addView(
            parentGrid(lists.trashed) { id ->
                listOf(
                    iconButton(R.drawable.ic_put_back, "put back") { act(id) { trash.restore(id) } }
                )
            }
        )
    }

    /**
     * Runs a change on the disk thread, then shows the lists again. A page a screen still holds, or
     * with a save pending, is not touched: the save would put it back half-made.
     */
    private fun <T> act(id: PageId?, change: () -> Outcome<T, StoreError>) {
        if (id != null && PageSaves.busy(id)) {
            Log.w(TAG, "page busy, left alone")
            refresh()
            return
        }
        PageDisk.run(change, failed = Outcome.Err(StoreError.IO)) { done ->
            if (done is Outcome.Err) Log.w(TAG, "parent change failed: ${done.error}")
            if (!gone()) refresh()
        }
    }

    /** Reads one page's versions and its coloring page, then draws each version small. */
    private fun versions(id: PageId) {
        PageDisk.run(
            {
                when (val all = store.versions(id)) {
                    is Outcome.Ok ->
                        all.value to
                            backingOf(
                                assets,
                                photos,
                                id,
                                all.value.firstNotNullOfOrNull { it.second },
                            )
                    is Outcome.Err -> null
                }
            },
            failed = null,
        ) { found ->
            if (found == null) Log.w(TAG, "versions not read")
            if (!gone() && found != null) {
                val (all, backing) = found
                // Drawing strokes touches Ink, which may be used only once it is loaded.
                InkLoader.whenLoaded {
                    if (!gone()) {
                        showVersions(
                            id,
                            all.map { (n, page) ->
                                n to page?.let { picture.draw(it, backing.coloring, backing.photo) }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun showVersions(id: PageId, all: List<Pair<Long, Bitmap?>>) {
        if (!inVersions) {
            inVersions = true
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                backToList,
            )
        }
        column.removeAllViews()
        column.addView(parentHeader(R.string.parent_versions) { refresh() })
        val grid = GridLayout(this).apply { columnCount = PARENT_COLUMNS }
        for ((i, version) in all.withIndex()) {
            val (n, preview) = version
            val tile = parentTile(preview, "version")
            // A version that does not read back cannot be restored; it shows as an empty tile. The
            // newest is already the page.
            if (preview != null && i > 0) tile.setOnClickListener { rollBack(id, n) }
            grid.addView(tile, parentTileParams())
        }
        column.addView(grid)
    }

    /** Makes version [n] the newest, then redraws the gallery picture from it. */
    private fun rollBack(id: PageId, n: Long) {
        if (rollingBack) return
        if (PageSaves.busy(id)) {
            Log.w(TAG, "page busy, left alone")
            return
        }
        PageDisk.run(
            {
                when (val page = store.restoreVersion(id, n)) {
                    is Outcome.Ok -> page.value to backingOf(assets, photos, id, page.value)
                    is Outcome.Err -> null.also { Log.w(TAG, "roll back failed: ${page.error}") }
                }
            },
            failed = null,
        ) { restored ->
            if (restored == null) {
                rollingBack = false
                if (!gone()) refresh()
            } else {
                InkLoader.whenLoaded {
                    val (page, backing) = restored
                    val thumb = picture.draw(page, backing.coloring, backing.photo)
                    act(id) { store.saveThumbnail(id, thumb) }
                }
            }
        }
        rollingBack = true
    }

    private fun leaveVersions() {
        if (!inVersions) return
        inVersions = false
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(backToList)
    }

    private companion object {
        const val TAG = "ParentActivity"
        const val HOLD_DP = 140
        const val BACKGROUND = 0xFFE0E0E0.toInt()
    }
}

/**
 * Saves version [n] again as the page's newest (disk thread), so a roll back is itself one more
 * version: what the child drew last stays among the kept versions.
 */
internal fun PageStore.restoreVersion(id: PageId, n: Long): Outcome<History, StoreError> =
    when (val all = versions(id)) {
        is Outcome.Ok -> {
            val page = all.value.firstOrNull { it.first == n }?.second
            when {
                page == null -> Outcome.Err(StoreError.NO_READABLE_VERSION)
                else ->
                    when (val saved = save(id, page)) {
                        is Outcome.Ok -> Outcome.Ok(page)
                        is Outcome.Err -> saved
                    }
            }
        }
        is Outcome.Err -> all
    }
