package com.easyswitchcontroller.device

import android.bluetooth.BluetoothManager
import android.content.Context
import com.easyswitchcontroller.data.aclConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** 명령 전달 성공과 실제 채널 이동을 구분한다. 연결을 새로 열어 전환을 방해하지 않는다. */
suspend fun verifySwitch(context: Context, device: LogiDevice, targetHost: Int): SwitchIndicator {
    return awaitSwitchConfirmation(device.localHostIndex, targetHost) {
        currentAclConnection(context, device.address)
    }
}

fun currentAclConnection(context: Context, address: String): Boolean? = runCatching {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    if (adapter?.isEnabled == true) adapter.getRemoteDevice(address).aclConnectionState() else null
}.getOrNull()

/** 확인되는 즉시 반환한다. 링크가 아직 남아 있을 때만 짧게 재확인한다. */
internal suspend fun awaitSwitchConfirmation(
    localHost: Int?,
    targetHost: Int,
    timeoutMs: Long = 1_500,
    pollMs: Long = 100,
    connected: () -> Boolean?,
): SwitchIndicator {
    fun read() = confirmedSwitchIndicator(localHost, targetHost, connected())
    return withTimeoutOrNull(timeoutMs) {
        var state = read()
        while (state != SwitchIndicator.Success) {
            delay(pollMs)
            state = read()
        }
        state
    } ?: read()
}
