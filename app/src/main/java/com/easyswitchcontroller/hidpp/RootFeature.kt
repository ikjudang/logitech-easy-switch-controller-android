package com.easyswitchcontroller.hidpp

/** Root feature 조회 결과: featureId → 런타임 index + 메타. */
data class FeatureLookup(
    val featureIndex: Int,
    val typeFlags: Int = 0,
    val version: Int = 0,
) {
    /** index 0은 Root 자신이므로, 조회 대상 feature에 대해서는 "미지원"을 뜻한다. */
    val isSupported: Boolean get() = featureIndex != 0
}

/**
 * Root feature(index 0x00). function 0 = getFeature(featureId: UInt16).
 * 응답 params[0]이 그 feature의 런타임 index다. feature index는 절대 하드코딩하지 않고
 * 이 조회로만 얻는다(펌웨어 버전에 따라 달라짐).
 */
object RootFeature {

    /** getFeature 요청 바이트. params = [featureId_hi, featureId_lo]. */
    fun buildGetFeature(
        featureId: Int,
        softwareId: Int = HidppConstants.DEFAULT_SOFTWARE_ID,
    ): ByteArray {
        require(featureId in 0..0xFFFF) { "featureId는 16-bit: $featureId" }
        val hi = (featureId ushr 8) and 0xFF
        val lo = featureId and 0xFF
        return HidppFrame.build(
            reportType = ReportType.SHORT,
            deviceIndex = HidppConstants.DEVICE_INDEX_BLE,
            featureIndex = HidppConstants.ROOT_FEATURE_INDEX,
            functionId = HidppConstants.ROOT_FN_GET_FEATURE,
            softwareId = softwareId,
            params = byteArrayOf(hi.toByte(), lo.toByte()),
        ).toBytes()
    }

    /** getFeature 응답 파싱. params[0]=index, [1]=type flags, [2]=version. */
    fun parseGetFeatureResponse(frame: HidppFrame): FeatureLookup {
        val index = frame.params.getOrElse(0) { 0 }.toInt() and 0xFF
        val typeFlags = frame.params.getOrElse(1) { 0 }.toInt() and 0xFF
        val version = frame.params.getOrElse(2) { 0 }.toInt() and 0xFF
        return FeatureLookup(index, typeFlags, version)
    }
}
