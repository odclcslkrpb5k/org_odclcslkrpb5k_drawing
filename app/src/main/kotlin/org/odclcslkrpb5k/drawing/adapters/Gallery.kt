package org.odclcslkrpb5k.drawing.adapters

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.IOException
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.PageId

/**
 * Pictures of pages in the tablet's shared `Pictures/Drawing/`, where the gallery shows them and
 * Google Photos can back them up (the parent's setup): one PNG per page, named by its id, written
 * again when the page changes. The app needs no permission for pictures it made itself, and sees
 * only those. These are copies; the pages themselves stay in the app (PageStore).
 */
object Gallery {
    private val IMAGES: Uri =
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /**
     * Writes the page's picture over the app's own earlier one (disk thread). After a reinstall, a
     * data wipe or a restore, the old picture is no longer the app's and cannot be seen; the
     * gallery then names the new one `<id> (1).png`, which [find] matches from then on, so a page
     * has at most one picture the app keeps writing.
     */
    fun export(resolver: ContentResolver, id: PageId, picture: Bitmap): Outcome<Unit, StoreError> {
        var inserted: Uri? = null
        var written = false
        return try {
            val uri =
                find(resolver, id) ?: insert(resolver, "${id.value}.png").also { inserted = it }
            if (uri != null) {
                written =
                    resolver.openOutputStream(uri, "wt")?.use {
                        picture.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
                    } ?: false
                // A new picture is shown in the gallery only once whole.
                if (written && inserted != null) publish(resolver, uri)
            }
            if (written) Outcome.Ok(Unit) else io("write")
        } catch (e: IOException) {
            io("export: ${e.javaClass.simpleName}")
        } catch (e: SecurityException) {
            io("export: ${e.javaClass.simpleName}")
        } catch (e: IllegalStateException) {
            // The shared storage is not mounted.
            io("export: ${e.javaClass.simpleName}")
        } finally {
            // A new picture that was not written whole is removed, not left pending.
            inserted?.let { if (!written) resolver.delete(it, null, null) }
        }
    }

    /** The app's own picture of the page, by its name or the gallery's `<id> (n).png` for it. */
    private fun find(resolver: ContentResolver, id: PageId): Uri? =
        resolver
            .query(
                IMAGES,
                arrayOf(MediaStore.Images.Media._ID),
                "(${MediaStore.Images.Media.DISPLAY_NAME} = ? OR " +
                    "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?) AND " +
                    "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                arrayOf("${id.value}.png", "${id.value} (%).png", FOLDER),
                "${MediaStore.Images.Media._ID} DESC",
            )
            ?.use {
                if (it.moveToFirst()) Uri.withAppendedPath(IMAGES, it.getLong(0).toString())
                else null
            }

    private fun insert(resolver: ContentResolver, name: String): Uri? =
        resolver.insert(
            IMAGES,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            },
        )

    private fun publish(resolver: ContentResolver, uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        if (resolver.update(uri, values, null, null) != 1) Log.w(TAG, "picture not published")
    }

    private fun io(what: String): Outcome.Err<StoreError> {
        Log.w(TAG, "$what failed")
        return Outcome.Err(StoreError.IO)
    }

    private const val TAG = "Gallery"
    private const val FOLDER = "Pictures/Drawing/"
    private const val PNG_QUALITY = 100
}
