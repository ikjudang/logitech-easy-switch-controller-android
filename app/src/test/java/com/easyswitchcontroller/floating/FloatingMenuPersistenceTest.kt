package com.easyswitchcontroller.floating

import org.junit.Assert.*
import org.junit.Test

class FloatingMenuPersistenceTest {
    @Test fun existingInstallationsKeepTheNormalCollapsingMenuByDefault() {
        assertFalse(FloatingPreferences.State(false, 0, .88f, .58f).alwaysShowMenu)
        val menu = FloatingMenuPersistence()
        menu.requestRestore()
        assertFalse(menu.enabled)
        assertFalse(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
    }

    @Test fun enablingRestoresTheClosedMenuExactlyOnceWithoutRecreatingTheHome() {
        val menu = FloatingMenuPersistence()
        assertTrue(menu.setEnabled(true))
        assertTrue(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
        assertFalse(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
    }

    @Test fun enablingDuringAbsorptionOrDragWaitsUntilTheInteractionFinishes() {
        val menu = FloatingMenuPersistence()
        menu.setEnabled(true)
        repeat(4) {
            assertFalse(menu.takeRestore(homeVisible = true, menuOpen = false, busy = true))
            menu.requestRestore()
        }
        assertTrue(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
        assertFalse(menu.takeRestore(homeVisible = true, menuOpen = true, busy = false))
    }

    @Test fun disablingBeforeAnAnimationCompletesCancelsThePendingReopen() {
        val menu = FloatingMenuPersistence()
        menu.setEnabled(true)
        assertFalse(menu.takeRestore(homeVisible = true, menuOpen = false, busy = true))
        menu.setEnabled(false)
        menu.requestRestore()
        assertFalse(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
        assertTrue(menu.setEnabled(true))
        assertTrue(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
    }

    @Test fun anAlreadyOpenMenuDoesNotReplayItsOpeningOnEverySettingsUpdate() {
        val menu = FloatingMenuPersistence()
        menu.setEnabled(true)
        repeat(5) {
            menu.requestRestore()
            assertFalse(menu.takeRestore(homeVisible = true, menuOpen = true, busy = false))
        }
        assertFalse(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
        menu.requestRestore()
        assertTrue(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
    }

    @Test fun hidingWithXDoesNotResurrectTheHomeAfterAQueuedCallback() {
        val menu = FloatingMenuPersistence()
        menu.setEnabled(true)
        assertFalse(menu.takeRestore(homeVisible = true, menuOpen = false, busy = true))
        menu.cancelRestore()
        assertTrue(menu.enabled)
        assertFalse(menu.takeRestore(homeVisible = false, menuOpen = false, busy = false))
        assertFalse(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
    }

    @Test fun unavailableHomeOrZeroOpacityCannotBeOverriddenByThePreference() {
        val menu = FloatingMenuPersistence()
        menu.setEnabled(true)
        assertFalse(menu.takeRestore(homeVisible = false, menuOpen = false, busy = false))
        assertTrue(menu.enabled)
        assertFalse(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
        menu.requestRestore()
        assertTrue(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
    }

    @Test fun repeatedSyncDoesNotLoseAPendingRestoreOrCreateAnotherOne() {
        val menu = FloatingMenuPersistence()
        assertTrue(menu.setEnabled(true))
        assertFalse(menu.setEnabled(true))
        assertTrue(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
        assertFalse(menu.setEnabled(true))
        assertFalse(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
    }

    @Test fun enablingWithClosedNumbersKeepsTheFirstSequentialOpening() {
        val menu = FloatingMenuPersistence()
        menu.setEnabled(true)
        assertTrue(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
        assertTrue(menu.shouldAnimateNumbers(requested = true, hasNumbers = false))
        assertFalse(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
    }

    @Test fun enablingWithExistingNumbersDoesNotEmitThemAgain() {
        val menu = FloatingMenuPersistence()
        menu.setEnabled(true)
        repeat(3) {
            menu.requestRestore()
            assertFalse(menu.shouldAnimateNumbers(requested = true, hasNumbers = true))
            assertFalse(menu.takeRestore(homeVisible = true, menuOpen = true, busy = false))
        }
    }

    @Test fun anExplicitStaticRepositionNeverStartsANumberEmission() {
        val menu = FloatingMenuPersistence()
        for (enabled in listOf(false, true)) {
            menu.setEnabled(enabled)
            for (hasNumbers in listOf(false, true)) {
                assertFalse(menu.shouldAnimateNumbers(requested = false, hasNumbers = hasNumbers))
            }
        }
    }

    @Test fun rapidOffAndOnKeepsAnExistingOpeningAndDoesNotQueueAnotherOne() {
        val menu = FloatingMenuPersistence()
        menu.setEnabled(true)
        assertTrue(menu.shouldAnimateNumbers(requested = true, hasNumbers = false))
        assertFalse(menu.takeRestore(homeVisible = true, menuOpen = false, busy = true))
        menu.setEnabled(false)
        assertTrue(menu.shouldAnimateNumbers(requested = true, hasNumbers = true))
        menu.setEnabled(true)
        assertFalse(menu.shouldAnimateNumbers(requested = true, hasNumbers = true))
        assertFalse(menu.takeRestore(homeVisible = true, menuOpen = false, busy = true))
        assertFalse(menu.takeRestore(homeVisible = true, menuOpen = true, busy = false))
        assertFalse(menu.takeRestore(homeVisible = true, menuOpen = false, busy = false))
    }
}
