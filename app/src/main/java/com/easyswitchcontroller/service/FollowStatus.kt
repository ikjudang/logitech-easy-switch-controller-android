package com.easyswitchcontroller.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 따라가기 서비스의 [KeyboardFollowManager]와 UI 사이의 프로세스 공유 상태.
 *
 * 키보드 연결은 팔로우 매니저가 GATT로 **상시 소유**한다. 그래서 OS의 ACL 리플렉션
 * (`BluetoothDevice.isConnected()`)은 우리 연결 때문에 키보드를 항상 연결됨으로 보고해
 * (초록불이 안 꺼짐) UI 판정에 못 쓴다. 대신 팔로우 매니저가 자기 GATT 상태를 여기 게시하고,
 * UI는 키보드 표시를 이 값으로 판정한다. 따라가기 서비스가 없어 매니저가 없으면 UI는 ACL 폴백을 쓴다
 * (그땐 우리가 붙잡고 있지 않아 ACL이 정확하다).
 */
object FollowStatus {
    /** 팔로우 매니저(따라가기 서비스)가 살아있는지. false면 UI는 ACL 리플렉션으로 폴백한다. */
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** 팔로우 매니저가 지금 GATT 연결(ServicesReady)을 유지 중인 키보드 주소 집합. */
    private val _connectedKeyboards = MutableStateFlow<Set<String>>(emptySet())
    val connectedKeyboards: StateFlow<Set<String>> = _connectedKeyboards.asStateFlow()

    /** 이 기기에 연결됐을 때 직접 조회한 기기별 슬롯. 이 값만으로 현재 연결을 뜻하지 않는다. */
    private val _localChannels = MutableStateFlow<Map<String, Int>>(emptyMap())
    val localChannels: StateFlow<Map<String, Int>> = _localChannels.asStateFlow()

    fun rememberLocalChannel(address: String, channel1Based: Int) {
        require(channel1Based in 1..3)
        _localChannels.update { it + (address to channel1Based) }
    }

    /**
     * 마지막으로 명령을 전달했거나 전환 신호로 관측한 목적지(1-based).
     * 단순 연결과 로컬 슬롯 조회는 이 이력을 바꾸지 않는다. 연결 여부는 별도 정보다.
     */
    private val _activeChannel = MutableStateFlow<Int?>(null)
    val activeChannel: StateFlow<Int?> = _activeChannel.asStateFlow()

    fun setRunning(value: Boolean) {
        _running.value = value
        if (!value) _connectedKeyboards.value = emptySet()
    }

    fun setKeyboardConnected(address: String, connected: Boolean) {
        if (!_running.value) return
        _connectedKeyboards.update { if (connected) it + address else it - address }
    }

    fun setActiveChannel(channel1Based: Int) {
        _activeChannel.value = channel1Based
    }
}
