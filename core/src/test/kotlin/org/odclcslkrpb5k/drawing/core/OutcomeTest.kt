package org.odclcslkrpb5k.drawing.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

class OutcomeTest :
    StringSpec({
        "an Ok keeps its value" {
            checkAll(Arb.int()) { n ->
                val outcome: Outcome<Int, String> = Outcome.Ok(n)
                check(outcome == Outcome.Ok(n))
            }
        }
    })
