package com.easyswitchcontroller.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MouseChordDetectorTest {
    private val detector = MouseChordDetector()
    private fun sample(buttons: Int, time: Long, device: Int = 1, display: Int = 0, x: Float = 100f, y: Float = 200f, cancelled: Boolean = false) =
        MouseChordDetector.Sample(device, display, time, buttons, x, y, cancelled)
    private fun event(buttons: Int, time: Long, device: Int = 1, display: Int = 0, x: Float = 100f, y: Float = 200f) =
        detector.accept(sample(buttons, time, device, display, x, y))

    @Test fun summonsOnlyAfterBothButtonsAreReleased() {
        assertNull(event(1, 0)); assertNull(event(3, 50)); assertNull(event(2, 70))
        assertEquals(MouseChordDetector.Summon(1, 0, 100f, 200f), event(0, 90))
        assertNull(event(0, 100))
    }

    @Test fun eitherPressAndReleaseOrderWorks() {
        for (first in listOf(1, 2)) for (remaining in listOf(1, 2)) {
            detector.reset()
            event(first, 0); event(3, 30); event(remaining, 40)
            assertEquals(MouseChordDetector.Summon(1, 0, 100f, 200f), event(0, 60))
        }
    }

    @Test fun aSingleReportCanContainBothButtons() {
        assertNull(event(3, 0))
        assertEquals(MouseChordDetector.Summon(1, 0, 100f, 200f), event(0, 20))
    }

    @Test fun singleClicksAndSequentialClicksNeverSummon() {
        for (buttons in listOf(1, 2, 1, 2)) {
            assertNull(event(buttons, 0)); assertNull(event(0, 20))
        }
    }

    @Test fun aLateSecondButtonIsNotSimultaneous() {
        event(1, 0); event(3, 151)
        assertNull(event(0, 180))
    }

    @Test fun theSimultaneousBoundaryIsInclusive() {
        event(1, 0); event(3, 150)
        assertEquals(MouseChordDetector.Summon(1, 0, 100f, 200f), event(0, 200))
    }

    @Test fun draggingBeforeSecondPressDoesNotSummon() {
        event(1, 0); event(1, 20, x = 120f); event(3, 30, x = 120f)
        assertNull(event(0, 50, x = 120f))
    }

    @Test fun aChordUsesThePointerPositionAtRelease() {
        event(1, 0); event(3, 30)
        assertEquals(MouseChordDetector.Summon(1, 2, 140f, 260f), run {
            detector.reset()
            event(3, 100, display = 2)
            event(0, 200, display = 2, x = 140f, y = 260f)
        })
    }

    @Test fun buttonsOnDifferentMiceDoNotCombine() {
        event(1, 0, device = 1); event(2, 20, device = 2)
        assertNull(event(0, 30, device = 1)); assertNull(event(0, 40, device = 2))
    }

    @Test fun displayChangeCancelsTheChord() {
        event(3, 0, display = 0)
        assertNull(event(0, 20, display = 1))
    }

    @Test fun cancelledRemovedAndDisabledInputsDoNotSummonLater() {
        event(3, 0); detector.accept(sample(3, 10, cancelled = true)); assertNull(event(0, 30))
        event(3, 40); detector.removeDevice(1); assertNull(event(0, 60))
        event(3, 70); detector.enabled = false; detector.enabled = true; assertNull(event(0, 90))
    }

    @Test fun staleAndReversedEventsDoNotSummon() {
        event(3, 0); assertNull(event(0, 5_001))
        event(1, 200); event(3, 210); assertNull(event(0, 190))
    }

    @Test fun anotherButtonOrInvalidCoordinatesCancelTheChord() {
        event(3, 0); event(7, 10); assertNull(event(0, 20))
        event(3, 30); event(3, 40, x = Float.NaN); assertNull(event(0, 50))
    }

    @Test fun repeatedReportsAndRepeatedSummonsRemainIndependentOfVisibility() {
        repeat(30) { index ->
            val start = index * 100L
            event(1, start); event(3, start + 20); event(3, start + 30)
            assertEquals(MouseChordDetector.Summon(1, 0, 100f, 200f), event(0, start + 40))
            assertNull(event(0, start + 50))
        }
    }
}
