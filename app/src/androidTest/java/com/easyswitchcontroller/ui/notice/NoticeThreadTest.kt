package com.easyswitchcontroller.ui.notice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.easyswitchcontroller.device.SwitchIndicator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** 실제 토스트 뷰의 소유 스레드를 검증한다. 기기 화면이나 실제 오버레이 창은 읽거나 붙이지 않는다. */
@RunWith(AndroidJUnit4::class)
class NoticeThreadTest {
    @Test fun nativeToastWorkDoesNotWaitOnTheAnimationThreadAndKeepsItsOwnViewOwnership() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val thread = HandlerThread("NoticeIsolationTest").apply { start() }
        val owner = Handler(thread.looper)
        val main = Handler(Looper.getMainLooper())
        val enteredAttach = CountDownLatch(1)
        val allowAttach = CountDownLatch(1)
        val mainResponded = CountDownLatch(1)
        val rendered = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        var overlay: MemoryOverlay? = null
        val queued = QueuedNoticePresenter(
            post = { owner.post(it) }, postToCaller = { main.post(it) },
            create = {
                MemoryOverlay(context, thread.looper) {
                    assertNotSame(Looper.getMainLooper(), Looper.myLooper())
                    enteredAttach.countDown()
                    assertTrue("Test release never arrived", allowAttach.await(5, TimeUnit.SECONDS))
                }.also { overlay = it }
            },
            onFailure = { failure.compareAndSet(null, AssertionError("Unexpected fallback")) },
            onError = { failure.compareAndSet(null, it) },
        )
        try {
            instrumentation.runOnMainSync {
                assertTrue(queued.show(DeviceNoticeMessage(null, listOf(
                    DeviceNoticeMessage.Row("MX Mechanical", "연결됨", SwitchIndicator.NotSelected),
                    DeviceNoticeMessage.Row("MX Master 4", "연결됨", SwitchIndicator.NotSelected),
                )), false))
            }
            assertTrue(enteredAttach.await(5, TimeUnit.SECONDS))
            main.post { mainResponded.countDown() }
            assertTrue("The main animation thread waited for toast work", mainResponded.await(2, TimeUnit.SECONDS))
            allowAttach.countDown()
            owner.post {
                try { requireNotNull(overlay).drawInMemory() }
                catch (error: Throwable) { failure.compareAndSet(null, error) }
                finally { rendered.countDown() }
            }
            assertTrue(rendered.await(5, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Toast owner thread failed", it) }
        } finally {
            allowAttach.countDown()
            queued.close { thread.quitSafely(); stopped.countDown() }
            assertTrue(stopped.await(5, TimeUnit.SECONDS))
        }
    }

    private class MemoryOverlay(context: Context, private val looper: Looper,
        private val beforeAttach: () -> Unit) : NoticeOverlay(context, looper) {
        private var root: View? = null
        override fun attach(view: View) {
            assertSame(looper, Looper.myLooper())
            beforeAttach()
            root = view
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        override fun detach(view: View) {
            assertSame(looper, Looper.myLooper())
            assertSame(root, view)
            root = null
        }
        fun drawInMemory() {
            assertSame(looper, Looper.myLooper())
            val view = requireNotNull(root)
            view.viewTreeObserver.dispatchOnPreDraw()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            assertTrue(view.width > 0 && view.height > 0)
            bitmap.recycle()
        }
    }
}
