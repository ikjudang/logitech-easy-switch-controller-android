package com.easyswitchcontroller.fakes

import com.easyswitchcontroller.hidpp.HidppConstants
import com.easyswitchcontroller.hidpp.HidppErrorCode
import com.easyswitchcontroller.hidpp.HidppErrorInfo
import com.easyswitchcontroller.hidpp.HidppFrame
import com.easyswitchcontroller.hidpp.HidppFrameParser
import com.easyswitchcontroller.hidpp.HidppProtocolException
import com.easyswitchcontroller.hidpp.ReportType
import com.easyswitchcontroller.transport.ConnectionState
import com.easyswitchcontroller.transport.HidppTransport
import com.easyswitchcontroller.transport.WriteAck
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import kotlin.time.Duration

/**
 * 하드웨어 없이 상위 계층(device/UI)을 테스트하기 위한 in-memory 전송.
 *
 * - Root getFeature 요청에는 [featureIndexProvider]가 주는 index로 응답을 조립한다(0이면 미지원).
 * - [protocolErrorFeatureIds]에 든 feature id 조회는 HID++ 에러로 응답한다.
 * - sendNoResponse는 실제 기기처럼 직후 연결 해제를 흉내낼 수 있다([simulateDisconnectOnSend]).
 */
class FakeHidppTransport(
    private val featureIndexProvider: (featureId: Int) -> Int = { 0 },
    private val protocolErrorFeatureIds: Set<Int> = emptySet(),
    var simulateDisconnectOnSend: Boolean = true,
    var failSend: Boolean = false,
    var sendError: Exception? = null,
) : HidppTransport {

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    override val connectionState = _connectionState.asStateFlow()

    private val _notifications = MutableSharedFlow<HidppFrame>(extraBufferCapacity = 8)
    override val notifications = _notifications.asSharedFlow()

    /** 검증용 기록. */
    val requestedFrames = mutableListOf<ByteArray>()
    val sentFrames = mutableListOf<ByteArray>()

    override suspend fun connect() {
        _connectionState.value = ConnectionState.ServicesReady
    }

    override suspend fun disconnect() {
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun request(frame: ByteArray, timeout: Duration): HidppFrame {
        requestedFrames.add(frame)
        val parsed = HidppFrameParser.parse(frame)
            ?: error("fake: 파싱 불가한 요청")

        val isRootGetFeature = parsed.featureIndex == HidppConstants.ROOT_FEATURE_INDEX &&
            parsed.functionId == HidppConstants.ROOT_FN_GET_FEATURE
        require(isRootGetFeature) { "fake: 처리하지 않는 요청 featureIndex=${parsed.featureIndex}" }

        val featureId = ((parsed.params[0].toInt() and 0xFF) shl 8) or (parsed.params[1].toInt() and 0xFF)

        if (featureId in protocolErrorFeatureIds) {
            throw HidppProtocolException(
                HidppErrorInfo(
                    protocol = 2,
                    funcSwByte = parsed.funcSwByte,
                    failedFeatureIndex = 0,
                    errorCode = HidppErrorCode.BUSY.code,
                ),
            )
        }

        val index = featureIndexProvider(featureId)
        return HidppFrame.build(
            reportType = ReportType.SHORT,
            deviceIndex = parsed.deviceIndex,
            featureIndex = HidppConstants.ROOT_FEATURE_INDEX,
            functionId = HidppConstants.ROOT_FN_GET_FEATURE,
            softwareId = parsed.softwareId,
            params = byteArrayOf(index.toByte(), 0x00, 0x04),
        )
    }

    override suspend fun sendNoResponse(frame: ByteArray): WriteAck {
        sentFrames.add(frame)
        sendError?.let { throw it }
        if (failSend) throw IOException("fake: write 실패")
        if (simulateDisconnectOnSend) _connectionState.value = ConnectionState.Disconnected
        return WriteAck.Written
    }
}
