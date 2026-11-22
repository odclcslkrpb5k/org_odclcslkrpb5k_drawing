package org.odclcslkrpb5k.drawing.core

/**
 * A coloring page's regions. Every paper pixel belongs to exactly one region: the 4-connected area
 * it reaches without crossing line art. Lines are [LINE]. A tap fills a region by lookup, so the
 * app never flood-fills at run time (Eliminate: no slow fill, no fill that leaks at the child's
 * touch).
 *
 * 4-connectivity for paper means two line pixels touching only at a corner still close a shape, so
 * a diagonal line drawn one pixel wide holds.
 */
class Regions internal constructor(val width: Int, val height: Int, private val labels: IntArray) {
    /** The number of regions; labels run from 1 to this. */
    val count: Int = labels.maxOrNull() ?: LINE

    /** The region at a pixel, or [LINE] on a line or off the page. */
    fun at(x: Int, y: Int): Int =
        if (x in 0 until width && y in 0 until height) labels[y * width + x] else LINE

    /**
     * The region at a pixel or, when that is a line, the nearest region within [radius] pixels (a
     * finger often lands on the line it meant to fill beside). [LINE] when there is none.
     */
    fun near(x: Int, y: Int, radius: Int): Int {
        var best = LINE
        var bestDistance = Int.MAX_VALUE
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val distance = dx * dx + dy * dy
                val label = at(x + dx, y + dy)
                if (label != LINE && distance < bestDistance && distance <= radius * radius) {
                    best = label
                    bestDistance = distance
                }
            }
        }
        return best
    }

    /** How many pixels each region has; index 0 counts the lines. */
    fun sizes(): IntArray {
        val sizes = IntArray(count + 1)
        for (label in labels) sizes[label]++
        return sizes
    }

    /** Which regions reach the page's edge: the paper around the picture, not a shape in it. */
    fun atEdge(): BooleanArray {
        val edge = BooleanArray(count + 1)
        for (x in 0 until width) {
            edge[at(x, 0)] = true
            edge[at(x, height - 1)] = true
        }
        for (y in 0 until height) {
            edge[at(0, y)] = true
            edge[at(width - 1, y)] = true
        }
        edge[LINE] = false
        return edge
    }

    /** A copy of every pixel's label, row by row. */
    fun labels(): IntArray = labels.copyOf()

    /**
     * A region's pixels as row runs, three ints each: y, first x, length. A fill paints these, so
     * filling never searches the page. Empty for [LINE] or a label that is not a region.
     */
    fun runs(label: Int): IntArray = if (label in 1..count) runsByRegion[label] else IntArray(0)

    // Built with the regions, on whatever thread makes them (the disk thread in the app): it walks
    // every pixel, too slow for the main thread at a first fill.
    private val runsByRegion: Array<IntArray> = run {
        val all = Array(count + 1) { IntArrayBuilder() }
        forEachRun { y, x, length, label -> all[label].add(y, x, length) }
        Array(count + 1) { all[it].toArray() }
    }

    /** Every run of one region's pixels in a row, in row order then left to right. */
    internal fun forEachRun(each: (y: Int, x: Int, length: Int, label: Int) -> Unit) {
        for (y in 0 until height) {
            var x = 0
            while (x < width) {
                val label = labels[y * width + x]
                var end = x + 1
                while (end < width && labels[y * width + end] == label) end++
                if (label != LINE) each(y, x, end - x, label)
                x = end
            }
        }
    }

    companion object {
        const val LINE = 0

        /**
         * Labels [lines] (row by row, true where there is line art). Paper areas smaller than
         * [minPixels] are too small for a finger and become part of the lines. Regions are numbered
         * in the order their first pixel is met, row by row, so the same art gives the same labels.
         */
        fun label(width: Int, height: Int, lines: BooleanArray, minPixels: Int): Regions {
            require(width > 0 && height > 0 && lines.size == width * height) { "bad page size" }
            require(minPixels >= 1) { "minPixels must be at least 1" }
            val labeller = Labeller(width, height, lines)
            var next = 1
            for (start in lines.indices) {
                if (labeller.labels[start] != UNSEEN) continue
                val size = labeller.flood(start, next)
                if (size >= minPixels) next++ else labeller.erase(size)
            }
            return Regions(width, height, labeller.labels)
        }
    }
}

/** One labelling pass; the scratch arrays are made once and serve every region. */
private class Labeller(val width: Int, val height: Int, lines: BooleanArray) {
    val labels = IntArray(lines.size) { if (lines[it]) Regions.LINE else UNSEEN }
    private val stack = IntArray(lines.size)
    private val area = IntArray(lines.size)

    /** Gives [label] to every unseen pixel 4-connected to [start]; returns how many. */
    fun flood(start: Int, label: Int): Int {
        var top = 0
        var size = 0
        labels[start] = label
        stack[top++] = start
        while (top > 0) {
            val p = stack[--top]
            area[size++] = p
            val x = p % width
            val y = p / width
            if (x > 0) top = visit(p - 1, label, top)
            if (x < width - 1) top = visit(p + 1, label, top)
            if (y > 0) top = visit(p - width, label, top)
            if (y < height - 1) top = visit(p + width, label, top)
        }
        return size
    }

    /** The last [size] pixels flooded become line: a speck no finger could fill. */
    fun erase(size: Int) {
        for (i in 0 until size) labels[area[i]] = Regions.LINE
    }

    private fun visit(p: Int, label: Int, top: Int): Int {
        if (labels[p] != UNSEEN) return top
        labels[p] = label
        stack[top] = p
        return top + 1
    }
}

private const val UNSEEN = -1

/** A growing IntArray, for runs gathered one region at a time. */
private class IntArrayBuilder {
    private var items = IntArray(INITIAL)
    private var size = 0

    fun add(a: Int, b: Int, c: Int) {
        if (size + RUN_INTS > items.size) items = items.copyOf(items.size * 2)
        items[size++] = a
        items[size++] = b
        items[size++] = c
    }

    fun toArray(): IntArray = items.copyOf(size)

    private companion object {
        const val RUN_INTS = 3
        const val INITIAL = RUN_INTS * 16
    }
}
