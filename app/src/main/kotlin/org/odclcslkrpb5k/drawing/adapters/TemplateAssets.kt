package org.odclcslkrpb5k.drawing.adapters

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.core.graphics.createBitmap
import java.io.IOException
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.Page
import org.odclcslkrpb5k.drawing.core.Regions
import org.odclcslkrpb5k.drawing.core.RegionsCodec
import org.odclcslkrpb5k.drawing.core.Templates

/** A coloring page, ready to draw on: its regions to fill and its line art to draw on top. */
class Coloring(val name: String, val regions: Regions, val lines: Bitmap)

/**
 * The coloring pages built into the app (`assets/templates/`, made at build time by
 * tools/templates): the only code that reads them. Every call reads files, so it runs on the disk
 * thread.
 */
object TemplateAssets {
    /** The coloring pages, in the order the app shows them; empty if the list cannot be read. */
    fun names(assets: AssetManager): List<String> =
        read(assets, "$DIR/pages.txt")
            ?.toString(Charsets.US_ASCII)
            ?.lines()
            ?.filter { Templates.isName(it) }
            .orEmpty()

    /** A coloring page's regions and line art, or why it cannot be used. */
    fun load(assets: AssetManager, name: String): Outcome<Coloring, String> {
        if (!Templates.isName(name)) return Outcome.Err("not a coloring page name")
        val bytes = read(assets, "$DIR/$name.rgn") ?: return Outcome.Err("$name.rgn unreadable")
        val regions =
            when (val decoded = RegionsCodec.decode(bytes)) {
                is Outcome.Ok -> decoded.value
                is Outcome.Err -> return Outcome.Err("$name.rgn: ${decoded.error}")
            }
        val lines = bitmap(assets, "$DIR/$name.png", 1)
        val fits =
            lines != null &&
                regions.width == PAGE_W &&
                regions.height == PAGE_H &&
                lines.width == PAGE_W &&
                lines.height == PAGE_H
        return if (fits) Outcome.Ok(Coloring(name, regions, lines))
        else Outcome.Err("$name: line art missing or not the page size")
    }

    /** The page's line art on white, a quarter of the page's size, for the picker. */
    fun preview(assets: AssetManager, name: String): Bitmap? {
        val lines = bitmap(assets, "$DIR/$name.png", PREVIEW_SCALE) ?: return null
        val preview = createBitmap(lines.width, lines.height)
        preview.eraseColor(Color.WHITE)
        Canvas(preview).drawBitmap(lines, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        lines.recycle()
        return preview
    }

    private fun bitmap(assets: AssetManager, path: String, sample: Int): Bitmap? =
        read(assets, path)?.let { bytes ->
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }

    private fun read(assets: AssetManager, path: String): ByteArray? =
        try {
            assets.open(path).use { it.readBytes() }
        } catch (e: IOException) {
            Log.w(TAG, "$path not read: ${e.javaClass.simpleName}")
            null
        }

    private const val TAG = "TemplateAssets"
    private const val DIR = "templates"
    private const val PREVIEW_SCALE = 4
    private val PAGE_W = Page.WIDTH.toInt()
    private val PAGE_H = Page.HEIGHT.toInt()
}
