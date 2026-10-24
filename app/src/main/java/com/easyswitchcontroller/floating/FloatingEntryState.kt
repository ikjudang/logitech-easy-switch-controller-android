package com.easyswitchcontroller.floating

/** 외부 소환은 설정을 몰래 바꾸지 않는다. 0 크기/완전 투명은 설정 열기로 안내한다. */
internal enum class FloatingEntryState {
    OFF, NEEDS_PERMISSION, ZERO_SIZE, TRANSPARENT, READY;

    companion object {
        fun resolve(enabled: Boolean, overlayAllowed: Boolean, sizePercent: Int, transparency: Int) = when {
            !enabled -> OFF
            !overlayAllowed -> NEEDS_PERMISSION
            sizePercent <= 0 -> ZERO_SIZE
            transparency >= 100 -> TRANSPARENT
            else -> READY
        }
    }
}
