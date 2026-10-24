package com.easyswitchcontroller.ble

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** 응답 없는 명령의 무응답만 허용한다. 명시적인 write 오류와 외부 취소는 전파한다. */
internal suspend fun awaitWriteCompletion(
    completion: Deferred<Unit>,
    requireAck: Boolean,
    timeoutMs: Long,
): Boolean = if (requireAck) {
    withTimeout(timeoutMs) { completion.await() }
    true
} else {
    withTimeoutOrNull(timeoutMs) { completion.await(); true } ?: false
}
