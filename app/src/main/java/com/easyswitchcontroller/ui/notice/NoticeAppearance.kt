package com.easyswitchcontroller.ui.notice

/** 기존 배경색을 유지하고 배경·글자·아이콘 전체에 같은 배율을 적용한다. */
internal data class NoticeAppearance(val enabled: Boolean = true, val transparency: Int = 0) {
    val backgroundColor: Int get() = 0xF0303030.toInt()
    val opacity: Float get() = (100 - transparency.coerceIn(0, 100)) / 100f
}
