package org.odclcslkrpb5k.drawing.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.nio.ByteBuffer
import java.nio.ByteOrder

private fun decoded(bytes: ByteArray): Regions? =
    when (val outcome = RegionsCodec.decode(bytes)) {
        is Outcome.Ok -> outcome.value
        is Outcome.Err -> null
    }

private const val HEADER_BYTES = 18
private const val RUN_BYTES = 10

/**
 * The same page written with one run of two or more pixels as two touching runs, or null when no
 * run is long enough. [pick] chooses which of the long runs.
 */
private fun splitRun(bytes: ByteArray, pick: Int): ByteArray? {
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val runs = buf.getInt(HEADER_BYTES - 4)
    val long = (0 until runs).filter { buf.getShort(HEADER_BYTES + it * RUN_BYTES + 4) >= 2 }
    if (long.isEmpty()) return null
    val at = HEADER_BYTES + long[pick % long.size] * RUN_BYTES
    val x = buf.getShort(at + 2).toInt()
    val length = buf.getShort(at + 4).toInt()
    val out = ByteBuffer.allocate(bytes.size + RUN_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    out.put(bytes, 0, at + RUN_BYTES)
    out.putShort(at + 4, 1)
    out.put(bytes, at, RUN_BYTES)
    out.putShort(at + RUN_BYTES + 2, (x + 1).toShort())
    out.putShort(at + RUN_BYTES + 4, (length - 1).toShort())
    out.put(bytes, at + RUN_BYTES, bytes.size - at - RUN_BYTES)
    out.putInt(HEADER_BYTES - 4, runs + 1)
    return out.array()
}

class RegionsCodecTest :
    StringSpec({
        "regions survive encoding with every label in place" {
            checkAll(arbArt(), Arb.int(1..6)) { art, minPixels ->
                val regions = art.regions(minPixels)
                val back = checkNotNull(decoded(RegionsCodec.encode(regions)))
                back.labels().toList() shouldBe regions.labels().toList()
                back.count shouldBe regions.count
            }
        }

        "a changed byte is refused or decodes to bytes that encode the same: one encoding each" {
            checkAll(arbArt(), Arb.int(0..10_000), Arb.byte()) { art, at, value ->
                val bytes = RegionsCodec.encode(art.regions())
                val changed = bytes.copyOf()
                changed[at % changed.size] = value
                val back = decoded(changed)
                if (back != null) RegionsCodec.encode(back).toList() shouldBe changed.toList()
            }
        }

        "a run split in two is refused: the same pixels may not be written another way" {
            checkAll(arbArt(), Arb.int(0..10_000)) { art, pick ->
                val bytes = RegionsCodec.encode(art.regions())
                val split = splitRun(bytes, pick)
                if (split != null) check(decoded(split) == null)
            }
        }

        "every truncation is refused" {
            checkAll(arbArt()) { art ->
                val bytes = RegionsCodec.encode(art.regions())
                for (n in 0 until bytes.size) check(decoded(bytes.copyOf(n)) == null)
            }
        }

        "trailing bytes are refused" {
            checkAll(arbArt()) { art ->
                val bytes = RegionsCodec.encode(art.regions())
                RegionsCodec.decode(bytes + 0)
                    .shouldBeInstanceOf<Outcome.Err<DecodeError>>()
                    .error shouldBe DecodeError.TRAILING_BYTES
            }
        }

        "random bytes never throw" {
            checkAll(Arb.byteArray(Arb.int(0..200), Arb.byte())) { bytes ->
                RegionsCodec.decode(bytes)
            }
        }

        "a region's runs cover exactly its pixels" {
            checkAll(arbArt()) { art ->
                val regions = art.regions()
                val labels = regions.labels()
                for (label in 0..regions.count + 1) {
                    val painted = BooleanArray(labels.size)
                    val runs = regions.runs(label)
                    for (i in runs.indices step 3) {
                        val start = runs[i] * regions.width + runs[i + 1]
                        for (p in start until start + runs[i + 2]) {
                            check(!painted[p])
                            painted[p] = true
                        }
                    }
                    val expected = label in 1..regions.count
                    for (p in labels.indices) check(painted[p] == (expected && labels[p] == label))
                }
            }
        }
    })
