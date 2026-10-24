package com.easyswitchcontroller.hidpp

/** short(0x10, 7B) / long(0x11, 20B) 리포트 종류. */
enum class ReportType(val reportId: Int, val totalLength: Int) {
    SHORT(HidppConstants.REPORT_ID_SHORT, HidppConstants.SHORT_LENGTH),
    LONG(HidppConstants.REPORT_ID_LONG, HidppConstants.LONG_LENGTH);

    /** 헤더 4바이트를 제외한 파라미터 영역 길이. */
    val paramLength: Int get() = totalLength - HidppConstants.HEADER_LENGTH

    companion object {
        fun fromReportId(reportId: Int): ReportType? =
            entries.firstOrNull { it.reportId == reportId }
    }
}

/**
 * HID++ 2.0 프레임의 구조적 표현.
 *
 * 빌드는 [build] 팩토리로 하고, 파싱 결과는 [HidppFrameParser.parse]가 이 타입으로 돌려준다.
 * [params]는 항상 [ReportType.paramLength]와 정확히 같은 길이로 유지된다(부족분은 0 패딩).
 */
data class HidppFrame(
    val reportType: ReportType,
    val deviceIndex: Int,
    val featureIndex: Int,
    /** function_id<<4 | software_id */
    val funcSwByte: Int,
    val params: ByteArray,
) {
    init {
        require(params.size == reportType.paramLength) {
            "params 길이는 ${reportType.paramLength}여야 함(현재 ${params.size})"
        }
        require(deviceIndex in 0..0xFF) { "deviceIndex 범위 밖: $deviceIndex" }
        require(featureIndex in 0..0xFF) { "featureIndex 범위 밖: $featureIndex" }
        require(funcSwByte in 0..0xFF) { "funcSwByte 범위 밖: $funcSwByte" }
    }

    val functionId: Int get() = (funcSwByte ushr 4) and 0x0F
    val softwareId: Int get() = funcSwByte and 0x0F

    /** 전송용 바이트 배열. 길이는 [ReportType.totalLength]. */
    fun toBytes(): ByteArray {
        val out = ByteArray(reportType.totalLength)
        out[0] = reportType.reportId.toByte()
        out[1] = deviceIndex.toByte()
        out[2] = featureIndex.toByte()
        out[3] = funcSwByte.toByte()
        params.copyInto(out, destinationOffset = HidppConstants.HEADER_LENGTH)
        return out
    }

    /** 응답이 이 요청을 에코하는지(device_index / feature_index / func|sw 일치). */
    fun echoes(deviceIndex: Int, featureIndex: Int, funcSwByte: Int): Boolean =
        this.deviceIndex == deviceIndex &&
            this.featureIndex == featureIndex &&
            this.funcSwByte == funcSwByte

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HidppFrame) return false
        return reportType == other.reportType &&
            deviceIndex == other.deviceIndex &&
            featureIndex == other.featureIndex &&
            funcSwByte == other.funcSwByte &&
            params.contentEquals(other.params)
    }

    override fun hashCode(): Int {
        var result = reportType.hashCode()
        result = 31 * result + deviceIndex
        result = 31 * result + featureIndex
        result = 31 * result + funcSwByte
        result = 31 * result + params.contentHashCode()
        return result
    }

    companion object {
        /**
         * 검증과 0 패딩을 거쳐 프레임을 만든다.
         * @param functionId 0..15 (상위 nibble)
         * @param softwareId 1..15, 0 제외 (하위 nibble)
         * @param params paramLength 이하. 부족분은 0으로 채워진다.
         */
        fun build(
            reportType: ReportType = ReportType.SHORT,
            deviceIndex: Int = HidppConstants.DEVICE_INDEX_BLE,
            featureIndex: Int,
            functionId: Int,
            softwareId: Int = HidppConstants.DEFAULT_SOFTWARE_ID,
            params: ByteArray = ByteArray(0),
        ): HidppFrame {
            require(functionId in 0x0..0xF) { "functionId는 nibble(0..15): $functionId" }
            require(softwareId in 0x1..0xF) { "softwareId는 1..15(0 제외): $softwareId" }
            val paramLen = reportType.paramLength
            require(params.size <= paramLen) {
                "params가 ${reportType}에 비해 김: ${params.size} > $paramLen"
            }
            val padded = if (params.size == paramLen) params else params.copyOf(paramLen)
            val funcSw = (functionId shl 4) or softwareId
            return HidppFrame(reportType, deviceIndex, featureIndex, funcSw, padded)
        }
    }
}
