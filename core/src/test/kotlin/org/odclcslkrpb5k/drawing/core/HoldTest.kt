package org.odclcslkrpb5k.drawing.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

private val RULE = HoldRule(fingers = 2, holdMs = 3_000, slop = 20f)

/** One event in a touch; times only grow, as a touch's do. */
private sealed interface Ev {
    data class Down(val id: Int, val x: Float, val y: Float) : Ev

    data class Move(val id: Int, val x: Float, val y: Float) : Ev

    data class Up(val id: Int) : Ev

    data class Wait(val ms: Long) : Ev
}

private val coord = Arb.float(0f, 1_000f, includeNaNs = false)
private val pointer = Arb.int(0, 3)

private val arbEv: Arb<Ev> =
    Arb.choice(
        pointer.map { Ev.Up(it) },
        Arb.long(0, 4_000).map { Ev.Wait(it) },
        Arb.constant(Ev.Wait(1)),
        pointer.map { Ev.Down(it, 100f, 100f) },
        pointer.map { Ev.Move(it, 100f, 100f) },
    )

/** Plays [events] from a fresh hold, counting how often it opened. */
private fun play(events: List<Ev>, start: Hold = Hold(RULE)): Pair<Hold, Int> {
    var hold = start
    var t = 0L
    var opens = 0
    for (e in events) {
        hold =
            when (e) {
                is Ev.Down -> hold.down(e.id, e.x, e.y, t)
                is Ev.Move -> hold.move(e.id, e.x, e.y)
                is Ev.Up -> hold.up(e.id)
                is Ev.Wait -> hold.also { t += e.ms }
            }
        val (next, opened) = hold.tick(t)
        hold = next
        if (opened) opens += 1
    }
    return hold to opens
}

class HoldTest :
    StringSpec({
        "two fingers held still for the whole time open it, once" {
            checkAll(coord, coord, Arb.long(3_000, 100_000)) { x, y, wait ->
                val events =
                    listOf(
                        Ev.Down(0, x, y),
                        Ev.Down(1, x + 50, y),
                        Ev.Move(0, x + 10, y + 10),
                        Ev.Wait(wait),
                        Ev.Wait(wait),
                    )
                play(events).second shouldBe 1
            }
        }

        "a hold let go a moment too soon does not open" {
            checkAll(Arb.long(0, 2_999)) { wait ->
                val events = listOf(Ev.Down(0, 0f, 0f), Ev.Down(1, 50f, 0f), Ev.Wait(wait))
                play(events).second shouldBe 0
            }
        }

        "one finger, however long, never opens it" {
            checkAll(Arb.long(0, 1_000_000)) { wait ->
                play(listOf(Ev.Down(0, 0f, 0f), Ev.Wait(wait))).second shouldBe 0
            }
        }

        "three fingers held still never open it (a palm, not a parent)" {
            checkAll(Arb.long(3_000, 100_000)) { wait ->
                val events =
                    listOf(
                        Ev.Down(0, 0f, 0f),
                        Ev.Down(1, 50f, 0f),
                        Ev.Down(2, 90f, 0f),
                        Ev.Wait(wait),
                    )
                play(events).second shouldBe 0
            }
        }

        "a third finger spoils the touch, even once it lifts again" {
            checkAll(Arb.long(3_000, 100_000)) { wait ->
                val events =
                    listOf(
                        Ev.Down(0, 0f, 0f),
                        Ev.Down(1, 50f, 0f),
                        Ev.Down(2, 90f, 0f),
                        Ev.Up(2),
                        Ev.Wait(wait),
                    )
                play(events).second shouldBe 0
            }
        }

        "a finger moved past the slop spoils the touch until every finger lifts" {
            checkAll(Arb.float(20.5f, 500f, includeNaNs = true), Arb.long(3_000, 100_000)) {
                dx,
                wait ->
                val spoiled =
                    listOf(Ev.Down(0, 0f, 0f), Ev.Down(1, 50f, 0f), Ev.Move(1, 50f + dx, 0f))
                play(spoiled + Ev.Wait(wait)).second shouldBe 0
                val again =
                    spoiled +
                        listOf(Ev.Up(0), Ev.Up(1), Ev.Down(0, 0f, 0f), Ev.Down(1, 9f, 9f)) +
                        Ev.Wait(wait)
                play(again).second shouldBe 1
            }
        }

        "a move to a position that is not a number spoils the touch" {
            for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                val events =
                    listOf(
                        Ev.Down(0, 0f, 0f),
                        Ev.Down(1, 50f, 0f),
                        Ev.Move(1, bad, 0f),
                        Ev.Wait(9_000),
                    )
                play(events).second shouldBe 0
            }
        }

        "a finger lifted and put back does not restart a hold within the same touch" {
            val events =
                listOf(
                    Ev.Down(0, 0f, 0f),
                    Ev.Down(1, 50f, 0f),
                    Ev.Up(1),
                    Ev.Down(1, 50f, 0f),
                    Ev.Wait(10_000),
                )
            play(events).second shouldBe 0
        }

        "any touch opens at most once, and only after the rule's time with its fingers down" {
            checkAll(Arb.list(arbEv, 0..40)) { events ->
                // Split into touches: each ends when the last finger lifts.
                var hold = Hold(RULE)
                var t = 0L
                var opensThisTouch = 0
                for (e in events) {
                    hold =
                        when (e) {
                            is Ev.Down -> hold.down(e.id, e.x, e.y, t)
                            is Ev.Move -> hold.move(e.id, e.x, e.y)
                            is Ev.Up -> hold.up(e.id)
                            is Ev.Wait -> hold.also { t += e.ms }
                        }
                    if (hold.starts.isEmpty()) opensThisTouch = 0
                    val start = hold.since
                    val (next, opened) = hold.tick(t)
                    if (opened) {
                        opensThisTouch += 1
                        check(hold.starts.size == RULE.fingers && !hold.spoiled)
                        check(start != null && t - start >= RULE.holdMs)
                    }
                    check(opensThisTouch <= 1)
                    check(next.progress(t) in 0f..1f)
                    hold = next
                }
            }
        }

        "progress runs from nothing to whole over the hold" {
            val held = Hold(RULE).down(0, 0f, 0f, 1_000).down(1, 9f, 9f, 1_000)
            held.progress(1_000) shouldBe 0f
            held.progress(2_500) shouldBe 0.5f
            held.progress(9_999) shouldBe 1f
            Hold(RULE).progress(9_999) shouldBe 0f
        }

        "a rule that cannot be held is refused" {
            shouldThrow<IllegalArgumentException> { HoldRule(0, 1, 0f) }
            shouldThrow<IllegalArgumentException> { HoldRule(1, 0, 0f) }
            shouldThrow<IllegalArgumentException> { HoldRule(1, 1, -1f) }
        }
    })
