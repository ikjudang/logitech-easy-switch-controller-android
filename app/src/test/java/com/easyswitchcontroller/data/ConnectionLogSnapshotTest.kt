package com.easyswitchcontroller.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionLogSnapshotTest {
    private fun entry(time: Long, message: String) =
        ConnectionLog.Entry(time, message, ConnectionLog.Level.Info)

    @Test fun exportIncludesRecentEntriesWhenFileWriteFails() {
        val old = entry(1, "old")
        val missing = entry(2, "missing from file")
        val latest = entry(3, "latest")
        assertEquals(
            listOf(old, missing, latest),
            mergeLogEntries(listOf(old, latest), listOf(missing, latest)),
        )
    }

    @Test fun enablingSavingWritesOnlyEntriesMissingFromFile() {
        // 저장을 껐다 켜면, 꺼져 있던 동안 메모리에만 쌓인 줄만 파일에 덧붙이고 이미 쓴 줄은 다시 쓰지 않는다.
        val written = entry(1, "written before off")
        val whileOff = entry(2, "while off")
        assertEquals(listOf(whileOff), unwrittenEntries(listOf(written), listOf(written, whileOff)))
    }

    @Test fun exportPreservesRepeatedEntriesAtSameMillisecond() {
        val repeated = entry(1, "same event")
        assertEquals(
            listOf(repeated, repeated),
            mergeLogEntries(listOf(repeated), listOf(repeated, repeated)),
        )
        assertEquals(
            listOf(repeated, repeated),
            mergeLogEntries(listOf(repeated, repeated), listOf(repeated)),
        )
    }
}
