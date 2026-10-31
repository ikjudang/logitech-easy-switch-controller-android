package com.easyswitchcontroller.ui.notice

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.FrameLayout
import com.easyswitchcontroller.floating.FloatingPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.easyswitchcontroller.R
import com.easyswitchcontroller.device.SwitchIndicator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import kotlin.math.ceil

/** 화면 캡처 없이 실제 토스트 뷰만 Bitmap에 그린다. 네이티브 창은 붙이지 않는다. */
@RunWith(AndroidJUnit4::class)
class NoticeRenderTest {
    @Test fun duplicatePreferencesDoNotPublishRevisionsOrRepeatSettingsWork() = onMain {
        withNoticePreferences { context ->
            val before = NoticePreferences.revision.value
            assertFalse(NoticePreferences.setEnabled(context, true))
            assertFalse(NoticePreferences.setTransparency(context, 0))
            assertEquals(before, NoticePreferences.revision.value)
            assertTrue(NoticePreferences.setTransparency(context, 50))
            assertFalse(NoticePreferences.setTransparency(context, 50))
            assertEquals(before + 1, NoticePreferences.revision.value)
        }
        withNoticePreferences { context ->
            val before = FloatingPreferences.revision.value
            FloatingPreferences.setTransparency(context, 0)
            FloatingPreferences.setSizePercent(context, 100)
            FloatingPreferences.setAnimationSpeedPercent(context, 100)
            FloatingPreferences.setEnabled(context, false)
            FloatingPreferences.setBounceEnabled(context, true)
            FloatingPreferences.setAlwaysShowMenu(context, false)
            assertEquals(before, FloatingPreferences.revision.value)
            repeat(2) {
                FloatingPreferences.setTransparency(context, 80)
                FloatingPreferences.setSizePercent(context, 200)
                FloatingPreferences.setAnimationSpeedPercent(context, 300)
                FloatingPreferences.setEnabled(context, true)
                FloatingPreferences.setBounceEnabled(context, false)
                FloatingPreferences.setAlwaysShowMenu(context, true)
            }
            assertEquals(before + 6, FloatingPreferences.revision.value)
            FloatingPreferences.setTransparency(context, 101)
            val atLimit = FloatingPreferences.revision.value
            FloatingPreferences.setTransparency(context, 100)
            assertEquals(atLimit, FloatingPreferences.revision.value)
        }
    }

    @Test fun toastPreferencesReloadAndDisablingDoesNotResetTransparency() = onMain {
        withNoticePreferences { context ->
            assertEquals(NoticeAppearance(), NoticePreferences.load(context))
            NoticePreferences.setTransparency(context, 47)
            NoticePreferences.setEnabled(context, false)
            assertEquals(NoticeAppearance(false, 47), NoticePreferences.load(context))
            val overlay = MemoryOverlay(context)
            try {
                assertTrue(overlay.show("disabled"))
                assertEquals(0, overlay.attachments)
                NoticePreferences.setEnabled(context, true)
                assertTrue(overlay.show("fresh"))
                assertEquals(1, overlay.attachments)
                assertEquals(47, NoticePreferences.load(context).transparency)
                NoticePreferences.setTransparency(context, 101)
                assertEquals(100, NoticePreferences.load(context).transparency)
                NoticePreferences.setTransparency(context, -1)
                assertEquals(0, NoticePreferences.load(context).transparency)
            } finally { overlay.dismiss() }
        }
    }

