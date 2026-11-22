package org.odclcslkrpb5k.drawing.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/** A small page of random line art: width, height and the line mask. */
internal data class Art(val width: Int, val height: Int, val lines: BooleanArray)

internal fun arbArt(): Arb<Art> = arbitrary {
    val width = Arb.int(1..24).bind()
    val height = Arb.int(1..24).bind()
    val density = Arb.int(0..70).bind()
    val coin = Arb.int(0..99)
    Art(width, height, BooleanArray(width * height) { coin.bind() < density })
}

/**
 * The reference: union-find over 4-neighbours, written independently of the flood. Returns each
 * pixel's component root (or -1 on a line).
 */
private fun reference(art: Art): IntArray {
    val parent = IntArray(art.lines.size) { it }
    fun find(p: Int): Int {
        var r = p
        while (parent[r] != r) r = parent[r]
        return r
    }
    for (y in 0 until art.height) for (x in 0 until art.width) {
        val p = y * art.width + x
        if (art.lines[p]) continue
        if (x + 1 < art.width && !art.lines[p + 1]) parent[find(p + 1)] = find(p)
        if (y + 1 < art.height && !art.lines[p + art.width]) parent[find(p + art.width)] = find(p)
    }
    return IntArray(art.lines.size) { if (art.lines[it]) -1 else find(it) }
}

private fun art(vararg rows: String): Art =
    Art(
        rows[0].length,
        rows.size,
        BooleanArray(rows.size * rows[0].length) {
            rows[it / rows[0].length][it % rows[0].length] == '#'
        },
    )

internal fun Art.regions(minPixels: Int = 1) = Regions.label(width, height, lines, minPixels)

class RegionsTest :
    StringSpec({
        "regions are exactly the reference's 4-connected paper areas" {
            checkAll(arbArt()) { art ->
                val regions = art.regions()
                val roots = reference(art)
                val labels = regions.labels()
                // Same region exactly when same component: label to root is a bijection.
                val rootOf = HashMap<Int, Int>()
                val labelOf = HashMap<Int, Int>()
                for (p in labels.indices) {
                    check((labels[p] == Regions.LINE) == art.lines[p])
                    if (art.lines[p]) continue
                    check(rootOf.getOrPut(labels[p]) { roots[p] } == roots[p])
                    check(labelOf.getOrPut(roots[p]) { labels[p] } == labels[p])
                }
                regions.count shouldBe rootOf.size
            }
        }

        "regions are numbered in the order their first pixel is met" {
            checkAll(arbArt()) { art ->
                val firsts = art.regions().labels().filter { it != Regions.LINE }.distinct()
                firsts shouldBe (1..firsts.size).toList()
            }
        }

        "areas smaller than the minimum become lines; the rest are kept whole" {
            checkAll(arbArt(), Arb.int(1..12)) { art, minPixels ->
                val labels = art.regions(minPixels).labels()
                val roots = reference(art)
                val sizes = roots.filter { it >= 0 }.groupingBy { it }.eachCount()
                for (p in labels.indices) {
                    val kept = !art.lines[p] && (sizes[roots[p]] ?: 0) >= minPixels
                    check((labels[p] != Regions.LINE) == kept)
                }
            }
        }

        "near is the pixel's own region off a line, and finds a region only within the radius" {
            checkAll(arbArt(), Arb.int(-2..26), Arb.int(-2..26), Arb.int(0..4)) { art, x, y, r ->
                val regions = art.regions()
                val found = regions.near(x, y, r)
                if (regions.at(x, y) != Regions.LINE) found shouldBe regions.at(x, y)
                val within =
                    (-r..r).any { dy ->
                        (-r..r).any { dx ->
                            dx * dx + dy * dy <= r * r && regions.at(x + dx, y + dy) == found
                        }
                    }
                if (found != Regions.LINE) check(within)
                val anyWithin =
                    (-r..r).any { dy ->
                        (-r..r).any { dx ->
                            dx * dx + dy * dy <= r * r && regions.at(x + dx, y + dy) != Regions.LINE
                        }
                    }
                (found != Regions.LINE) shouldBe anyWithin
            }
        }

        "sizes count every pixel once" {
            checkAll(arbArt()) { art ->
                val regions = art.regions()
                regions.sizes().sum() shouldBe art.width * art.height
                regions.sizes()[Regions.LINE] shouldBe art.lines.count { it }
            }
        }

        "a closed outline makes an inside and an outside, and only the outside is at the edge" {
            val regions = art(".......", ".#####.", ".#...#.", ".#####.", ".......").regions()
            regions.count shouldBe 2
            regions.atEdge().toList() shouldBe listOf(false, true, false)
        }

        "a one-pixel gap lets the inside leak into the outside" {
            art(".......", ".##.##.", ".#...#.", ".#####.", ".......").regions().count shouldBe 1
        }

        "a diagonal line one pixel wide still closes a shape" {
            val regions = art("...#...", "..#.#..", ".#...#.", "..#.#..", "...#...").regions()
            regions.count shouldBe 3
            regions.at(3, 2) shouldBe regions.at(2, 2)
            check(regions.at(3, 2) != regions.at(0, 0))
        }

        "a bad page size is refused" {
            shouldThrow<IllegalArgumentException> { Regions.label(2, 2, BooleanArray(3), 1) }
            shouldThrow<IllegalArgumentException> { Regions.label(2, 2, BooleanArray(4), 0) }
        }
    })
