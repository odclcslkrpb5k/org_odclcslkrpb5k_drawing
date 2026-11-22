package org.odclcslkrpb5k.drawing.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class PageIdTest :
    StringSpec({
        "a new id parses back to itself" {
            checkAll(Arb.long(), Arb.int()) { t, n ->
                val id = PageId.next(t, n)
                PageId.parse(id.value) shouldBe id
            }
        }

        "a path-like or empty name is not a page id" {
            for (raw in
                listOf("", "..", "../p0000000000001-1", "p0000000000001-1/x", "P0000000000001-1")) {
                PageId.parse(raw) shouldBe null
            }
            checkAll(Arb.string()) { raw ->
                val id = PageId.parse(raw)
                if (id != null) id.value shouldBe raw
            }
        }
    })
