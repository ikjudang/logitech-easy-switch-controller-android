package com.easyswitchcontroller.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.launch

/**
 * 안드로이드 OS에 **현재 연결(ACL)된** 블루투스 기기 주소 집합을 실시간으로 제공한다.
 *
 * UI 연결 뱃지 표시와 키보드 재연결 감지용 **참고값**이다. 전환/진단 대상 판정은
 * GattConnection.connect()의 실시간 ACL 가드가 담당하며, 이 모니터 값은 대상 산정에 쓰지 않는다.
 * `ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED` 브로드캐스트로 즉시 갱신하고,
 * 브로드캐스트를 놓쳐도 표시가 굳지 않게 주기 폴링으로 보강한다.
 *
 * ⚠ 반드시 [com.easyswitchcontroller.di.ServiceLocator.connectedDevicesMonitor]의 **프로세스 전역
 * 싱글톤**으로 써야 한다. 화면마다 새로 만들면, 액티비티 재생성 후 캐시된 ViewModel이 아무도
 * 갱신하지 않는 죽은 인스턴스의 flow를 계속 들고 있어 연결 표시가 그 시점에 얼어붙는다(실측 버그).
 */
class ConnectedDevicesMonitor(
    private val context: Context,
    private val onDevicesConnected: (List<String>) -> Unit = {},
) {
    private val observed = ConcurrentHashMap<String, Boolean>()

    private val _connectedAddresses = MutableStateFlow(currentConnected())
    val connectedAddresses: StateFlow<Set<String>> = _connectedAddresses.asStateFlow()
    private val connectionNotices = ConnectionNoticeTracker(_connectedAddresses.value)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectionBatch = ConnectionNoticeBatcher(scope) { addresses ->
        runCatching { onDevicesConnected(addresses) }
            .onFailure { android.util.Log.w("BluetoothMonitor", "연결 안내 실패", it) }
    }
    private var started = false

    private val _aclEvents = MutableSharedFlow<AclEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * OS가 알려 준 ACL 연결·끊김 그 자체(공개 브로드캐스트). [connectedAddresses]는 숨은 API를
     * 리플렉션으로 불러 만든 판정이라 틀릴 수 있는데, 이쪽은 OS가 직접 보낸 사실이라 둘을 대조할 수 있다.
     */
    val aclEvents: SharedFlow<AclEvent> = _aclEvents.asSharedFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                -> {
                    val device = runCatching { deviceOf(intent) }.getOrNull()
                    device?.let {
                        val connected = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED
                        observed[device.address] = connected
                        _aclEvents.tryEmit(
                            AclEvent(device.address, connected),
                        )
                        if (connectionNotices.onAclEvent(device.address, connected)) {
                            connectionBatch.add(device.address)
                        } else if (!connected) {
                            connectionBatch.remove(device.address)
                        }
                    }
                    refresh()
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                        connectionNotices.clear()
                        connectionBatch.clear()
                    }
                    refresh()
                }
            }
        }
    }

    private fun deviceOf(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    /** 프로세스 수명 동안 감시 시작(중복 호출 무해). 중지는 없다 — 프로세스와 함께 산다. */
    @Synchronized
    fun start() {
        if (started) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        // 시스템 브로드캐스트라 EXPORTED로 등록해야 수신된다(Android 14+ 필수 플래그).
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        started = true
        scope.launch {
            while (true) {
                refresh()
                delay(POLL_MS)
            }
        }
    }

    fun refresh() {
        _connectedAddresses.value = currentConnected()
    }

    @SuppressLint("MissingPermission")
    private fun currentConnected(): Set<String> {
        return try {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?: return emptySet()
            if (!adapter.isEnabled) { observed.clear(); return emptySet() }
            adapter.bondedDevices.orEmpty()
                .filter { device -> device.aclConnectionState() ?: (observed[device.address] == true) }
                .map { it.address }
                .toSet()
        } catch (e: SecurityException) {
            observed.clear()
            emptySet()
        } catch (e: IllegalStateException) {
            emptySet()
        }
    }

    private companion object {
        /**
         * ACL 브로드캐스트를 놓쳤을 때의 보강 폴링 간격. 이 값이 곧 "기기가 돌아왔는데 앱이 아직
         * 모르는 공백"이라 짧게 잡는다. 본딩 목록 순회 + 리플렉션 한 번이라 비용은 가볍다.
         */
        const val POLL_MS = 800L
    }
}

/** OS가 브로드캐스트로 알려 준 ACL 연결([connected]=true) 또는 끊김. */
data class AclEvent(val address: String, val connected: Boolean)
