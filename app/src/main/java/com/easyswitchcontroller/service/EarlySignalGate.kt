package com.easyswitchcontroller.service

/**
 * 키보드 감시가 준비되기 전에 온 프레임을 붙잡아 두는 문.
 *
 * 전환 신호는 0x1814 index와 이 기기 슬롯을 알아야 해석할 수 있다. 슬롯을 모른 채 해석하면 도착 공지를
 * 출발로 오인한다. 그렇다고 슬롯 조회가 끝난 뒤에 구독하면, 키보드가 붙자마자 다시 떠날 때 보낸 신호는
 * 아무도 받지 않은 채 버려진다(2026-10-20 12:43 실측: 연결 0.9초, 조회 단계에서 실패 → 마우스가 남음).
 * 그래서 구독은 연결 전에 걸고, 경로([Route])를 알기 전에 온 프레임은 여기 쌓았다가 경로를 알면 넘긴다.
 * 지난번 감시에서 확인한 경로가 있으면 처음부터 열어 둔다.
 */
internal class EarlySignalGate<T>(route: Route?) {

    /** 신호를 해석하는 데 필요한 두 값: 0x1814의 런타임 index와 이 기기 슬롯(0-based). */
    data class Route(val featureIndex: Int, val localHost: Int)

    private var route: Route? = route
    private val held = ArrayList<T>()

    /** 감시가 준비를 마쳤는지. 그 전에 처리한 신호는 로그에 따로 남긴다. */
    @Volatile var ready = false

    /** 경로를 알면 돌려주고, 모르면 [frame]을 쌓아 두고 null을 돌려준다. */
    @Synchronized
    fun offer(frame: T): Route? = route ?: run {
        held += frame
        null
    }

    /** 경로를 정하고(바꾸고), 그동안 쌓인 프레임을 도착 순서대로 돌려준다. */
    @Synchronized
    fun open(route: Route): List<T> {
        this.route = route
        return drainLocked()
    }

    /** 경로를 끝내 알지 못했을 때 버려지는 프레임을 돌려주고 비운다. */
    @Synchronized
    fun drain(): List<T> = drainLocked()

    private fun drainLocked(): List<T> = held.toList().also { held.clear() }
}
