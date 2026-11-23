package org.odclcslkrpb5k.drawing.adapters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import android.util.Log
import androidx.core.util.tryWrite
import java.io.File
import java.io.IOException
import org.odclcslkrpb5k.drawing.core.BackupBudget
import org.odclcslkrpb5k.drawing.core.History
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.PageCodec
import org.odclcslkrpb5k.drawing.core.PageId

/** Why the page store could not do what was asked. */
enum class StoreError {
    IO,
    NOT_READ_BACK,
    NO_READABLE_VERSION,
}

/**
 * Pages on disk: the only code that touches the files (ER-3). Each page is a directory under
 * `drawings/` holding its newest [KEEP] saved versions, each written atomically (`tryWrite`
 * finishes the write, or fails it and leaves the old file, whatever is thrown); Auto Backup carries
 * `drawings/` (data_extraction_rules.xml). Thumbnails live in `thumbs/`, outside the backup;
 * trashed pages are [PageTrash]'s. A directory that cannot be listed is an error, never an empty
 * page: an empty page would be saved over the real one.
 */
class PageStore(filesDir: File) {
    internal val pages = File(filesDir, "drawings")
    internal val thumbs = File(filesDir, "thumbs")

    /** Saved pages with at least one version, newest first. */
    fun list(): List<PageId> =
        (pages.listFiles()
                ?: emptyArray<File>().also {
                    if (pages.exists()) Log.e(TAG, "drawings cannot be listed")
                })
            .mapNotNull { dir -> PageId.parse(dir.name)?.let { it to dir.lastModified() } }
            .filter { (id, _) -> (hasVersions(id) as? Outcome.Ok)?.value == true }
            .sortedByDescending { it.second }
            .map { it.first }

    /**
     * Writes a new version of the page, reads it back, and only once it decodes to the same page
     * deletes versions beyond the newest [KEEP], so the newest readable version is never deleted. A
     * new version that cannot be shown to read back is deleted again.
     */
    fun save(id: PageId, history: History): Outcome<Unit, StoreError> {
        val dir = File(pages, id.value)
        if (!dir.isDirectory && !dir.mkdirs()) return io("mkdirs")
        val versions = versions(dir) ?: return io("list")
        val next = (versions.firstOrNull()?.first ?: 0L) + 1
        val file = AtomicFile(File(dir, "v$next.page"))
        val verified =
            try {
                file.tryWrite { it.write(PageCodec.encode(history)) }
                PageCodec.decode(file.readFully()) == Outcome.Ok(history.saved())
            } catch (e: IOException) {
                Log.w(TAG, "save failed: ${e.javaClass.simpleName}")
                false
            }
        if (!verified) {
            file.delete()
            if (file.baseFile.exists()) Log.e(TAG, "unverified version v$next could not be deleted")
            return Outcome.Err(StoreError.NOT_READ_BACK)
        }
        for ((n, old) in versions.drop(KEEP - 1)) {
            if (!old.delete()) Log.w(TAG, "old version v$n not deleted")
        }
        if (!dir.setLastModified(System.currentTimeMillis())) Log.w(TAG, "page time not updated")
        return Outcome.Ok(Unit)
    }

    /**
     * Whether the page has any saved version. A folder with none (a first save that never finished)
     * holds nothing to lose, so the page opens as new; one that cannot be listed is an error.
     */
    fun hasVersions(id: PageId): Outcome<Boolean, StoreError> {
        val dir = File(pages, id.value)
        if (!dir.exists()) return Outcome.Ok(false)
        return versions(dir)?.let { Outcome.Ok(it.isNotEmpty()) } ?: io("list")
    }

    /**
     * The newest version of the page that reads back whole. A version that cannot be read or does
     * not decode is skipped and logged (a write cut short), so the child gets the last good page.
     */
    fun load(id: PageId): Outcome<History, StoreError> {
        val versions = versions(File(pages, id.value)) ?: return io("list")
        for ((n, file) in versions) read(n, file)?.let {
            return Outcome.Ok(it)
        }
        return Outcome.Err(StoreError.NO_READABLE_VERSION)
    }

    /** One version file decoded, or null (logged) when it cannot be read or does not decode. */
    private fun read(n: Long, file: File): History? {
        val page =
            try {
                PageCodec.decode(AtomicFile(file).readFully())
            } catch (e: IOException) {
                Log.w(TAG, "version $n unreadable: ${e.javaClass.simpleName}")
                return null
            }
        return when (page) {
            is Outcome.Ok -> page.value
            is Outcome.Err -> {
                Log.w(TAG, "version $n does not decode: ${page.error}")
                null
            }
        }
    }

    /**
     * The page's saved versions, newest first, by number, each decoded or null when it does not
     * read back whole (the parent sees it, and cannot restore it).
     */
    fun versions(id: PageId): Outcome<List<Pair<Long, History?>>, StoreError> {
        val versions = versions(File(pages, id.value)) ?: return io("list")
        return Outcome.Ok(versions.map { (n, file) -> n to read(n, file) })
    }

    fun saveThumbnail(id: PageId, bitmap: Bitmap): Outcome<Unit, StoreError> =
        try {
            if (!thumbs.isDirectory && !thumbs.mkdirs()) throw IOException("mkdirs")
            AtomicFile(thumbFile(id)).tryWrite {
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)
            }
            Outcome.Ok(Unit)
        } catch (e: IOException) {
            Log.w(TAG, "thumbnail failed: ${e.javaClass.simpleName}")
            Outcome.Err(StoreError.IO)
        }

    /** The page's thumbnail, or null when it has none yet. */
    fun thumbnail(id: PageId): Bitmap? = decodePng(thumbFile(id))

    internal fun thumbFile(id: PageId) = File(thumbs, "${id.value}.png")

    private fun io(what: String): Outcome.Err<StoreError> {
        Log.w(TAG, "$what failed")
        return Outcome.Err(StoreError.IO)
    }

    /** The page's version files, newest first; null when the directory cannot be listed. */
    private fun versions(dir: File): List<Pair<Long, File>>? {
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles() ?: return null
        return files
            .mapNotNull { f ->
                VERSION.matchEntire(f.name)?.groupValues?.get(1)?.toLongOrNull()?.let { it to f }
            }
            .sortedByDescending { it.first }
    }

    private companion object {
        const val TAG = "PageStore"
        const val KEEP = 5
        const val PNG_QUALITY = 100
        val VERSION = Regex("v([0-9]{1,18})\\.page")
    }
}

/** A PNG file decoded, or null when there is none (or it does not decode). */
internal fun decodePng(file: File): Bitmap? =
    file.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }

/**
 * Bytes of everything Auto Backup carries (drawings/, every kept version), with the backup's own
 * per-entry cost: what counts against Google's allowance (core BackupBudget). Negative when part of
 * drawings/ cannot be read, so the parent sees no size rather than a size too small. Disk thread.
 */
fun PageStore.backupBytes(): Long {
    var unreadable = false
    val entries = pages.walkTopDown().onFail { _, _ -> unreadable = true }.toList()
    if (unreadable) return -1L
    return entries.sumOf { (if (it.isFile) it.length() else 0L) + BackupBudget.PER_ENTRY }
}
