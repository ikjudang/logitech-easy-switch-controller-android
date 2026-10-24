package com.easyswitchcontroller.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionNoticeBatcherTest {
    @Test fun simultaneousConnectionsShareOneNoticeWithoutExtendingTheWindow() = runTest {
        val batches = mutableListOf<List<String>>()
        val batcher = ConnectionNoticeBatcher(this) { batches += it }
        batcher.add("keyboard")
        advanceTimeBy(200)
        batcher.add("mouse")
        advanceTimeBy(150)
        runCurrent()
        assertEquals(350L, currentTime)
        assertEquals(listOf(listOf("keyboard", "mouse")), batches)
    }

    @Test fun aSingleConnectionStillDisplaysItsOwnNotice() = runTest {
        val batches = mutableListOf<List<String>>()
        val batcher = ConnectionNoticeBatcher(this) { batches += it }
        batcher.add("keyboard")
        advanceTimeBy(350)
        runCurrent()
        assertEquals(listOf(listOf("keyboard")), batches)
    }

    @Test fun aDisconnectBeforeDisplayRemovesTheStaleConnection() = runTest {
        val batches = mutableListOf<List<String>>()
        val batcher = ConnectionNoticeBatcher(this) { batches += it }
        batcher.add("keyboard")
        batcher.add("mouse")
        batcher.remove("keyboard")
        advanceTimeBy(350)
        runCurrent()
        assertEquals(listOf(listOf("mouse")), batches)
    }

    @Test fun bluetoothOffCancelsThePendingNoticeAndAllowsANewBatch() = runTest {
        val batches = mutableListOf<List<String>>()
        val batcher = ConnectionNoticeBatcher(this) { batches += it }
        batcher.add("keyboard")
        batcher.clear()
        advanceTimeBy(350)
        runCurrent()
        assertTrue(batches.isEmpty())
        batcher.add("mouse")
        advanceTimeBy(350)
        runCurrent()
        assertEquals(listOf(listOf("mouse")), batches)
    }
}
