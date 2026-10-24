package com.easyswitchcontroller.transport

import com.easyswitchcontroller.hidpp.HidppFrame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import kotlin.time.Duration

/**
 * ProtocolConfig가 미설정(isConfigured == false)일 때만 쓰이는 방어용 전송.
 * 정상 빌드에서는 ServiceLocator가 실측 UUID를 기본 주입하므로 도달하지 않는다.
 *
 * 모든 동작에서 [reason]을 담은 IOException을 던져, 상위(LogiDevice/ViewModel)가
 * 크래시 없이 "실패" 상태로 표시하게 한다.
 */
class NotConfiguredTransport(private val reason: String) : HidppTransport {

    override val connectionState = MutableStateFlow(ConnectionState.Disconnected).asStateFlow()
    override val notifications = MutableSharedFlow<HidppFrame>().asSharedFlow()

    override suspend fun connect(): Unit = throw IOException(reason)

    override suspend fun disconnect() = Unit

    override suspend fun request(frame: ByteArray, timeout: Duration): HidppFrame =
        throw IOException(reason)

    override suspend fun sendNoResponse(frame: ByteArray): WriteAck =
        throw IOException(reason)
}
