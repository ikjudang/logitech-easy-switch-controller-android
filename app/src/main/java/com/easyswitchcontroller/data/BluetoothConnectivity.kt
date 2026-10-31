package com.easyswitchcontroller.data

import android.bluetooth.BluetoothDevice
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

private val aclMethod by lazy {
    runCatching { BluetoothDevice::class.java.getMethod("isConnected") }.getOrNull()
}

/**
 * 시스템이 이 기기와 실제 연결(ACL)을 유지 중인지.
 * 공개 API에 없어 hidden `isConnected()`를 리플렉션으로 호출한다.
 * 조회 불가한 OS/기기에서는 true를 반환해 기존 동작(연결 시도)을 유지한다.
 */
fun BluetoothDevice.aclConnectionState(): Boolean? {
    val method = aclMethod
    if (method == null) {
        noteAclUnavailable("isConnected 메서드를 찾지 못했어요")
        return null
    }
    return try {
        method.invoke(this) as? Boolean
    } catch (e: Exception) {
        noteAclUnavailable("isConnected 호출이 실패했어요: ${e.javaClass.simpleName}")
        null
    }
}

private val aclUnavailableLogged = AtomicBoolean(false)

/**
 * 연결 판정을 얻지 못하면 호출처가 모두 '연결됨'으로 간주해 조용히 오동작한다. 프로세스당 한 번 로그에 남겨
 * OS 업데이트로 이 숨은 API가 막혔는지 사후에 가를 수 있게 한다(화면에는 띄우지 않는다).
 */
private fun noteAclUnavailable(reason: String) {
    if (aclUnavailableLogged.compareAndSet(false, true)) {
        ConnectionLog.append("연결 판정을 쓸 수 없어요($reason). 모든 기기를 연결된 것으로 간주합니다.", ConnectionLog.Level.Dim)
    }
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
