package org.odclcslkrpb5k.drawing.adapters

import android.graphics.Bitmap
import android.util.Log
import java.io.File
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.PageId

/**
 * Pages the parent put aside, under `trash/` (outside the backup): each page's directory with its
 * thumbnail and photo beside it, so the trash shows what each page was and a page put back keeps
 * its picture. Only the parent screen uses it; nothing the child can reach deletes a page.
 */
class PageTrash(filesDir: File, private val store: PageStore) {
    private val trash = File(filesDir, "trash")
    private val photos = PhotoStore(filesDir)

    /** Moves a saved page and its thumbnail to the trash. */
    fun trash(id: PageId): Outcome<Unit, StoreError> {
        val moved =
            (trash.isDirectory || trash.mkdirs()) &&
                File(store.pages, id.value).renameTo(File(trash, id.value))
        if (!moved) return io("trash")
        // A rename keeps the directory's time; the trash lists the most recently trashed first.
        if (!File(trash, id.value).setLastModified(System.currentTimeMillis())) {
            Log.w(TAG, "trash time not updated")
        }
        move(store.thumbFile(id), thumbFile(id))
        move(photos.photoFile(id), photoFile(id))
        return Outcome.Ok(Unit)
    }

    /** Pages in the trash, most recently trashed first. */
    fun list(): List<PageId> =
        (trash.listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            .mapNotNull { dir -> PageId.parse(dir.name)?.let { it to dir.lastModified() } }
            .sortedByDescending { it.second }
            .map { it.first }

    /** A trashed page's thumbnail, or null when it has none. */
    fun thumbnail(id: PageId): Bitmap? = decodePng(thumbFile(id))

    /** Puts a page back among the saved pages, newest, with its thumbnail. */
    fun restore(id: PageId): Outcome<Unit, StoreError> {
        val back = File(store.pages, id.value)
        if (back.exists()) return io("restore over a saved page")
        val moved =
            (store.pages.isDirectory || store.pages.mkdirs()) &&
                File(trash, id.value).renameTo(back)
        if (!moved) return io("restore")
        if (!back.setLastModified(System.currentTimeMillis())) Log.w(TAG, "page time not updated")
        if (store.thumbs.isDirectory || store.thumbs.mkdirs()) {
            move(thumbFile(id), store.thumbFile(id))
        }
        if (photos.dir.isDirectory || photos.dir.mkdirs()) {
            move(photoFile(id), photos.photoFile(id))
        }
        return Outcome.Ok(Unit)
    }

    /** Deletes every trashed page for good: how many, or an error if any is left. */
    fun empty(): Outcome<Int, StoreError> {
        val all = list()
        val left = all.count { id ->
            val leftovers =
                listOf(thumbFile(id), photoFile(id)).filter { it.exists() && !it.delete() }
            !File(trash, id.value).deleteRecursively() || leftovers.isNotEmpty()
        }
        return if (left == 0) Outcome.Ok(all.size) else io("emptying ($left left)")
    }

    private fun thumbFile(id: PageId) = File(trash, "${id.value}.png")

    private fun photoFile(id: PageId) = File(trash, "${id.value}.jpg")

    /** A thumbnail or photo that cannot follow its page is only logged: the page matters most. */
    private fun move(from: File, to: File) {
        if (from.exists() && !from.renameTo(to)) Log.w(TAG, "${from.name} not moved")
    }

    private fun io(what: String): Outcome.Err<StoreError> {
        Log.w(TAG, "$what failed")
        return Outcome.Err(StoreError.IO)
    }

    private companion object {
        const val TAG = "PageTrash"
    }
}