    @Test fun liveTransparencyScalesTheWholeToastWithoutReattachingOrResizing() = onMain {
        withNoticePreferences { context ->
            val overlay = MemoryOverlay(context)
            try {
                overlay.show(SwitchNoticeMessage("Channel 2", listOf(
                    DeviceNoticeMessage.Row("MX Mechanical", "Completed", SwitchIndicator.Success)), operationId = 84L))
                val initialFile = File(context.filesDir, "toast-settings-default.png")
                overlay.render(initialFile)
                val initial = BitmapFactory.decodeFile(initialFile.path)
                val initialPixels = IntArray(initial.width * initial.height)
                initial.getPixels(initialPixels, 0, initial.width, 0, 0, initial.width, initial.height)
                initial.recycle()
                val root = overlay.root as ViewGroup
                val background = root.background as GradientDrawable
                assertEquals(0xF0303030.toInt(), requireNotNull(background.color).defaultColor)
                val bounds = root.width to root.height
                val icon = root.getChildAt(0) as ImageView
                val drawable = icon.drawable
                val colors = overlay.rows().map { (name, status) -> name.currentTextColor to status.currentTextColor }
                for (transparency in listOf(50, 100)) {
                    NoticePreferences.setTransparency(context, transparency)
                    overlay.updateAppearance()
                    val output = File(context.filesDir, "toast-settings-$transparency.png")
                    overlay.render(output)
                    val rendered = BitmapFactory.decodeFile(output.path)
                    val pixels = IntArray(rendered.width * rendered.height)
                    rendered.getPixels(pixels, 0, rendered.width, 0, 0, rendered.width, rendered.height)
                    rendered.recycle()
                    val opacity = NoticeAppearance(transparency = transparency).opacity
                    initialPixels.indices.forEach { index ->
                        assertEquals("Background, text and launcher icon must fade together",
                            Color.alpha(initialPixels[index]) * opacity, Color.alpha(pixels[index]).toFloat(), 1f)
                    }
                    assertSame(background, root.background)
                    assertEquals(NoticeAppearance(transparency = transparency).backgroundColor, requireNotNull(background.color).defaultColor)
                    assertEquals(bounds, root.width to root.height)
                    assertEquals(NoticeAppearance(transparency = transparency).opacity, root.alpha, 0f)
                    assertEquals(1f, icon.alpha, 0f)
                    assertSame(drawable, icon.drawable)
                    assertEquals(colors, overlay.rows().map { (name, status) -> name.currentTextColor to status.currentTextColor })
                    overlay.rows().forEach { (name, status) ->
                        assertEquals(1f, name.alpha, 0f)
                        assertEquals(1f, status.alpha, 0f)
                    }
                }
                assertEquals(1, overlay.attachments)
                assertEquals(0, overlay.relayouts)
            } finally { overlay.dismiss() }
        }
    }

    @Test fun localizedColumnsKeepTheirBoundsForEveryStatusAndLongDeviceNames() = onMain {
        for (language in listOf("ko", "en")) {
            val context = layoutContext(language)
            val directory = File(context.filesDir, "toast-layout-preview/$language").apply { mkdirs() }
            val statuses = statusLabels(context)
            var referenceWidth: Int? = null
            for (withHeader in listOf(false, true)) for (count in 1..2) {
                val overlay = MemoryOverlay(context)
                try {
                    var referenceHeight: Int? = null
                    for ((index, status) in statuses.withIndex()) {
                        val longName = "MX Mechanical — Very Long Device Name That Must Be Ellipsized"
                        val rows = (0 until count).map {
                            DeviceNoticeMessage.Row(if (index % 2 == 0) longName else "MX", status, SwitchIndicator.Pending)
                        }
                        val message = if (withHeader) {
                            SwitchNoticeMessage(context.getString(R.string.toast_switch_title, 2), rows, operationId = index.toLong())
                        } else DeviceNoticeMessage(null, rows)
                        assertTrue(overlay.show(message))
                        overlay.render(File(directory, "${if (withHeader) "switch" else "connection"}-$count-$index.png"))
                        val root = overlay.root as ViewGroup
                        assertEquals(dp(context, 12), root.paddingLeft)
                        assertEquals(root.paddingLeft, root.paddingTop)
                        assertEquals(root.paddingLeft, root.paddingRight)
                        assertEquals(root.paddingLeft, root.paddingBottom)
                        referenceWidth?.let { assertEquals("Neither names, statuses, nor row count may change the width", it, root.width) }
                        referenceWidth = root.width
                        referenceHeight?.let { assertEquals("Status updates must not change row height", it, root.height) }
                        referenceHeight = root.height
                        var statusLeft: Int? = null
                        for ((name, state) in overlay.rows()) {
                            assertEquals(dp(context, 100), name.width)
                            assertEquals(dp(context, 8), state.left - name.right)
                            statusLeft?.let { assertEquals(it, state.left) }
                            statusLeft = state.left
                            assertEquals(Gravity.START or Gravity.CENTER_VERTICAL, state.gravity)
                            assertEquals(ceil(statuses.maxOf { state.paint.measureText(it) }.toDouble()).toInt(), state.width)
                            assertEquals((name.top + name.baseline).toFloat(), (state.top + state.baseline).toFloat(), 1f)
                            assertEquals(1, state.lineCount)
                            assertEquals(0, state.layout.getEllipsisCount(0))
                            assertTrue(state.layout.height <= state.height)
                            assertEquals(index % 2 == 0, name.layout.getEllipsisCount(0) > 0)
                        }
                    }
                    assertEquals(1, overlay.attachments)
                    assertEquals(0, overlay.relayouts)
                } finally { overlay.dismiss() }
            }
        }
    }

