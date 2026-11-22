package org.odclcslkrpb5k.drawing

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.odclcslkrpb5k.drawing.adapters.PageStore
import org.odclcslkrpb5k.drawing.adapters.StoreError
import org.odclcslkrpb5k.drawing.core.History
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.PageId

/**
 * Every page change not yet safely on disk, for the whole app, on the main thread. There is no save
 * button: nothing the child does can skip a save.
 * - A save always writes a page's newest change, never the one current when an earlier attempt
 *   began, so a retry can never put older work back over newer work.
 * - One save per page is on [PageDisk] at a time; a change made meanwhile is saved after it.
 * - Only the screen that opened a page most recently may change it ([open]); a closing screen's
 *   late change cannot replace what a newer screen drew.
 * - A page with a change not yet on disk is opened from that change ([unsaved]).
 * - A failed save is tried again with growing waits for as long as the change is unsaved; a
 *   thumbnail that failed alone is retried alone.
 */
object PageSaves {
    private class Entry(var latest: History, var thumbnail: Bitmap) {
        var changes = 0L
        var saved = 0L
        var thumbSaved = 0L
        var inFlight = false
        var retryMs = FIRST_RETRY_MS
        var task: Runnable? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private val entries = mutableMapOf<PageId, Entry>()
    private val owners = mutableMapOf<PageId, Any>()

    /** [screen] now shows the page; changes from any other screen are ignored from now on. */
    fun open(id: PageId, screen: Any) {
        owners[id] = screen
    }

    /** The page changed on [screen]: it is saved after a short pause, or by [flush]. */
    fun changed(store: PageStore, id: PageId, screen: Any, history: History, thumbnail: Bitmap) {
        if (owners[id] !== screen) {
            Log.w(TAG, "change from a screen that no longer owns the page, ignored")
            return
        }
        val e = entries.getOrPut(id) { Entry(history, thumbnail) }
        e.latest = history
        e.thumbnail = thumbnail
        e.changes += 1
        e.retryMs = FIRST_RETRY_MS
        // A save already pending is not pushed back, so continuous drawing is still saved within
        // DELAY_MS of its first unsaved change.
        if (e.task == null) schedule(store, id, e, DELAY_MS)
    }

    /** [screen] is gone; it no longer owns the page, and is not kept in memory by it. */
    fun close(id: PageId, screen: Any) {
        if (owners[id] === screen) owners.remove(id)
    }

    /** Saves the page's newest change now, if one is unsaved. */
    fun flush(store: PageStore, id: PageId) {
        val e = entries[id] ?: return
        if (e.saved < e.changes || e.thumbSaved < e.saved) schedule(store, id, e, 0L)
    }

    /** Whether a screen may still change the page, or a change to it is not yet on disk. */
    fun busy(id: PageId): Boolean = id in owners || id in entries

    /** The page's newest change if it is not yet on disk; newer than anything a load would read. */
    fun unsaved(id: PageId): History? = entries[id]?.takeIf { it.saved < it.changes }?.latest

    private fun schedule(store: PageStore, id: PageId, e: Entry, delayMs: Long) {
        e.task?.let(main::removeCallbacks)
        val task = Runnable { save(store, id, e) }
        e.task = task
        main.postDelayed(task, delayMs)
    }

    private fun save(store: PageStore, id: PageId, e: Entry) {
        e.task = null
        // One save at a time: a change made meanwhile is picked up when this one finishes.
        if (e.inFlight || entries[id] !== e) return
        e.inFlight = true
        val change = e.changes
        val history = e.latest
        val thumbnail = e.thumbnail
        val pageDue = e.saved < change
        PageDisk.run(
            {
                val page: Outcome<Unit, StoreError> =
                    if (pageDue) store.save(id, history) else Outcome.Ok(Unit)
                // The thumbnail is written only once the page is safe.
                Attempt(
                    change,
                    page,
                    if (page is Outcome.Ok) store.saveThumbnail(id, thumbnail) else null,
                )
            },
            failed = Attempt(change, Outcome.Err(StoreError.IO), null),
        ) { attempt ->
            finished(store, id, e, attempt)
        }
    }

    /** What one save did: the page (or nothing due), and the thumbnail if the page was safe. */
    private class Attempt(
        val change: Long,
        val page: Outcome<Unit, StoreError>,
        val thumb: Outcome<Unit, StoreError>?,
    )

    private fun finished(store: PageStore, id: PageId, e: Entry, attempt: Attempt) {
        val change = attempt.change
        val page = attempt.page
        val thumb = attempt.thumb
        e.inFlight = false
        if (page is Outcome.Ok) e.saved = maxOf(e.saved, change)
        if (thumb is Outcome.Ok) e.thumbSaved = maxOf(e.thumbSaved, change)
        val why =
            when {
                page is Outcome.Err -> "page: ${page.error}"
                thumb is Outcome.Err -> "thumbnail: ${thumb.error}"
                else -> null
            }
        val done = e.saved >= e.changes && e.thumbSaved >= e.saved
        when {
            done && e.task == null -> if (entries[id] === e) entries.remove(id)
            why != null -> {
                Log.e(TAG, "save failed ($why); retrying in ${e.retryMs} ms")
                schedule(store, id, e, e.retryMs)
                e.retryMs = (e.retryMs * 2).coerceAtMost(MAX_RETRY_MS)
            }
            // A change arrived while this save ran: save it now.
            !done && e.task == null -> schedule(store, id, e, 0L)
            else -> Unit
        }
    }

    private const val TAG = "PageSaves"
    private const val DELAY_MS = 400L
    private const val FIRST_RETRY_MS = 1_000L
    private const val MAX_RETRY_MS = 30_000L
}
