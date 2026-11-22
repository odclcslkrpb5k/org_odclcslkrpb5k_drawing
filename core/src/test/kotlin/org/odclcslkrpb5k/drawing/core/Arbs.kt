package org.odclcslkrpb5k.drawing.core

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map

private const val MAX_POINTS = 40
private const val MAX_STROKES = 12
private const val MAX_MS = 3_600_000L

/** A finite float in [min, max]; Kotest's float edge cases include NaN and infinities. */
private fun finite(min: Float, max: Float): Arb<Float> =
    Arb.float(min, max).filter { it.isFinite() }

fun arbPoints(): Arb<List<InputPoint>> =
    Arb.list(
            Arb.bind(
                finite(-100f, Page.WIDTH + 100f),
                finite(-100f, Page.HEIGHT + 100f),
                Arb.long(0L, MAX_MS),
                finite(-1f, 1f),
            ) { x, y, t, p ->
                InputPoint(x, y, t, p)
            },
            0..MAX_POINTS,
        )
        .map { points ->
            val times = points.map { it.elapsedMillis }.sorted()
            points.mapIndexed { i, p -> p.copy(elapsedMillis = times[i]) }
        }

fun arbStroke(): Arb<StrokeData> =
    Arb.bind(Arb.enum<BrushKind>(), Arb.int(), finite(0.5f, 200f), arbPoints()) { k, c, s, pts ->
        StrokeData(BrushSpec(k, Argb(c), s), pts)
    }

fun arbHistory(): Arb<History> =
    Arb.list(arbStroke(), 0..MAX_STROKES).map { strokes ->
        History(strokes.map { Op.AddStroke(it) })
    }
