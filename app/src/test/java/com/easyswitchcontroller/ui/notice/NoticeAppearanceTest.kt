package com.easyswitchcontroller.ui.notice

import org.junit.Assert.*
import org.junit.Test

class NoticeAppearanceTest {
    @Test fun defaultsKeepTheExistingBackgroundAndEnableToasts() {
        val appearance = NoticeAppearance()
        assertTrue(appearance.enabled)
        assertEquals(0xF0303030.toInt(), appearance.backgroundColor)
        assertEquals(1f, appearance.opacity, 0f)
    }

    @Test fun transparencyScalesTheWholeToastWithoutApplyingTwiceToTheBackground() {
        assertEquals(.5f, NoticeAppearance(transparency = 50).opacity, 0f)
        assertEquals(0f, NoticeAppearance(transparency = 100).opacity, 0f)
        var previousOpacity = 1f
        for (value in 0..100) {
            val appearance = NoticeAppearance(transparency = value)
            assertEquals(0xF0303030.toInt(), appearance.backgroundColor)
            assertTrue(appearance.opacity <= previousOpacity)
            previousOpacity = appearance.opacity
        }
    }

    @Test fun outOfRangeTransparencyCannotWrapTheAlphaChannel() {
        assertEquals(1f, NoticeAppearance(transparency = Int.MIN_VALUE).opacity, 0f)
        assertEquals(0f, NoticeAppearance(transparency = Int.MAX_VALUE).opacity, 0f)
    }
}
