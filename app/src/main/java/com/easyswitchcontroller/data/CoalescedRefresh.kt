package com.easyswitchcontroller.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** 조회 중 도착한 여러 요청은 최신 상태를 다시 읽을 요청 하나로 합친다. 추가 지연은 없다. */
internal class CoalescedRefresh(scope: CoroutineScope, refresh: suspend () -> Unit) {
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            try {
                for (request in requests) {
                    try { refresh() }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { /* 다음 이벤트와 안전망 폴링은 계속 받는다. */ }
                }
            } finally { requests.cancel() }
        }
    }

    fun request() { requests.trySend(Unit) }
}