    @Test fun narrowScreensPreserveTheStatusColumnBeforeReducingTheNameColumn() = onMain {
        val context = layoutContext("en", widthDp = 240)
        val overlay = MemoryOverlay(context)
        try {
            overlay.show(DeviceNoticeMessage(null, listOf(DeviceNoticeMessage.Row(
                "MX Mechanical with a very long name", context.getString(R.string.toast_switch_status_unconfirmed), SwitchIndicator.Pending))))
            overlay.render(File(context.filesDir, "toast-layout-narrow.png"))
            val (name, status) = overlay.rows().single()
            assertEquals(240 - 48, (overlay.root.width / context.resources.displayMetrics.density).toInt())
            assertTrue(name.width < dp(context, 100))
            assertTrue(name.width > 0)
            assertEquals(LinearLayout.HORIZONTAL, (name.parent as LinearLayout).orientation)
            assertTrue(name.layout.getEllipsisCount(0) > 0)
            assertEquals(ceil(statusLabels(context).maxOf { status.paint.measureText(it) }.toDouble()).toInt(), status.width)
            assertEquals(1, status.lineCount)
            assertEquals(0, status.layout.getEllipsisCount(0))
        } finally { overlay.dismiss() }
    }

    @Test fun largeFontsWrapStatusesOnVeryNarrowScreensWithoutClippingOrResizingOnUpdates() = onMain {
        for (language in listOf("ko", "en")) {
            val context = layoutContext(language, widthDp = 200, fontScale = 2f)
            val directory = File(context.filesDir, "toast-layout-preview/$language").apply { mkdirs() }
            val overlay = MemoryOverlay(context)
            try {
                var bounds: Pair<Int, Int>? = null
                for ((index, state) in statusLabels(context).withIndex()) {
                    overlay.show(SwitchNoticeMessage(context.getString(R.string.toast_switch_title, 2), listOf(
                        DeviceNoticeMessage.Row("MX Mechanical", state, SwitchIndicator.Pending)), operationId = 99L))
                    overlay.render(File(directory, "large-font-$index.png"))
                    bounds?.let { assertEquals(it, overlay.root.width to overlay.root.height) }
                    bounds = overlay.root.width to overlay.root.height
                    val (name, status) = overlay.rows().single()
                    assertEquals(LinearLayout.VERTICAL, (name.parent as LinearLayout).orientation)
                    assertEquals(name.left, status.left)
                    assertEquals(name.width, status.width)
                    assertEquals(dp(context, 8), status.top - name.bottom)
                    assertNull(status.ellipsize)
                    assertEquals(state.length, status.layout.getLineEnd(status.lineCount - 1))
                    assertTrue("All wrapped status lines must fit in the row", status.bottom <= (status.parent as View).height)
                    assertTrue(status.layout.height <= status.height)
                    val title = ((overlay.root as ViewGroup).getChildAt(1) as ViewGroup).getChildAt(0) as TextView
                    assertEquals(title.text.length, title.layout.getLineEnd(title.lineCount - 1))
                    assertTrue("$language title layout=${title.layout.height} view=${title.height}", title.layout.height <= title.height)
                    assertTrue(overlay.root.width <= context.resources.displayMetrics.widthPixels - dp(context, 48))
                }
                assertEquals(0, overlay.relayouts)
            } finally { overlay.dismiss() }
        }
    }

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
        private val container = FrameLayout(context)
        lateinit var root: View
        var attachments = 0
        var relayouts = 0
        var width = 0
        var height = 0
        var bottom = 0
        override fun attach(view: View) {
            attachments++
            root = view
            container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
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
        override fun detach(view: View) { container.removeView(view) }
        fun rows(): List<Pair<TextView, TextView>> {
            val body = (root as ViewGroup).getChildAt(1) as ViewGroup
            val rows = body.getChildAt(1) as ViewGroup
            return (0 until rows.childCount).map { index ->
                val row = rows.getChildAt(index) as ViewGroup
                (row.getChildAt(0) as TextView) to (row.getChildAt(1) as TextView)
            }
        }
        fun render(file: File) {
            container.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            container.layout(0, 0, width, height)
            root.viewTreeObserver.dispatchOnPreDraw()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            container.draw(Canvas(bitmap))
            val icon = (root as ViewGroup).getChildAt(0)
            assertEquals("Every rendered toast icon must stay at the vertical center of the whole toast",
                root.height / 2f, icon.y + icon.height / 2f, 1f)
            if (root.alpha == 1f) {
            val reference = Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ARGB_8888)
            icon.draw(Canvas(reference))
            val expected = reference.getPixel(icon.width / 2, 2)
            val actual = bitmap.getPixel((icon.x + icon.width / 2f).toInt(), kotlin.math.ceil(icon.y.toDouble()).toInt() + 2)
            val difference = listOf(android.graphics.Color.red(expected) - android.graphics.Color.red(actual),
                android.graphics.Color.green(expected) - android.graphics.Color.green(actual),
                android.graphics.Color.blue(expected) - android.graphics.Color.blue(actual)).maxOf { kotlin.math.abs(it) }
            assertTrue("Vertical centering must not clip the launcher icon's top edge", difference <= 12)
            reference.recycle()
            }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    /** Separate probe preferences: never read or modify the installed product's settings. */
    private fun withNoticePreferences(block: (Context) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "toast_appearance_fixture"
        val preferences = base.getSharedPreferences(name, Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = preferences
        }
        try { block(context) }
        finally { preferences.edit().clear().commit(); base.deleteSharedPreferences(name) }
    }

    /** Independent resources: no screen reads, system font changes, or app preference changes. */
    @Suppress("DEPRECATION")
    private fun layoutContext(language: String, widthDp: Int = 360, fontScale: Float = 1f): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
            this.fontScale = fontScale
            screenWidthDp = widthDp
        }
        val configured = base.createConfigurationContext(configuration)
        val metrics = DisplayMetrics().apply {
            setTo(configured.resources.displayMetrics)
            widthPixels = (widthDp * density + 0.5f).toInt()
        }
        val resources = Resources(base.assets, metrics, configuration).apply {
            // Resources construction recalculates display metrics from the device display.
            displayMetrics.widthPixels = (widthDp * displayMetrics.density + 0.5f).toInt()
        }
        return object : ContextWrapper(configured) {
            override fun getResources() = resources
        }
    }

    private fun statusLabels(context: Context) = listOf(
        R.string.toast_switch_status_complete, R.string.toast_switch_status_failed,
        R.string.toast_switch_status_switching, R.string.toast_switch_status_unconfirmed, R.string.device_connected,
    ).map { context.getString(it) }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
