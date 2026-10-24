package com.easyswitchcontroller.ui.channel

import androidx.lifecycle.ViewModelStore
import com.easyswitchcontroller.device.DeviceKind
import com.easyswitchcontroller.device.FirmwareProbeOutcome
import com.easyswitchcontroller.device.FirmwareProbePhase
import com.easyswitchcontroller.device.LogiDevice
import com.easyswitchcontroller.device.SwitchTransactions
import com.easyswitchcontroller.fakes.FakeHidppTransport
import com.easyswitchcontroller.hidpp.HidppFrame
import com.easyswitchcontroller.hidpp.HidppFrameParser
import com.easyswitchcontroller.transport.HidppTransport
import com.easyswitchcontroller.transport.WriteAck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class FirmwareReturnProbeIntegrationTest {
    @Test fun `immediate viewmodel destruction before first dispatch still releases probe reservation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val model = ChannelViewModel(listOf(LogiDevice(DeviceKind.MOUSE, "Mouse", "mouse", FakeHidppTransport())),
            MutableStateFlow(setOf("mouse")), isConnected = { true })
        val store = ViewModelStore().apply { put("probe", model) }
        SwitchTransactions.mutex.lock()
        try {
            model.startFirmwareReturnProbe("mouse", 2)
            store.clear()
            runCurrent()
            assertFalse(SwitchTransactions.firmwareProbeActive)
            assertEquals(FirmwareProbeOutcome.CANCELLED, model.firmwareProbe.value.outcome)
        } finally {
            SwitchTransactions.mutex.unlock()
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun `manual switching and mouse maintenance stay blocked during probe and cancellation releases everything`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fake = FakeHidppTransport(featureIndexProvider = { 14 })
        val acl = MutableStateFlow(setOf("mouse"))
        val transport = object : HidppTransport by fake {
            override suspend fun request(frame: ByteArray, timeout: Duration): HidppFrame {
                val request = HidppFrameParser.parse(frame)!!
                return if (request.featureIndex == 0) fake.request(frame, timeout)
                else HidppFrame.build(featureIndex = request.featureIndex, functionId = request.functionId,
                    params = if (request.functionId == 0) byteArrayOf(3, 2, 1) else byteArrayOf(1, 0, 1))
            }
            override suspend fun sendNoResponse(frame: ByteArray): WriteAck {
                acl.value = emptySet()
                return fake.sendNoResponse(frame)
            }
        }
        var saved = emptyList<String>()
        val model = ChannelViewModel(listOf(LogiDevice(DeviceKind.MOUSE, "Mouse", "mouse", transport)), acl,
            isConnected = { it.address in acl.value }, saveFirmwareProbeLog = { saved = it })
        val store = ViewModelStore().apply { put("probe", model) }
        try {
            runCurrent()
            model.startFirmwareReturnProbe("mouse", 2)
            runCurrent()
            assertEquals(FirmwareProbePhase.OBSERVING, model.firmwareProbe.value.phase)
            assertTrue(SwitchTransactions.firmwareProbeActive)
            assertTrue(SwitchTransactions.mutex.isLocked)
            assertTrue(SwitchTransactions.mouseMaintenanceMutex.isLocked)
            model.onChannelClicked(1)
            model.startFirmwareReturnProbe("mouse", 1)
            runCurrent()
            assertEquals(1, fake.sentFrames.size)
            model.cancelFirmwareReturnProbe()
            runCurrent()
            assertEquals(FirmwareProbeOutcome.CANCELLED, model.firmwareProbe.value.outcome)
            assertFalse(SwitchTransactions.firmwareProbeActive)
            assertFalse(SwitchTransactions.mutex.isLocked)
            assertFalse(SwitchTransactions.mouseMaintenanceMutex.isLocked)
            assertFalse(model.uiState.value.busy)
            assertTrue(saved.any { "RESULT=CANCELLED" in it })
            assertTrue(saved.all { it.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} .*")) })
        } finally {
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun `changing selected devices cancels a waiting probe without leaking its reservation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val address = "mouse"
        val fake = FakeHidppTransport()
        val model = ChannelViewModel(listOf(LogiDevice(DeviceKind.MOUSE, "Mouse", address, fake)), MutableStateFlow(setOf(address)), isConnected = { true })
        val store = ViewModelStore().apply { put("probe", model) }
        SwitchTransactions.mutex.lock()
        try {
            model.startFirmwareReturnProbe(address, 2)
            runCurrent()
            assertTrue(SwitchTransactions.firmwareProbeActive)
            model.updateDevices(emptyList())
            assertFalse(SwitchTransactions.firmwareProbeActive)
            assertTrue(fake.sentFrames.isEmpty())
        } finally {
            SwitchTransactions.mutex.unlock()
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }
}
