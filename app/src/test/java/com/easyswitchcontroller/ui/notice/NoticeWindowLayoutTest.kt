package com.easyswitchcontroller.ui.notice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeWindowLayoutTest {
    @Test fun completingEitherDeviceDoesNotRequestAnotherWindowPlacement() {
        val window = NoticeWindowLayout(initialBottom = 64, minimumBottom = 8)
        assertEquals(NoticeWindowLayout.Bounds(280, 76, 64), window.update(280, 76))
        // 키보드 완료, 마우스 전환 중, 전체 완료 모두 같은 창을 그대로 쓴다.
        repeat(3) { assertNull(window.update(280, 76)) }
    }

    @Test fun aLateSecondDeviceExtendsDownwardWithoutMovingTheHeaderOrHorizontalEdges() {
        val window = NoticeWindowLayout(initialBottom = 64, minimumBottom = 8)
        val first = requireNotNull(window.update(280, 58))
        val second = requireNotNull(window.update(280, 76))
        val screenHeight = 900
        assertEquals(first.width, second.width)
        assertEquals(screenHeight - first.bottom - first.height, screenHeight - second.bottom - second.height)
        assertEquals(18, second.height - first.height)
        assertNull(window.update(280, 76))
    }

    @Test fun aLongNoticeStaysWithinTheBottomMargin() {
        val window = NoticeWindowLayout(initialBottom = 64, minimumBottom = 8)
        window.update(280, 58)
        assertTrue(requireNotNull(window.update(280, 180)).bottom >= 8)
    }

    @Test fun aDismissedNoticeHasNoEffectOnTheNextNoticesAnchor() {
        val previous = NoticeWindowLayout(64, 8)
        previous.update(280, 58)
        previous.update(280, 76)
        val replacement = NoticeWindowLayout(64, 8)
        assertEquals(64, requireNotNull(replacement.update(220, 40)).bottom)
    }
}
