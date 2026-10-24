package com.easyswitchcontroller.ui.notice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.easyswitchcontroller.R
import com.easyswitchcontroller.device.SwitchIndicator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 화면 캡처 없이 실제 토스트 뷰만 Bitmap에 그린다. 네이티브 창은 붙이지 않는다. */
@RunWith(AndroidJUnit4::class)
class NoticeRenderTest {
    @Test fun renderConnectionAndSwitchStatesAndKeepStatusUpdatesInTheSameBounds() = onMain {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "toast-preview").apply { mkdirs() }
        val names = listOf("MX Mechanical", "MX Master 4")
        val connected = context.getString(R.string.device_connected)
        for (count in 1..2) {
            val overlay = MemoryOverlay(context)
            assertTrue(overlay.show(DeviceNoticeMessage(null, names.take(count).map {
                DeviceNoticeMessage.Row(it, connected, SwitchIndicator.NotSelected)
            })))
            overlay.render(File(directory, "connected-$count.png"))
            overlay.dismiss()
        }

        val overlay = MemoryOverlay(context)
        val title = context.getString(R.string.toast_switch_title, 2)
        fun message(first: SwitchIndicator, second: SwitchIndicator? = null) = SwitchNoticeMessage(title,
            listOfNotNull(first, second).mapIndexed { index, indicator ->
                val status = when (indicator) {
                    SwitchIndicator.Pending -> R.string.toast_switch_status_switching
                    SwitchIndicator.Success -> R.string.toast_switch_status_complete
                    else -> R.string.toast_switch_status_failed
                }
                DeviceNoticeMessage.Row(names[index], context.getString(status), indicator)
            }, operationId = 42L)
        assertTrue(overlay.show(message(SwitchIndicator.Pending), ongoing = true))
        overlay.render(File(directory, "switch-one-pending.png"))
        val oneWidth = overlay.root.width
        val oneHeight = overlay.root.height
        assertTrue(overlay.show(message(SwitchIndicator.Success)))
        assertEquals(0, overlay.relayouts)
        overlay.render(File(directory, "switch-one-complete.png"))
        assertEquals(oneWidth, overlay.root.width)
        assertEquals(oneHeight, overlay.root.height)

        assertTrue(overlay.show(message(SwitchIndicator.Success, SwitchIndicator.Pending), ongoing = true))
        assertEquals(1, overlay.relayouts)
        assertEquals(oneWidth, overlay.width)
        overlay.render(File(directory, "switch-two-partial.png"))
        val twoHeight = overlay.root.height
        assertEquals(64 * context.resources.displayMetrics.density, (overlay.bottom + twoHeight - oneHeight).toFloat(), 1f)
        assertTrue(overlay.show(message(SwitchIndicator.Success, SwitchIndicator.Success)))
        assertEquals(1, overlay.relayouts)
        overlay.render(File(directory, "switch-two-complete.png"))
        assertEquals(twoHeight, overlay.root.height)
        assertTrue(overlay.show(message(SwitchIndicator.Success, SwitchIndicator.Failure)))
        assertEquals(1, overlay.relayouts)
        overlay.render(File(directory, "switch-two-failed.png"))
        assertEquals(1, overlay.attachments)
        overlay.dismiss()

        val error = MemoryOverlay(context)
        error.show(context.getString(R.string.toast_switch_no_devices, 2))
        error.render(File(directory, "message-error.png"))
        error.dismiss()
        File(directory, "render-result.txt").writeText("PASS: every toast icon is vertically centered; status changes keep native placement; late second row grows downward.\n")
    }

    private class MemoryOverlay(context: Context) : NoticeOverlay(context) {
        lateinit var root: View
        var attachments = 0
        var relayouts = 0
        var width = 0
        var height = 0
        var bottom = 0
        override fun attach(view: View) {
            attachments++
            root = view
            width = view.measuredWidth
            height = view.measuredHeight
            bottom = dp(64)
            view.layout(0, 0, width, height)
        }
        override fun relayout(view: View, width: Int, height: Int, bottomOffset: Int) {
            relayouts++
            this.width = width
            this.height = height
            bottom = bottomOffset
        }
        override fun detach(view: View) {}
        fun render(file: File) {
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            root.viewTreeObserver.dispatchOnPreDraw()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val icon = (root as ViewGroup).getChildAt(0)
            assertEquals("Every rendered toast icon must stay at the vertical center of the whole toast",
                root.height / 2f, icon.y + icon.height / 2f, 1f)
            val reference = Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ARGB_8888)
            icon.draw(Canvas(reference))
            val expected = reference.getPixel(icon.width / 2, 2)
            val actual = bitmap.getPixel((icon.x + icon.width / 2f).toInt(), kotlin.math.ceil(icon.y.toDouble()).toInt() + 2)
            val difference = listOf(android.graphics.Color.red(expected) - android.graphics.Color.red(actual),
                android.graphics.Color.green(expected) - android.graphics.Color.green(actual),
                android.graphics.Color.blue(expected) - android.graphics.Color.blue(actual)).maxOf { kotlin.math.abs(it) }
            assertTrue("Vertical centering must not clip the launcher icon's top edge", difference <= 12)
            reference.recycle()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
