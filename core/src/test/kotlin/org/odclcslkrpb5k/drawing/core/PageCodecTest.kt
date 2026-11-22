package org.odclcslkrpb5k.drawing.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

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
                check(
                    PageCodec.decode(PageCodec.encode(withUndo)) ==
                        Outcome.Ok(History(withUndo.done))
                )
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

        "arbitrary bytes never throw" {
            checkAll(Arb.byteArray(Arb.int(0, 256), Arb.byte())) { bytes ->
                PageCodec.decode(bytes)
            }
        }

        "another version is refused by name" {
            val bytes = PageCodec.encode(History())
            bytes[4] = 2
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
