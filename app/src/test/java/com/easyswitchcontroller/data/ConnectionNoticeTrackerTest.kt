package com.easyswitchcontroller.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionNoticeTrackerTest {
    @Test fun alreadyConnectedDevicesDoNotRepeatTheirNoticeAtStartup() {
        val tracker = ConnectionNoticeTracker(setOf("keyboard"))
        assertFalse(tracker.onAclEvent("keyboard", true))
        assertTrue(tracker.onAclEvent("mouse", true))
    }

    @Test fun duplicateConnectedBroadcastsProduceOnlyOneNotice() {
        val tracker = ConnectionNoticeTracker(emptySet())
        assertTrue(tracker.onAclEvent("keyboard", true))
        assertFalse(tracker.onAclEvent("keyboard", true))
    }

    @Test fun aDeviceCanNotifyAgainAfterDisconnectingAndReturning() {
        val tracker = ConnectionNoticeTracker(setOf("keyboard"))
        assertFalse(tracker.onAclEvent("keyboard", false))
        assertTrue(tracker.onAclEvent("keyboard", true))
    }

    @Test fun bluetoothOffResetsNoticesEvenWithoutIndividualDisconnectBroadcasts() {
        val tracker = ConnectionNoticeTracker(setOf("keyboard"))
        tracker.clear()
        assertTrue(tracker.onAclEvent("keyboard", true))
    }
}
