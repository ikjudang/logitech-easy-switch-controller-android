package com.easyswitchcontroller.ui.notice

import org.junit.Assert.*
import org.junit.Test

class QueuedNoticePresenterTest {
    private class Queue {
        val work = ArrayDeque<() -> Unit>()
        var accepts = true
        fun post(action: () -> Unit): Boolean {
            if (!accepts) return false
            work.addLast(action)
            return true
        }
        fun drain() { while (work.isNotEmpty()) work.removeFirst()() }
    }

    private class Renderer : NoticePresenter {
        val messages = mutableListOf<String>()
        val ongoing = mutableListOf<Boolean>()
        var dismissals = 0
        var works = true
        var throws = false
        override fun show(message: CharSequence, ongoing: Boolean): Boolean {
            if (throws) error("attach failed")
            messages += message.toString()
            this.ongoing += ongoing
            return works
        }
        override fun dismiss() { dismissals++ }
    }

    @Test fun submittingDoesNotConstructOrMeasureAWindowOnTheCaller() {
        val owner = Queue()
        val caller = Queue()
        val renderer = Renderer()
        var created = 0
        val presenter = QueuedNoticePresenter(owner::post, { caller.post(it) },
            { created++; renderer }, { fail("Unexpected failure") })
        assertTrue(presenter.show("switching", true))
        assertEquals(0, created)
        assertTrue(renderer.messages.isEmpty())
        owner.drain()
        assertEquals(1, created)
        assertEquals(listOf("switching"), renderer.messages)
        assertEquals(listOf(true), renderer.ongoing)
    }

    @Test fun statusUpdatesReuseTheWindowAndDeliverRepeatedNotices() {
        val owner = Queue()
        val renderer = Renderer()
        var created = 0
        val presenter = QueuedNoticePresenter(owner::post, { it() }, { created++; renderer }, {})
        repeat(30) { presenter.show("same", false); owner.drain() }
        assertEquals(1, created)
        assertEquals(30, renderer.messages.size)
        assertEquals(0, renderer.dismissals)
    }

    @Test fun aSlowQueueKeepsTheLatestStatusAndDismissalCancelsUnshownWork() {
        val owner = Queue()
        val renderer = Renderer()
        val presenter = QueuedNoticePresenter(owner::post, { it() }, { renderer }, {})
        presenter.show("switching", true)
        presenter.show("complete", false)
        owner.drain()
        assertEquals(listOf("complete"), renderer.messages)
        presenter.show("outdated", false)
        presenter.dismiss()
        owner.drain()
        assertEquals(listOf("complete"), renderer.messages)
        assertEquals(1, renderer.dismissals)
    }

    @Test fun aNewRequestMakesAnOlderQueuedFailureHarmless() {
        val owner = Queue()
        val caller = Queue()
        val renderer = Renderer().apply { works = false }
        var failures = 0
        val presenter = QueuedNoticePresenter(owner::post, { caller.post(it) }, { renderer }, { failures++ })
        presenter.show("old", false)
        owner.drain()
        renderer.works = true
        presenter.show("new", false)
        caller.drain()
        assertEquals(0, failures)
        owner.drain()
        assertEquals(listOf("old", "new"), renderer.messages)
    }

    @Test fun attachExceptionsUseTheCallerFailurePathWithoutKillingTheOwnerQueue() {
        val owner = Queue()
        val caller = Queue()
        val renderer = Renderer().apply { throws = true }
        var failures = 0
        var errors = 0
        val presenter = QueuedNoticePresenter(owner::post, { caller.post(it) }, { renderer }, { failures++ }, { errors++ })
        presenter.show("failed", false)
        owner.drain()
        assertEquals(1, errors)
        assertEquals(0, failures)
        caller.drain()
        assertEquals(1, failures)
        renderer.throws = false
        presenter.show("recovered", false)
        owner.drain()
        assertEquals(listOf("recovered"), renderer.messages)
    }

    @Test fun closeCancelsPendingRequestsAndCleansUpBeforeStoppingTheOwner() {
        val owner = Queue()
        val renderer = Renderer()
        val presenter = QueuedNoticePresenter(owner::post, { it() }, { renderer }, {})
        presenter.show("shown", false)
        owner.drain()
        presenter.show("pending", false)
        var stops = 0
        presenter.close { assertEquals(1, renderer.dismissals); stops++ }
        presenter.close { fail("Close must be idempotent") }
        assertFalse(presenter.show("closed", false))
        assertEquals(0, stops)
        owner.drain()
        assertEquals(listOf("shown"), renderer.messages)
        assertEquals(1, stops)
    }

    @Test fun anUnavailableOwnerQueueReturnsFalseForImmediateFallback() {
        val owner = Queue().apply { accepts = false }
        val presenter = QueuedNoticePresenter(owner::post, { it() }, { fail("No owner thread"); Renderer() }, {})
        assertFalse(presenter.show("unavailable", false))
        var stopped = false
        presenter.close { stopped = true }
        assertTrue(stopped)
    }
}
