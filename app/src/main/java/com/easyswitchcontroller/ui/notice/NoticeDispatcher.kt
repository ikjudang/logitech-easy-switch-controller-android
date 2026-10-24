package com.easyswitchcontroller.ui.notice

/** 디스패처 호출은 메인 스레드에서 직렬화하고, 실제 창의 수명과 소유 스레드는 구현체가 관리한다. */
interface NoticePresenter {
    fun show(message: CharSequence, ongoing: Boolean = false): Boolean
    fun dismiss()
}

/** 다른 앱 위 창을 우선 사용하고, 붙일 수 없으면 현재 앱 화면에 표시한다. */
class NoticeDispatcher(
    private val nowMillis: () -> Long,
    private val lifetimeMs: Long = 2_000,
    private val ongoingLifetimeMs: Long = 30_000,
) {
    private var system: NoticePresenter? = null
    private var foreground: NoticePresenter? = null
    private var active: NoticePresenter? = null
    private var message: CharSequence? = null
    private var requestedAt = 0L
    private var ongoing = false

    fun show(message: CharSequence, ongoing: Boolean = false): Boolean {
        this.message = message
        this.ongoing = ongoing
        requestedAt = nowMillis()
        return deliver()
    }

    fun attachSystem(presenter: NoticePresenter) {
        if (system === presenter) return
        system?.let(::retire)
        system = presenter
        deliverIfChanged()
    }

    fun detachSystem(presenter: NoticePresenter) {
        if (system !== presenter) return
        system = null
        retire(presenter)
        deliverIfChanged()
    }

    fun attachForeground(presenter: NoticePresenter) {
        if (foreground === presenter) return
        foreground?.let(::retire)
        foreground = presenter
        deliverIfChanged()
    }

    fun detachForeground(presenter: NoticePresenter) {
        if (foreground !== presenter) return
        foreground = null
        retire(presenter)
        deliverIfChanged()
    }

    /** 비동기 창 연결이 실패하면 같은 안내를 현재 앱 화면의 대체 경로로 넘긴다. */
    fun presentationFailed(presenter: NoticePresenter) {
        if (active !== presenter) return
        retire(presenter)
        deliver(excluding = presenter)
    }

    private fun retire(presenter: NoticePresenter) {
        presenter.dismiss()
        if (active === presenter) active = null
    }

    private fun deliverIfChanged() {
        if ((system ?: foreground) !== active) deliver()
    }

    private fun deliver(excluding: NoticePresenter? = null): Boolean {
        val text = message ?: return false
        if (nowMillis() - requestedAt >= if (ongoing) ongoingLifetimeMs else lifetimeMs) {
            message = null
            return false
        }
        for (presenter in listOfNotNull(system, foreground).distinct()) {
            if (presenter === excluding) continue
            if (active !== presenter) active?.let(::retire)
            if (presenter.show(text, ongoing)) {
                active = presenter
                return true
            }
            retire(presenter)
        }
        return false
    }
}
