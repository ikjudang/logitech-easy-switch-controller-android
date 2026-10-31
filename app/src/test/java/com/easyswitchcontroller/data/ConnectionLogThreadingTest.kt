package com.easyswitchcontroller.data

import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConnectionLogThreadingTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun transientFileAccessFailureRetriesEveryRecordWithoutDuplicatingExistingIdenticalEvents() {
        val actual = temporaryFolder.newFile("batch-recovery.tsv")
        val line = "1\tInfo\tsame event\n"
        actual.writeText(line)
        val firstWrite = AtomicBoolean(true)
        val failsOnce = object : File(actual.path) {
            override fun getPath(): String {
                if (firstWrite.getAndSet(false)) throw IOException("transient file access failure")
                return super.getPath()
            }
        }
        val entry = ConnectionLog.Entry(1, "same event", ConnectionLog.Level.Info)
        val write = ConnectionLog::class.java.getDeclaredMethod("writeLines", File::class.java, List::class.java)
            .apply { isAccessible = true }
        write.invoke(ConnectionLog, failsOnce, listOf(entry, entry, entry))
        assertTrue("All three new identical events and the old event must survive", actual.readText() == line.repeat(4))
    }

    @Test fun aBlockedExternalSnapshotDoesNotBlockTheInternalLogWriter() {
        val actualFile = temporaryFolder.newFile("independent-log.tsv")
        val externalStarted = CountDownLatch(1)
        val releaseExternal = CountDownLatch(1)
        val field = ConnectionLog::class.java.getDeclaredField("snapshotIo").apply { isAccessible = true }
        val external = field.get(null) as CoroutineScope
        val sink = ConnectionLog::class.java.getDeclaredField("sink").apply { isAccessible = true }
        val previous = sink.get(null)
        val caller = Executors.newSingleThreadExecutor()
        val blocked = external.launch {
            externalStarted.countDown()
            check(releaseExternal.await(10, TimeUnit.SECONDS))
        }
        try {
            assertTrue(externalStarted.await(5, TimeUnit.SECONDS))
            sink.set(null, actualFile)
            val flushed = caller.submit {
                ConnectionLog.append("internal logging while cloud provider is blocked")
                runBlocking { ConnectionLog.exportAllText().await() }
            }
            flushed.get(2, TimeUnit.SECONDS)
            assertTrue(actualFile.readText().contains("internal logging while cloud provider is blocked"))
        } finally {
            releaseExternal.countDown()
            runBlocking { blocked.join(); ConnectionLog.exportAllText().await() }
            sink.set(null, previous)
            caller.shutdownNow()
        }
    }

    @Test fun uiLoggingDoesNotWaitForAnInFlightFileWrite() {
        val actualFile = temporaryFolder.newFile("connection-log.tsv")
        val storageEntered = CountDownLatch(1)
        val releaseStorage = CountDownLatch(1)
        // FileOutputStream이 경로를 요청하는 지점에서 느린 저장 장치를 재현한다.
        val slowFile = object : File(actualFile.path) {
            override fun getPath(): String {
                storageEntered.countDown()
                check(releaseStorage.await(10, TimeUnit.SECONDS))
                return super.getPath()
            }
        }
        val sink = ConnectionLog::class.java.getDeclaredField("sink").apply { isAccessible = true }
        val previousSink = sink.get(null)
        val callers = Executors.newFixedThreadPool(2)
        try {
            sink.set(null, slowFile)
            val first = callers.submit { ConnectionLog.append("storage is busy") }
            assertTrue("The file writer must be in progress", storageEntered.await(5, TimeUnit.SECONDS))
            val ui = callers.submit { ConnectionLog.append("animation frame event") }
            try {
                ui.get(2, TimeUnit.SECONDS)
            } catch (_: TimeoutException) {
                fail("UI logging waited for file I/O to finish")
            } finally {
                releaseStorage.countDown()
                first.get(5, TimeUnit.SECONDS)
                ui.get(5, TimeUnit.SECONDS)
            }
            // 저장 큐를 비운 뒤 두 이벤트가 빠짐없이 파일에 남는지도 확인한다.
            runBlocking { ConnectionLog.exportAllText().await() }
            val persisted = actualFile.readText()
            assertTrue(persisted.contains("storage is busy"))
            assertTrue(persisted.contains("animation frame event"))
        } finally {
            releaseStorage.countDown()
            callers.shutdown()
            callers.awaitTermination(5, TimeUnit.SECONDS)
            runBlocking { ConnectionLog.exportAllText().await() }
            sink.set(null, previousSink)
        }
    }
}
