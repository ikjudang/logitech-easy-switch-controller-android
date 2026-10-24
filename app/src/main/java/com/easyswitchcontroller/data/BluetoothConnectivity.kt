package com.easyswitchcontroller.data

import android.bluetooth.BluetoothDevice
import java.io.IOException

/**
 * 시스템이 이 기기와 실제 연결(ACL)을 유지 중인지.
 * 공개 API에 없어 hidden `isConnected()`를 리플렉션으로 호출한다.
 * 조회 불가한 OS/기기에서는 true를 반환해 기존 동작(연결 시도)을 유지한다.
 */
fun BluetoothDevice.aclConnectionState(): Boolean? = try {
    javaClass.getMethod("isConnected").invoke(this) as? Boolean
} catch (e: Exception) {
    null
}

fun BluetoothDevice.isAclConnected(): Boolean = aclConnectionState() ?: true

/**
 * 기기가 이 폰/태블릿에 연결(ACL)돼 있지 않아 생긴 실패.
 *
 * **오류가 아니라 정상 상황이다** — 그 기기가 지금 다른 채널(PC 등)에 가 있다는 뜻일 뿐이다.
 * 그래서 사용자에게 알리지 않는다(매 전환마다 "실패했다"고 뜨면 소음이다). 일반 통신 오류와
 * 구분해야 하므로 문구가 아니라 **타입으로** 가른다.
 */
class DeviceNotConnectedException(message: String) : IOException(message)
