package com.easyswitchcontroller.hidpp

/** HID++ 2.0 표준 error code. */
enum class HidppErrorCode(val code: Int) {
    NO_ERROR(0x00),
    UNKNOWN(0x01),
    INVALID_ARGUMENT(0x02),
    OUT_OF_RANGE(0x03),
    HARDWARE_ERROR(0x04),
    LOGITECH_INTERNAL(0x05),
    INVALID_FEATURE_INDEX(0x06),
    INVALID_FUNCTION_ID(0x07),
    BUSY(0x08),
    UNSUPPORTED(0x09);

    companion object {
        fun fromCode(code: Int): HidppErrorCode? = entries.firstOrNull { it.code == code }
    }
}

/** 기기가 에러 응답을 돌려줬을 때. */
class HidppProtocolException(
    val info: HidppErrorInfo,
) : Exception(
    "HID++ ${info.protocol}.0 error: code=0x%02X(%s) feature=0x%02X".format(
        info.errorCode,
        info.errorCodeEnum?.name ?: "?",
        info.failedFeatureIndex,
    ),
)

/**
 * feature index 조회 결과가 0(미지원)일 때.
 *
 * 사양서 지침: index==0이면 그 feature가 없는 것이므로, 조용히 엉뚱한 feature(index 0 = Root)를
 * 실행하지 않도록 반드시 예외로 승격한다.
 */
class FeatureUnsupportedException(
    val featureId: Int,
) : Exception("Feature 0x%04X 미지원(index 0)".format(featureId))
