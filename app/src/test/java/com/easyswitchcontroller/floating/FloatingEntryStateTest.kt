package com.easyswitchcontroller.floating

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingEntryStateTest {
    @Test fun disabledToggleNeverOffersSummon() {
        for (permission in listOf(false, true)) for (size in listOf(0, 100, 200)) {
            assertEquals(FloatingEntryState.OFF, FloatingEntryState.resolve(false, permission, size, 0))
        }
    }
    @Test fun revokedOverlayPermissionOpensTheApp() {
        assertEquals(FloatingEntryState.NEEDS_PERMISSION, FloatingEntryState.resolve(true, false, 100, 0))
    }
    @Test fun invisibleSettingsRemainDistinctInsteadOfSilentlyChangingTheSavedValue() {
        assertEquals(FloatingEntryState.ZERO_SIZE, FloatingEntryState.resolve(true, true, 0, 0))
        assertEquals(FloatingEntryState.TRANSPARENT, FloatingEntryState.resolve(true, true, 100, 100))
    }
    @Test fun enabledVisibleButtonsCanBeRestoredAtBothSizeLimits() {
        for (size in listOf(1, 100, 200)) for (transparency in listOf(0, 50, 99)) {
            assertEquals(FloatingEntryState.READY, FloatingEntryState.resolve(true, true, size, transparency))
        }
    }
}
