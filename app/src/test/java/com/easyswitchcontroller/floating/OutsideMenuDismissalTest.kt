package com.easyswitchcontroller.floating

import org.junit.Assert.*
import org.junit.Test

class OutsideMenuDismissalTest {
    @Test fun outsidePressCanDismissOnlyAnOpenMenu() {
        val menu = OutsideMenuDismissal()
        assertNull(menu.request(100))
        menu.opened(200)
        val outside = menu.request(250)!!
        assertTrue(menu.isCurrent(outside))
        menu.closed()
        assertFalse(menu.isCurrent(outside))
        assertNull(menu.request(300))
    }

    @Test fun delayedNotificationFromTheSummoningClickDoesNotCloseTheNewMenu() {
        val menu = OutsideMenuDismissal()
        menu.opened(500)
        assertNull(menu.request(490))
        assertTrue(menu.isCurrent(menu.request(510)!!))
    }

    @Test fun queuedDismissalCannotAffectAReopenedMenu() {
        val menu = OutsideMenuDismissal()
        menu.opened(100)
        val firstPress = menu.request(120)!!
        menu.closed()
        menu.opened(200)
        assertFalse(menu.isCurrent(firstPress))
        assertNull(menu.request(150))
        assertTrue(menu.isCurrent(menu.request(220)!!))
    }

    @Test fun replacementWhileAlreadyOpenAlsoInvalidatesPendingPresses() {
        val menu = OutsideMenuDismissal()
        menu.opened(100)
        val firstPress = menu.request(110)!!
        menu.opened(120)
        assertFalse(menu.isCurrent(firstPress))
        assertTrue(menu.isCurrent(menu.request(120)!!))
    }
}
