package com.easyswitchcontroller.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopGuardTest {

    /** 연결 시간 초과를 흉내 낸다 — GattConnection의 withTimeout이 실제로 던지는 예외 그대로다. */
    private suspend fun timeOut() = withTimeout(1) { delay(10) }

    @Test
    fun `근거 - 취소로 오인된 시간 초과는 루프를 조용히 끝낸다`() = runTest {
        var turns = 0
        var failuresLogged = 0
        val loop = launch {
            repeat(3) {
                try {
                    turns++
                    timeOut()
                } catch (e: CancellationException) {
                    throw e // 고치기 전 KeyboardFollowManager.ensureWatching의 패턴
                } catch (e: Exception) {
                    failuresLogged++ // 실패 기록 — 시간 초과는 여기까지 오지 못한다
                }
            }
        }
        loop.join()
        assertEquals("두 번째 바퀴는 오지 않는다", 1, turns)
        assertEquals("실패 기록도 남지 않는다", 0, failuresLogged)
        assertTrue("크래시가 아니라 '취소'로 끝나 아무 흔적도 없다", loop.isCancelled)
    }

    @Test
    fun `survive 안에서는 시간 초과가 나도 루프가 계속 돌고 실패가 전달된다`() = runTest {
        var turns = 0
        val errors = mutableListOf<Throwable>()
        val loop = launch {
            repeat(3) { survive({ errors += it }) { turns++; timeOut() } }
        }
        loop.join()
        assertEquals(3, turns)
        assertEquals(3, errors.size)
        assertTrue(errors.all { it is TimeoutCancellationException })
        assertFalse(loop.isCancelled)
    }

    @Test
    fun `일반 예외도 루프를 죽이지 않는다`() = runTest {
        var turns = 0
        val loop = launch {
            repeat(3) { survive({}) { turns++; error("boom") } }
        }
        loop.join()
        assertEquals(3, turns)
        assertFalse(loop.isCancelled)
    }

    @Test
    fun `바깥이 진짜로 취소되면 루프는 멈춘다`() = runTest {
        var turns = 0
        val loop = launch {
            while (true) survive({}) { turns++; delay(100) }
        }
        advanceTimeBy(250) // t=0, 100, 200에 한 바퀴씩
        loop.cancel()
        loop.join()
        assertTrue(loop.isCancelled)
        assertEquals(3, turns)
    }
}
