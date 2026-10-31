package com.easyswitchcontroller.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** 한 소비자가 도착 순서를 유지한다. flush는 호출 전에 접수한 항목까지만 기다린다. */
internal class LogBatchWriter<T>(
    scope: CoroutineScope,
    private val batchSize: Int = 64,
    private val write: (List<T>) -> Unit,
) {
    private sealed interface Command<T>
    private class Item<T>(val value: T) : Command<T>
    private class Barrier<T>(val completion: CompletableDeferred<Unit>) : Command<T>
    private val pending = Channel<Command<T>>(Channel.UNLIMITED, onUndeliveredElement = {
        if (it is Barrier) it.completion.cancel()
    })

    init {
        require(batchSize > 0)
        scope.launch {
            var next: Command<T>? = null
            try {
                while (true) {
                    val first = next ?: pending.receiveCatching().getOrNull() ?: break
                    next = null
                    when (first) {
                        is Barrier -> first.completion.complete(Unit)
                        is Item -> {
                            val batch = ArrayList<T>(batchSize)
                            batch += first.value
                            while (batch.size < batchSize) {
                                when (val item = pending.tryReceive().getOrNull() ?: break) {
                                    is Item -> batch += item.value
                                    is Barrier -> { next = item; break }
                                }
                            }
                            try { write(batch) }
                            catch (error: CancellationException) { throw error }
                            catch (_: Exception) { /* 파일 실패가 이후 기록·flush까지 막지 않는다. */ }
                        }
                    }
                }
            } finally {
                if (next is Barrier) next.completion.cancel()
                pending.cancel()
            }
        }
    }

    fun append(value: T) { pending.trySend(Item(value)) }

    suspend fun flush() {
        val completion = CompletableDeferred<Unit>()
        pending.send(Barrier(completion))
        completion.await()
    }
}
