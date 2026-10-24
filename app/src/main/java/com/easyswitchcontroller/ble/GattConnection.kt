package com.easyswitchcontroller.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.easyswitchcontroller.R
import android.util.Log
import com.easyswitchcontroller.config.ProtocolConfig
import com.easyswitchcontroller.data.DeviceNotConnectedException
import com.easyswitchcontroller.data.isAclConnected
import com.easyswitchcontroller.transport.ConnectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

private data class GattProfile(
    val writeChar: BluetoothGattCharacteristic,
    val notifyChar: BluetoothGattCharacteristic,
)

/**
 * BluetoothGatt 콜백을 코루틴 suspend로 래핑한 저수준 연결 엔진.
 *
 * 설계 요점(사양서/계획 반영):
 *  - BluetoothGatt는 한 번에 op 1개만 허용 → [opMutex]로 in-flight 1개 직렬화.
 *  - connect→discover→CCCD 활성→명령을 선형 suspend 코드로.
 *  - notify는 요청과 1:1이 아니므로 [notifications] SharedFlow로 흘려보낸다.
 *
 * 실기기 검증 완료: notify는 report_id 없는 18B 벤더 프레임(BleTransport가 변환),
 * write type은 [preferredWriteType]으로 결정.
 */
class GattConnection(
    private val context: Context,
    private val device: BluetoothDevice,
    private val config: ProtocolConfig,
) {
    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _notifications = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val notifications = _notifications.asSharedFlow()

    private val opMutex = Mutex()

    @Volatile private var gatt: BluetoothGatt? = null
    @Volatile private var profile: GattProfile? = null
    @Volatile private var generation = 0L

    // opMutex가 in-flight를 1개로 제한하므로 pending 필드는 단일로 충분하다.
    @Volatile private var pendingConnect: CompletableDeferred<Unit>? = null
    @Volatile private var pendingDiscover: CompletableDeferred<Unit>? = null
    @Volatile private var pendingWrite: CompletableDeferred<Unit>? = null
    @Volatile private var pendingDescriptor: CompletableDeferred<Unit>? = null
    @Volatile private var writeAllowsDisconnect = false

    private fun callbackFor(epoch: Long) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (epoch != generation) return
            when (newState) {
                // status를 함께 본다 — CONNECTED로 오면서 status가 실패(133 등)인 경우가 있고,
                // 그걸 성공으로 받으면 이어지는 discoverServices가 조용히 타임아웃까지 끌려간다.
                BluetoothProfile.STATE_CONNECTED ->
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        pendingConnect?.complete(Unit)
                    } else {
                        pendingConnect?.completeExceptionally(IOException("연결 실패(status=$status)"))
                    }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Disconnected
                    // setCurrentHost 직후의 정상 끊김일 수 있으므로 여기서 에러로 확정하지 않는다.
                    pendingConnect?.takeIf { !it.isCompleted }
                        ?.completeExceptionally(IOException("연결 해제(status=$status)"))
                    val failure = IOException("GATT 연결 해제(status=$status)")
                    pendingDiscover?.completeExceptionally(failure)
                    pendingDescriptor?.completeExceptionally(failure)
                    if (writeAllowsDisconnect) pendingWrite?.complete(Unit)
                    else pendingWrite?.completeExceptionally(failure)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (epoch != generation) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                pendingDiscover?.complete(Unit)
            } else {
                pendingDiscover?.completeExceptionally(IOException("서비스 디스커버리 실패(status=$status)"))
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (epoch != generation) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                pendingWrite?.complete(Unit)
            } else {
                pendingWrite?.completeExceptionally(IOException("write 실패(status=$status)"))
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (epoch != generation) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                pendingDescriptor?.complete(Unit)
            } else {
                pendingDescriptor?.completeExceptionally(IOException("CCCD write 실패(status=$status)"))
            }
        }

        // API 33+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (epoch != generation || characteristic.uuid != profile?.notifyChar?.uuid) return
            Log.d(TAG, "notify(${value.size}B) on ${characteristic.uuid}: ${value.toHex()}")
            _notifications.tryEmit(value.copyOf())
        }

        // API 31·32
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (epoch != generation || characteristic.uuid != profile?.notifyChar?.uuid) return
            @Suppress("DEPRECATION")
            characteristic.value?.let {
                Log.d(TAG, "notify(${it.size}B) on ${characteristic.uuid}: ${it.toHex()}")
                _notifications.tryEmit(it.copyOf())
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect() = opMutex.withLock {
        require(config.isConfigured) { "ProtocolConfig 미설정: vendor UUID가 없습니다" }
        if (_connectionState.value == ConnectionState.ServicesReady && gatt != null && profile != null) {
            return@withLock
        }

        // 현재 폰에 실제로 연결(ACL)된 기기만 연다. 폰에 없는 기기(다른 장소 세트 등)는 GATT 시도
        // 자체를 하지 않아, BLE 스택이 없는 기기를 붙잡으려다 생기는 간섭(간헐적 마우스 연타)을 원천 차단한다.
        if (!device.isAclConnected()) {
            _connectionState.value = ConnectionState.Disconnected
            throw DeviceNotConnectedException(context.getString(R.string.err_not_connected, bestName()))
        }

        // 재연결 지원: 전환 후 마우스가 떠나 끊긴 경우 기존 gatt를 정리하고 새로 연결한다.
        closeGatt()

        _connectionState.value = ConnectionState.Connecting

        val connected = CompletableDeferred<Unit>().also { pendingConnect = it }
        try {
            val g = device.connectGatt(context, false, callbackFor(generation), BluetoothDevice.TRANSPORT_LE)
                ?: throw IOException("connectGatt 실패")
            gatt = g
            withTimeout(CONNECT_TIMEOUT_MS) { connected.await() }
            _connectionState.value = ConnectionState.Connected

            val discovered = CompletableDeferred<Unit>().also { pendingDiscover = it }
            if (!g.discoverServices()) throw IOException("discoverServices 큐잉 실패")
            withTimeout(DISCOVER_TIMEOUT_MS) { discovered.await() }

            profile = resolveProfile(g) ?: throw IOException("vendor 서비스/characteristic을 찾지 못함")
            enableNotifications(g, profile!!.notifyChar)
            if (_connectionState.value == ConnectionState.Disconnected || gatt !== g) {
                throw IOException("서비스 준비 중 연결이 끊겼어요.")
            }
            _connectionState.value = ConnectionState.ServicesReady
        } catch (e: Throwable) {
            // 연결/디스커버리 실패 시 gatt를 확실히 닫는다. 정리하지 않으면 안드로이드 BLE 스택이
            // 백그라운드에서 그 기기를 계속 붙잡으려 해, 연결된 다른 마우스 입력에 간섭(간헐적 연타)할 수 있다.
            closeGatt()
            throw e
        } finally {
            pendingConnect = null
            pendingDiscover = null
            pendingDescriptor = null
        }
    }

    /**
     * write 후 완료를 기다린다.
     * @param expectAck true면 write 콜백을 requestTimeout 내에 반드시 받아야 성공(응답 오는 명령).
     *   false면 write-완료-즉-성공: 짧은 상한 내 콜백을 기다리되, 안 와도(직후 링크 끊김 등) 성공.
     */
    @SuppressLint("MissingPermission")
    suspend fun writeQueued(bytes: ByteArray, expectAck: Boolean) = opMutex.withLock {
        val g = gatt ?: throw IOException("연결되지 않음")
        val wc = profile?.writeChar ?: throw IOException("write characteristic 없음")

        val done = CompletableDeferred<Unit>().also { pendingWrite = it }
        writeAllowsDisconnect = !expectAck
        try {
            Log.d(TAG, "write(${bytes.size}B, type=${wc.preferredWriteType()}) to ${wc.uuid}: ${bytes.toHex()}")
            if (!g.writeCharacteristicCompat(wc, bytes, wc.preferredWriteType())) {
                throw IOException("writeCharacteristic 큐잉 실패")
            }
            val completed = awaitWriteCompletion(
                done,
                requireAck = expectAck,
                timeoutMs = if (expectAck) config.requestTimeoutMs else config.writeCompleteTimeoutMs,
            )
            // 콜백이 끝나지 않은 GATT를 재사용하면 늦은 콜백이 다음 write를 완료시킬 수 있다.
            if (!completed) closeGatt()
        } catch (error: Throwable) {
            closeGatt()
            throw error
        } finally {
            pendingWrite = null
            writeAllowsDisconnect = false
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun enableNotifications(g: BluetoothGatt, notifyChar: BluetoothGattCharacteristic) {
        if (!g.setCharacteristicNotification(notifyChar, true)) {
            throw IOException("notify 등록 실패")
        }
        val cccd = notifyChar.getDescriptor(config.cccdUuid)
            ?: throw IOException("CCCD(0x2902) descriptor 없음")
        val done = CompletableDeferred<Unit>().also { pendingDescriptor = it }
        if (!g.writeDescriptorCompat(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
            throw IOException("CCCD write 큐잉 실패")
        }
        withTimeout(config.requestTimeoutMs) { done.await() }
    }

    /** vendor service 안에서 write/notify characteristic을 config 또는 속성 스캔으로 찾는다. */
    private fun resolveProfile(g: BluetoothGatt): GattProfile? {
        val service = g.getService(config.vendorServiceUuid) ?: return null
        val writeChar = config.writeCharUuid?.let { service.getCharacteristic(it) }
            ?: service.characteristics.firstOrNull { it.supportsWrite() }
            ?: return null
        val notifyChar = config.notifyCharUuid?.let { service.getCharacteristic(it) }
            ?: service.characteristics.firstOrNull { it.supportsNotify() }
            ?: return null
        return GattProfile(writeChar, notifyChar)
    }

    /** 취소된 코루틴의 finally에서 불려도 끝까지 닫는다 — 락을 기다리다 취소되면 GATT가 주인 없이 열린 채 남는다. */
    @SuppressLint("MissingPermission")
    suspend fun close() = withContext(NonCancellable) { opMutex.withLock { closeGatt() } }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        generation++
        val previous = gatt
        gatt = null
        profile = null
        _connectionState.value = ConnectionState.Disconnected
        if (previous != null) {
            runCatching { previous.disconnect() }
            runCatching { previous.close() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun bestName(): String = try {
        device.name ?: device.address
    } catch (e: SecurityException) {
        device.address
    }

    private fun BluetoothGattCharacteristic.supportsWrite(): Boolean {
        val p = properties
        return p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
            p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
    }

    private fun BluetoothGattCharacteristic.supportsNotify(): Boolean =
        properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0

    private companion object {
        const val TAG = "HidppBle"
        /**
         * 연결·디스커버리 상한. 짧게 잡는 게 **더 빠르다** — BLE 연결이 이 시간을 넘기면 사실상
         * 실패라서, 길게 기다리는 대신 빨리 포기하고 [com.easyswitchcontroller.device.LogiDevice]의
         * 재시도가 새 GATT로 다시 붙는 편이 전환이 먼저 끝난다.
         */
        const val CONNECT_TIMEOUT_MS = 4_000L
        const val DISCOVER_TIMEOUT_MS = 4_000L
    }
}
