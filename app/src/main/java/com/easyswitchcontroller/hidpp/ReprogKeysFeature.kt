package com.easyswitchcontroller.hidpp

/**
 * 0x1B04 Reprogrammable Keys / Special Keys and Buttons (v4).
 *
 * 키 목록/flags 진단 덤프용(ChannelViewModel.probeReprogKeys). divert 가설은 실측으로 기각됨:
 * Easy-Switch 키(cid 0xD1~D3)는 flags=0x04로 divert 불가라, 전환 감지는 ChangeHost(0x1814)
 * 이벤트 수신으로 한다([com.easyswitchcontroller.service.KeyboardFollowManager] 참조).
 *
 * flags 비트/응답 오프셋은 Solaar/logiops 기준이며 D0 진단 실측으로 확인됨(MX MCHNCL).
 */
object ReprogKeysFeature {

    const val FN_GET_COUNT = 0x00
    const val FN_GET_CID_INFO = 0x01
    const val FN_GET_CID_REPORTING = 0x02
    const val FN_SET_CID_REPORTING = 0x03

    // getCidInfo flags 바이트 비트
    const val FLAG_MOUSE = 0x01
    const val FLAG_FKEY = 0x02
    const val FLAG_HOTKEY = 0x04
    const val FLAG_FNTOGGLE = 0x08
    const val FLAG_REPROG = 0x10
    const val FLAG_DIVERTABLE = 0x20
    const val FLAG_PERSIST = 0x40
    const val FLAG_VIRTUAL = 0x80

    // setCidReporting flags: dvalid(0x02)로 divert 비트를 유효화, divert(0x01)로 켬
    private const val SET_DIVERT_ON = 0x03 // dvalid | divert
    private const val SET_DIVERT_OFF = 0x02 // dvalid, divert=0

    private fun frame(featureIndex: Int, fn: Int, sw: Int, params: ByteArray) =
        HidppFrame.build(
            reportType = ReportType.SHORT,
            deviceIndex = HidppConstants.DEVICE_INDEX_BLE,
            featureIndex = featureIndex,
            functionId = fn,
            softwareId = sw,
            params = params,
        ).toBytes()

    /** fn0 getCount → 컨트롤(키/버튼) 개수. */
    fun buildGetCount(featureIndex: Int, softwareId: Int = HidppConstants.DEFAULT_SOFTWARE_ID): ByteArray =
        frame(featureIndex, FN_GET_COUNT, softwareId, ByteArray(0))

    fun parseGetCount(response: HidppFrame): Int = response.params.getOrElse(0) { 0 }.toInt() and 0xFF

    /** fn1 getCidInfo(index) → 해당 컨트롤의 CID/task/flags 등. */
    fun buildGetCidInfo(featureIndex: Int, index: Int, softwareId: Int = HidppConstants.DEFAULT_SOFTWARE_ID): ByteArray =
        frame(featureIndex, FN_GET_CID_INFO, softwareId, byteArrayOf(index.toByte()))

    fun parseCidInfo(response: HidppFrame): CidInfo {
        val p = response.params
        fun b(i: Int) = p.getOrElse(i) { 0 }.toInt() and 0xFF
        val cid = (b(0) shl 8) or b(1)
        val taskId = (b(2) shl 8) or b(3)
        val flags = b(4)
        return CidInfo(cid = cid, taskId = taskId, flags = flags)
    }

    /** fn3 setCidReporting: 이 CID를 divert 켜기/끄기. divert 켜면 키가 divertedButtonsEvent로 온다. */
    fun buildSetCidReporting(
        featureIndex: Int,
        cid: Int,
        divert: Boolean,
        softwareId: Int = HidppConstants.DEFAULT_SOFTWARE_ID,
    ): ByteArray {
        val flags = if (divert) SET_DIVERT_ON else SET_DIVERT_OFF
        val params = byteArrayOf(
            ((cid ushr 8) and 0xFF).toByte(),
            (cid and 0xFF).toByte(),
            flags.toByte(),
        )
        return frame(featureIndex, FN_SET_CID_REPORTING, softwareId, params)
    }

    /**
     * event0 divertedButtonsEvent (swId=0 notify): 눌린 CID를 2바이트씩 최대 4개. 모두 뗐을 때 0.
     * 응답 프레임의 func|sw는 (fn0<<4 | swId0) = 0x00 이어야 한다(호출측에서 확인).
     */
    fun parseDivertedButtons(response: HidppFrame): List<Int> {
        val p = response.params
        val cids = mutableListOf<Int>()
        var i = 0
        while (i + 1 < p.size) {
            val cid = ((p[i].toInt() and 0xFF) shl 8) or (p[i + 1].toInt() and 0xFF)
            if (cid != 0) cids.add(cid)
            i += 2
        }
        return cids
    }
}

data class CidInfo(
    val cid: Int,
    val taskId: Int,
    val flags: Int,
) {
    val divertable: Boolean get() = flags and ReprogKeysFeature.FLAG_DIVERTABLE != 0
    val persist: Boolean get() = flags and ReprogKeysFeature.FLAG_PERSIST != 0
}
