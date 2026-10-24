package com.easyswitchcontroller.device

import com.easyswitchcontroller.data.DeviceNotConnectedException
import com.easyswitchcontroller.hidpp.ChangeHostFeature
import com.easyswitchcontroller.hidpp.FeatureUnsupportedException
import com.easyswitchcontroller.hidpp.HidppConstants
import com.easyswitchcontroller.hidpp.HidppFrame
import com.easyswitchcontroller.hidpp.HidppProtocolException
import com.easyswitchcontroller.hidpp.RootFeature
import com.easyswitchcontroller.transport.ConnectionState
import com.easyswitchcontroller.transport.HidppTransport
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * 마우스/키보드 공통 기기 추상. HID++ 절차(연결 → feature index 조회 → 명령)를 캡슐화한다.
 * 마우스·키보드를 [kind]로 구분해 동일 코드로 다룬다.
 */
class LogiDevice(
    kind: DeviceKind,
    name: String,
    val address: String,
    private val transport: HidppTransport,
    private val softwareId: Int = HidppConstants.DEFAULT_SOFTWARE_ID,
) {
    @Volatile var kind: DeviceKind = kind
        private set
    @Volatile var name: String = name
        private set

    fun updateMetadata(name: String, kind: DeviceKind) {
        if (name.isNotBlank() && name != address) this.name = name
        this.kind = kind
    }

    private val features = FeatureIndexCache()

    /**
     * 마지막 전환을 시작한 단조 증가 시계 값(ms). 0이면 아직 보낸 적 없다.
     *
     * 전환 직후의 이 기기는 **떠나는 중**이라 건드리면 안 된다. 상시 연결이 곧바로 다시 붙잡으면
     * 떠나려던 기기가 그대로 묶여 전환이 되돌려진다(실측 2026-09-17: 전환 8번이 전부 되돌아왔다).
     * 인스턴스는 [com.easyswitchcontroller.di.ServiceLocator]에서 전역 공유되므로
     * 따라가기·UI 어느 경로로 보냈든 같은 값을 본다.
     */
    @Volatile
    var lastSwitchAtMs: Long = 0L
        private set

    fun noteSwitchStarted() { lastSwitchAtMs = System.nanoTime() / 1_000_000 }
    fun endSwitchGrace() { lastSwitchAtMs = 0L }
    val switchGraceActive: Boolean get() = lastSwitchAtMs != 0L &&
        System.nanoTime() / 1_000_000 - lastSwitchAtMs < 8_000

    @Volatile var localHostIndex: Int? = null
        private set

    fun rememberLocalHost(host: Int) {
        require(host in 0..2)
        localHostIndex = host
    }

    @Volatile var heldByService: Boolean = false

    val connectionState: StateFlow<ConnectionState> get() = transport.connectionState

    suspend fun connect() = transport.connect()

    suspend fun disconnect(force: Boolean = false) {
        if (force || !heldByService) transport.disconnect()
    }

    /** 임의 feature 요청(0x1B04 진단, 0x1814 getHostInfo 등). 표준 프레임을 넘기면 전송 계층이 BLE 형식으로 변환한다. */
    suspend fun request(frame: ByteArray): HidppFrame = transport.request(frame)

    /** 펌웨어 복귀 시험 전용. 일반 전환의 재시도 경로를 거치지 않고 정확히 한 번 보낸다. */
    internal suspend fun sendFirmwareProbeSwitch(featureIndex: Int, hostIndex: Int) {
        require(hostIndex in 0..2)
        noteSwitchStarted()
        transport.sendNoResponse(ChangeHostFeature.buildSetCurrentHost(featureIndex, hostIndex, softwareId))
    }

    /** 기기가 자발적으로 보내는 이벤트 스트림(요청과 1:1 아님). ChangeHost(0x1814) 이벤트 수신(키 따라가기 핵심 경로)·이벤트 감시용. */
    val notifications get() = transport.notifications

    /**
     * feature id의 런타임 index를 조회(캐시). Root feature getFeature 사용.
     * 응답 index가 0이면 미지원이므로 [FeatureUnsupportedException]을 던진다
     * (조용히 index 0 = Root를 실행하는 사고 방지).
     */
    suspend fun getFeatureIndex(featureId: Int): Int =
        features.getOrResolve(featureId) { id ->
            val request = RootFeature.buildGetFeature(id, softwareId)
            val response = transport.request(request)
            val lookup = RootFeature.parseGetFeatureResponse(response)
            if (!lookup.isSupported) throw FeatureUnsupportedException(id)
            lookup.featureIndex
        }

    /** 이미 조회한 feature index. 조회하지 않고 바로 알아야 할 때(감시 준비 전 신호 해석) 쓴다. */
    suspend fun cachedFeatureIndex(featureId: Int): Int? = features.cached(featureId)

    /**
     * 지정 호스트(0-based)로 채널 전환. 0x1814 index를 조회한 뒤 setCurrentHost를
     * 응답 없는 경로로 전송한다. 전송 직후의 링크 끊김은 정상이므로 성공으로 처리한다.
     *
     * **일시적 실패는 재시도한다.** BLE는 연결(status=133)·응답 타임아웃이 산발적으로 실패하는
     * 물건이라 1회 시도로는 "간혹 안 따라감"이 남는다. 전체 소요는 [BUDGET_MS]로 묶어
     * 따라가기가 기기 하나에 오래 붙잡히지 않게 한다.
     *
     * **재시도하지 않는 경우 둘:** 미지원·프로토콜 에러(다시 해도 결과가 같다)와, 기기가 이 폰에
     * 연결돼 있지 않은 경우다. 후자는 그 기기가 지금 다른 채널에 가 있다는 뜻이라 기다려도 안 생긴다
     * — 전환마다 헛되이 [RETRY_DELAY_MS]씩 쓰면 정작 연결된 기기의 전환만 늦어진다.
     */
    suspend fun setCurrentHost(hostIndex: Int): SwitchResult {
        var lastReason = "통신 오류"
        var attempted = 0
        val result = withTimeoutOrNull(BUDGET_MS) {
            repeat(ATTEMPTS) { attempt ->
                attempted = attempt + 1
                try {
                    val featureIndex = getFeatureIndex(HidppConstants.FEATURE_CHANGE_HOST)
                    transport.sendNoResponse(
                        ChangeHostFeature.buildSetCurrentHost(featureIndex, hostIndex, softwareId),
                    )
                    noteSwitchStarted()
                    return@withTimeoutOrNull SwitchResult.Switched
                } catch (e: FeatureUnsupportedException) {
                    return@withTimeoutOrNull SwitchResult.Failed(e.message ?: "ChangeHost(0x1814) 미지원")
                } catch (e: HidppProtocolException) {
                    return@withTimeoutOrNull SwitchResult.Failed(e.message ?: "프로토콜 에러")
                } catch (e: TimeoutCancellationException) {
                    // 예산 만료로 인한 취소라면 여기서 다시 던져 루프를 끝낸다(취소를 삼키지 않는다).
                    currentCoroutineContext().ensureActive()
                    lastReason = "응답 타임아웃"
                } catch (e: DeviceNotConnectedException) {
                    // 이 폰에 없는 기기 — 고장이 아니라 다른 채널에 가 있다는 뜻이라 기다려도
                    // 안 생긴다. 재시도 없이 즉시 확정한다. 앞선 시도가 다른 이유로 실패한 뒤라면
                    // 그 이유가 링크가 끊긴 원인일 수 있으니 덮지 않고 함께 남긴다.
                    val reason = e.message ?: "연결돼 있지 않음"
                    return@withTimeoutOrNull SwitchResult.Failed(
                        if (attempted > 1) "$reason (앞선 시도: $lastReason)" else reason,
                        notConnected = true,
                    )
                } catch (e: IOException) {
                    lastReason = e.message ?: "통신 오류"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SecurityException) {
                    return@withTimeoutOrNull SwitchResult.Failed("블루투스 권한이 필요해요.")
                } catch (e: IllegalStateException) {
                    return@withTimeoutOrNull SwitchResult.Failed(e.message ?: "블루투스를 사용할 수 없어요.")
                }
                if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MS)
            }
            null
        }
        return result ?: SwitchResult.Failed(
            if (attempted > 1) "$lastReason (${attempted}회 시도)" else lastReason,
        )
    }

    private companion object {
        /** 일시적 실패(BLE 연결 실패·응답 타임아웃·깨어나는 중)에 대한 시도 횟수. */
        const val ATTEMPTS = 3
        const val RETRY_DELAY_MS = 250L

        /** 재시도를 포함한 전체 상한. 최악의 연결 타임아웃이 겹쳐도 여기서 끊는다. */
        const val BUDGET_MS = 9_000L
    }
}
