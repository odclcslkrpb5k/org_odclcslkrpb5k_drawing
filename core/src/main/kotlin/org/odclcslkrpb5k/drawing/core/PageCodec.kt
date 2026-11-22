package org.odclcslkrpb5k.drawing.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Why saved bytes could not be read as a page (BD-1). */
enum class DecodeError {
    NOT_A_PAGE,
    UNKNOWN_VERSION,
    TRUNCATED,
    OUT_OF_RANGE,
    TRAILING_BYTES,
}

/**
 * The saved form of a page: the operations that are done, nothing else (undone operations are
 * session state). Little-endian:
 *
 *     magic "ODRW" | version u16 | op count u32 | ops
 *     op: kind u8 (1 = stroke) | brush u8 | color i32 | size f32 | point count u32 |
 *         points: x f32, y f32, elapsed-ms i64, pressure f32
 *
 * Point times never decrease (Ink refuses a stroke whose time runs backwards). The reader checks
 * every length against what remains before reading, so a damaged file is an error value, never an
 * exception or an oversized allocation.
 */
object PageCodec {
    const val VERSION: Int = 1
    private const val MAGIC = 0x5752444F // "ODRW" read little-endian
    private const val OP_STROKE = 1
    private const val HEADER_BYTES = 4 + 2 + 4
    private const val STROKE_HEADER_BYTES = 1 + 4 + 4 + 4
    private const val POINT_BYTES = 4 + 4 + 8 + 4
    private const val U16 = 0xFFFF
    private const val U8 = 0xFF

    fun encode(history: History): ByteArray {
        val strokes = history.strokes
        val size =
            HEADER_BYTES + strokes.sumOf { 1 + STROKE_HEADER_BYTES + it.points.size * POINT_BYTES }
        val out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(MAGIC)
        out.putShort(VERSION.toShort())
        out.putInt(strokes.size)
        for (s in strokes) {
            out.put(OP_STROKE.toByte())
            out.put(s.brush.kind.ordinal.toByte())
            out.putInt(s.brush.color.value)
            out.putFloat(s.brush.size)
            out.putInt(s.points.size)
            for (p in s.points) {
                out.putFloat(p.x)
                out.putFloat(p.y)
                out.putLong(p.elapsedMillis)
                out.putFloat(p.pressure)
            }
        }
        return out.array()
    }

    fun decode(bytes: ByteArray): Outcome<History, DecodeError> {
        val r = Reader(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN))
        return when (val count = header(r)) {
            is Outcome.Ok -> ops(r, count.value)
            is Outcome.Err -> count
        }
    }

    /** The magic, the version and the operation count. */
    private fun header(r: Reader): Outcome<Int, DecodeError> {
        if (r.remaining() < HEADER_BYTES || r.int() != MAGIC)
            return Outcome.Err(DecodeError.NOT_A_PAGE)
        if (r.short() and U16 != VERSION) return Outcome.Err(DecodeError.UNKNOWN_VERSION)
        val count = r.int()
        if (count !in 0..History.MAX_OPS) return Outcome.Err(DecodeError.OUT_OF_RANGE)
        return Outcome.Ok(count)
    }

    /** [count] operations, which must end exactly at the end of the bytes. */
    private fun ops(r: Reader, count: Int): Outcome<History, DecodeError> {
        val ops = ArrayList<Op>(minOf(count, r.remaining() / (1 + STROKE_HEADER_BYTES)))
        var error: DecodeError? = null
        var i = 0
        while (error == null && i < count) {
            when (val op = stroke(r)) {
                is Outcome.Ok -> ops += op.value
                is Outcome.Err -> error = op.error
            }
            i += 1
        }
        val failed = error ?: if (r.remaining() != 0) DecodeError.TRAILING_BYTES else null
        return if (failed == null) Outcome.Ok(History(ops)) else Outcome.Err(failed)
    }

    private fun stroke(r: Reader): Outcome<Op, DecodeError> {
        if (r.remaining() < 1 + STROKE_HEADER_BYTES) return Outcome.Err(DecodeError.TRUNCATED)
        if (r.byte() and U8 != OP_STROKE) return Outcome.Err(DecodeError.OUT_OF_RANGE)
        val kind = BrushKind.entries.getOrNull(r.byte() and U8)
        val color = Argb(r.int())
        val size = r.float()
        val n = r.int()
        if (kind == null || n !in 0..MAX_STROKE_POINTS) return Outcome.Err(DecodeError.OUT_OF_RANGE)
        if (r.remaining() / POINT_BYTES < n) return Outcome.Err(DecodeError.TRUNCATED)
        val points =
            List(n) {
                InputPoint(
                    x = r.float(),
                    y = r.float(),
                    elapsedMillis = r.long(),
                    pressure = r.float(),
                )
            }
        val stroke = StrokeData(BrushSpec(kind, color, size), points)
        if (!stroke.isSaveable()) return Outcome.Err(DecodeError.OUT_OF_RANGE)
        return Outcome.Ok(Op.AddStroke(stroke))
    }

    /** Reads only after the caller has checked [remaining]; never past the end. */
    private class Reader(private val buf: ByteBuffer) {
        fun remaining(): Int = buf.remaining()

        fun byte(): Int = buf.get().toInt()

        fun short(): Int = buf.getShort().toInt()

        fun int(): Int = buf.getInt()

        fun long(): Long = buf.getLong()

        fun float(): Float = buf.getFloat()
    }
}
