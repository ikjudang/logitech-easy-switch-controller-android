package com.easyswitchcontroller.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.easyswitchcontroller.R
import android.util.Log
import com.easyswitchcontroller.config.ProtocolConfig
import com.easyswitchcontroller.data.ConnectionLog
import com.easyswitchcontroller.hidpp.HidppConstants
import com.easyswitchcontroller.hidpp.HidppFrame
import com.easyswitchcontroller.hidpp.HidppFrameParser
import com.easyswitchcontroller.hidpp.HidppProtocolException
import com.easyswitchcontroller.transport.ConnectionState
import com.easyswitchcontroller.transport.HidppTransport
import com.easyswitchcontroller.transport.WriteAck
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * [GattConnection] 위에 HID++ 요청/응답 상관을 얹은 전송 계층.
 *
 * 로지텍 BLE 벤더 characteristic(00010001)의 실측 형식:
 *  - report_id/device_index 없이 **[feature_index][func<<4|sw][params...]** 18바이트를 쓴다.
 *  - 응답도 같은 형식(feature_index/func|sw echo + 응답 params).
 * 따라서 hidpp 순수 로직(표준 프레임)과 실제 전송 사이를 [toBleFrame]/[fromBleFrame]으로 변환한다:
 *  - write: 표준 프레임 앞 2바이트(report_id, device_index)를 떼고 18바이트로.
 *  - read : BLE 18바이트 앞에 [0x11][0xFF]를 붙여 표준 long(20바이트)으로 되돌려 파싱.
 */
class BleTransport(
    context: Context,
    device: BluetoothDevice,
    private val config: ProtocolConfig,
) : HidppTransport {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gattConnection = GattConnection(appContext, device, config)
    private val requestMutex = Mutex()
    private val oddSizesLogged = ConcurrentHashMap.newKeySet<Int>()
    @SuppressLint("MissingPermission")
    private val label: String = runCatching { device.name }.getOrNull() ?: device.address

    override val connectionState = gattConnection.connectionState

    override val notifications = gattConnection.notifications
        .mapNotNull { HidppFrameParser.parse(fromBleFrame(it)) }
        .shareIn(scope, SharingStarted.Eagerly)

    override suspend fun connect() = gattConnection.connect()

    override suspend fun disconnect() = gattConnection.close()

    override suspend fun request(frame: ByteArray, timeout: Duration): HidppFrame = requestMutex.withLock {
        ensureReady()
        val requestFrame = HidppFrameParser.parse(frame)
            ?: throw IOException("보낼 요청 프레임을 파싱할 수 없음")

        coroutineScope {
            // 구독이 **실제로 등록된 뒤에** write한다. async만 띄우고 곧바로 write하면, 구독이 걸리기
            // 전에 응답 notify가 도착할 수 있고 notify flow는 replay=0이라 그 응답은 아무도 받지 못한 채
            // 증발한다 → 타임아웃 실패. 스레드 스케줄링 운에 갈리므로 "간혹 전환이 안 먹는" 모양이 된다.
            val subscribed = CompletableDeferred<Unit>()
            val awaited = async {
                gattConnection.notifications
                    .onSubscription { subscribed.complete(Unit) }
                    .first { raw -> matchesRequest(fromBleFrame(raw), requestFrame) }
            }
            subscribed.await()
            gattConnection.writeQueued(toBleFrame(frame), expectAck = true)
            val rawBle = try {
                withTimeout(timeout) { awaited.await() }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "request 타임아웃: 매칭 응답 없음 (요청 ${frame.toHex()})")
                throw e
            }

            val std = fromBleFrame(rawBle)
            Log.d(TAG, "response: ble=${rawBle.toHex()} -> std=${std.toHex()}")
            if (HidppFrameParser.isError(std)) {
                throw HidppProtocolException(HidppFrameParser.parseError(std)!!)
            }
            HidppFrameParser.parse(std) ?: throw IOException("응답 프레임 파싱 불가")
        }
    }

    override suspend fun sendNoResponse(frame: ByteArray): WriteAck = requestMutex.withLock {
        ensureReady()
        gattConnection.writeQueued(toBleFrame(frame), expectAck = false)
        WriteAck.Written
    }

    /**
     * 전송 전에 연결을 보장한다. 전환 후 기기가 떠나 GATT가 끊긴 경우 재연결을 시도한다.
     * 기기가 폰에 없으면 connect()의 실시간 ACL 가드가 즉시 IOException을 던지고,
     * ACL은 연결됐지만 GATT 연결이 타임아웃되는 경우만 여기서 명확한 IOException으로 변환한다.
     */
    private suspend fun ensureReady() {
        if (gattConnection.connectionState.value == ConnectionState.ServicesReady) return
        try {
            gattConnection.connect()
        } catch (e: TimeoutCancellationException) {
            throw IOException(appContext.getString(R.string.err_gatt_timeout))
        }
    }

    /** 표준 [report_id][device_index][feature_index][func|sw][params...] → BLE [feature_index][func|sw][params...] 18B. */
    private fun toBleFrame(frame: ByteArray): ByteArray {
        val out = ByteArray(HidppConstants.BLE_REPORT_LENGTH)
        var i = 0
        while (2 + i < frame.size && i < out.size) {
            out[i] = frame[2 + i]
            i++
        }
        return out
    }

    /**
     * BLE [feature_index][func|sw][params...] → 표준 long [0x11][0xFF][feature_index][func|sw][params...] 20B.
     *
     * 실측한 기기는 모두 18바이트지만, 다른 길이를 버리면 그 기기의 신호·응답이 흔적 없이 사라진다.
     * 0.2.14까지처럼 모자라면 0으로 채우고 넘치면 잘라 읽되, 길이별로 처음 한 번은 연결 로그에 남긴다.
     */
    private fun fromBleFrame(ble: ByteArray): ByteArray {
        if (ble.isEmpty()) return ByteArray(0)
        if (ble.size != HidppConstants.BLE_REPORT_LENGTH && oddSizesLogged.add(ble.size)) {
            ConnectionLog.append("$label: 예상과 다른 길이의 BLE 프레임(${ble.size}B): ${ble.toHex()}", ConnectionLog.Level.Dim)
        }
        val out = ByteArray(HidppConstants.LONG_LENGTH)
        out[0] = HidppConstants.REPORT_ID_LONG.toByte()
        out[1] = HidppConstants.DEVICE_INDEX_BLE.toByte()
        var i = 0
        while (i < ble.size && 2 + i < out.size) {
            out[2 + i] = ble[i]
            i++
        }
        return out
    }

    /** 역변환한 표준 응답이 이 요청에 대응하는지: 정상은 echo, 에러는 func|sw + 실패 feature index로 상관. */
    private fun matchesRequest(std: ByteArray, request: HidppFrame): Boolean {
        if (HidppFrameParser.isError(std)) {
            val error = HidppFrameParser.parseError(std) ?: return false
            return error.funcSwByte == request.funcSwByte &&
                error.failedFeatureIndex == request.featureIndex
        }
        val response = HidppFrameParser.parse(std) ?: return false
        return response.echoes(request.deviceIndex, request.featureIndex, request.funcSwByte)
    }

    private companion object {
        const val TAG = "HidppBle"
    }
}
