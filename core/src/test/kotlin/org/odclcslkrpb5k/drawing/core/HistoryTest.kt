package org.odclcslkrpb5k.drawing.core

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

        "the strokes are the done operations, in order" {
            checkAll(arbHistory()) { page ->
                check(page.strokes == page.done.mapNotNull { (it as? Op.AddStroke)?.stroke })
                check(page.stamps == page.done.mapNotNull { (it as? Op.AddStamp)?.stamp })
            }
        }
    })
