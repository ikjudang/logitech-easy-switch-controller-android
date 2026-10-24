package com.easyswitchcontroller.device

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureIndexCacheTest {

    @Test
    fun `같은 featureId는 한 번만 resolve된다`() = runTest {
        val cache = FeatureIndexCache()
        var calls = 0

        val a = cache.getOrResolve(0x1814) { calls++; 0x05 }
        val b = cache.getOrResolve(0x1814) { calls++; 0x99 }

        assertEquals(0x05, a)
        assertEquals(0x05, b) // 두 번째 값은 캐시라 무시됨
        assertEquals(1, calls)
    }

    @Test
    fun `서로 다른 featureId는 각각 resolve된다`() = runTest {
        val cache = FeatureIndexCache()
        var calls = 0

        cache.getOrResolve(0x1814) { calls++; 0x05 }
        cache.getOrResolve(0x1815) { calls++; 0x06 }

        assertEquals(2, calls)
    }

    @Test
    fun `resolve가 예외를 던지면 캐시하지 않는다`() = runTest {
        val cache = FeatureIndexCache()
        var calls = 0

        runCatching {
            cache.getOrResolve(0x1814) { calls++; throw RuntimeException("boom") }
        }
        val v = cache.getOrResolve(0x1814) { calls++; 0x05 }

        assertEquals(0x05, v)
        assertEquals(2, calls) // 실패가 캐시됐다면 여기서 1이었을 것
    }
}
