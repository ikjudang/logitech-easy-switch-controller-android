package com.easyswitchcontroller.ui.channel

import com.easyswitchcontroller.device.DeviceKind
import com.easyswitchcontroller.device.LogiDevice
import com.easyswitchcontroller.device.SwitchIndicator
import com.easyswitchcontroller.device.SwitchReport
import com.easyswitchcontroller.device.SwitchTransactions
import com.easyswitchcontroller.fakes.FakeHidppTransport
import com.easyswitchcontroller.hidpp.hex
import com.easyswitchcontroller.hidpp.HidppFrame
import com.easyswitchcontroller.hidpp.HidppFrameParser
import com.easyswitchcontroller.hidpp.ReportType
import com.easyswitchcontroller.service.FollowStatus
import com.easyswitchcontroller.ui.components.ChannelBadgeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.easyswitchcontroller.transport.HidppTransport
import com.easyswitchcontroller.transport.WriteAck

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelViewModelTest {
    @Test
    fun `한 기기의 전송이 대기 중이어도 다른 종류 기기의 명령은 바로 시작한다`() = modelTest {
        for (slowKind in listOf(DeviceKind.MOUSE, DeviceKind.KEYBOARD)) {
            val release = CompletableDeferred<Unit>()
            val slowStarted = CompletableDeferred<Unit>()
            val slowFake = FakeHidppTransport(featureIndexProvider = { 0x0E })
            val fastFake = FakeHidppTransport(featureIndexProvider = { 0x0E })
            val slowTransport = object : HidppTransport by slowFake {
                override suspend fun sendNoResponse(frame: ByteArray): WriteAck {
                    slowStarted.complete(Unit)
                    release.await()
                    return slowFake.sendNoResponse(frame)
                }
            }
            val slow = LogiDevice(slowKind, "대기 기기", "SLOW:$slowKind", slowTransport)
            val fastKind = if (slowKind == DeviceKind.MOUSE) DeviceKind.KEYBOARD else DeviceKind.MOUSE
            val fast = LogiDevice(fastKind, "즉시 기기", "FAST:$fastKind", fastFake)
            // 느린 기기를 먼저 넣어 목록 순서나 기기 종류에 따른 대기를 모두 검사한다.
            val vm = model(listOf(slow, fast), connected(slow.address, fast.address), isConnected = { true })
            try {
                vm.onChannelClicked(2)
                runCurrent()
                assertTrue(slowStarted.isCompleted)
                assertTrue(slowFake.sentFrames.isEmpty())
                assertArrayEquals(hex("10 FF 0E 1A 01 00 00"), fastFake.sentFrames.single())
                assertTrue(vm.uiState.value.busy)
            } finally {
                release.complete(Unit)
            }
            runCurrent()
            assertArrayEquals(hex("10 FF 0E 1A 01 00 00"), slowFake.sentFrames.single())
            assertEquals(false, vm.uiState.value.busy)
        }
    }

    @Test
    fun `마우스 전송 재시도를 기다리지 않고 키보드에 전환 명령을 보낸다`() = modelTest {
        val mouseFake = FakeHidppTransport(featureIndexProvider = { 0x0E }, failSend = true)
        val keyboardFake = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val vm = model(listOf(mouse(mouseFake), keyboard(keyboardFake)), connected(mouseAddr, kbAddr),
            isConnected = { true })
        vm.onChannelClicked(3)
        runCurrent()
        assertEquals(1, mouseFake.sentFrames.size)
        assertArrayEquals(hex("10 FF 0E 1A 02 00 00"), keyboardFake.sentFrames.single())
        advanceTimeBy(500)
        runCurrent()
        assertEquals(3, mouseFake.sentFrames.size)
        assertEquals(1, keyboardFake.sentFrames.size)
        assertEquals(false, vm.uiState.value.busy)
    }

    @Test
    fun `키보드 결과가 먼저 와도 늦은 마우스 결과를 같은 전환에 누적한다`() = modelTest {
        val delayedMouse = CompletableDeferred<SwitchIndicator>()
        val reports = mutableListOf<SwitchReport>()
        val vm = model(
            listOf(keyboard(FakeHidppTransport(featureIndexProvider = { 0x0E })),
                mouse(FakeHidppTransport(featureIndexProvider = { 0x0E }))),
            connected(kbAddr, mouseAddr), onSwitchReport = { reports += it },
            verify = { device, _ -> if (device.kind == DeviceKind.MOUSE) delayedMouse.await() else SwitchIndicator.Success },
            isConnected = { true },
        )
        vm.onChannelClicked(2)
        runCurrent()
        val early = reports.last()
        assertTrue(early.inProgress)
        assertEquals(SwitchIndicator.Success, early.keyboard)
        assertEquals(SwitchIndicator.Pending, early.mouse)
        delayedMouse.complete(SwitchIndicator.Success)
        runCurrent()
        val completed = reports.last()
        assertEquals(early.operationId, completed.operationId)
        assertEquals(false, completed.inProgress)
        assertEquals(SwitchIndicator.Success, completed.keyboard)
        assertEquals(SwitchIndicator.Success, completed.mouse)
    }

    private val models = mutableListOf<ChannelViewModel>()
    private fun modelTest(block: suspend TestScope.() -> Unit) = runTest {
        try { block() } finally {
            models.forEach { model -> ViewModelStore().apply { put("test", model); clear() } }
            models.clear()
        }
    }

    private fun model(
        devices: List<LogiDevice>,
        connectedAddresses: kotlinx.coroutines.flow.StateFlow<Set<String>>,
        onSwitchReport: (SwitchReport) -> Unit = {},
        verify: suspend (LogiDevice, Int) -> SwitchIndicator = { _, _ -> SwitchIndicator.Pending },
        isConnected: (LogiDevice) -> Boolean? = { null },
    ) = ChannelViewModel(devices, connectedAddresses, onSwitchReport = onSwitchReport, verify = verify, isConnected = isConnected)
        .also { models += it }

    @Test
    fun `명령을 전달하면 확인 대기 전부터 목적지만 마지막 전환으로 기록하고 재연결은 이력을 덮지 않는다`() = modelTest {
        val verification = CompletableDeferred<SwitchIndicator>()
        val fake = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val device = keyboard(fake).apply { rememberLocalHost(0) }
        FollowStatus.setRunning(true)
        FollowStatus.rememberLocalChannel(kbAddr, 1)
        FollowStatus.setKeyboardConnected(kbAddr, true)
        FollowStatus.setActiveChannel(1)
        val vm = model(listOf(device), connected(kbAddr), verify = { _, _ -> verification.await() })
        try {
            vm.onChannelClicked(2)
            runCurrent()
            assertEquals(2, FollowStatus.activeChannel.value)
            assertEquals(ChannelBadgeState.NONE, ChannelBadgeState.resolve(1,
                FollowStatus.activeChannel.value, vm.uiState.value.connectedLocalChannels))
            assertEquals(ChannelBadgeState.LAST_SWITCH, ChannelBadgeState.resolve(2,
                FollowStatus.activeChannel.value, vm.uiState.value.connectedLocalChannels))
            // 기기가 돌아오면 로컬 연결만 복원한다. 과거 원격 목적지를 현재 사용 중이라고 단정하지 않는다.
            FollowStatus.setKeyboardConnected(kbAddr, true)
            runCurrent()
            assertEquals(2, FollowStatus.activeChannel.value)
            assertEquals(ChannelBadgeState.CONNECTED_HERE, ChannelBadgeState.resolve(1,
                FollowStatus.activeChannel.value, vm.uiState.value.connectedLocalChannels))
        } finally {
            verification.complete(SwitchIndicator.Success)
            FollowStatus.setRunning(false)
        }
    }

    @Test
    fun `로컬 슬롯 조회는 다른 목적지의 전환 이력을 바꾸지 않는다`() = modelTest {
        FollowStatus.setActiveChannel(3)
        val fake = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val transport = object : HidppTransport by fake {
            override suspend fun request(frame: ByteArray, timeout: kotlin.time.Duration): HidppFrame {
                val parsed = HidppFrameParser.parse(frame)!!
                return if (parsed.featureIndex == 0x0E) HidppFrame.build(ReportType.SHORT,
                    parsed.deviceIndex, parsed.featureIndex, parsed.functionId, parsed.softwareId,
                    byteArrayOf(3, 1, 0)) else fake.request(frame, timeout)
            }
        }
        val vm = model(listOf(LogiDevice(DeviceKind.MOUSE, "MX Master 4", mouseAddr, transport)), connected(mouseAddr))
        runCurrent()
        assertEquals(3, FollowStatus.activeChannel.value)
        assertEquals(setOf(2), vm.uiState.value.connectedLocalChannels)
    }

    @Test
    fun `전환 명령을 보내지 못하면 마지막 목적지를 덮어쓰지 않는다`() = modelTest {
        FollowStatus.setActiveChannel(3)
        val vm = model(listOf(keyboard(FakeHidppTransport())), connected(kbAddr))
        vm.onChannelClicked(2)
        runCurrent()
        assertEquals(3, FollowStatus.activeChannel.value)
    }

    @Test
    fun `이전 전환의 늦은 확인은 더 최근 신호의 목적지를 덮어쓰지 않는다`() = modelTest {
        val verification = CompletableDeferred<SwitchIndicator>()
        val vm = model(listOf(keyboard(FakeHidppTransport(featureIndexProvider = { 0x0E }))),
            connected(kbAddr), verify = { _, _ -> verification.await() })
        vm.onChannelClicked(2)
        runCurrent()
        SwitchTransactions.begin()
        FollowStatus.setActiveChannel(3)
        verification.complete(SwitchIndicator.Success)
        runCurrent()
        assertEquals(3, FollowStatus.activeChannel.value)
    }

    @Test
    fun `처음부터 연결되지 않은 마우스는 명령과 결과에서 제외한다`() = modelTest {
        val mouseFake = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val keyboardFake = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val reports = mutableListOf<SwitchReport>()
        val vm = model(
            listOf(mouse(mouseFake), keyboard(keyboardFake)), connected(kbAddr),
            onSwitchReport = { reports += it }, verify = { _, _ -> SwitchIndicator.Success },
            isConnected = { it.address == kbAddr },
        )
        vm.onChannelClicked(2)
        runCurrent()
        assertTrue(mouseFake.sentFrames.isEmpty())
        assertEquals(1, keyboardFake.sentFrames.size)
        assertEquals(listOf("MX Keys S"), reports.last().devices.map { it.name })
        assertEquals(SwitchIndicator.NotSelected, reports.last().mouse)
    }

    @Test
    fun `처음부터 연결되지 않은 키보드도 명령과 결과에서 제외한다`() = modelTest {
        val mouseFake = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val keyboardFake = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val reports = mutableListOf<SwitchReport>()
        val vm = model(
            listOf(mouse(mouseFake), keyboard(keyboardFake)), connected(mouseAddr),
            onSwitchReport = { reports += it }, verify = { _, _ -> SwitchIndicator.Success },
            isConnected = { it.address == mouseAddr },
        )
        vm.onChannelClicked(2)
        runCurrent()
        assertTrue(keyboardFake.sentFrames.isEmpty())
        assertEquals(1, mouseFake.sentFrames.size)
        assertEquals(listOf("MX Master 4"), reports.last().devices.map { it.name })
        assertEquals(SwitchIndicator.NotSelected, reports.last().keyboard)
    }

    @Test
    fun `기존 준비 작업의 종료를 기다리기 전에 전환 진행을 안내한다`() = modelTest {
        val closing = CompletableDeferred<Unit>()
        val allowClose = CompletableDeferred<Unit>()
        val fake = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val transport = object : HidppTransport by fake {
            override suspend fun disconnect() {
                closing.complete(Unit)
                allowClose.await()
                fake.disconnect()
            }
        }
        val reports = mutableListOf<SwitchReport>()
        val vm = model(
            listOf(LogiDevice(DeviceKind.MOUSE, "MX Master 4", mouseAddr, transport)), connected(mouseAddr),
            onSwitchReport = { reports += it }, verify = { _, _ -> SwitchIndicator.Success },
        )
        closing.await()
        try {
            vm.onChannelClicked(2)
            runCurrent()
            assertTrue(reports.single().inProgress)
            assertTrue(fake.sentFrames.isEmpty())
        } finally { allowClose.complete(Unit) }
        runCurrent()
        assertEquals(false, reports.last().inProgress)
        assertEquals(SwitchIndicator.Success, reports.last().mouse)
    }

    @Test
    fun `선택 변경 중 호출자가 취소돼도 대상 변경과 정리를 끝낸다`() = modelTest {
        val closing = CompletableDeferred<Unit>()
        val allowClose = CompletableDeferred<Unit>()
        val fake = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val transport = object : HidppTransport by fake {
            override suspend fun disconnect() {
                closing.complete(Unit)
                allowClose.await()
                fake.disconnect()
            }
        }
        val original = LogiDevice(DeviceKind.MOUSE, "MX Master 4", mouseAddr, transport)
        val vm = model(listOf(original), connected(mouseAddr))
        closing.await()
        val update = launch { vm.updateDevices(listOf(keyboard(FakeHidppTransport()))) }
        runCurrent()
        update.cancel()
        allowClose.complete(Unit)
        update.join()
        assertEquals(DeviceKind.KEYBOARD, vm.uiState.value.devices.single().kind)
        vm.updateDevices(listOf(original))
        assertEquals(DeviceKind.MOUSE, vm.uiState.value.devices.single().kind)
    }

    @Test
    fun `선택을 변경한 뒤에는 새 전환 대상에만 명령을 보낸다`() = modelTest {
        val old = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val next = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val vm = model(listOf(mouse(old)), connected(mouseAddr, kbAddr))
        vm.updateDevices(listOf(keyboard(next)))
        vm.onChannelClicked(2)
        runCurrent()
        assertEquals(0, old.sentFrames.size)
        assertEquals(1, next.sentFrames.size)
        assertEquals(DeviceKind.KEYBOARD, vm.uiState.value.devices.single().kind)
        vm.updateDevices(emptyList())
    }

    @Test
    fun `마우스 실패와 키보드 성공은 서로 독립적으로 표시한다`() = modelTest {
        val reports = mutableListOf<SwitchReport>()
        val vm = model(
            listOf(mouse(FakeHidppTransport()), keyboard(FakeHidppTransport(featureIndexProvider = { 0x0E }))),
            connected(mouseAddr, kbAddr),
            onSwitchReport = { reports += it },
            verify = { _, _ -> SwitchIndicator.Success },
            isConnected = { true },
        )
        vm.onChannelClicked(2)
        runCurrent()
        assertEquals(SwitchIndicator.Success, reports.last().keyboard)
        assertEquals(SwitchIndicator.Failure, reports.last().mouse)
        assertEquals(listOf("MX Master 4", "MX Keys S"), reports.last().devices.map { it.name })
        assertEquals(false, vm.uiState.value.busy)
        vm.updateDevices(emptyList())
    }

    @Test
    fun `마우스만 선택하면 키보드를 실패로 표시하지 않는다`() = modelTest {
        val reports = mutableListOf<SwitchReport>()
        val vm = model(
            listOf(mouse(FakeHidppTransport(featureIndexProvider = { 0x0E }))), connected(mouseAddr),
            onSwitchReport = { reports += it }, verify = { _, _ -> SwitchIndicator.Success },
        )
        vm.onChannelClicked(1)
        runCurrent()
        assertEquals(SwitchIndicator.NotSelected, reports.last().keyboard)
        assertEquals(SwitchIndicator.Success, reports.last().mouse)
        vm.updateDevices(emptyList())
    }

    private val mouseAddr = "MO:US:E"
    private val kbAddr = "KB:BD"

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun mouse(fake: FakeHidppTransport) = LogiDevice(DeviceKind.MOUSE, "MX Master 4", mouseAddr, fake)
    private fun keyboard(fake: FakeHidppTransport) = LogiDevice(DeviceKind.KEYBOARD, "MX Keys S", kbAddr, fake)
    private fun connected(vararg addr: String) = MutableStateFlow(addr.toSet())

    @Test
    fun `채널1 클릭은 연결된 마우스와 키보드 둘 다 host 0으로 전환한다`() = modelTest {
        val mouseFake = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val kbFake = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val vm = model(listOf(mouse(mouseFake), keyboard(kbFake)), connected(mouseAddr, kbAddr))

        vm.onChannelClicked(1)

        // feature 0x0E, fn1|sw0A=0x1A, host 0 (표준 형식, device 0xFF)
        assertArrayEquals(hex("10 FF 0E 1A 00 00 00"), mouseFake.sentFrames.single())
        assertArrayEquals(hex("10 FF 0E 1A 00 00 00"), kbFake.sentFrames.single())
    }

    @Test
    fun `채널3은 host 2로 매핑되어 연결된 기기에 전송된다`() = modelTest {
        val mouseFake = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val kbFake = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val vm = model(listOf(mouse(mouseFake), keyboard(kbFake)), connected(mouseAddr, kbAddr))

        vm.onChannelClicked(3)

        assertArrayEquals(hex("10 FF 0E 1A 02 00 00"), mouseFake.sentFrames.single())
        assertArrayEquals(hex("10 FF 0E 1A 02 00 00"), kbFake.sentFrames.single())
    }

    @Test
    fun `단일 기기(마우스만)도 정상 전환된다`() = modelTest {
        val mouseFake = FakeHidppTransport(featureIndexProvider = { 0x0E })
        val vm = model(listOf(mouse(mouseFake)), connected(mouseAddr))

        vm.onChannelClicked(2)

        assertArrayEquals(hex("10 FF 0E 1A 01 00 00"), mouseFake.sentFrames.single())
    }

    @Test
    fun `feature 미지원 기기는 실패로 로그에 남고 명령을 보내지 않는다`() = modelTest {
        val mouseFake = FakeHidppTransport(featureIndexProvider = { 0 }) // 미지원
        val vm = model(listOf(mouse(mouseFake)), connected(mouseAddr))

        vm.onChannelClicked(1)

        assertEquals(0, mouseFake.sentFrames.size)
        // LogiDevice가 FeatureUnsupportedException 메시지("...미지원...")를 실패 사유로 돌려준다.
        assertTrue(vm.uiState.value.log.any { it.contains("전환 안 됨") || it.contains("미지원") })
    }

    @Test
    fun `UI 상태에 등록 기기가 종류와 함께 노출된다`() = modelTest {
        val vm = model(
            listOf(
                mouse(FakeHidppTransport(featureIndexProvider = { 0x0E })),
                keyboard(FakeHidppTransport(featureIndexProvider = { 0x0E })),
            ),
            connected(mouseAddr, kbAddr),
        )

        val kinds = vm.uiState.value.devices.map { it.kind }
        assertTrue(kinds.contains(DeviceKind.MOUSE))
        assertTrue(kinds.contains(DeviceKind.KEYBOARD))
        // 연결된 기기는 connected=true로 노출
        assertTrue(vm.uiState.value.devices.all { it.connected })
    }
}
