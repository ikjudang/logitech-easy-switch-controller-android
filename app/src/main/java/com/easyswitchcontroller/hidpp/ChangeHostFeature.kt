package com.easyswitchcontroller.hidpp

/**
 * ChangeHost(0x1814).
 *  - function 0 = getHostInfo (현재 호스트/슬롯 수 등, 선택)
 *  - function 1 = setCurrentHost(hostIndex) — hostIndex는 0-based
 *
 * setCurrentHost 주의: 보내면 기기가 즉시 링크를 끊고 ack가 오지 않는다.
 * 따라서 이 빌더가 만든 프레임은 응답을 기다리지 않는 경로(sendNoResponse)로 전송해야 한다.
 * featureIndex는 반드시 [RootFeature] 조회로 얻은 런타임 값을 주입한다(하드코딩 금지).
 */
object ChangeHostFeature {

    fun buildSetCurrentHost(
        featureIndex: Int,
        hostIndex: Int,
        softwareId: Int = HidppConstants.DEFAULT_SOFTWARE_ID,
    ): ByteArray {
        require(hostIndex in 0..0xFF) { "hostIndex(0-based) 범위 밖: $hostIndex" }
        return HidppFrame.build(
            reportType = ReportType.SHORT,
            deviceIndex = HidppConstants.DEVICE_INDEX_BLE,
            featureIndex = featureIndex,
            functionId = HidppConstants.CHANGEHOST_FN_SET_CURRENT_HOST,
            softwareId = softwareId,
            params = byteArrayOf(hostIndex.toByte()),
        ).toBytes()
    }

    fun buildGetHostInfo(
        featureIndex: Int,
        softwareId: Int = HidppConstants.DEFAULT_SOFTWARE_ID,
    ): ByteArray =
        HidppFrame.build(
            reportType = ReportType.SHORT,
            deviceIndex = HidppConstants.DEVICE_INDEX_BLE,
            featureIndex = featureIndex,
            functionId = HidppConstants.CHANGEHOST_FN_GET_HOST_INFO,
            softwareId = softwareId,
        ).toBytes()

    /** fn0 getHostInfo 응답: params[0]=호스트 수, params[1]=현재 호스트(0-based). */
    fun parseGetHostInfo(frame: HidppFrame): HostInfo {
        val count = frame.params.getOrElse(0) { 0 }.toInt() and 0xFF
        val current = frame.params.getOrElse(1) { 0 }.toInt() and 0xFF
        require(count in 1..3 && current in 0 until count) { "잘못된 호스트 정보: count=$count, current=$current" }
        return HostInfo(hostCount = count, currentHost = current, flags = frame.params[2].toInt() and 0xFF)
    }

    /** x1814 v0 fn2: 채널별 영구 쿠키를 읽기만 한다. fn3(쓰기)는 진단에 사용하지 않는다. */
    fun buildGetCookies(featureIndex: Int, softwareId: Int = HidppConstants.DEFAULT_SOFTWARE_ID): ByteArray =
        HidppFrame.build(featureIndex = featureIndex, functionId = 2, softwareId = softwareId).toBytes()

    fun parseCookies(frame: HidppFrame, hostCount: Int): List<Int> {
        require(hostCount in 1..3 && frame.params.size >= hostCount)
        return frame.params.take(hostCount).map { it.toInt() and 0xFF }
    }

    /**
     * 기기가 **스스로 채널 전환을 시작할 때** 현재 호스트로 보내는 이벤트를 파싱한다.
     *
     * MX MCHNCL 실측(2026-07): Easy-Switch 키를 누르면 링크가 끊기기 직전
     * `[featureIndex=0x1814의 index][funcSw=0x00][00][targetHost]...` 이벤트가 온다.
     * funcSw=0x00(function 0, softwareId 0)은 기기 자발 이벤트의 표식이다(응답은 swId를 에코).
     *
     * params[0]은 판정에 쓰지 않는다. 0.2.17에서 "params[0]이 0이 아니면 버린다"를 넣은 뒤
     * MX KEYS S의 전환 감지가 0건이 됐다(2026-10-19, 8회 전환 모두 무반응). 그 조건이 없던
     * 0.2.14까지는 같은 키보드에서 감시 260회 중 254회 감지됐다. MX KEYS S가 params[0]에
     * 무엇을 싣는지는 아직 실측 전이라, 감시 쪽에서 원본 바이트를 로그로 남긴다.
     * 실측 잡음(MX MCHNCL `09 00 4B 08`)은 목적지 범위 검사로 걸러진다.
     *
     * @return 목적지 호스트(0-based). 이 프레임이 해당 이벤트가 아니면 null.
     */
    fun parseHostChangeEvent(frame: HidppFrame, changeHostFeatureIndex: Int): Int? {
        if (frame.featureIndex != changeHostFeatureIndex) return null
        if (frame.funcSwByte != 0x00) return null
        return frame.params.getOrNull(1)?.toInt()?.and(0xFF)?.takeIf { it in 0..2 }
    }
}

/** getHostInfo 결과. */
data class HostInfo(val hostCount: Int, val currentHost: Int, val flags: Int = 0) {
    val enhancedHostSwitch: Boolean get() = flags and 1 != 0
}
