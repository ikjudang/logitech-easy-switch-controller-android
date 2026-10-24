package com.easyswitchcontroller.ui.notice

/** 창의 생성·변경·제거를 같은 소유 스레드에 예약한다. 호출자는 창 작업이 끝나기를 기다리지 않는다. */
internal class QueuedNoticePresenter(
    private val post: (() -> Unit) -> Boolean,
    private val postToCaller: (() -> Unit) -> Unit,
    private val create: () -> NoticePresenter,
    private val onFailure: () -> Unit,
    private val onError: (Exception) -> Unit = {},
) : NoticePresenter {
    private val lock = Any()
    @Volatile private var generation = 0L
    @Volatile private var closed = false
    // 아래 참조는 post가 실행하는 스레드에서만 읽고 쓴다.
    private var renderer: NoticePresenter? = null

    override fun show(message: CharSequence, ongoing: Boolean): Boolean {
        val ticket = synchronized(lock) {
            if (closed) return false
            ++generation
        }
        return post {
            if (!current(ticket)) return@post
            val shown = try {
                val target = renderer ?: create().also { renderer = it }
                target.show(message, ongoing)
            } catch (error: Exception) {
                onError(error)
                false
            }
            if (!shown) postToCaller { if (current(ticket)) onFailure() }
        }
    }

    override fun dismiss() {
        synchronized(lock) {
            if (closed) return
            generation++
        }
        post { dismissRenderer() }
    }

    fun close(onClosed: () -> Unit) {
        synchronized(lock) {
            if (closed) return
            closed = true
            generation++
        }
        val accepted = post {
            try { dismissRenderer() }
            finally { renderer = null; onClosed() }
        }
        if (!accepted) onClosed()
    }

    private fun current(ticket: Long) = !closed && generation == ticket

    private fun dismissRenderer() {
        try { renderer?.dismiss() }
        catch (error: Exception) { onError(error) }
    }
}
