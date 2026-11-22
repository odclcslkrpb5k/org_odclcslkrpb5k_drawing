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
 * The saved form of a page: the operations that are done, in order, nothing else (undone operations
 * are session state). Little-endian:
 *
 *     magic "ODRW" | version u16 | op count u32 | ops
 *     stroke: kind u8 = 1 | brush u8 | color i32 | size f32 | point count u32 |
 *             points: x f32, y f32, elapsed-ms i64, pressure f32
 *     stamp:  kind u8 = 2 | face u8 | x f32 | y f32 | size f32            (version 2 on)
 *
 * The writer writes [VERSION]; the reader also accepts version 1, which had strokes only, in the
 * same layout (SV-24). Every stroke and stamp read must be saveable (Ink refuses a stroke whose
 * time runs backwards). The reader checks every length against what remains before reading, so a
 * damaged file is an error value, never an exception or an oversized allocation.
 */
object PageCodec {
    const val VERSION: Int = 2
    private const val OLDEST_READABLE = 1
    private const val MAGIC = 0x5752444F // "ODRW" read little-endian
    private const val OP_STROKE = 1
    private const val OP_STAMP = 2
    private const val HEADER_BYTES = 4 + 2 + 4
    private const val STROKE_HEADER_BYTES = 1 + 4 + 4 + 4
    /** After its kind byte: face u8, x, y and size f32. */
    private const val STAMP_BODY_BYTES = 1 + 4 + 4 + 4
    private const val POINT_BYTES = 4 + 4 + 8 + 4
    private const val U16 = 0xFFFF
    private const val U8 = 0xFF

    fun encode(history: History): ByteArray {
        val size =
            HEADER_BYTES +
                history.done.sumOf { op ->
                    1 +
                        when (op) {
                            is Op.AddStroke ->
                                STROKE_HEADER_BYTES + op.stroke.points.size * POINT_BYTES
                            is Op.AddStamp -> STAMP_BODY_BYTES
                        }
                }
        val out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(MAGIC)
        out.putShort(VERSION.toShort())
        out.putInt(history.done.size)
        for (op in history.done) {
            when (op) {
                is Op.AddStroke -> putStroke(out, op.stroke)
                is Op.AddStamp -> putStamp(out, op.stamp)
            }
        }
        return out.array()
    }

    private fun putStroke(out: ByteBuffer, s: StrokeData) {
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

    private fun putStamp(out: ByteBuffer, s: StampData) {
        out.put(OP_STAMP.toByte())
        out.put(s.stamp.ordinal.toByte())
        out.putFloat(s.x)
        out.putFloat(s.y)
        out.putFloat(s.size)
    }

    fun decode(bytes: ByteArray): Outcome<History, DecodeError> {
        val r = Reader(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN))
        return when (val header = header(r)) {
            is Outcome.Ok -> ops(r, header.value)
            is Outcome.Err -> header
        }
    }

    private class Header(val version: Int, val count: Int)

    /** The magic, the version and the operation count. */
    private fun header(r: Reader): Outcome<Header, DecodeError> {
        if (r.remaining() < HEADER_BYTES || r.int() != MAGIC)
            return Outcome.Err(DecodeError.NOT_A_PAGE)
        val version = r.short() and U16
        if (version !in OLDEST_READABLE..VERSION) return Outcome.Err(DecodeError.UNKNOWN_VERSION)
        val count = r.int()
        if (count !in 0..History.MAX_OPS) return Outcome.Err(DecodeError.OUT_OF_RANGE)
        return Outcome.Ok(Header(version, count))
    }

    /** The header's operations, which must end exactly at the end of the bytes. */
    private fun ops(r: Reader, header: Header): Outcome<History, DecodeError> {
        val ops = ArrayList<Op>(minOf(header.count, r.remaining() / (1 + STAMP_BODY_BYTES)))
        var error: DecodeError? = null
        var i = 0
        while (error == null && i < header.count) {
            when (val op = op(r, header.version)) {
                is Outcome.Ok -> ops += op.value
                is Outcome.Err -> error = op.error
            }
            i += 1
        }
        val failed = error ?: if (r.remaining() != 0) DecodeError.TRAILING_BYTES else null
        return if (failed == null) Outcome.Ok(History(ops)) else Outcome.Err(failed)
    }

    private fun op(r: Reader, version: Int): Outcome<Op, DecodeError> {
        if (r.remaining() < 1) return Outcome.Err(DecodeError.TRUNCATED)
        return when (r.byte() and U8) {
            OP_STROKE -> stroke(r)
            OP_STAMP ->
                if (version >= OP_STAMP_SINCE) stamp(r) else Outcome.Err(DecodeError.OUT_OF_RANGE)
            else -> Outcome.Err(DecodeError.OUT_OF_RANGE)
        }
    }

    private const val OP_STAMP_SINCE = 2

    private fun stroke(r: Reader): Outcome<Op, DecodeError> {
        if (r.remaining() < STROKE_HEADER_BYTES) return Outcome.Err(DecodeError.TRUNCATED)
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

    private fun stamp(r: Reader): Outcome<Op, DecodeError> {
        if (r.remaining() < STAMP_BODY_BYTES) return Outcome.Err(DecodeError.TRUNCATED)
        val face = Stamp.entries.getOrNull(r.byte() and U8)
        val x = r.float()
        val y = r.float()
        val size = r.float()
        val stamp = face?.let { StampData(it, x, y, size) }
        if (stamp == null || !stamp.isSaveable()) return Outcome.Err(DecodeError.OUT_OF_RANGE)
        return Outcome.Ok(Op.AddStamp(stamp))
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
