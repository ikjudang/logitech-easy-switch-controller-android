package com.easyswitchcontroller.floating

import kotlin.math.roundToInt

internal object FineSliderMotion {
    const val TRAVEL_MULTIPLIER = 1.5f
    fun move(fraction: Float, deltaPixels: Float, trackPixels: Float): Float =
        (fraction + deltaPixels / (trackPixels.coerceAtLeast(1f) * TRAVEL_MULTIPLIER)).coerceIn(0f, 1f)

    fun fraction(value: Int, minimum: Int, maximum: Int): Float =
        ((value.coerceIn(minimum, maximum) - minimum).toFloat() / (maximum - minimum)).coerceIn(0f, 1f)

    fun valueAt(fraction: Float, minimum: Int, maximum: Int): Int =
        (minimum + fraction.coerceIn(0f, 1f) * (maximum - minimum)).roundToInt().coerceIn(minimum, maximum)
}
