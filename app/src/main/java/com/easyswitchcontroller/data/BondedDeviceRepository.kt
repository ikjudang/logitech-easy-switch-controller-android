package com.easyswitchcontroller.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

data class BondedDeviceInfo(
    val name: String,
    val address: String,
    val likelyLogitech: Boolean,
    /** Bluetooth Class of Device(deviceClass). 키보드/마우스 판별의 1차 근거. 0이면 미상. */
    val deviceClass: Int = 0,
    val deviceType: Int = BluetoothDevice.DEVICE_TYPE_LE,
)

/** 삭제 판정에는 필터링 전의 전체 목록만 사용한다. 조회 실패는 빈 목록과 구분한다. */
data class BondedDeviceSnapshot(
    val devices: List<BondedDeviceInfo>,
    val complete: Boolean,
) {
    fun candidates(selected: Set<String> = emptySet()): List<BondedDeviceInfo> =
        devices.filter {
            it.address in selected ||
                (it.deviceType != BluetoothDevice.DEVICE_TYPE_CLASSIC && isSwitchCandidate(it))
        }.sortedWith(compareByDescending<BondedDeviceInfo> { it.likelyLogitech }.thenBy { it.name })
}

/**
 * 시스템에 이미 본딩된 기기 중 **전환 후보**만 읽는다(스캔하지 않음). 후보 기준은 [isSwitchCandidate].
 * name/type 접근은 Android 12+에서 BLUETOOTH_CONNECT 권한을 요구하므로 SecurityException을 방어한다.
 */
class BondedDeviceRepository(private val context: Context) {
    @Volatile private var lastKnown: List<BondedDeviceInfo> = emptyList()
    private fun unavailable() = BondedDeviceSnapshot(lastKnown, false)

    @SuppressLint("MissingPermission")
    fun snapshot(): BondedDeviceSnapshot {
        val manager = context.getSystemService(BluetoothManager::class.java)
            ?: return unavailable()
        val adapter = manager.adapter ?: return unavailable()
        return try {
            if (!adapter.isEnabled) return unavailable()
            val bonded = adapter.bondedDevices ?: return unavailable()
            val devices = bonded.map { device ->
                val name = device.name?.takeIf { it.isNotBlank() } ?: device.address
                val deviceClass = device.bluetoothClass?.deviceClass ?: 0
                BondedDeviceInfo(name, device.address, likelyLogitech(name), deviceClass, device.type)
            }
            if (adapter.state != BluetoothAdapter.STATE_ON) return unavailable()
            lastKnown = devices
            BondedDeviceSnapshot(devices, true)
        } catch (e: SecurityException) {
            unavailable()
        } catch (e: IllegalStateException) {
            unavailable()
        }
    }

    fun bondedDevices(): List<BondedDeviceInfo> = snapshot().candidates(SelectionStore(context).loadSelected())

    private fun likelyLogitech(name: String): Boolean {
        val n = name.lowercase()
        return listOf("mx ", "mx_", "mchncl", "mechanical", "logi", "keys", "master", "anywhere", "ergo", "pop")
            .any { n.contains(it) }
    }
}

/**
 * 전환 후보 판별: CoD가 주변기기(키보드·마우스 부류)이면 남기고, CoD로 비주변기기가 확실한
 * 기기(오디오·차량·컴퓨터 등)는 목록에서 뺀다 — HID++ 전환 대상이 될 수 없는데, 특히 삼성
 * 버즈는 설정 앱에는 안 보이는 동반 LE 본딩(다른 MAC)까지 만들어 같은 이름이 두 줄로 떠
 * 사용자를 혼란시킨다(실측). CoD 미상(0)·미분류(0x1F00)는 비주변기기라는 근거가 아니므로
 * 로지텍 추정 이름일 때만 남긴다.
 *
 * 순수 함수 — Context 없이 단위테스트한다.
 */
fun isSwitchCandidate(info: BondedDeviceInfo): Boolean {
    val major = info.deviceClass and 0x1F00 // CoD major class 비트
    if (major == 0x0500) return true // PERIPHERAL — 키보드·마우스·콤보가 여기 속한다
    if (info.deviceClass == 0 || major == 0x1F00) return info.likelyLogitech // 미상·미분류
    return false // 오디오·차량·컴퓨터 등 — 비주변기기 확실
}
