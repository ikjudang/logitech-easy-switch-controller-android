package com.easyswitchcontroller.service

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EarlySignalGateTest {

    private val route = EarlySignalGate.Route(featureIndex = 0x0A, localHost = 1)

    @Test
    fun `근거 - 신호가 온 뒤에 구독하면 그 신호는 아무도 받지 못한다`() = runTest {
        // GattConnection의 notify 흐름과 같은 설정(replay 0) — 구독자가 없을 때 온 값은 남지 않는다.
        val notify = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 16)
        notify.tryEmit("전환 신호") // 고치기 전: 슬롯 조회 중에 도착
        val received = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { notify.collect { received += it } }
        job.cancel()
        assertTrue("조회 뒤에 건 구독은 그 신호를 못 받는다", received.isEmpty())
    }

    @Test
    fun `연결 전에 건 구독은 조회 중에 온 신호를 받는다`() = runTest {
        val notify = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 16)
        val gate = EarlySignalGate<String>(route = null)
        val handled = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            notify.collect { frame -> gate.offer(frame)?.let { handled += frame } }
        }
        notify.emit("전환 신호") // 슬롯 조회 중에 도착 — 아직 경로를 몰라 붙잡아 둔다
        runCurrent()
        assertTrue("경로를 알기 전에는 처리하지 않는다", handled.isEmpty())
        handled += gate.open(route)
        job.cancel()
        assertEquals(listOf("전환 신호"), handled)
    }

    @Test
    fun `경로를 모르면 쌓아 두고 열 때 도착 순서대로 돌려준다`() {
        val gate = EarlySignalGate<Int>(route = null)
        assertNull(gate.offer(1))
        assertNull(gate.offer(2))
        assertEquals(listOf(1, 2), gate.open(route))
        assertEquals("연 뒤에는 바로 경로를 돌려준다", route, gate.offer(3))
        assertTrue("연 뒤에 온 것은 쌓지 않는다", gate.drain().isEmpty())
    }

    @Test
    fun `지난번 경로가 있으면 처음부터 열려 있다`() {
        val gate = EarlySignalGate<Int>(route)
        assertEquals(route, gate.offer(1))
        assertTrue(gate.open(route).isEmpty())
    }

    @Test
    fun `조회한 경로가 다르면 그 뒤 신호는 새 경로로 해석한다`() {
        val gate = EarlySignalGate<Int>(route)
        val moved = EarlySignalGate.Route(featureIndex = 0x0A, localHost = 2)
        gate.open(moved)
        assertEquals(moved, gate.offer(1))
    }

    @Test
    fun `경로를 끝내 모르면 버려진 신호를 한 번만 돌려준다`() {
        val gate = EarlySignalGate<Int>(route = null)
        gate.offer(7)
        assertEquals(listOf(7), gate.drain())
        assertTrue(gate.drain().isEmpty())
    }
}
