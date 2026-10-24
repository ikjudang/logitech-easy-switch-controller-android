package com.easyswitchcontroller.device

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * featureId → 런타임 feature index 캐시.
 *
 * feature index는 절대 하드코딩·주입하지 않고 이 캐시에만 존재한다(사양서 요구를 구조로 강제).
 * [resolve]는 락 안에서 한 번만 호출되어 중복 조회를 막는다. resolve가 예외를 던지면(미지원 등)
 * 캐시에 저장하지 않는다.
 */
class FeatureIndexCache {
    private val cache = mutableMapOf<Int, Int>()
    private val mutex = Mutex()

    suspend fun getOrResolve(featureId: Int, resolve: suspend (Int) -> Int): Int =
        mutex.withLock {
            cache.getOrPut(featureId) { resolve(featureId) }
        }

    suspend fun cached(featureId: Int): Int? = mutex.withLock { cache[featureId] }
}
