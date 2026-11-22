package org.odclcslkrpb5k.drawing.core

/** The result of an operation that can fail in an expected way (ER-1). */
sealed interface Outcome<out T, out E> {
    data class Ok<out T>(val value: T) : Outcome<T, Nothing>

    data class Err<out E>(val error: E) : Outcome<Nothing, E>
}
