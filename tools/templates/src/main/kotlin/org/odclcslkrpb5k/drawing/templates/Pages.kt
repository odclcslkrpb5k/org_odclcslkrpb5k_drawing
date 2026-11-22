package org.odclcslkrpb5k.drawing.templates

import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.Page
import org.odclcslkrpb5k.drawing.core.Regions
import org.odclcslkrpb5k.drawing.core.Templates

/** A rendered page: opaque ARGB pixels, row by row. */
class Raster(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(width > 0 && height > 0 && argb.size == width * height) { "bad raster size" }
    }
}

/** The rules every coloring page must meet before it ships. */
object PageRules {
    val WIDTH = Page.WIDTH.toInt()
    val HEIGHT = Page.HEIGHT.toInt()

    /** Darker than this (0 to 255) is line art. */
    const val INK_BELOW = 128

    /** Paper areas smaller than this fold into the lines: too small for a finger to fill. */
    const val MIN_PIXELS = 400

    /** No shape inside the picture may cover more than this share of the page, in percent. */
    const val MAX_SHAPE_PERCENT = 40
}

/** Each page's name and how many regions it must have, from `templates/pages.tsv`. */
object Manifest {
    /** One `name<TAB>regions` per line; `#` starts a comment line. */
    fun parse(text: String): Outcome<Map<String, Int>, String> {
        val pages = LinkedHashMap<String, Int>()
        for ((index, raw) in text.lines().withIndex()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val fields = line.split('\t')
            val count = fields.getOrNull(1)?.toIntOrNull()
            val name = fields[0]
            val bad =
                fields.size != 2 ||
                    count == null ||
                    count < 1 ||
                    !Templates.isName(name) ||
                    name in pages
            if (bad) return Outcome.Err("pages.tsv line ${index + 1}: expected 'name<TAB>regions'")
            pages[name] = count
        }
        return Outcome.Ok(pages)
    }
}

/** A pixel's brightness, 0 (black) to 255 (white), composited over white paper. */
private fun brightness(c: Int): Int {
    val alpha = c ushr ALPHA_SHIFT and BYTE
    fun over(channel: Int) = (channel * alpha + BYTE * (BYTE - alpha)) / BYTE
    val r = over(c shr RED_SHIFT and BYTE)
    val g = over(c shr GREEN_SHIFT and BYTE)
    val b = over(c and BYTE)
    return (r * RED_WEIGHT + g * GREEN_WEIGHT + b * BLUE_WEIGHT) / WEIGHTS
}

/** True where a pixel is line art: dark after compositing over white paper. */
fun lines(raster: Raster): BooleanArray =
    BooleanArray(raster.argb.size) { brightness(raster.argb[it]) < PageRules.INK_BELOW }

/**
 * The line art the app draws over the child's colors: black, as opaque as the page is dark, so the
 * paper is transparent and the lines keep their smooth edges.
 */
fun lineArt(raster: Raster): IntArray =
    IntArray(raster.argb.size) { (BYTE - brightness(raster.argb[it])) shl ALPHA_SHIFT }

/** Every rule the page breaks, as sentences naming it; empty when it may ship. */
fun problems(name: String, raster: Raster, regions: Regions, expected: Int?): List<String> {
    val found = mutableListOf<String>()
    if (raster.width != PageRules.WIDTH || raster.height != PageRules.HEIGHT) {
        found +=
            "$name: is ${raster.width}x${raster.height}, not ${PageRules.WIDTH}x${PageRules.HEIGHT}"
    }
    val edge = regions.atEdge()
    val edgeCount = edge.count { it }
    if (edgeCount != 1) {
        found +=
            "$name: $edgeCount regions reach the page edge; the paper around the picture must be one"
    }
    if (expected == null) {
        found += "$name: not in pages.tsv (it has ${regions.count} regions)"
    } else if (regions.count != expected) {
        found +=
            "$name: has ${regions.count} regions, pages.tsv says $expected (a gap in a line " +
                "merges two shapes)"
    }
    val sizes = regions.sizes()
    val limit = regions.width.toLong() * regions.height * PageRules.MAX_SHAPE_PERCENT / PERCENT
    for (label in 1..regions.count) {
        if (!edge[label] && sizes[label] > limit) {
            found +=
                "$name: region $label covers more than ${PageRules.MAX_SHAPE_PERCENT}% of the page"
        }
    }
    return found
}

private const val BYTE = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val RED_WEIGHT = 299
private const val GREEN_WEIGHT = 587
private const val BLUE_WEIGHT = 114
private const val WEIGHTS = 1000
private const val PERCENT = 100
