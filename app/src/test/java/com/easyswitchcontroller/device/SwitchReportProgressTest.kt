package com.easyswitchcontroller.device

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class SwitchReportProgressTest {
    private val keyboard = DeviceSwitchStatus("MX Keys S", DeviceKind.KEYBOARD, SwitchIndicator.Pending)
    private val mouse = DeviceSwitchStatus("MX Master 4", DeviceKind.MOUSE, SwitchIndicator.Pending)

    @Test fun aFastKeyboardResultDoesNotFinishWhileTheMouseIsPending() {
        val reports = mutableListOf<SwitchReport>()
        val progress = SwitchReportProgress(2, 10, linkedMapOf("keyboard" to keyboard, "mouse" to mouse), onReport = reports::add)
        progress.start()
        progress.resolved("keyboard", SwitchIndicator.Success)
        assertTrue(reports.last().inProgress)
        assertEquals(listOf(SwitchIndicator.Success, SwitchIndicator.Pending), reports.last().devices.map { it.indicator })
        progress.resolved("mouse", SwitchIndicator.Success)
        assertFalse(reports.last().inProgress)
        assertEquals(2, reports.last().devices.size)
        assertTrue(reports.all { it.operationId == 10L })
    }

    @Test fun targetDiscoveryMustFinishBeforeClaimingAKeyboardOnlyResult() {
        val reports = mutableListOf<SwitchReport>()
        val progress = SwitchReportProgress(2, 10, mapOf("keyboard" to keyboard), targetsKnown = false, onReport = reports::add)
        progress.start()
        progress.resolved("keyboard", SwitchIndicator.Success)
        assertTrue(reports.last().inProgress)
        assertFalse(reports.last().targetsConfirmed)
        progress.discover(mapOf("mouse" to mouse), confirmed = true)
        assertTrue(reports.last().inProgress)
        assertEquals(SwitchIndicator.Success, reports.last().keyboard)
        progress.resolved("mouse", SwitchIndicator.Failure)
        assertFalse(reports.last().inProgress)
        assertEquals(SwitchIndicator.Success, reports.last().keyboard)
        assertEquals(SwitchIndicator.Failure, reports.last().mouse)
    }

    @Test fun aConfirmedSingleTargetCanFinishAlone() {
        val reports = mutableListOf<SwitchReport>()
        val progress = SwitchReportProgress(2, 10, mapOf("keyboard" to keyboard), targetsKnown = false, onReport = reports::add)
        progress.resolved("keyboard", SwitchIndicator.Success)
        progress.discover(emptyMap(), confirmed = true)
        assertFalse(reports.last().inProgress)
        assertTrue(reports.last().targetsConfirmed)
        assertEquals(listOf(keyboard.copy(indicator = SwitchIndicator.Success)), reports.last().devices)
    }

    @Test fun incompleteDiscoveryCannotConfirmASoloSuccess() {
        val reports = mutableListOf<SwitchReport>()
        val progress = SwitchReportProgress(2, 10, mapOf("keyboard" to keyboard), targetsKnown = false, onReport = reports::add)
        progress.resolved("keyboard", SwitchIndicator.Success)
        progress.discover(emptyMap(), confirmed = false)
        assertFalse(reports.last().inProgress)
        assertFalse(reports.last().targetsConfirmed)
    }

    @Test fun cancellationKeepsUnknownResultsAndDoesNotResurrectTheNotice() {
        val reports = mutableListOf<SwitchReport>()
        val progress = SwitchReportProgress(2, 10, mapOf("keyboard" to keyboard, "mouse" to mouse), onReport = reports::add)
        progress.start()
        progress.resolved("keyboard", SwitchIndicator.Success)
        progress.finishUnconfirmed()
        val count = reports.size
        progress.resolved("mouse", SwitchIndicator.Success)
        assertEquals(count, reports.size)
        assertFalse(reports.last().inProgress)
        assertFalse(reports.last().targetsConfirmed)
        assertEquals(SwitchIndicator.Pending, reports.last().mouse)
    }

    @Test fun identicalNamesStillKeepSeparateDeviceRows() {
        val reports = mutableListOf<SwitchReport>()
        val progress = SwitchReportProgress(2, 10, linkedMapOf("first" to mouse, "second" to mouse), onReport = reports::add)
        progress.resolved("first", SwitchIndicator.Success)
        progress.resolved("second", SwitchIndicator.Failure)
        assertEquals(listOf(SwitchIndicator.Success, SwitchIndicator.Failure), reports.last().devices.map { it.indicator })
    }

    @Test fun concurrentChecksDoNotLoseTheOtherDevicesResult() {
        val reports = mutableListOf<SwitchReport>()
        val progress = SwitchReportProgress(2, 10, mapOf("keyboard" to keyboard, "mouse" to mouse), onReport = reports::add)
        progress.start()
        val checks = Executors.newFixedThreadPool(2)
        try {
            val keyboardCheck = checks.submit { progress.resolved("keyboard", SwitchIndicator.Success) }
            val mouseCheck = checks.submit { progress.resolved("mouse", SwitchIndicator.Success) }
            keyboardCheck.get(2, TimeUnit.SECONDS)
            mouseCheck.get(2, TimeUnit.SECONDS)
            assertFalse(reports.last().inProgress)
            assertTrue(reports.last().devices.all { it.indicator == SwitchIndicator.Success })
        } finally { checks.shutdownNow() }
    }
}
