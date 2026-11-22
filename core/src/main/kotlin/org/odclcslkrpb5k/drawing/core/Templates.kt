package org.odclcslkrpb5k.drawing.core

/** Coloring pages by name: the name a page started from one records, and its asset files' names. */
object Templates {
    /** The longest name; a saved page stores its length in one byte. */
    const val MAX_NAME: Int = 32

    private val NAME = Regex("[a-z][a-z0-9-]{0,${MAX_NAME - 1}}")

    /**
     * Lower-case letters, digits and dashes, starting with a letter: safe as a file name anywhere.
     */
    fun isName(name: String): Boolean = NAME.matches(name)
}
