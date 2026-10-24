package com.easyswitchcontroller.ui.components

/** 원격 채널의 사용 여부는 알 수 없다. 로컬 연결과 마지막으로 관측한 전환을 구분한다. */
internal enum class ChannelBadgeState {
    NONE, CONNECTED_HERE, LAST_SWITCH;

    companion object {
        fun resolve(channel: Int, lastSwitch: Int?, connectedHere: Set<Int>): ChannelBadgeState = when {
            channel in connectedHere -> CONNECTED_HERE
            channel == lastSwitch -> LAST_SWITCH
            else -> NONE
        }
    }
}
