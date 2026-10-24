package com.easyswitchcontroller.device

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchVerificationTest {
    @Test fun aDeviceThatAlreadyLeftReturnsImmediately() = runTest {
        assertEquals(SwitchIndicator.Success, awaitSwitchConfirmation(0, 1) { false })
        assertEquals(0L, currentTime)
    }

    @Test fun aDeviceReturnsAsSoonAsItsLinkDisconnects() = runTest {
        val result = awaitSwitchConfirmation(0, 1) { currentTime < 200 }
        assertEquals(SwitchIndicator.Success, result)
        assertEquals(200L, currentTime)
    }

    @Test fun stayingOnTheCurrentHostDoesNotAddADelay() = runTest {
        assertEquals(SwitchIndicator.Success, awaitSwitchConfirmation(1, 1) { true })
        assertEquals(0L, currentTime)
    }

    @Test fun anUnchangedLinkHasAShortBoundedConfirmationWindow() = runTest {
        assertEquals(SwitchIndicator.Failure, awaitSwitchConfirmation(0, 1) { true })
        assertEquals(1_500L, currentTime)
    }

    @Test fun unknownLinkStateStaysUnconfirmed() = runTest {
        assertEquals(SwitchIndicator.Pending, awaitSwitchConfirmation(0, 1) { null })
        assertEquals(1_500L, currentTime)
    }

    @Test fun cancellingAnOldSwitchDoesNotProduceALateResult() = runTest {
        var reported = false
        val job = launch {
            awaitSwitchConfirmation(0, 1) { true }
            reported = true
        }
        runCurrent()
        advanceTimeBy(100)
        job.cancel()
        job.join()
        assertFalse(reported)
    }
}
