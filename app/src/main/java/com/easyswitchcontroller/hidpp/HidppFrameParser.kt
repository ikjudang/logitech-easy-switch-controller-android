package com.easyswitchcontroller.hidpp

/** HID++ 2.0 에러 응답에서 뽑아낸 정보. */
data class HidppErrorInfo(
    /** 2 = HID++ 2.0 에러(byte[2]==0xFF), 1 = HID++ 1.0 에러(byte[2]==0x8F). */
    val protocol: Int,
    /** 실패한 요청의 func|sw 에코(byte[3]). 어느 요청의 에러인지 상관하는 데 쓴다. */
    val funcSwByte: Int,
    /** 실패한 feature index(2.0 기준 byte[4]). */
    val failedFeatureIndex: Int,
    /** error code(byte[5]). */
    val errorCode: Int,
) {
    val errorCodeEnum: HidppErrorCode? get() = HidppErrorCode.fromCode(errorCode)
}

/**
 * raw 바이트 → [HidppFrame] 파싱 및 에러 판별.
 *
 * 이 파서는 표준 [report_id][device_index][...] 프레임만 받는다. BLE 벤더 char의 실측 형식
 * (report_id 없는 18B)은 BleTransport의 toBleFrame/fromBleFrame이 변환하므로,
 * 파서 입력에는 항상 report_id가 포함된다(구조적 보장).
 */
object HidppFrameParser {

    /** 정상 프레임 파싱. 리포트 종류/길이가 맞지 않으면 null. 에러 프레임도 형식상 파싱은 됨. */
    fun parse(raw: ByteArray): HidppFrame? {
        if (raw.isEmpty()) return null
        val type = ReportType.fromReportId(raw[0].toInt() and 0xFF) ?: return null
        if (raw.size < type.totalLength) return null
        val deviceIndex = raw[1].toInt() and 0xFF
        val featureIndex = raw[2].toInt() and 0xFF
        val funcSw = raw[3].toInt() and 0xFF
        val params = raw.copyOfRange(HidppConstants.HEADER_LENGTH, type.totalLength)
        return HidppFrame(type, deviceIndex, featureIndex, funcSw, params)
    }

    /** HID++ 1.0/2.0 에러 응답인지. byte[2]가 에러 마커(SHORT/LONG 모두 허용). */
    fun isError(raw: ByteArray): Boolean {
        if (raw.size < ReportType.SHORT.totalLength) return false
        val reportId = raw[0].toInt() and 0xFF
        if (reportId != HidppConstants.REPORT_ID_SHORT && reportId != HidppConstants.REPORT_ID_LONG) return false
        val marker = raw[2].toInt() and 0xFF
        return marker == HidppConstants.HIDPP20_ERROR || marker == HidppConstants.HIDPP10_ERROR
    }

    /** 에러 응답을 [HidppErrorInfo]로. 에러가 아니면 null. */
    fun parseError(raw: ByteArray): HidppErrorInfo? {
        if (!isError(raw)) return null
        val marker = raw[2].toInt() and 0xFF
        val funcSw = raw[3].toInt() and 0xFF
        val b4 = raw[4].toInt() and 0xFF
        val b5 = raw[5].toInt() and 0xFF
        val protocol = if (marker == HidppConstants.HIDPP20_ERROR) 2 else 1
        return HidppErrorInfo(
            protocol = protocol,
            funcSwByte = funcSw,
            failedFeatureIndex = b4,
            errorCode = b5,
        )
    }
}
