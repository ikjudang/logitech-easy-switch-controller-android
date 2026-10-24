package com.easyswitchcontroller.floating

/** 비동기 창 밖 알림이 이전 메뉴를 대상으로 했으면 새로 연 메뉴를 접지 않는다. */
internal class OutsideMenuDismissal {
    class Ticket internal constructor(internal val generation: Long)
    private var generation = 0L
    private var openedAt: Long? = null

    fun opened(eventTime: Long) {
        generation++
        openedAt = eventTime
    }

    fun closed() {
        generation++
        openedAt = null
    }

    fun request(pressTime: Long): Ticket? {
        val start = openedAt ?: return null
        return if (pressTime >= start) Ticket(generation) else null
    }

    fun isCurrent(ticket: Ticket): Boolean = openedAt != null && ticket.generation == generation
}
