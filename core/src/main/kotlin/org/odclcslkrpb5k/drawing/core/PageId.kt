package org.odclcslkrpb5k.drawing.core

/** A page's name on disk (BD-5): only [parse] and [next] make one. */
@JvmInline
value class PageId private constructor(val value: String) {
    companion object {
        private val SHAPE = Regex("p[0-9]{13}-[0-9]{1,6}")

        /** A page id read from outside (a directory name, an intent extra), or null. */
        fun parse(raw: String): PageId? = if (SHAPE.matches(raw)) PageId(raw) else null

        /** A new id from the wall clock and a counter, ordered by creation. */
        fun next(nowMillis: Long, counter: Int): PageId {
            val millis = nowMillis.coerceIn(0, MAX_MILLIS).toString().padStart(MILLIS_DIGITS, '0')
            return PageId("p$millis-${counter.coerceIn(0, MAX_COUNTER)}")
        }

        private const val MAX_MILLIS = 9_999_999_999_999L
        private const val MILLIS_DIGITS = 13
        private const val MAX_COUNTER = 999_999
    }
}
