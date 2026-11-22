package org.odclcslkrpb5k.drawing.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll

class HistoryTest :
    StringSpec({
        "undo after an operation gives back the page before it" {
            checkAll(arbHistory(), arbStroke()) { page, stroke ->
                check(page.apply(Op.AddStroke(stroke)).undo().done == page.done)
            }
        }

        "redo after undo restores the page" {
            checkAll(arbHistory()) { page -> check(page.undo().redo().done == page.done) }
        }

        "undoing everything leaves an empty page, and undo on it changes nothing" {
            checkAll(arbHistory()) { page ->
                var h = page
                repeat(page.done.size) { h = h.undo() }
                check(h.strokes.isEmpty() && !h.canUndo && h.undo() == h)
            }
        }

        "a new operation clears what could be redone" {
            checkAll(arbHistory(), arbStroke()) { page, stroke ->
                check(!page.undo().apply(Op.AddStroke(stroke)).canRedo)
            }
        }

        "moving the stamp just placed replaces it, so one undo takes it away" {
            checkAll(arbHistory(), arbStamp(), arbStamp()) { page, placed, moved ->
                val after = page.apply(Op.AddStamp(placed)).moveStamp(placed, moved)
                after.stamps.last() shouldBe moved
                after.undo().done shouldBe page.done
            }
        }

        "a move never overwrites an operation that is not the stamp being moved" {
            checkAll(arbHistory(), arbStamp(), arbStamp(), arbStroke()) {
                page,
                placed,
                moved,
                stroke ->
                val strokeSince = page.apply(Op.AddStamp(placed)).apply(Op.AddStroke(stroke))
                strokeSince.moveStamp(placed, moved) shouldBe strokeSince
                val undoneSince = page.apply(Op.AddStamp(placed)).undo()
                undoneSince.moveStamp(placed, moved) shouldBe undoneSince
            }
        }

        "undo, redo and every change keep the coloring page" {
            checkAll(arbHistory(), arbOp()) { page, op ->
                page.undo().template shouldBe page.template
                page.undo().redo().template shouldBe page.template
                page.apply(op).template shouldBe page.template
                page.saved().template shouldBe page.template
            }
        }

        "a region filled again shows its newest color, and undo brings back the one before" {
            val first = Op.Fill(3, Argb(1))
            val second = Op.Fill(3, Argb(2))
            val page = History(template = "star").apply(first).apply(second)
            page.fills shouldBe mapOf(3 to Argb(2))
            page.undo().fills shouldBe mapOf(3 to Argb(1))
        }

        "a page names only coloring pages that are names" {
            shouldThrow<IllegalArgumentException> { History(template = "Star") }
        }

        "a fill cannot be on a page with no coloring page, done or undone" {
            val fill = Op.Fill(1, Argb(0))
            shouldThrow<IllegalArgumentException> { History(listOf(fill)) }
            shouldThrow<IllegalArgumentException> { History(undone = listOf(fill)) }
            shouldThrow<IllegalArgumentException> { History().apply(fill) }
        }

        "the strokes are the done operations, in order" {
            checkAll(arbHistory()) { page ->
                check(page.strokes == page.done.mapNotNull { (it as? Op.AddStroke)?.stroke })
                check(page.stamps == page.done.mapNotNull { (it as? Op.AddStamp)?.stamp })
            }
        }
    })
