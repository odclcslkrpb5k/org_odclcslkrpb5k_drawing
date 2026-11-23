package org.odclcslkrpb5k.drawing.core

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map

private const val MAX_POINTS = 40
private const val MAX_STROKES = 12
private const val MAX_MS = 3_600_000L
private const val MAX_REGION = 200

/** A finite float in [min, max]; Kotest's float edge cases include NaN and infinities. */
private fun finite(min: Float, max: Float): Arb<Float> =
    Arb.float(min, max, includeNaNs = false).filter { it.isFinite() }

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

/** The brushes a page of format 3 or older could hold: all but the eraser. */
val OLDER_BRUSHES: List<BrushKind> = BrushKind.entries - BrushKind.ERASER

fun arbStroke(kinds: List<BrushKind> = BrushKind.entries): Arb<StrokeData> =
    Arb.bind(Arb.element(kinds), Arb.int(), finite(0.5f, 200f), arbPoints()) { k, c, s, pts ->
        StrokeData(BrushSpec(k, Argb(c), s), pts)
    }

/** The stamps a page of format 4 or older could hold: the faces. */
val FACES: List<Stamp> = Stamp.entries.filter { it.isFace }

fun arbStamp(stamps: List<Stamp> = Stamp.entries): Arb<StampData> =
    Arb.bind(
        Arb.element(stamps),
        finite(0f, Page.WIDTH),
        finite(0f, Page.HEIGHT),
        finite(StampData.MIN_SIZE, StampData.MAX_SIZE),
    ) { face, x, y, size ->
        StampData(face, x, y, size)
    }

fun arbFill(): Arb<Op.Fill> =
    Arb.bind(Arb.int(1, MAX_REGION), Arb.int()) { region, color -> Op.Fill(region, Argb(color)) }

/** An operation; fills only when [fills], since only a page with a template has regions. */
fun arbOp(fills: Boolean = false): Arb<Op> =
    Arb.bind(arbStroke(), arbStamp(), arbFill(), Arb.int(0, 3)) { stroke, stamp, fill, pick ->
        when {
            pick == 0 -> Op.AddStamp(stamp)
            pick == 1 && fills -> fill
            else -> Op.AddStroke(stroke)
        }
    }

/** Blank pages and coloring pages, including the longest name a page can carry. */
val TEMPLATES: List<String?> = listOf(null, null, "star", "unicorn", "a", "z" + "9".repeat(31))

fun arbHistory(): Arb<History> =
    Arb.element(TEMPLATES).flatMap { template ->
        Arb.list(arbOp(fills = template != null), 0..MAX_STROKES).map {
            History(it, template = template)
        }
    }

/** A blank page of strokes without the eraser and faces only, as version 2 could hold. */
fun arbVersion2(): Arb<History> =
    Arb.list(
            Arb.bind(arbStroke(OLDER_BRUSHES), arbStamp(FACES), Arb.boolean()) { s, f, stamp ->
                if (stamp) Op.AddStamp(f) else Op.AddStroke(s)
            },
            0..MAX_STROKES,
        )
        .map { History(it) }

/** A page of strokes only, as version 1 could hold. */
fun arbStrokesOnly(): Arb<History> =
    Arb.list(arbStroke(OLDER_BRUSHES), 0..MAX_STROKES).map { strokes ->
        History(strokes.map { Op.AddStroke(it) })
    }
