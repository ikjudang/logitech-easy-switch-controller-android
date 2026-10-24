package com.easyswitchcontroller.floating

/** 메뉴의 네 번째 항목은 앱 열기다. 블루투스 채널 4로 전달하지 않는다. */
internal enum class FloatingMenuAction(val channel: Int?) {
    CHANNEL_1(1), CHANNEL_2(2), CHANNEL_3(3), OPEN_APP(null);

    val id: Int get() = ordinal + 1

    fun perform(onChannelSelected: (Int) -> Unit, onAppSelected: () -> Unit) {
        if (channel != null) onChannelSelected(channel) else onAppSelected()
    }

    companion object {
        fun fromId(id: Int?): FloatingMenuAction? = entries.firstOrNull { it.id == id }
    }
}
