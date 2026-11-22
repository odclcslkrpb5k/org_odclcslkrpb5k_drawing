package org.odclcslkrpb5k.drawing.core

import io.kotest.core.spec.style.StringSpec
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

        "the strokes are the done operations, in order" {
            checkAll(arbHistory()) { page ->
                check(page.strokes == page.done.map { (it as? Op.AddStroke)?.stroke })
            }
        }
    })
