package org.odclcslkrpb5k.drawing.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

class BackupBudgetTest :
    StringSpec({
        "the warning comes before the limit, and over the limit is over" {
            BackupBudget.state(0) shouldBe BackupState.FINE
            BackupBudget.state(BackupBudget.WARN - 1) shouldBe BackupState.FINE
            BackupBudget.state(BackupBudget.WARN) shouldBe BackupState.NEARLY_FULL
            BackupBudget.state(BackupBudget.LIMIT) shouldBe BackupState.NEARLY_FULL
            BackupBudget.state(BackupBudget.LIMIT + 1) shouldBe BackupState.OVER
        }

        "more bytes never look better" {
            checkAll(Arb.long(0, 60L * 1024 * 1024), Arb.long(0, 1024L * 1024)) { b, more ->
                check(BackupBudget.state(b + more) >= BackupBudget.state(b))
            }
        }

        "megabytes are shown rounded up, to a tenth" {
            BackupBudget.megabytes(0) shouldBe "0.0"
            BackupBudget.megabytes(1) shouldBe "0.1"
            BackupBudget.megabytes(1024L * 1024) shouldBe "1.0"
            BackupBudget.megabytes(BackupBudget.WARN) shouldBe "20.0"
            BackupBudget.megabytes(BackupBudget.WARN + 1) shouldBe "20.1"
        }
    })
