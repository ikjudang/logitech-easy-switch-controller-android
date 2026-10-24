package com.easyswitchcontroller.device

import com.easyswitchcontroller.hidpp.ChangeHostFeature
import com.easyswitchcontroller.hidpp.HidppConstants
import com.easyswitchcontroller.hidpp.HidppFrame
import com.easyswitchcontroller.hidpp.HostInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class FirmwareProbePhase { IDLE, PREPARING, OBSERVING, VERIFYING, FINISHED }
enum class FirmwareProbeOutcome { RETURN_OBSERVED, NO_DEPARTURE_OBSERVED, NO_RETURN_OBSERVED, INCONCLUSIVE, INTERRUPTED, ERROR, CANCELLED }

data class FirmwareProbeState(
    val phase: FirmwareProbePhase = FirmwareProbePhase.IDLE,
    val deviceName: String = "",
    val originChannel: Int? = null,
    val targetChannel: Int? = null,
    val elapsedSeconds: Int = 0,
    val outcome: FirmwareProbeOutcome? = null,
) {
    val running: Boolean get() = phase != FirmwareProbePhase.IDLE && phase != FirmwareProbePhase.FINISHED
}

/**
 * x1814 공식 v0 사양의 펌웨어 복귀를 관찰한다. flags/cookie는 읽기 전용이다.
 * 한 번의 목적지 명령 뒤 GATT를 닫고, ACL의 끊김→연결을 수동 관찰한다.
 * ACL 복귀가 관측되기 전에는 connect/request를 절대 호출하지 않는다.
 * 호출자는 SwitchTransactions.mutex와 mouseMaintenanceMutex를 시험 전체에 보유해야 한다.
 */
