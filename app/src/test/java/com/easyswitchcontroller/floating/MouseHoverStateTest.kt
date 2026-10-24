package com.easyswitchcontroller.floating

import org.junit.Assert.*
import org.junit.Test

class MouseHoverStateTest {
    @Test fun exitClearsTheLastInsideCoordinateInsteadOfHitTestingItAgain() {
        val state = MouseHoverState<Any>()
        val window = Any()
        state.update(FloatingMenuGeometry.Point(40f, 50f), window)
        assertTrue(state.exit(window))
        assertNull(state.point)
    }

    @Test fun lateExitFromTheOldWindowKeepsTheNewWindowsHover() {
        val state = MouseHoverState<Any>()
        val old = Any(); val next = Any()
        val point = FloatingMenuGeometry.Point(70f, 60f)
        state.update(FloatingMenuGeometry.Point(40f, 50f), old)
        state.update(point, next)
        assertFalse(state.exit(old))
        assertEquals(point, state.point)
        assertTrue(state.exit(next))
        assertNull(state.point)
    }

    @Test fun removingAnUnrelatedWindowDoesNotEraseThePointer() {
        val state = MouseHoverState<Any>()
        val window = Any()
        state.update(FloatingMenuGeometry.Point(40f, 50f), window)
        assertFalse(state.removed(Any()))
        assertNotNull(state.point)
        assertTrue(state.removed(window))
        assertNull(state.point)
    }

    @Test fun anExitAlsoClearsAPositionWithoutAWindowOwner() {
        val state = MouseHoverState<Any>()
        state.update(FloatingMenuGeometry.Point(40f, 50f))
        assertTrue(state.exit(Any()))
        assertNull(state.point)
    }
}
