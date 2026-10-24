package com.easyswitchcontroller.data

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConnectionLogThreadingTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

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
