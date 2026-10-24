package com.easyswitchcontroller.device

import com.easyswitchcontroller.fakes.FakeHidppTransport
import com.easyswitchcontroller.hidpp.HidppFrame
import com.easyswitchcontroller.hidpp.HidppFrameParser
import com.easyswitchcontroller.hidpp.ReportType
import com.easyswitchcontroller.transport.HidppTransport
import com.easyswitchcontroller.transport.WriteAck
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class FirmwareReturnProbeTest {
    private class Harness(val now: () -> Long) {
        val fake = FakeHidppTransport(featureIndexProvider = { 14 })
        val connects = mutableListOf<Long>()
        val requests = mutableListOf<HidppFrame>()
        var sentAt: Long? = null
        var hostAfter = 2
        var flags = 1
        var failCookies = false
        var stallBefore = false
        var afterSend: suspend () -> Unit = {}
        val logs = mutableListOf<String>()
        val states = mutableListOf<FirmwareProbeState>()
        val device = LogiDevice(DeviceKind.MOUSE, "Test mouse", "AA:BB:CC:DD:EE:FF", object : HidppTransport by fake {
            override suspend fun connect() { connects += now(); fake.connect() }
            override suspend fun request(frame: ByteArray, timeout: Duration): HidppFrame {
                val request = HidppFrameParser.parse(frame)!!
                requests += request
                if (request.featureIndex == 0) return fake.request(frame, timeout)
                if (stallBefore && request.functionId == 0) awaitCancellation()
                if (failCookies && request.functionId == 2) throw IOException("cookies unavailable")
                return HidppFrame.build(ReportType.SHORT, request.deviceIndex, request.featureIndex, request.functionId, request.softwareId,
                    when (request.functionId) {
                        0 -> byteArrayOf(3, (if (sentAt == null) 2 else hostAfter).toByte(), flags.toByte())
                        2 -> byteArrayOf(0x11, 0, 0x22)
                        else -> error("Unexpected request ${request.functionId}")
                    })
            }
            override suspend fun sendNoResponse(frame: ByteArray): WriteAck {
                sentAt = now()
                val ack = fake.sendNoResponse(frame)
                afterSend()
                return ack
            }
        })

        suspend fun run(acl: () -> Boolean?, target: Int = 2, interrupted: () -> Boolean = { false }, events: Flow<Boolean> = emptyFlow()) {
            FirmwareReturnProbe(connected = { acl() }, nowMs = now, observationMs = 2_000, pollMs = 100,
                log = logs::add, update = states::add, interrupted = interrupted, connectionEvents = events).run(device, target)
        }
        fun outcome() = states.last().outcome
    }

    @Test fun `return requires a real ACL departure and original channel reply without app reconnection while absent`() = runTest {
        val h = Harness { testScheduler.currentTime }
        h.run(acl = { h.sentAt?.let { testScheduler.currentTime - it >= 600 } ?: true })
        assertEquals(FirmwareProbeOutcome.RETURN_OBSERVED, h.outcome())
        assertEquals(listOf(0L, 600L), h.connects)
        assertEquals(1, h.fake.sentFrames.size)
        assertEquals(1, HidppFrameParser.parse(h.fake.sentFrames.single())!!.functionId)
        assertTrue(h.requests.none { it.functionId == 3 })
        assertTrue(h.logs.any { "reconnect_requests_before_return=0" in it })
    }

    @Test fun `GATT cleanup alone is never counted as Bluetooth departure or firmware return`() = runTest {
        val h = Harness { testScheduler.currentTime }
        h.run(acl = { true })
        assertEquals(FirmwareProbeOutcome.NO_DEPARTURE_OBSERVED, h.outcome())
        assertEquals(1, h.connects.size)
    }

    @Test fun `missing return is bounded and never causes a recovery command or connection attempt`() = runTest {
        val h = Harness { testScheduler.currentTime }
        h.run(acl = { h.sentAt == null })
        assertEquals(FirmwareProbeOutcome.NO_RETURN_OBSERVED, h.outcome())
        assertEquals(2_000L, testScheduler.currentTime)
        assertEquals(1, h.connects.size)
        assertEquals(1, h.fake.sentFrames.size)
    }

    @Test fun `unknown ACL stays inconclusive and cannot produce a return`() = runTest {
        val h = Harness { testScheduler.currentTime }
        h.run(acl = { if (h.sentAt == null) true else null })
        assertEquals(FirmwareProbeOutcome.INCONCLUSIVE, h.outcome())
        assertEquals(1, h.connects.size)
    }

    @Test fun `other reported host after reconnection is not original channel return`() = runTest {
        val h = Harness { testScheduler.currentTime }
        h.hostAfter = 0
        h.run(acl = { h.sentAt?.let { testScheduler.currentTime - it >= 600 } ?: true })
        assertEquals(FirmwareProbeOutcome.INCONCLUSIVE, h.outcome())
    }

    @Test fun `inactive EHS and unavailable cookies remain read only and still permit observation`() = runTest {
        val h = Harness { testScheduler.currentTime }
        h.flags = 0
        h.failCookies = true
        h.run(acl = { h.sentAt?.let { testScheduler.currentTime - it >= 600 } ?: true })
        assertEquals(FirmwareProbeOutcome.RETURN_OBSERVED, h.outcome())
        assertTrue(h.logs.any { "ehs=false" in it })
        assertTrue(h.logs.any { "COOKIES_UNAVAILABLE" in it })
        assertTrue(h.requests.none { it.functionId == 3 })
    }

    @Test fun `same channel and disconnected device are rejected before any switch command`() = runTest {
        val h = Harness { testScheduler.currentTime }
        h.run(acl = { true }, target = 3)
        assertEquals(FirmwareProbeOutcome.ERROR, h.outcome())
        assertTrue(h.fake.sentFrames.isEmpty())
        val absent = Harness { testScheduler.currentTime }
        absent.run(acl = { false })
        assertTrue(absent.connects.isEmpty())
        assertTrue(absent.fake.sentFrames.isEmpty())
    }

    @Test fun `ambiguous write is observed without retrying a channel change`() = runTest {
        val h = Harness { testScheduler.currentTime }
        h.fake.sendError = IOException("write result unknown")
        h.run(acl = { h.sentAt?.let { testScheduler.currentTime - it >= 600 } ?: true })
        assertEquals(FirmwareProbeOutcome.RETURN_OBSERVED, h.outcome())
        assertEquals(1, h.fake.sentFrames.size)
    }

    @Test fun `another switch interrupts observation rather than attributing external reconnection to firmware`() = runTest {
        val h = Harness { testScheduler.currentTime }
        h.run(acl = { h.sentAt == null }, interrupted = { testScheduler.currentTime >= 300 })
        assertEquals(FirmwareProbeOutcome.INTERRUPTED, h.outcome())
        assertEquals(1, h.connects.size)
    }

    @Test fun `cancellation closes control connection without switching back`() = runTest {
        val h = Harness { testScheduler.currentTime }
        val job = async { h.run(acl = { h.sentAt == null }) }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(FirmwareProbeOutcome.CANCELLED, h.outcome())
        assertEquals(1, h.fake.sentFrames.size)
        assertEquals(com.easyswitchcontroller.transport.ConnectionState.Disconnected, h.device.connectionState.value)
    }

    @Test fun `short ACL cycle during command completion is preserved by events`() = runTest {
        val h = Harness { testScheduler.currentTime }
        val events = MutableSharedFlow<Boolean>()
        h.afterSend = { events.emit(false); events.emit(true) }
        h.run(acl = { true }, events = events)
        assertEquals(FirmwareProbeOutcome.RETURN_OBSERVED, h.outcome())
        assertEquals(1, h.fake.sentFrames.size)
    }

    @Test fun `preflight timeout is an error without a switch and not user cancellation`() = runTest {
        val h = Harness { testScheduler.currentTime }
        h.stallBefore = true
        h.run(acl = { true })
        assertEquals(FirmwareProbeOutcome.ERROR, h.outcome())
        assertTrue(h.fake.sentFrames.isEmpty())
    }
}
