package com.easyswitchcontroller.config

import com.easyswitchcontroller.hidpp.HidppConstants
import java.util.UUID

/**
 * 프로토콜 파라미터(vendor UUID·타임아웃)의 격리 지점.
 *
 * 실측 확정된 vendor service/characteristic UUID는
 * [com.easyswitchcontroller.di.ServiceLocator]가 이 타입으로 주입한다(리터럴은 그곳에만).
 *
 * 주의: **feature index는 여기에 두지 않는다.** feature index는 펌웨어마다 다르므로
 * 런타임에 Root feature로 조회해 [com.easyswitchcontroller.device.FeatureIndexCache]에만 존재한다.
 */
data class ProtocolConfig(
    /** Logitech 벤더 전용 128-bit 서비스 UUID. 이 앱의 유일한 hard dependency. */
    val vendorServiceUuid: UUID,
    /**
     * write characteristic UUID. null이면 vendor service 안에서
     * WRITE / WRITE_NO_RESPONSE 속성을 가진 char를 스캔해 찾는다(nice-to-have).
     */
    val writeCharUuid: UUID? = null,
    /** notify characteristic UUID. null이면 NOTIFY 속성 스캔으로 찾는다. */
    val notifyCharUuid: UUID? = null,
    /** Client Characteristic Configuration Descriptor. 표준값 고정. */
    val cccdUuid: UUID = CCCD_UUID,
    val softwareId: Int = HidppConstants.DEFAULT_SOFTWARE_ID,
    val requestTimeoutMs: Long = 1000,
    /** write-without-response에서 완료 콜백이 늦을 때 성공으로 간주하는 상한. */
    val writeCompleteTimeoutMs: Long = 500,
) {
    /** 실측 UUID가 주입됐는지. false면 BLE 전송을 시작하면 안 된다. */
    val isConfigured: Boolean get() = vendorServiceUuid != ZERO_UUID

    companion object {
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val ZERO_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

        /** 미설정 상태를 나타내는 값(isConfigured == false). 방어/테스트용. */
        val PLACEHOLDER = ProtocolConfig(vendorServiceUuid = ZERO_UUID)
    }
}
