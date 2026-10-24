package com.easyswitchcontroller.transport

import com.easyswitchcontroller.hidpp.HidppFrame
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** GATT 연결 수명 상태. */
enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected, // 링크는 됐으나 서비스 디스커버리 전
    ServicesReady, // vendor char 확보 + CCCD 활성 완료. 명령 전송 가능
}

/** write가 물리적으로 나갔음을 뜻하는 최소 ack. 응답(HidppFrame)과는 다르다. */
sealed interface WriteAck {
    data object Written : WriteAck
}

/**
 * HID++ 프레임을 실어 나르는 전송 계층 추상.
 *
 * 이 인터페이스의 목적은 (1) 실제 BLE 스택을 [com.easyswitchcontroller.hidpp]/device 로직에서
 * 분리해 단위테스트 가능하게 하고(FakeHidppTransport 주입), (2) 마우스·키보드를
 * 동일 코드로 다루기 위한 경계를 제공하는 것이다.
 *
 * 응답이 오는 명령([request])과 응답이 오지 않는 명령([sendNoResponse])을 **타입 수준에서 분리**한다.
 */
interface HidppTransport {

    val connectionState: StateFlow<ConnectionState>

    /** 기기가 보내는 모든 notify 프레임 스트림(요청과 1:1이 아님). */
    val notifications: SharedFlow<HidppFrame>

    /** connectGatt → discoverServices → vendor char 확보 → CCCD 활성까지 한 번에. */
    suspend fun connect()

    suspend fun disconnect()

    /**
     * 응답이 오는 명령(예: Root getFeature). 요청을 write하고 [timeout] 내에 에코가 일치하는
     * notify를 기다려 돌려준다. 에러 프레임이면 [com.easyswitchcontroller.hidpp.HidppProtocolException].
     * 타임아웃이면 [kotlinx.coroutines.TimeoutCancellationException].
     */
    suspend fun request(
        frame: ByteArray,
        timeout: Duration = 1000.milliseconds,
    ): HidppFrame

    /**
     * 응답이 오지 않는 명령(예: ChangeHost setCurrentHost).
     *
     * 사양서 핵심 함정: setCurrentHost를 보내면 기기가 즉시 링크를 끊고 ack가 오지 않는다.
     * 이 메서드에는 notify를 기다리는 경로가 **아예 없어서** 타임아웃 오탐이 구조적으로 불가능하다.
     * write가 나갔거나(또는 직후 정상 disconnect가 관측되면) [WriteAck.Written]으로 성공 처리한다.
     */
    suspend fun sendNoResponse(frame: ByteArray): WriteAck
}
