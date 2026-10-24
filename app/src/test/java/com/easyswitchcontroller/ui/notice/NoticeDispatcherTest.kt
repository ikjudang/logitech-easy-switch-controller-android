package com.easyswitchcontroller.ui.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeDispatcherTest {
    @Test fun aSlowMouseUpdatesTheSamePresenterAndEndsTheOngoingState() {
        var now = 0L
        val dispatcher = NoticeDispatcher({ now })
        val service = Presenter()
        dispatcher.attachSystem(service)
        dispatcher.show("Channel 2\nKeyboard ○\nMouse …", ongoing = true)
        now = 9_000
        dispatcher.show("Channel 2\nKeyboard ○\nMouse ○")
        assertEquals(listOf(true, false), service.ongoingStates)
        assertEquals(0, service.dismissals)
    }

    @Test fun anOngoingSwitchSurvivesTheNormalLifetimeWhenThePresenterChanges() {
        var now = 0L
        val dispatcher = NoticeDispatcher({ now })
        dispatcher.show("Switching", ongoing = true)
        now = 9_000
        val replacement = Presenter()
        dispatcher.attachSystem(replacement)
        assertEquals(listOf("Switching"), replacement.messages)
        assertEquals(listOf(true), replacement.ongoingStates)
    }

    @Test fun theCompletedSwitchGetsTheNormalLifetimeFromItsLatestResult() {
        var now = 0L
        val dispatcher = NoticeDispatcher({ now })
        dispatcher.show("Switching", ongoing = true)
        now = 9_000
        dispatcher.show("Complete")
        now = 11_000
        val replacement = Presenter()
        dispatcher.attachSystem(replacement)
        assertTrue(replacement.messages.isEmpty())
    }

    private class Presenter(var works: Boolean = true) : NoticePresenter {
        val messages = mutableListOf<String>()
        var dismissals = 0
        val ongoingStates = mutableListOf<Boolean>()
        override fun show(message: CharSequence, ongoing: Boolean): Boolean {
            ongoingStates += ongoing
            if (works) messages += message.toString()
            return works
        }
        override fun dismiss() { dismissals++ }
    }

    @Test fun identicalNoticesAreDeliveredEveryTimeWithoutARateLimit() {
        val dispatcher = NoticeDispatcher({ 0L })
        val service = Presenter()
        dispatcher.attachSystem(service)
        repeat(30) { assertTrue(dispatcher.show("same connection")) }
        assertEquals(30, service.messages.size)
        assertEquals(0, service.dismissals)
    }

    @Test fun anActiveSystemOverlayTakesPriorityOverTheActivity() {
        val dispatcher = NoticeDispatcher({ 0L })
        val service = Presenter()
        val activity = Presenter()
        dispatcher.attachSystem(service)
        dispatcher.attachForeground(activity)
        dispatcher.show("result")
        assertEquals(listOf("result"), service.messages)
        assertTrue(activity.messages.isEmpty())
    }

    @Test fun theActivityCanDisplayNoticesWithoutTheSystemOverlay() {
        val dispatcher = NoticeDispatcher({ 0L })
        val activity = Presenter()
        dispatcher.attachForeground(activity)
        assertTrue(dispatcher.show("manual switch"))
        assertEquals(listOf("manual switch"), activity.messages)
    }

    @Test fun onlyTheLatestWaitingNoticeIsDeliveredAfterBinding() {
        var now = 0L
        val dispatcher = NoticeDispatcher({ now })
        assertFalse(dispatcher.show("old"))
        now = 100
        assertFalse(dispatcher.show("latest"))
        val service = Presenter()
        now = 200
        dispatcher.attachSystem(service)
        assertEquals(listOf("latest"), service.messages)
    }

    @Test fun expiredNoticesDoNotReappearWhenTheServiceReconnects() {
        var now = 0L
        val dispatcher = NoticeDispatcher({ now })
        dispatcher.show("old")
        now = 2_000
        val service = Presenter()
        dispatcher.attachSystem(service)
        assertTrue(service.messages.isEmpty())
    }

    @Test fun everyRepeatedRequestRenewsTheWaitingNoticeLifetime() {
        var now = 0L
        val dispatcher = NoticeDispatcher({ now })
        dispatcher.show("same")
        now = 1_500
        dispatcher.show("same")
        now = 2_500
        val service = Presenter()
        dispatcher.attachSystem(service)
        assertEquals(listOf("same"), service.messages)
    }

    @Test fun stoppingAnOldActivityDoesNotDetachItsReplacement() {
        val dispatcher = NoticeDispatcher({ 0L })
        val old = Presenter()
        val replacement = Presenter()
        dispatcher.attachForeground(old)
        dispatcher.attachForeground(replacement)
        dispatcher.detachForeground(old)
        dispatcher.show("new activity")
        assertEquals(listOf("new activity"), replacement.messages)
    }

    @Test fun stoppingTheActivityDoesNotRemoveTheSystemWindow() {
        val dispatcher = NoticeDispatcher({ 0L })
        val service = Presenter()
        val activity = Presenter()
        dispatcher.attachSystem(service)
        dispatcher.attachForeground(activity)
        dispatcher.show("result")
        dispatcher.detachForeground(activity)
        assertEquals(0, service.dismissals)
        assertEquals(listOf("result"), service.messages)
    }

    @Test fun stoppingTheServiceTransfersAnActiveNoticeToTheActivity() {
        val dispatcher = NoticeDispatcher({ 0L })
        val service = Presenter()
        val activity = Presenter()
        dispatcher.attachSystem(service)
        dispatcher.attachForeground(activity)
        dispatcher.show("result")
        dispatcher.detachSystem(service)
        assertEquals(1, service.dismissals)
        assertEquals(listOf("result"), activity.messages)
    }

    @Test fun anUnavailableSystemWindowFallsBackToTheActivity() {
        val dispatcher = NoticeDispatcher({ 0L })
        val service = Presenter(works = false)
        val activity = Presenter()
        dispatcher.attachSystem(service)
        dispatcher.attachForeground(activity)
        assertTrue(dispatcher.show("result"))
        assertEquals(1, service.dismissals)
        assertEquals(listOf("result"), activity.messages)
    }

    @Test fun aQueuedWindowFailureTransfersTheSameOngoingNoticeToTheActivity() {
        val dispatcher = NoticeDispatcher({ 0L })
        val service = Presenter()
        val activity = Presenter()
        dispatcher.attachSystem(service)
        dispatcher.attachForeground(activity)
        dispatcher.show("switching", ongoing = true)
        dispatcher.presentationFailed(service)
        assertEquals(listOf("switching"), service.messages)
        assertEquals(1, service.dismissals)
        assertEquals(listOf("switching"), activity.messages)
        assertEquals(listOf(true), activity.ongoingStates)
        dispatcher.show("next")
        assertEquals(listOf("switching", "next"), service.messages)
    }

    @Test fun aFailureFromARetiredPresenterDoesNotReplaceTheNewNotice() {
        val dispatcher = NoticeDispatcher({ 0L })
        val old = Presenter()
        val replacement = Presenter()
        val activity = Presenter()
        dispatcher.attachSystem(old)
        dispatcher.show("old")
        dispatcher.attachSystem(replacement)
        dispatcher.attachForeground(activity)
        dispatcher.show("latest")
        dispatcher.presentationFailed(old)
        assertTrue(activity.messages.isEmpty())
        assertEquals(listOf("old", "latest"), replacement.messages)
    }
}
