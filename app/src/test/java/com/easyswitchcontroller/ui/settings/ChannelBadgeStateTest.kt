package com.easyswitchcontroller.ui.settings

import com.easyswitchcontroller.ui.components.ChannelBadgeState
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelBadgeStateTest {
    @Test fun remoteSwitchHistoryDoesNotClaimTheChannelIsInUse() {
        assertEquals(ChannelBadgeState.LAST_SWITCH, ChannelBadgeState.resolve(2, 2, emptySet()))
        assertEquals(ChannelBadgeState.NONE, ChannelBadgeState.resolve(3, 2, emptySet()))
    }

    @Test fun confirmedLocalConnectionTakesPriorityOverHistory() {
        assertEquals(ChannelBadgeState.CONNECTED_HERE, ChannelBadgeState.resolve(2, 2, setOf(2)))
    }

    @Test fun remainingLocalDeviceAndLastRemoteSwitchCanBothBeShown() {
        assertEquals(ChannelBadgeState.CONNECTED_HERE, ChannelBadgeState.resolve(1, 2, setOf(1)))
        assertEquals(ChannelBadgeState.LAST_SWITCH, ChannelBadgeState.resolve(2, 2, setOf(1)))
    }

    @Test fun disconnectRemovesTheLocalClaimButKeepsOnlyObservedHistory() {
        assertEquals(ChannelBadgeState.CONNECTED_HERE, ChannelBadgeState.resolve(1, 1, setOf(1)))
        assertEquals(ChannelBadgeState.LAST_SWITCH, ChannelBadgeState.resolve(1, 1, emptySet()))
        assertEquals(ChannelBadgeState.NONE, ChannelBadgeState.resolve(1, null, emptySet()))
    }
}
