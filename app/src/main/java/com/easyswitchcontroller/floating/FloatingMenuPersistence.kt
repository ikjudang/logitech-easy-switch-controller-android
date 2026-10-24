package com.easyswitchcontroller.floating

/** 항상 표시가 켜져 있어도 진행 중인 입력·흡수를 끝낸 뒤 한 번만 다시 펼친다. */
internal class FloatingMenuPersistence {
    var enabled = false
        private set
    private var restoreRequested = false

    fun setEnabled(value: Boolean): Boolean {
        if (enabled == value) return false
        enabled = value
        restoreRequested = value
        return true
    }

    fun requestRestore() { if (enabled) restoreRequested = true }
    fun cancelRestore() { restoreRequested = false }

    fun shouldAnimateNumbers(requested: Boolean, hasNumbers: Boolean): Boolean =
        requested && (!enabled || !hasNumbers)

    fun takeRestore(homeVisible: Boolean, menuOpen: Boolean, busy: Boolean): Boolean {
        if (!enabled || !restoreRequested) return false
        if (!homeVisible || menuOpen) { restoreRequested = false; return false }
        if (busy) return false
        restoreRequested = false
        return true
    }
}
