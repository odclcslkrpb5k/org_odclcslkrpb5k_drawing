package org.odclcslkrpb5k.drawing.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The shipped form of a coloring page's [Regions], built once from its line art at build time.
 * Little-endian:
 *
 *     magic "ORGN" | version u16 | width u16 | height u16 | region count u32 | run count u32 |
 *     runs: y u16 | x u16 | length u16 | label u32
 *
 * Runs are in row order then left to right, never overlap, and two runs of the same region never
 * touch in a row (they would be one run), so each page has exactly one encoding. Pixels in no run
 * are line. Every label from 1 to the count has a run. The reader checks all of that, so damaged
 * bytes are an error value, never an exception or a page that fills wrongly.
 */
object RegionsCodec {
    const val VERSION: Int = 1
    const val MAX_SIDE: Int = 4096
    private const val MAGIC = 0x4E47524F // "ORGN" read little-endian
    private const val HEADER_BYTES = 4 + 2 + 2 + 2 + 4 + 4
    private const val RUN_BYTES = 2 + 2 + 2 + 4
    private const val U16 = 0xFFFF

    fun encode(regions: Regions): ByteArray {
        var runs = 0
        regions.forEachRun { _, _, _, _ -> runs++ }
        val out =
            ByteBuffer.allocate(HEADER_BYTES + runs * RUN_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(MAGIC)
        out.putShort(VERSION.toShort())
        out.putShort(regions.width.toShort())
        out.putShort(regions.height.toShort())
        out.putInt(regions.count)
        out.putInt(runs)
        regions.forEachRun { y, x, length, label ->
            out.putShort(y.toShort())
            out.putShort(x.toShort())
            out.putShort(length.toShort())
            out.putInt(label)
        }
        return out.array()
    }

    fun decode(bytes: ByteArray): Outcome<Regions, DecodeError> {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < HEADER_BYTES || buf.getInt() != MAGIC)
            return Outcome.Err(DecodeError.NOT_A_PAGE)
        if (buf.getShort().toInt() and U16 != VERSION)
            return Outcome.Err(DecodeError.UNKNOWN_VERSION)
        val width = buf.getShort().toInt() and U16
        val height = buf.getShort().toInt() and U16
        val count = buf.getInt()
        val runs = buf.getInt()
        val sane =
            width in 1..MAX_SIDE &&
                height in 1..MAX_SIDE &&
                count in 0..width * height &&
                runs in 0..width * height
        return when {
            !sane -> Outcome.Err(DecodeError.OUT_OF_RANGE)
            buf.remaining() / RUN_BYTES < runs -> Outcome.Err(DecodeError.TRUNCATED)
            buf.remaining() != runs * RUN_BYTES -> Outcome.Err(DecodeError.TRAILING_BYTES)
            else -> fill(buf, width, height, count, runs)
        }
    }

    /** Paints the runs into a label array, refusing any run out of order, overlapping or merged. */
    private fun fill(
        buf: ByteBuffer,
        width: Int,
        height: Int,
        count: Int,
        runs: Int,
    ): Outcome<Regions, DecodeError> {
        val labels = IntArray(width * height)
        val seen = BooleanArray(count + 1)
        var end = 0 // the first pixel after the last run, row-major
        var lastLabel = Regions.LINE
        var valid = true
        var i = 0
        while (valid && i < runs) {
            val y = buf.getShort().toInt() and U16
            val x = buf.getShort().toInt() and U16
            val length = buf.getShort().toInt() and U16
            val label = buf.getInt()
            val start = y * width + x
            valid =
                y < height &&
                    length >= 1 &&
                    x + length <= width &&
                    label in 1..count &&
                    start >= end &&
                    !(start == end && x != 0 && label == lastLabel)
            if (valid) {
                labels.fill(label, start, start + length)
                seen[label] = true
                end = start + length
                lastLabel = label
            }
            i++
        }
        if (!valid || (1..count).any { !seen[it] }) return Outcome.Err(DecodeError.OUT_OF_RANGE)
        return Outcome.Ok(Regions(width, height, labels))
    }
}
