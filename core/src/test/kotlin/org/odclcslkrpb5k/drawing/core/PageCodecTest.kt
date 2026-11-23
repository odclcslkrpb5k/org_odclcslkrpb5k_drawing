package org.odclcslkrpb5k.drawing.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * The bytes version 1 or 2 wrote for the same page: no template byte (so the page must have no
 * template), and the older version number.
 */
private fun asOlderVersion(bytes: ByteArray, version: Int): ByteArray {
    check(bytes[6] == 0.toByte()) { "a page with a template has no older form" }
    val older = bytes.copyOfRange(0, 6) + bytes.copyOfRange(7, bytes.size)
    older[4] = version.toByte()
    return older
}

class PageCodecTest :
    StringSpec({
        "a page decodes to the page that was encoded" {
            checkAll(arbHistory()) { page ->
                PageCodec.decode(PageCodec.encode(page)) shouldBe Outcome.Ok(page)
            }
        }

        "undone operations are not saved" {
            checkAll(arbHistory()) { page ->
                val withUndo = page.undo()
                check(PageCodec.decode(PageCodec.encode(withUndo)) == Outcome.Ok(withUndo.saved()))
            }
        }

        "every truncation of a saved page is an error, never an exception" {
            checkAll(arbHistory()) { page ->
                val bytes = PageCodec.encode(page)
                for (n in 0 until bytes.size) {
                    PageCodec.decode(bytes.copyOf(n)).shouldBeInstanceOf<Outcome.Err<DecodeError>>()
                }
            }
        }

        "a saved page with bytes after it is an error" {
            checkAll(arbHistory()) { page ->
                val decoded = PageCodec.decode(PageCodec.encode(page) + byteArrayOf(0))
                decoded shouldBe Outcome.Err(DecodeError.TRAILING_BYTES)
            }
        }

        "a stroke that is not saveable is refused by the codec, never saved unreadable" {
            val p = InputPoint(1f, 1f, 0L, -1f)
            val bad =
                listOf(
                    p.copy(x = Float.NaN),
                    p.copy(y = Float.POSITIVE_INFINITY),
                    p.copy(elapsedMillis = -1L),
                )
            for (point in bad) {
                val stroke = StrokeData(BrushSpec(BrushKind.PEN, Argb(0), 2f), listOf(point))
                stroke.isSaveable() shouldBe false
                PageCodec.decode(PageCodec.encode(History(listOf(Op.AddStroke(stroke))))) shouldBe
                    Outcome.Err(DecodeError.OUT_OF_RANGE)
            }
        }

        "every generated stroke is saveable" {
            checkAll(arbStroke()) { it.isSaveable() shouldBe true }
        }

        "every generated stamp is saveable" {
            checkAll(arbStamp()) { it.isSaveable() shouldBe true }
        }

        "a version 1 file, written byte by byte as version 1 wrote it, opens" {
            val v1 =
                java.nio.ByteBuffer.allocate(4 + 2 + 4 + 1 + 1 + 4 + 4 + 4 + (4 + 4 + 8 + 4))
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .putInt(0x5752444F)
                    .putShort(1)
                    .putInt(1)
                    .put(1)
                    .put(BrushKind.MARKER.ordinal.toByte())
                    .putInt(0xFF336699.toInt())
                    .putFloat(12f)
                    .putInt(1)
                    .putFloat(10f)
                    .putFloat(20f)
                    .putLong(5L)
                    .putFloat(-1f)
                    .array()
            val stroke =
                StrokeData(
                    BrushSpec(BrushKind.MARKER, Argb(0xFF336699.toInt()), 12f),
                    listOf(InputPoint(10f, 20f, 5L, -1f)),
                )
            PageCodec.decode(v1) shouldBe Outcome.Ok(History(listOf(Op.AddStroke(stroke))))
        }

        "a page saved by version 1, strokes only, still opens" {
            checkAll(arbStrokesOnly()) { page ->
                PageCodec.decode(asOlderVersion(PageCodec.encode(page), 1)) shouldBe
                    Outcome.Ok(page)
            }
        }

        "a stamp in a version 1 page is refused, since version 1 had none" {
            val stamp = StampData(Stamp.HAPPY, 100f, 100f, StampData.DEFAULT_SIZE)
            val bytes = asOlderVersion(PageCodec.encode(History(listOf(Op.AddStamp(stamp)))), 1)
            PageCodec.decode(bytes) shouldBe Outcome.Err(DecodeError.OUT_OF_RANGE)
        }

        "a page saved by version 2, strokes and stamps, still opens" {
            checkAll(arbVersion2()) { page ->
                PageCodec.decode(asOlderVersion(PageCodec.encode(page), 2)) shouldBe
                    Outcome.Ok(page)
            }
        }

        "brushes and stamps keep the numbers pages are saved with" {
            BrushKind.entries.associate { it.name to it.ordinal } shouldBe
                mapOf("PEN" to 0, "MARKER" to 1, "HIGHLIGHTER" to 2, "ERASER" to 3)
            Stamp.entries.associate { it.name to it.ordinal } shouldBe
                mapOf(
                    "HAPPY" to 0,
                    "SAD" to 1,
                    "ANGRY" to 2,
                    "SURPRISED" to 3,
                    "SILLY" to 4,
                    "LOVE" to 5,
                    "SLEEPY" to 6,
                    "SCARED" to 7,
                    "UNICORN" to 8,
                    "CROWN" to 9,
                    "RAINBOW" to 10,
                    "STAR" to 11,
                    "FLOWER" to 12,
                    "HEART" to 13,
                    "MICROPHONE" to 14,
                    "BUTTERFLY" to 15,
                )
        }

        "an eraser stroke in a version 3 page is refused, since version 3 had none" {
            val p = InputPoint(1f, 1f, 0L, -1f)
            val eraser = StrokeData(BrushSpec(BrushKind.ERASER, Argb(0), 48f), listOf(p))
            val bytes = PageCodec.encode(History(listOf(Op.AddStroke(eraser))))
            bytes[4] = 3
            PageCodec.decode(bytes) shouldBe Outcome.Err(DecodeError.OUT_OF_RANGE)
        }

        "a clip-art stamp in a version 4 page is refused, since version 4 had none" {
            checkAll(arbStamp(Stamp.entries - FACES)) { stamp ->
                val bytes = PageCodec.encode(History(listOf(Op.AddStamp(stamp))))
                bytes[4] = 4
                PageCodec.decode(bytes) shouldBe Outcome.Err(DecodeError.OUT_OF_RANGE)
            }
        }

        "a version 4 page of faces still opens" {
            checkAll(arbStamp(FACES)) { stamp ->
                val page = History(listOf(Op.AddStamp(stamp)))
                val bytes = PageCodec.encode(page)
                bytes[4] = 4
                PageCodec.decode(bytes) shouldBe Outcome.Ok(page)
            }
        }

        "a fill on a page with no coloring page is refused" {
            // History cannot hold one, so the bytes are a page with coloring page "a" and one fill,
            // with the name taken out: length byte 0, and the name's one byte removed.
            val named = PageCodec.encode(History(listOf(Op.Fill(1, Argb(0))), template = "a"))
            val bytes = named.copyOfRange(0, 7) + named.copyOfRange(8, named.size)
            bytes[6] = 0
            PageCodec.decode(bytes) shouldBe Outcome.Err(DecodeError.OUT_OF_RANGE)
        }

        "a version 2 file, written byte by byte as version 2 wrote it, opens" {
            val v2 =
                java.nio.ByteBuffer.allocate(4 + 2 + 4 + 1 + 1 + 4 + 4 + 4)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .putInt(0x5752444F)
                    .putShort(2)
                    .putInt(1)
                    .put(2)
                    .put(Stamp.LOVE.ordinal.toByte())
                    .putFloat(300f)
                    .putFloat(400f)
                    .putFloat(250f)
                    .array()
            PageCodec.decode(v2) shouldBe
                Outcome.Ok(History(listOf(Op.AddStamp(StampData(Stamp.LOVE, 300f, 400f, 250f)))))
        }

        "a fill of region 0, the lines, is refused" {
            val bytes = PageCodec.encode(History(listOf(Op.Fill(0, Argb(0))), template = "star"))
            PageCodec.decode(bytes) shouldBe Outcome.Err(DecodeError.OUT_OF_RANGE)
        }

        "a coloring page name that is not a name is refused" {
            val bytes = PageCodec.encode(History(template = "star"))
            bytes[7] = 'S'.code.toByte()
            PageCodec.decode(bytes) shouldBe Outcome.Err(DecodeError.OUT_OF_RANGE)
        }

        "a stamp that is not saveable is refused" {
            for (bad in listOf(Float.NaN, StampData.MAX_SIZE * 2, StampData.MIN_SIZE / 2)) {
                val stamp = StampData(Stamp.LOVE, 100f, 100f, bad)
                PageCodec.decode(PageCodec.encode(History(listOf(Op.AddStamp(stamp))))) shouldBe
                    Outcome.Err(DecodeError.OUT_OF_RANGE)
            }
        }

        "arbitrary bytes never throw" {
            checkAll(Arb.byteArray(Arb.int(0, 256), Arb.byte())) { bytes ->
                PageCodec.decode(bytes)
            }
        }

        "another version is refused by name" {
            val bytes = PageCodec.encode(History())
            bytes[4] = (PageCodec.VERSION + 1).toByte()
            PageCodec.decode(bytes) shouldBe Outcome.Err(DecodeError.UNKNOWN_VERSION)
        }

        "a stroke whose time runs backwards is refused" {
            val p = InputPoint(1f, 1f, 10L, -1f)
            val stroke =
                StrokeData(
                    BrushSpec(BrushKind.PEN, Argb(0), 2f),
                    listOf(p, p.copy(elapsedMillis = 9L)),
                )
            val bytes = PageCodec.encode(History(listOf(Op.AddStroke(stroke))))
            PageCodec.decode(bytes) shouldBe Outcome.Err(DecodeError.OUT_OF_RANGE)
        }
    })
