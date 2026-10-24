package com.easyswitchcontroller.device

import com.easyswitchcontroller.fakes.FakeHidppTransport
import com.easyswitchcontroller.hidpp.hex
import com.easyswitchcontroller.transport.ConnectionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogiDeviceTest {
    @Test
    fun `전환 중 권한이 회수돼도 호출자를 중단시키지 않고 실패를 반환한다`() = runTest {
        val fake = FakeHidppTransport(featureIndexProvider = { 0x05 }, sendError = SecurityException("revoked"))
        assertTrue(mouse(fake).setCurrentHost(1) is SwitchResult.Failed)
        assertEquals(1, fake.sentFrames.size)
    }

    private fun mouse(transport: FakeHidppTransport) =
        LogiDevice(DeviceKind.MOUSE, "MX Master 3S", "MO:US:E:01", transport)

    @Test
    fun `setCurrentHost는 feature index를 먼저 조회한 뒤 명령을 보낸다`() = runTest {
        val fake = FakeHidppTransport(featureIndexProvider = { if (it == 0x1814) 0x05 else 0 })
        val device = mouse(fake)
        device.connect()

        val result = device.setCurrentHost(hostIndex = 0)

        assertEquals(SwitchResult.Switched, result)
        // 먼저 0x1814 getFeature 조회
        assertArrayEquals(hex("10 FF 00 0A 18 14 00"), fake.requestedFrames.single())
        // 그 다음 조회된 index(0x05)로 setCurrentHost(host 0)
        assertArrayEquals(hex("10 FF 05 1A 00 00 00"), fake.sentFrames.single())
    }

    @Test
    fun `채널 파라미터가 그대로 0-based host로 전송된다`() = runTest {
        val fake = FakeHidppTransport(featureIndexProvider = { 0x05 })
        val device = mouse(fake)

        device.setCurrentHost(hostIndex = 2)

        assertArrayEquals(hex("10 FF 05 1A 02 00 00"), fake.sentFrames.single())
    }

    @Test
    fun `feature index는 한 번만 조회되고 캐시된다`() = runTest {
        val fake = FakeHidppTransport(featureIndexProvider = { 0x05 })
        val device = mouse(fake)

        device.getFeatureIndex(0x1814)
        device.getFeatureIndex(0x1814)

        assertEquals(1, fake.requestedFrames.size)
    }

    @Test
    fun `feature 미지원(index 0)이면 명령을 보내지 않고 실패한다`() = runTest {
        val fake = FakeHidppTransport(featureIndexProvider = { 0 }) // 미지원
        val device = mouse(fake)

        val result = device.setCurrentHost(hostIndex = 0)

        assertTrue(result is SwitchResult.Failed)
        assertEquals(0, fake.sentFrames.size)
    }

    @Test
    fun `전송 직후 링크가 끊겨도 성공으로 처리한다`() = runTest {
        val fake = FakeHidppTransport(
            featureIndexProvider = { 0x05 },
            simulateDisconnectOnSend = true,
        )
        val device = mouse(fake)
        device.connect()

        val result = device.setCurrentHost(hostIndex = 1)

        assertEquals(SwitchResult.Switched, result)
        assertEquals(ConnectionState.Disconnected, fake.connectionState.value)
    }

    @Test
    fun `조회 단계의 프로토콜 에러는 실패로 변환된다`() = runTest {
        val fake = FakeHidppTransport(
            featureIndexProvider = { 0x05 },
            protocolErrorFeatureIds = setOf(0x1814),
        )
        val device = mouse(fake)

        val result = device.setCurrentHost(hostIndex = 0)

        assertTrue(result is SwitchResult.Failed)
        assertEquals(0, fake.sentFrames.size)
    }

    @Test
    fun `write 실패는 실패로 처리한다`() = runTest {
        val fake = FakeHidppTransport(featureIndexProvider = { 0x05 }, failSend = true)
        val device = mouse(fake)

        val result = device.setCurrentHost(hostIndex = 0)

        assertTrue(result is SwitchResult.Failed)
    }
}
