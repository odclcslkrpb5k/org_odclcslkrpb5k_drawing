package org.odclcslkrpb5k.drawing.templates

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import org.odclcslkrpb5k.drawing.core.Outcome
import org.odclcslkrpb5k.drawing.core.Regions

private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()

/** A full-size white page with the outline of a box drawn in black, optionally with a gap. */
private fun box(left: Int, top: Int, right: Int, bottom: Int, gap: Boolean = false): Raster {
    val w = PageRules.WIDTH
    val h = PageRules.HEIGHT
    val argb = IntArray(w * h) { WHITE }
    for (x in left..right) {
        argb[top * w + x] = BLACK
        argb[bottom * w + x] = BLACK
    }
    for (y in top..bottom) {
        argb[y * w + left] = BLACK
        argb[y * w + right] = BLACK
    }
    if (gap) argb[top * w + (left + right) / 2] = WHITE
    return Raster(w, h, argb)
}

private fun regions(raster: Raster) =
    Regions.label(raster.width, raster.height, lines(raster), PageRules.MIN_PIXELS)

private fun problemsOf(raster: Raster, expected: Int?) =
    problems("box", raster, regions(raster), expected)

class PagesTest :
    StringSpec({
        "the manifest gives each page its count, in order" {
            val parsed = Manifest.parse("# comment\nunicorn\t23\n\nstar\t11\n")
            parsed.shouldBeInstanceOf<Outcome.Ok<Map<String, Int>>>()
            parsed.value.toList() shouldBe listOf("unicorn" to 23, "star" to 11)
        }

        "a manifest line that is not a name and a positive count is refused, with its line" {
            for (bad in
                listOf(
                    "star 11",
                    "star\t0",
                    "star\t-1",
                    "star\tx",
                    "Star\t3",
                    "a\t1\t2",
                    "star\t1\nstar\t2",
                )) {
                val parsed = Manifest.parse(bad)
                parsed.shouldBeInstanceOf<Outcome.Err<String>>()
                parsed.error shouldContain "pages.tsv line"
            }
        }

        "dark pixels are lines and light ones paper, transparent ones counting as paper" {
            val raster = Raster(4, 1, intArrayOf(BLACK, WHITE, 0x00000000, 0xFF7F7F7F.toInt()))
            lines(raster).toList() shouldBe listOf(true, false, false, true)
        }

        "line art is black, as opaque as the page is dark" {
            checkAll(Arb.int(0..255)) { grey ->
                val pixel = (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
                lineArt(Raster(1, 1, intArrayOf(pixel)))[0] shouldBe ((255 - grey) shl 24)
            }
        }

        "a closed box with the count in the manifest breaks no rule" {
            problemsOf(box(400, 300, 900, 800), expected = 2) shouldBe emptyList()
        }

        "a gap in the outline changes the count, and the build names it" {
            val found = problemsOf(box(400, 300, 900, 800, gap = true), expected = 2)
            found.single() shouldContain "has 1 regions, pages.tsv says 2"
        }

        "a page missing from the manifest is named with its count" {
            problemsOf(box(400, 300, 900, 800), expected = null).single() shouldContain
                "not in pages.tsv (it has 2 regions)"
        }

        "a shape covering most of the page is refused" {
            val found = problemsOf(box(20, 20, 1890, 1170), expected = 2)
            found.single() shouldContain "covers more than 40%"
        }

        "an outline that cuts the paper around the picture in two is refused" {
            val w = PageRules.WIDTH
            val argb = IntArray(w * PageRules.HEIGHT) { WHITE }
            for (y in 0 until PageRules.HEIGHT) argb[y * w + w / 2] = BLACK
            val found = problemsOf(Raster(w, PageRules.HEIGHT, argb), expected = 2)
            found.any { it.contains("2 regions reach the page edge") } shouldBe true
        }

        "a page that is not the page size is refused" {
            val raster = Raster(10, 10, IntArray(100) { WHITE })
            val found = problems("small", raster, regions(raster), expected = 1)
            found.any { it.contains("is 10x10, not 1920x1200") } shouldBe true
        }
    })
