package com.easyswitchcontroller.ui.settings

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

internal object LanguageMenuPlacement {
    fun position(anchor: IntRect, window: IntSize, direction: LayoutDirection,
        menu: IntSize, margin: Int, gap: Int): IntOffset {
        val starts = if (direction == LayoutDirection.Ltr)
            listOf(anchor.left, anchor.right - menu.width)
        else listOf(anchor.right - menu.width, anchor.left)
        val maxX = (window.width - menu.width - margin).coerceAtLeast(margin)
        val maxY = (window.height - menu.height - margin).coerceAtLeast(margin)
        val x = starts.firstOrNull { it in margin..maxX } ?: starts.first().coerceIn(margin, maxX)
        val below = anchor.bottom + gap
        val above = anchor.top - menu.height - gap
        val y = (if (below in margin..maxY) below else above).coerceIn(margin, maxY)
        return IntOffset(x, y)
    }
}
