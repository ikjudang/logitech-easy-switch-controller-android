package com.easyswitchcontroller.ui.settings

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageMenuPlacementTest {
    private val menu = IntSize(112, 80)
    private val window = IntSize(360, 640)
    private fun position(anchor: IntRect, direction: LayoutDirection = LayoutDirection.Ltr,
        viewport: IntSize = window) = LanguageMenuPlacement.position(anchor, viewport, direction, menu, 8, 2)

    @Test fun opensBelowWithTheRequestedGap() {
        assertEquals(IntOffset(120, 134), position(IntRect(120, 100, 200, 132)))
    }

    @Test fun opensAboveWhenThereIsNotEnoughSpaceBelow() {
        val anchor = IntRect(220, 590, 300, 622)
        val result = position(anchor)
        assertEquals(2, anchor.top - result.y - menu.height)
    }

    @Test fun usesTheSpaceBelowUntilTheWholeMenuNoLongerFits() {
        assertEquals(552, position(IntRect(120, 518, 200, 550)).y)
        assertEquals(437, position(IntRect(120, 519, 200, 551)).y)
    }

    @Test fun rightEdgeAlignsTheMenuInsideTheWindow() {
        assertEquals(238, position(IntRect(300, 100, 350, 132)).x)
    }

    @Test fun rightToLeftLayoutsPreferTheOppositeAnchorEdge() {
        assertEquals(88, position(IntRect(120, 100, 200, 132), LayoutDirection.Rtl).x)
    }

    @Test fun aShortViewportKeepsTheMenuInsideTheWindow() {
        assertEquals(8, position(IntRect(120, 40, 200, 72), viewport = IntSize(360, 120)).y)
    }
}
