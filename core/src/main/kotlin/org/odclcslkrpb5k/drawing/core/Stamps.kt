package org.odclcslkrpb5k.drawing.core

/**
 * What a child can stamp: the emotion faces, then clip art (from format 5). Their order is their
 * saved number, so only append.
 */
enum class Stamp {
    HAPPY,
    SAD,
    ANGRY,
    SURPRISED,
    SILLY,
    LOVE,
    SLEEPY,
    SCARED,
    UNICORN,
    CROWN,
    RAINBOW,
    STAR,
    FLOWER,
    HEART,
    MICROPHONE,
    BUTTERFLY;

    /** Whether it is one of the emotion faces, the stamps every format since 2 can hold. */
    val isFace: Boolean
        get() = ordinal <= SCARED.ordinal
}

/** A placed stamp: which face, its center and its width, in page units. */
data class StampData(val stamp: Stamp, val x: Float, val y: Float, val size: Float) {
    companion object {
        const val MIN_SIZE: Float = 60f
        const val MAX_SIZE: Float = 900f
        const val DEFAULT_SIZE: Float = 220f
    }
}

/** Whether a stamp can be saved and read back: finite, on or near the page, and a sane size. */
fun StampData.isSaveable(): Boolean =
    x.isFinite() &&
        y.isFinite() &&
        x in -size..(Page.WIDTH + size) &&
        y in -size..(Page.HEIGHT + size) &&
        size in StampData.MIN_SIZE..StampData.MAX_SIZE
