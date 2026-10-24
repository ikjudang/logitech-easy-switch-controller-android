package com.easyswitchcontroller.di

import android.bluetooth.BluetoothManager
import android.content.Context
import com.easyswitchcontroller.ble.BleTransport
import com.easyswitchcontroller.config.ProtocolConfig
import com.easyswitchcontroller.data.BondedDeviceInfo
import com.easyswitchcontroller.data.ConnectedDevicesMonitor
import com.easyswitchcontroller.data.BondedDeviceRepository
import com.easyswitchcontroller.data.SelectionStore
import com.easyswitchcontroller.device.DeviceKind
import com.easyswitchcontroller.device.LogiDevice
import com.easyswitchcontroller.transport.HidppTransport
import com.easyswitchcontroller.transport.NotConfiguredTransport
import com.easyswitchcontroller.ui.AppToast
import java.util.UUID

/**
 * 수동 DI. 현재 규모에서는 충분해 Hilt를 도입하지 않았다.
 */
object ServiceLocator {

    /**
     * MX Master 4 실측값 (nRF Connect, 2026-07).
     * vendor service `00010000-…-046d` 안에 write·notify·CCCD를 모두 가진 단일 characteristic
     * `00010001-…-046d`가 있어, write/notify UUID가 동일하다.
     */
    @Volatile
    var protocolConfig: ProtocolConfig = ProtocolConfig(
        vendorServiceUuid = UUID.fromString("00010000-0000-1000-8000-011f2000046d"),
        writeCharUuid = UUID.fromString("00010001-0000-1000-8000-011f2000046d"),
        notifyCharUuid = UUID.fromString("00010001-0000-1000-8000-011f2000046d"),
    )

    @Volatile
    private var monitorInstance: ConnectedDevicesMonitor? = null

    /**
     * 프로세스 전역 연결 모니터. UI·ViewModel·따라가기 서비스가 **반드시 같은 인스턴스**를 봐야 한다 —
     * 화면 수명에 묶으면 액티비티 재생성 후 캐시된 ViewModel이 죽은 flow를 들고 상태가 얼어붙는다.
     */
    fun connectedDevicesMonitor(context: Context): ConnectedDevicesMonitor =
        monitorInstance ?: synchronized(this) {
            monitorInstance ?: createMonitor(context.applicationContext)
                .also { it.start(); monitorInstance = it }
        }

    private fun createMonitor(appContext: Context) = ConnectedDevicesMonitor(appContext) { addresses ->
        val store = SelectionStore(appContext)
        val snapshot = BondedDeviceRepository(appContext).snapshot()
        val selected = store.reconcile(snapshot)
        val remembered = store.loadSelected()
        val names = addresses.filter { it in selected || it in remembered }.map { address ->
            snapshot.devices.firstOrNull { it.address == address }?.name
                ?.takeIf { it != address }
                ?: store.loadSelectedNames()[address] ?: address
        }
        if (names.isNotEmpty()) AppToast.showDeviceConnected(appContext, names)
    }

    private val deviceCache = mutableMapOf<String, LogiDevice>()

    /**
     * 주소별 [LogiDevice] **프로세스 전역 캐시**.
     *
     * 같은 기기는 반드시 같은 인스턴스를 돌려준다. 이유가 둘이다:
     *  - **반응 속도**: feature index 캐시([com.easyswitchcontroller.device.FeatureIndexCache])가
     *    인스턴스에 붙어 있어, 매번 새로 만들면 전환할 때마다 Root feature 조회 왕복이 한 번씩 더 붙는다.
     *    따라가기·앱 내 전환이 그만큼 늦어진다.
     *  - **충돌 방지**: 인스턴스가 갈리면 같은 기기에 GATT가 둘 열려 "writeCharacteristic 큐잉 실패"가 난다.
     *    한 인스턴스를 공유하면 [com.easyswitchcontroller.ble.GattConnection]의 opMutex가 직렬화해 준다.
     */
    fun device(
        appContext: Context,
        info: BondedDeviceInfo,
        kind: DeviceKind = DeviceKind.MOUSE,
    ): LogiDevice = synchronized(deviceCache) {
        deviceCache.getOrPut(info.address) { createDevice(appContext, info, kind) }
            .also { it.updateMetadata(info.name, kind) }
    }

    fun createDevice(
        appContext: Context,
        info: BondedDeviceInfo,
        kind: DeviceKind = DeviceKind.MOUSE,
    ): LogiDevice {
        val transport: HidppTransport = if (protocolConfig.isConfigured) {
            val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
                ?: error("블루투스 어댑터를 사용할 수 없음")
            val bluetoothDevice = adapter.getRemoteDevice(info.address)
            BleTransport(appContext, bluetoothDevice, protocolConfig)
        } else {
            NotConfiguredTransport(
                "ProtocolConfig에 vendor 서비스 UUID가 설정되지 않았습니다(기본값이 재설정된 경우)",
            )
        }
        return LogiDevice(kind, info.name, info.address, transport, protocolConfig.softwareId)
    }

    /**
     * 마우스/키보드 판별.
     * 1순위: Bluetooth Class of Device — Peripheral(major 0x0500)의 keyboard(0x40)/pointing(0x80) 비트.
     *   (이름 추측과 달리 OS가 아는 실제 기기 종류라, "MX MCHNCL"처럼 이름에 단서가 없어도 정확하다.)
     * 2순위: 이름 휴리스틱 fallback.
     */
    fun guessKind(info: BondedDeviceInfo): DeviceKind {
        val dc = info.deviceClass
        if ((dc and 0x1F00) == 0x0500) { // major = PERIPHERAL
            if (dc and 0x40 != 0) return DeviceKind.KEYBOARD // keyboard 비트 (keyboard+pointing 콤보 포함)
            if (dc and 0x80 != 0) return DeviceKind.MOUSE // pointing 비트
        }
        val n = info.name.lowercase()
        if ("keys" in n || "keyboard" in n || "kbd" in n || "mechanical" in n || "mchncl" in n) return DeviceKind.KEYBOARD
        if ("mouse" in n || "master" in n || "anywhere" in n) return DeviceKind.MOUSE
        return synchronized(deviceCache) { deviceCache[info.address]?.kind } ?: DeviceKind.MOUSE
    }
}
