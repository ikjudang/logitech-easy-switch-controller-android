package com.easyswitchcontroller.data

/** 이미 연결돼 있던 기기와 같은 연결의 중복 OS 알림에는 토스트를 반복하지 않는다. */
internal class ConnectionNoticeTracker(initialConnected: Set<String>) {
    private val connected = initialConnected.toMutableSet()

    @Synchronized
    fun clear() { connected.clear() }

    @Synchronized
    fun onAclEvent(address: String, isConnected: Boolean): Boolean = if (isConnected) {
        connected.add(address)
    } else {
        connected.remove(address)
        false
    }
}
