package org.odclcslkrpb5k.drawing.adapters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.util.AtomicFile
import android.util.Log
import androidx.core.util.tryWrite
import java.io.File
import java.io.IOException
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.Page
import org.odclcslkrpb5k.drawing.core.PageId

/**
 * Photos to draw on (the selfie stretch), one per page, in `photos/` (outside Auto Backup: photos
 * would soon fill its 25 MB, and a full backup stops every backup). The camera writes to a
 * [pending] file; [adopt] turns that into the page's photo, upright and cropped to the page, so a
 * page always opens with a photo exactly its size. Disk thread only.
 */
class PhotoStore(filesDir: File) {
    internal val dir = File(filesDir, "photos")

    /** Where the camera writes a new photo for [id], before it is the page's. */
    fun pending(id: PageId): File {
        if (!dir.isDirectory && !dir.mkdirs()) Log.w(TAG, "photos directory not made")
        return File(dir, "${id.value}.camera")
    }

    /**
     * Makes the camera's photo the page's: decoded upright (the decoder applies the camera's
     * orientation), scaled to cover the page, centre-cropped to it, and saved; the camera's file is
     * deleted either way. An error when there is no photo or it cannot be read.
     */
    fun adopt(id: PageId): Outcome<Unit, StoreError> {
        val raw = pending(id)
        return try {
            if (raw.isFile && raw.length() > 0L) {
                keep(id, cropped(raw))
                Outcome.Ok(Unit)
            } else {
                io("no photo from the camera")
            }
        } catch (e: IOException) {
            io("photo not adopted: ${e.javaClass.simpleName}")
        } catch (e: IllegalArgumentException) {
            io("photo not adopted: ${e.javaClass.simpleName}")
        } finally {
            if (raw.exists() && !raw.delete()) Log.w(TAG, "camera file not deleted")
        }
    }

    /**
     * The photo upright, scaled to cover the page and centre-cropped to it while decoding: the
     * result is page-sized whatever the photo's shape (a panorama is never held whole).
     */
    private fun cropped(raw: File): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(raw)) { decoder, info, _ ->
            val scale = maxOf(Page.WIDTH / info.size.width, Page.HEIGHT / info.size.height)
            val w = (info.size.width * scale).toInt().coerceAtLeast(PAGE_W)
            val h = (info.size.height * scale).toInt().coerceAtLeast(PAGE_H)
            decoder.setTargetSize(w, h)
            val left = (w - PAGE_W) / 2
            val top = (h - PAGE_H) / 2
            decoder.crop = Rect(left, top, left + PAGE_W, top + PAGE_H)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    /** Clears camera files no page is waiting for (left by a crash, or a camera never answered). */
    fun sweep() {
        val strays = dir.listFiles { f -> f.name.endsWith(".camera") } ?: return
        for (f in strays) if (!f.delete()) Log.w(TAG, "stray camera file not cleared")
    }

    /** Clears the page's photo (its page could not be made). */
    fun forget(id: PageId) {
        val file = photoFile(id)
        if (file.exists() && !file.delete()) Log.w(TAG, "photo not cleared")
    }

    private fun keep(id: PageId, page: Bitmap) {
        AtomicFile(photoFile(id)).tryWrite {
            page.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
        }
    }

    /** Drops a camera file that never became a photo (she backed out of the camera). */
    fun discard(id: PageId) {
        val raw = pending(id)
        if (raw.exists() && !raw.delete()) Log.w(TAG, "camera file not deleted")
    }

    /** The page's photo, or null when it has none (or it cannot be read: the page opens plain). */
    fun load(id: PageId): Bitmap? {
        val file = photoFile(id)
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.path).also {
            if (it == null) Log.w(TAG, "photo unreadable")
        }
    }

    internal fun photoFile(id: PageId) = File(dir, "${id.value}.jpg")

    private fun io(what: String): Outcome.Err<StoreError> {
        Log.w(TAG, what)
        return Outcome.Err(StoreError.IO)
    }

    private companion object {
        const val TAG = "PhotoStore"
        val PAGE_W = Page.WIDTH.toInt()
        val PAGE_H = Page.HEIGHT.toInt()
        const val JPEG_QUALITY = 90
    }
}
