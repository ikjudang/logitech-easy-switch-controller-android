package com.easyswitchcontroller.floating

import org.junit.Assert.*
import org.junit.Test

class FloatingMenuActionTest {
    @Test fun appIsFourthAndNeverInvokesBluetoothChannelSwitching() {
        val switched = mutableListOf<Int>()
        var appOpened = 0
        val action = FloatingMenuAction.fromId(4)!!
        assertEquals(FloatingMenuAction.OPEN_APP, action)
        assertNull(action.channel)
        action.perform({ switched += it }, { appOpened++ })
        assertTrue(switched.isEmpty())
        assertEquals(1, appOpened)
    }

    @Test fun numberedButtonsStillSwitchOnlyTheirOwnChannel() {
        val switched = mutableListOf<Int>()
        var appOpened = 0
        for (id in 1..3) FloatingMenuAction.fromId(id)!!.perform({ switched += it }, { appOpened++ })
        assertEquals(listOf(1, 2, 3), switched)
        assertEquals(0, appOpened)
    }

    @Test fun homeAndUnknownIdsAreNotAppOrChannelActions() {
        for (id in listOf(null, -1, 0, 5, Int.MAX_VALUE)) assertNull(FloatingMenuAction.fromId(id))
    }
}
