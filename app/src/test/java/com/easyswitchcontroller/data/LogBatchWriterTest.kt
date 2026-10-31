package com.easyswitchcontroller.data

import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogBatchWriterTest {
    @Test fun aBurstUsesBatchesWithoutDroppingReorderingOrDeduplicatingRecords() = runTest {
        val writes = mutableListOf<List<Int>>()
        val writer = LogBatchWriter<Int>(backgroundScope) { writes += it.toList() }
        val records = (0 until 1000).map { it / 2 }
        records.forEach(writer::append)
        val flush = async(start = CoroutineStart.UNDISPATCHED) { writer.flush() }
        runCurrent()
        flush.await()
        assertEquals(records, writes.flatten())
        assertEquals(16, writes.size)
        assertTrue(writes.all { it.size <= 64 })
    }

    @Test fun flushSeparatesTheAcceptedPrefixFromLaterRecords() = runTest {
        val writes = mutableListOf<List<Int>>()
        val writer = LogBatchWriter<Int>(backgroundScope) { writes += it.toList() }
        writer.append(1)
        writer.append(2)
        val flush = async(start = CoroutineStart.UNDISPATCHED) { writer.flush() }
        writer.append(3)
        runCurrent()
        flush.await()
        assertEquals(listOf(listOf(1, 2), listOf(3)), writes)
    }

    @Test fun aFailedFileBatchDoesNotKillTheWriterOrStrandExports() = runTest {
        val saved = mutableListOf<Int>()
        var calls = 0
        val writer = LogBatchWriter<Int>(backgroundScope, batchSize = 1) {
            if (++calls == 1) throw IOException("storage failure")
            saved += it
        }
        writer.append(1)
        writer.append(2)
        val flush = async(start = CoroutineStart.UNDISPATCHED) { writer.flush() }
        runCurrent()
        flush.await()
        assertEquals(listOf(2), saved)
    }

    @Test fun cappedHistoryKeepsDuplicateEventsAndDoesNotMutatePublishedSnapshots() {
        val old = listOf("first", "same", "same")
        assertEquals(listOf("same", "same", "last"), appendCapped(old, "last", 3))
        assertEquals(listOf("first", "same", "same"), old)
        assertEquals(listOf("only"), appendCapped(old, "only", 1))
    }
}
