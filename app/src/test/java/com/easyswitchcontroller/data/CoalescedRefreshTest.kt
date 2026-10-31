package com.easyswitchcontroller.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoalescedRefreshTest {
    @Test fun aBurstReadsTheLatestStateOnceWithoutAddingADelay() = runTest {
        var source = 0
        val reads = mutableListOf<Int>()
        val refresh = CoalescedRefresh(backgroundScope) { reads += source }
        repeat(1000) { source = it; refresh.request() }
        runCurrent()
        assertEquals(listOf(999), reads)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun eventsDuringAnInFlightReadCauseOneMoreFreshReadAndNeverRunConcurrently() = runTest {
        var source = 1
        val reads = mutableListOf<Int>()
        var active = 0
        val refresh = CoalescedRefresh(backgroundScope) {
            assertEquals(0, active++)
            reads += source
            delay(100)
            active--
        }
        refresh.request()
        runCurrent()
        repeat(1000) { source = it; refresh.request() }
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf(1, 999), reads)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(0, active)
    }

    @Test fun aTransientReadFailureDoesNotDisableTheFallbackOrFutureEvents() = runTest {
        var attempts = 0
        val refresh = CoalescedRefresh(backgroundScope) { if (++attempts == 1) error("temporary") }
        refresh.request()
        runCurrent()
        refresh.request()
        runCurrent()
        assertEquals(2, attempts)
    }
}
