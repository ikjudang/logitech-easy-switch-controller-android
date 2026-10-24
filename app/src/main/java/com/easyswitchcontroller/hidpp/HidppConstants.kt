package com.easyswitchcontroller.hidpp

/**
 * HID++ 2.0 프로토콜 상수. 순수 Kotlin(안드로이드 의존 없음).
 *
 * 프레임 구조:
 *   [report_id][device_index][feature_index][function_id<<4 | software_id][params...]
 *
 * 레퍼런스: Solaar, logiops 위키 "HIDPP 2.0", 리눅스 커널 drivers/hid/hid-logitech-hidpp.c
 */
object HidppConstants {
    // Report IDs / 길이
    const val REPORT_ID_SHORT = 0x10 // 총 7바이트
    const val REPORT_ID_LONG = 0x11 // 총 20바이트
    const val SHORT_LENGTH = 7
    const val LONG_LENGTH = 20
    const val HEADER_LENGTH = 4 // report_id, device_index, feature_index, func|sw

    /**
     * 로지텍 BLE 벤더 characteristic(00010001)이 쓰는 리포트 길이.
     * report_id/device_index 없이 feature_index부터 담는다(실측 확인).
     * 표준 프레임에서 앞 2바이트를 떼면 [feature_index][func|sw][params...] 18바이트가 된다.
     */
    const val BLE_REPORT_LENGTH = 18

    /**
     * BLE 직결 기기의 device_index.
     * Projecteur/Spotlight 문서는 0x01이라 하지만, MX Master 4는 실측 결과 0xFF에서만 응답한다
     * (0x01로 보내면 기기가 침묵). 기기마다 다를 수 있어 실측으로 확정한 값이다.
     */
    const val DEVICE_INDEX_BLE = 0xFF

    /**
     * software_id: func|sw 바이트의 하위 4비트(1..15, 0 제외).
     * 내가 보낸 요청의 응답을 식별하는 용도. 응답은 이 값을 에코한다.
     */
    const val DEFAULT_SOFTWARE_ID = 0x0A

    // Root feature (항상 index 0x00)
    const val ROOT_FEATURE_INDEX = 0x00
    const val ROOT_FN_GET_FEATURE = 0x00

    // Feature IDs (16-bit). index가 아니라 feature "id"임.
    const val FEATURE_ROOT = 0x0000
    const val FEATURE_CHANGE_HOST = 0x1814
    const val FEATURE_HOSTS_INFO = 0x1815

    /** 0x1B04 Reprogrammable Keys v4 — 키 목록/flags 진단 덤프용. Easy-Switch 키는 divert 불가(flags=0x04) 실측 확정. */
    const val FEATURE_REPROG_KEYS = 0x1B04

    // ChangeHost(0x1814) 함수
    const val CHANGEHOST_FN_GET_HOST_INFO = 0x00
    const val CHANGEHOST_FN_SET_CURRENT_HOST = 0x01

    /**
     * 에러 마커(feature_index 위치, byte[2]).
     * HID++ 2.0 에러 = 0xFF, HID++ 1.0 에러 sub_id = 0x8F.
     * 커널 hid-logitech-hidpp.c 기준: 2.0 에러는 SHORT 리포트, byte[2]==0xFF,
     * byte[3]=요청 func|sw 에코, byte[4]=실패한 feature index, byte[5]=error code.
     */
    const val HIDPP20_ERROR = 0xFF
    const val HIDPP10_ERROR = 0x8F
}
