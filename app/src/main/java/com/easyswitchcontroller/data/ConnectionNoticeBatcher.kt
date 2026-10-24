package com.easyswitchcontroller.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 동시 연결을 하나의 안내로 묶는다. 첫 연결로부터 350ms 뒤에 표시하며 대기를 연장하지 않는다. */
internal class ConnectionNoticeBatcher(
    private val scope: CoroutineScope,
    private val windowMs: Long = 350,
    private val onReady: (List<String>) -> Unit,
) {
    private val pending = linkedSetOf<String>()
    private var flushJob: Job? = null

    @Synchronized
    fun add(address: String) {
        pending += address
        if (flushJob != null) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(windowMs)
            val batch = synchronized(this@ConnectionNoticeBatcher) {
                pending.toList().also { pending.clear(); flushJob = null }
            }
            if (batch.isNotEmpty()) onReady(batch)
        }
        flushJob = job
        job.start()
    }

    @Synchronized
    fun remove(address: String) {
        pending -= address
        if (pending.isEmpty()) clear()
    }

    @Synchronized
    fun clear() {
        pending.clear()
        flushJob?.cancel()
        flushJob = null
    }
}
