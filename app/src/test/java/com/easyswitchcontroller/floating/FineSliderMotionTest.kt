package com.easyswitchcontroller.floating

import org.junit.Assert.assertEquals
import org.junit.Test

class FineSliderMotionTest {
    @Test fun speedSliderUsesBothEndpointsAndRoundTripsEveryAllowedPercent() {
        assertEquals(25, FineSliderMotion.valueAt(0f, 25, 300))
        assertEquals(300, FineSliderMotion.valueAt(1f, 25, 300))
        for (value in 25..300) {
            assertEquals(value, FineSliderMotion.valueAt(FineSliderMotion.fraction(value, 25, 300), 25, 300))
        }
        for (maximum in listOf(100, 200)) {
            assertEquals(.5f, FineSliderMotion.fraction(maximum / 2, 0, maximum), .0001f)
            assertEquals(maximum / 2, FineSliderMotion.valueAt(.5f, 0, maximum))
        }
    }

    @Test fun oneAndAHalfTimesTheTrackTravelChangesTheWholeRange() {
        assertEquals(1f, FineSliderMotion.move(0f, 450f, 300f), .0001f)
        assertEquals(.5f, FineSliderMotion.move(0f, 225f, 300f), .0001f)
    }
    @Test fun reversingDirectionKeepsTheCurrentPositionAndClampsAtBothEnds() {
        val advanced = FineSliderMotion.move(.5f, 90f, 300f)
        assertEquals(.5f, FineSliderMotion.move(advanced, -90f, 300f), .0001f)
        assertEquals(0f, FineSliderMotion.move(.1f, -450f, 300f), .0001f)
        assertEquals(1f, FineSliderMotion.move(.9f, 450f, 300f), .0001f)
    }
}
