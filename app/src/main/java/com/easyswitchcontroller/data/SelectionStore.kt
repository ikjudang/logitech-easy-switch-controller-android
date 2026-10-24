package com.easyswitchcontroller.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 선택한 기기(마우스+키보드)와 슬롯 라벨을 영속화한다.
 * 따라가기 서비스(키 따라가기)가 **대상 기기 목록의 원천**으로 읽고,
 * 앱 재진입 시 선택 화면 생략에도 쓴다.
 * (간단한 요구라 SharedPreferences 사용; 규모가 커지면 DataStore로.)
 */
class SelectionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("logi_easy_switch", Context.MODE_PRIVATE)

    fun loadSelected(): Set<String> =
        prefs.getStringSet(KEY_DEVICES, emptySet())?.toSet() ?: emptySet()

    /**
     * 선택을 저장한다. 주소와 함께 **이름도 기록**해 둔다 — 기기를 지웠다 같은 이름으로 다시
     * 등록하면 MAC이 바뀌는데, 그때 [reconcile]이 이름으로 새 주소를 찾아 승계하기 위해서다.
     * 이름은 [known]에서 찾고, 못 찾으면 기존에 기록해 둔 이름을 유지한다.
     */
    fun saveSelected(addresses: Set<String>, known: List<BondedDeviceInfo>) = synchronized(selectionLock) {
        val names = selectionNames(addresses, loadSelectedNames(), known).map { (address, name) ->
            "$address$NAME_SEPARATOR$name"
        }.toSet()
        prefs.edit()
            .putStringSet(KEY_DEVICES, addresses)
            .putStringSet(KEY_DEVICE_NAMES, names)
            .apply()
        _revision.update { it + 1 }
    }

    /** 선택된 주소 → 저장 시점의 기기 이름. 구버전에서 저장된 선택은 비어 있을 수 있다. */
    fun loadSelectedNames(): Map<String, String> =
        prefs.getStringSet(KEY_DEVICE_NAMES, emptySet()).orEmpty().mapNotNull { entry ->
            val index = entry.indexOf(NAME_SEPARATOR)
            if (index <= 0) null else entry.substring(0, index) to entry.substring(index + 1)
        }.toMap()

    /**
     * 저장된 선택을 현재 본딩 목록과 대조해 정리하고(필요하면 영속화) 결과를 돌려준다.
     * 규칙은 [reconcileSelection] 참고. 화면 진입·새로고침과 따라가기 서비스의 대상 조회에서 호출한다.
     */
    fun reconcile(snapshot: BondedDeviceSnapshot): Set<String> = synchronized(selectionLock) {
        val stored = loadSelected()
        val previousNames = loadSelectedNames()
        val resolution = resolveSelection(stored, previousNames, snapshot)
        if (!snapshot.complete) return@synchronized resolution.active
        val next = resolution.remembered
        // 이름 미기록(구버전 선택)이면 집합이 그대로여도 한 번 저장해 이름을 채워 둔다.
        if (next != stored || selectionNames(next, previousNames, snapshot.devices) != previousNames) {
            saveSelected(next, snapshot.devices)
        }
        resolution.active
    }

    /**
     * 마우스 상시 연결(실험) 켜짐 여부. 기본은 꺼짐.
     *
     * 켜면 [com.easyswitchcontroller.service.KeyboardFollowManager]가 선택된 마우스와 GATT를
     * 계속 유지하고 feature index까지 미리 받아 둔다 → Easy-Switch 키를 눌렀을 때 전환이
     * **write 한 번**으로 끝나 가장 빠르다. 다만 BLE 링크를 계속 붙잡고 있는 게 마우스 입력에
     * 영향을 주는지는 확인된 바가 없어(근거가 기록에 안 남아 있다) 사용자가 직접 켜고 끄며 비교한다.
     */
    fun loadKeepMouseConnected(): Boolean = prefs.getBoolean(KEY_KEEP_MOUSE, false)

    fun saveKeepMouseConnected(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_MOUSE, enabled).apply()
        _revision.update { it + 1 }
    }

    fun loadLabels(channelCount: Int): Map<Int, String> =
        (1..channelCount).mapNotNull { channel ->
            prefs.getString(labelKey(channel), null)?.let { channel to it }
        }.toMap()

    fun saveLabel(channel1Based: Int, label: String) {
        prefs.edit().putString(labelKey(channel1Based), label).apply()
    }

    private fun labelKey(channel: Int) = "label_$channel"

    companion object {
        private val selectionLock = Any()
        private const val KEY_DEVICES = "selected_devices"
        private const val KEY_DEVICE_NAMES = "selected_device_names"
        private const val KEY_KEEP_MOUSE = "keep_mouse_connected"

        /**
         * 선택 **또는 동작 설정**이 저장될 때마다 +1 되는 프로세스 전역 신호. 따라가기 서비스의 팔로우
         * 매니저가 구독해, **이미 연결된 키보드를 선택에 추가**하는 경우처럼 ACL 변화가 전혀 없는
         * 변경도 즉시 반영한다(실측 버그: ACL 변화만 기다리면 영영 인지 못 함).
         * 마우스 상시 연결 토글도 이 신호로 즉시 적용된다.
         */
        private val _revision = MutableStateFlow(0)
        val revision: StateFlow<Int> = _revision.asStateFlow()

        /** "주소<US>이름" 인코딩용. MAC·기기 이름에 나올 수 없는 제어문자(Unit Separator). */
        private const val NAME_SEPARATOR = '\u001F'
    }
}
