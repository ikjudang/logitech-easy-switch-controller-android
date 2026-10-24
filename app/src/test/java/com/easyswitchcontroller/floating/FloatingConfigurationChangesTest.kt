package com.easyswitchcontroller.floating

import android.content.pm.ActivityInfo
import org.junit.Assert.*
import org.junit.Test

class FloatingConfigurationChangesTest {
    @Test fun inputAttachDetachAndDuplicateChangesKeepTheWindows() {
        val input = listOf(ActivityInfo.CONFIG_KEYBOARD, ActivityInfo.CONFIG_KEYBOARD_HIDDEN,
            ActivityInfo.CONFIG_NAVIGATION)
        for (selection in 0 until (1 shl input.size)) {
            val changes = input.indices.filter { selection and (1 shl it) != 0 }
                .fold(0) { mask, index -> mask or input[index] }
            assertFalse("Input-only change $changes", FloatingConfigurationChanges.requiresRebuild(changes))
        }
    }

    @Test fun displayAndResourceChangesStillRebuildEvenWithInputChanges() {
        val input = ActivityInfo.CONFIG_KEYBOARD or ActivityInfo.CONFIG_KEYBOARD_HIDDEN or ActivityInfo.CONFIG_NAVIGATION
        val visual = listOf(ActivityInfo.CONFIG_ORIENTATION, ActivityInfo.CONFIG_SCREEN_SIZE,
            ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE, ActivityInfo.CONFIG_DENSITY,
            ActivityInfo.CONFIG_FONT_SCALE, ActivityInfo.CONFIG_LOCALE, ActivityInfo.CONFIG_LAYOUT_DIRECTION,
            ActivityInfo.CONFIG_UI_MODE, ActivityInfo.CONFIG_COLOR_MODE)
        for (change in visual) {
            assertTrue("Visual/resource change $change", FloatingConfigurationChanges.requiresRebuild(change))
            assertTrue("Mixed input and visual change $change", FloatingConfigurationChanges.requiresRebuild(change or input))
        }
    }

    @Test fun unknownAndHighBitsAreNotDiscarded() {
        val input = ActivityInfo.CONFIG_KEYBOARD or ActivityInfo.CONFIG_KEYBOARD_HIDDEN or ActivityInfo.CONFIG_NAVIGATION
        for (bit in 0..31) {
            val change = 1 shl bit
            if (change and input == 0) {
                assertTrue("Unmasked bit $bit", FloatingConfigurationChanges.requiresRebuild(change or input))
            }
        }
    }
}