internal class FirmwareReturnProbe(
    private val connected: (LogiDevice) -> Boolean?,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val observationMs: Long = 60_000,
    private val pollMs: Long = 100,
    private val log: (String) -> Unit,
    private val update: (FirmwareProbeState) -> Unit,
    private val interrupted: () -> Boolean = { false },
    private val connectionEvents: Flow<Boolean> = emptyFlow(),
) {
    suspend fun run(device: LogiDevice, targetChannel: Int) = coroutineScope {
        val started = nowMs()
        var state = FirmwareProbeState(FirmwareProbePhase.PREPARING, device.name, targetChannel = targetChannel)
        fun record(message: String) = log("[FW-RETURN] ${device.name} +${nowMs() - started}ms $message")
        fun publish(phase: FirmwareProbePhase = state.phase, outcome: FirmwareProbeOutcome? = state.outcome) {
            state = state.copy(phase = phase, elapsedSeconds = ((nowMs() - started) / 1000).toInt(), outcome = outcome)
            update(state)
        }
        val observing = java.util.concurrent.atomic.AtomicBoolean()
        val departureSeen = java.util.concurrent.atomic.AtomicBoolean()
        val aclEvents = launch(start = CoroutineStart.UNDISPATCHED) {
            connectionEvents.collect { connected ->
                if (observing.get()) {
                    if (!connected) departureSeen.set(true)
                    record("ACL_EVENT=$connected")
                }
            }
        }
        publish()
        record("BEGIN target=$targetChannel writes_allowed=setCurrentHost_once settings_writes=0")
        try {
            require(device.kind == DeviceKind.MOUSE) { "시험 대상은 마우스여야 합니다." }
            require(targetChannel in 1..3) { "잘못된 목적지 채널입니다." }
            check(connected(device) == true) { "이 기기에 연결된 마우스만 시험할 수 있습니다." }
            val (featureIndex, before) = withTimeout(8_000) {
                device.connect()
                val index = device.getFeatureIndex(HidppConstants.FEATURE_CHANGE_HOST)
                val frame = device.request(ChangeHostFeature.buildGetHostInfo(index))
                record("HOST_BEFORE ${frame.hexParams()}")
                index to ChangeHostFeature.parseGetHostInfo(frame)
            }
            require(targetChannel <= before.hostCount && targetChannel - 1 != before.currentHost) {
                "목적지는 현재 채널과 다른 등록 채널 범위여야 합니다."
            }
            state = state.copy(originChannel = before.currentHost + 1)
            record("ORIGIN=${before.currentHost + 1} flags=0x%02X ehs=${before.enhancedHostSwitch}".format(before.flags))
            try {
                val cookies = withTimeout(2_000) {
                    ChangeHostFeature.parseCookies(device.request(ChangeHostFeature.buildGetCookies(featureIndex)), before.hostCount)
                }
                record("COOKIES " + cookies.mapIndexed { index, cookie -> "ch${index + 1}=0x%02X".format(cookie) }.joinToString(" "))
                val candidates = cookies.indices.filter { it != before.currentHost && it != targetChannel - 1 && cookies[it] != 0 }.map { it + 1 }
                record("FALLBACK_CANDIDATES=$candidates (saved cookies do not prove host availability)")
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
                record("COOKIES_UNAVAILABLE ${error.javaClass.simpleName}")
            } catch (error: Exception) {
                record("COOKIES_UNAVAILABLE ${error.javaClass.simpleName}: ${error.message}")
            }
            check(connected(device) == true) { "명령 전 마우스 연결이 끊겼습니다." }
            if (interrupted()) {
                record("INTERRUPTED before command")
                publish(FirmwareProbePhase.FINISHED, FirmwareProbeOutcome.INTERRUPTED)
                return@coroutineScope
            }
            record("SWITCH_ONCE origin=${before.currentHost + 1} target=$targetChannel")
            observing.set(true)
            try {
                withTimeout(2_000) { device.sendFirmwareProbeSwitch(featureIndex, targetChannel - 1) }
                record("COMMAND_WRITE_COMPLETED")
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
                record("COMMAND_WRITE_UNCONFIRMED ${error.javaClass.simpleName}; observe without retry")
            } catch (error: Exception) {
                record("COMMAND_WRITE_UNCONFIRMED ${error.javaClass.simpleName}: ${error.message}; observe without retry")
            } finally {
                withContext(NonCancellable) { device.disconnect(force = true) }
            }
            publish(FirmwareProbePhase.OBSERVING)
            val observationStarted = nowMs()
            var departed = false
            var unknown = false
            var previous: Boolean? = true
            while (nowMs() - observationStarted < observationMs) {
                if (interrupted()) {
                    record("INTERRUPTED by another switch; automatic return cannot be attributed")
                    publish(FirmwareProbePhase.FINISHED, FirmwareProbeOutcome.INTERRUPTED)
                    return@coroutineScope
                }
                val acl = connected(device)
                unknown = unknown || acl == null
                if (acl != previous) { record("ACL=$acl"); previous = acl }
                if (acl == false || departureSeen.get()) departed = true
                if (departed && acl == true) {
                    record("ACL_RETURN_OBSERVED reconnect_requests_before_return=0")
                    publish(FirmwareProbePhase.VERIFYING)
                    val after: HostInfo = withTimeout(5_000) {
                        device.connect()
                        val frame = device.request(ChangeHostFeature.buildGetHostInfo(featureIndex))
                        record("HOST_AFTER ${frame.hexParams()}")
                        ChangeHostFeature.parseGetHostInfo(frame)
                    }
                    val outcome = if (interrupted()) FirmwareProbeOutcome.INTERRUPTED
                        else if (connected(device) != true) FirmwareProbeOutcome.INCONCLUSIVE
                        else if (after.currentHost == before.currentHost) FirmwareProbeOutcome.RETURN_OBSERVED
                        else FirmwareProbeOutcome.INCONCLUSIVE
                    record("RESULT=$outcome host_after=${after.currentHost + 1} ehs_before=${before.enhancedHostSwitch}; firmware-only attribution requires no external intervention")
                    publish(FirmwareProbePhase.FINISHED, outcome)
                    return@coroutineScope
                }
                if (state.elapsedSeconds != ((nowMs() - started) / 1000).toInt()) publish()
                delay(pollMs)
            }
            val outcome = when {
                unknown -> FirmwareProbeOutcome.INCONCLUSIVE
                departed -> FirmwareProbeOutcome.NO_RETURN_OBSERVED
                else -> FirmwareProbeOutcome.NO_DEPARTURE_OBSERVED
            }
            record("RESULT=$outcome observation_ms=$observationMs; this does not prove destination connection or firmware absence")
            publish(FirmwareProbePhase.FINISHED, outcome)
        } catch (error: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            record("RESULT=ERROR request timeout; return unconfirmed")
            publish(FirmwareProbePhase.FINISHED, FirmwareProbeOutcome.ERROR)
        } catch (error: CancellationException) {
            record("RESULT=CANCELLED no recovery command sent")
            publish(FirmwareProbePhase.FINISHED, FirmwareProbeOutcome.CANCELLED)
            throw error
        } catch (error: Exception) {
            record("RESULT=ERROR ${error.javaClass.simpleName}: ${error.message}")
            publish(FirmwareProbePhase.FINISHED, FirmwareProbeOutcome.ERROR)
        } finally {
            observing.set(false)
            aclEvents.cancel()
            withContext(NonCancellable) {
                try { device.disconnect(force = true) }
                catch (error: Exception) { record("CLEANUP_ERROR ${error.javaClass.simpleName}: ${error.message}") }
            }
            record("END settings_writes=0 recovery_commands=0")
        }
    }

    private fun HidppFrame.hexParams() = params.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
