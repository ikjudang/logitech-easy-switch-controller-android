package com.easyswitchcontroller.ui.notice

import android.app.Activity
import com.easyswitchcontroller.BuildConfig
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.LocaleList
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import androidx.appcompat.app.AppCompatDelegate
import com.easyswitchcontroller.R
import com.easyswitchcontroller.data.ConnectionLog
import com.easyswitchcontroller.device.SwitchIndicator
import kotlin.math.ceil

/** 같은 창의 내용과 자동 닫힘 시간만 갱신한다. 반복 횟수 제한이나 중복 문구 억제는 없다. */
abstract class NoticeOverlay(private val context: Context,
    private val ownerLooper: Looper = Looper.getMainLooper()) : NoticePresenter {
    private val handler = Handler(ownerLooper)
    private var bubble: LinearLayout? = null
    private var label: TextView? = null
    private var content: LinearLayout? = null
    private var deviceRows: LinearLayout? = null
    private val rowLabels = mutableListOf<Pair<TextView, TextView>>()
    private var frame: Frame? = null
    private var frameKey: FrameKey? = null
    private var windowLayout: NoticeWindowLayout? = null
    private var generation = 0L
    private val hide = Runnable { dismiss() }

    protected abstract fun attach(view: View)
    protected abstract fun detach(view: View)
    protected open fun relayout(view: View, width: Int, height: Int, bottomOffset: Int) {}

    /** 새 창을 붙일 수 있는지. false면 [show]가 조용히 실패해 디스패처가 다음 경로로 넘긴다. */
    protected open fun available(): Boolean = true

    final override fun show(message: CharSequence, ongoing: Boolean): Boolean {
        check(Looper.myLooper() == ownerLooper)
        if (!NoticePreferences.load(context).enabled) {
            dismiss()
            return true
        }
        val existing = bubble
        if (existing == null && !available()) return false
        val reused = existing != null
        val root = existing ?: createBubble()
        updateBackground(root)
        // 처음 창을 붙일 때부터 최종 크기를 사용한다.
        val fixed = updateContent(message)
        val width = dp(PADDING_DP) * 2 + dp(ICON_SIZE_DP) + dp(ICON_GAP_DP) + fixed.contentWidth
        val height = dp(PADDING_DP) * 2 + maxOf(dp(ICON_SIZE_DP), fixed.contentHeight)
        val layout = windowLayout ?: NoticeWindowLayout(dp(64), dp(8)).also { windowLayout = it }
        val changed = layout.update(width, height)
        if (existing == null) {
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            try {
                attach(root)
                bubble = root
            } catch (error: Exception) {
                runCatching { detach(root) }
                label = null
                content = null
                deviceRows = null
                rowLabels.clear()
                frame = null
                frameKey = null
                windowLayout = null
                Log.w("AppToast", "오버레이 창 연결 실패", error)
                return false
            }
        } else if (changed != null) {
            // 행이 늘어나도 첫 헤더는 제자리에 두고 아래로 확장한다.
            relayout(root, changed.width, changed.height, changed.bottom)
        }
        val frame = ++generation
        if (BuildConfig.DEBUG) root.doOnPreDraw {
            if (frame == generation && bubble === root && root.isShown) {
                val action = if (reused) "내용 갱신" else "창 추가"
                Log.i("AppToast", "오버레이 표시됨 [$frame, $action]: $message")
                val position = IntArray(2)
                root.getLocationOnScreen(position)
                ConnectionLog.append("NOTICE_FRAME generation=$frame reused=$reused resized=${changed != null} " +
                    "x=${position[0]} y=${position[1]} width=${root.width} height=${root.height} thread=${Thread.currentThread().name}", ConnectionLog.Level.Dim)
            }
        }
        handler.removeCallbacks(hide)
        root.invalidate()
        handler.postDelayed(hide, if (ongoing) ONGOING_DURATION_MS else DURATION_MS)
        return true
    }

    final override fun dismiss() {
        check(Looper.myLooper() == ownerLooper)
        handler.removeCallbacks(hide)
        generation++
        val old = bubble
        bubble = null
        label = null
        content = null
        deviceRows = null
        rowLabels.clear()
        frame = null
        frameKey = null
        windowLayout = null
        if (old != null) {
            runCatching { detach(old) }
            Log.i("AppToast", "오버레이 닫힘")
        }
    }

    final override fun updateAppearance() {
        check(Looper.myLooper() == ownerLooper)
        bubble?.let(::updateBackground)
    }

    private fun updateBackground(view: View) {
        val appearance = NoticePreferences.load(context)
        // 배경 알파를 따로 낮추면 전체 알파와 중복 적용된다.
        (view.background as GradientDrawable).setColor(appearance.backgroundColor)
        applyOpacity(view, appearance.opacity)
    }

    protected open fun applyOpacity(view: View, opacity: Float) { view.alpha = opacity }

    private fun createBubble(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        clipToPadding = false
        setPadding(dp(PADDING_DP), dp(PADDING_DP), dp(PADDING_DP), dp(PADDING_DP))
        background = GradientDrawable().apply {
            setColor(NoticePreferences.load(context).backgroundColor)
            cornerRadius = dp(24).toFloat()
        }
        elevation = dp(6).toFloat()
        isFocusable = false
        isClickable = false
        addView(ImageView(context).apply {
            setImageDrawable(context.applicationInfo.loadIcon(context.packageManager))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(ICON_SIZE_DP), dp(ICON_SIZE_DP)).apply {
            gravity = Gravity.CENTER_VERTICAL
            marginEnd = dp(ICON_GAP_DP)
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            content = this
            addView(createLabel().also { label = it },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                deviceRows = this
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun createLabel() = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        maxLines = 6
        ellipsize = TextUtils.TruncateAt.END
        textDirection = View.TEXT_DIRECTION_LOCALE
        isFocusable = false
    }

    private fun updateContent(message: CharSequence): Frame {
        val title = requireNotNull(label)
        val body = requireNotNull(content)
        val rows = requireNotNull(deviceRows)
        val devices = message as? DeviceNoticeMessage
        val key = FrameKey(Configuration(context.resources.configuration), context.resources.displayMetrics.widthPixels,
            context.resources.displayMetrics.heightPixels, AppCompatDelegate.getApplicationLocales().toLanguageTags(),
            devices?.rows?.size, devices?.title, (message as? SwitchNoticeMessage)?.operationId,
            message.takeUnless { it is DeviceNoticeMessage }?.toString())
        val previousKey = frameKey
        val sameSwitch = key.operationId != null && previousKey?.operationId == key.operationId &&
            previousKey.configuration == key.configuration && previousKey.screenWidth == key.screenWidth &&
            previousKey.screenHeight == key.screenHeight && previousKey.locales == key.locales
        val fixed = frame?.takeIf { frameKey == key } ?: measureFrame(title, message).let { measured ->
            // 늦게 확인된 기기가 추가되어도 같은 전환의 좌우 경계는 처음 위치를 유지한다.
            val next = if (sameSwitch) measured.copy(contentWidth = requireNotNull(frame).contentWidth) else measured
            next.also { frame = it; frameKey = key }
        }
        title.maxWidth = fixed.contentWidth
        body.layoutParams = body.layoutParams.apply {
            width = fixed.contentWidth
            height = maxOf(dp(ICON_SIZE_DP), fixed.contentHeight)
        }
        if (devices == null) {
            title.visibility = View.VISIBLE
            title.text = message
            title.maxLines = 6
            title.ellipsize = TextUtils.TruncateAt.END
            title.minHeight = fixed.headerHeight
            title.layoutParams = title.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            rows.visibility = View.GONE
            return fixed
        }

        title.visibility = if (devices.title == null) View.GONE else View.VISIBLE
        title.text = devices.title
        title.maxLines = Int.MAX_VALUE
        title.ellipsize = null
        title.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        title.minHeight = fixed.headerHeight
        title.layoutParams = title.layoutParams.apply { height = fixed.headerHeight }
        rows.visibility = View.VISIBLE
        val gap = dp(STATUS_GAP_DP)
        val statusWidth = fixed.statusWidth

        while (rowLabels.size > devices.rows.size) {
            rows.removeViewAt(rowLabels.lastIndex)
            rowLabels.removeAt(rowLabels.lastIndex)
        }
        while (rowLabels.size < devices.rows.size) {
            val name = createLabel().apply {
                maxLines = 1
                includeFontPadding = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
            val status = createLabel().apply {
                includeFontPadding = false
                ellipsize = null
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
            rows.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(status, LinearLayout.LayoutParams(statusWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = gap
                })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, fixed.rowHeight))
            rowLabels += name to status
        }
        devices.rows.forEachIndexed { index, row ->
            val (name, status) = rowLabels[index]
            val rowView = name.parent as LinearLayout
            rowView.orientation = if (fixed.stackedRows) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            rowView.gravity = if (fixed.stackedRows) Gravity.TOP else Gravity.CENTER_VERTICAL
            rowView.layoutParams = rowView.layoutParams.apply { height = fixed.rowHeight }
            name.layoutParams = if (fixed.stackedRows) {
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, fixed.nameHeight)
            } else {
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            status.maxLines = if (fixed.stackedRows) Int.MAX_VALUE else 1
            status.layoutParams = LinearLayout.LayoutParams(statusWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (fixed.stackedRows) topMargin = gap else marginStart = gap
            }
            name.text = row.name
            status.text = row.status
            status.setTextColor(when (row.indicator) {
                SwitchIndicator.Success -> 0xFF00E676.toInt()
                SwitchIndicator.Failure -> 0xFFE53935.toInt()
                else -> Color.WHITE
            })
        }
        return fixed
    }

    private data class Frame(
        val contentWidth: Int,
        val contentHeight: Int,
        val statusWidth: Int,
        val headerHeight: Int,
        val rowHeight: Int,
        val nameHeight: Int,
        val stackedRows: Boolean,
    )

    private data class FrameKey(val configuration: Configuration, val screenWidth: Int, val screenHeight: Int,
        val locales: String, val rowCount: Int?, val title: String?, val operationId: Long?,
        val plainMessage: String?)

    /** 전환은 1행·2행별 고정 높이, 일반·오류 안내는 실제 문구에 맞춘 크기를 사용한다. */
    private fun measureFrame(label: TextView, message: CharSequence): Frame {
        val locales = AppCompatDelegate.getApplicationLocales()
        val resources = if (locales.isEmpty) context.resources else {
            val config = Configuration(context.resources.configuration)
            config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
            context.createConfigurationContext(config).resources
        }
        val devices = message as? DeviceNoticeMessage
        val statusLabels = listOf(
            R.string.toast_switch_status_complete, R.string.toast_switch_status_failed,
            R.string.toast_switch_status_switching, R.string.toast_switch_status_unconfirmed,
            R.string.device_connected,
        ).map { resources.getString(it) }
        val outerMax = dp(if (devices != null) SWITCH_MAX_WIDTH_DP else MESSAGE_MAX_WIDTH_DP)
        val chrome = dp(PADDING_DP) * 2 + dp(ICON_SIZE_DP) + dp(ICON_GAP_DP)
        val maxWidth = (minOf(outerMax, context.resources.displayMetrics.widthPixels - dp(48)) - chrome)
            .coerceAtLeast(1)
        val gap = dp(STATUS_GAP_DP)
        val measuredStatusWidth = ceil(statusLabels.maxOf { label.paint.measureText(it) }.toDouble()).toInt()
        val stackedRows = devices != null && measuredStatusWidth + gap >= maxWidth
        val statusWidth = if (stackedRows) maxWidth else measuredStatusWidth
        val contentWidth = if (devices != null) {
            if (stackedRows) maxWidth else minOf(dp(DEVICE_NAME_WIDTH_DP) + gap + statusWidth, maxWidth)
        } else {
            ceil(maxOf(message.toString().lineSequence().maxOf { label.paint.measureText(it) }, dp(120).toFloat())
                .toDouble()).toInt().coerceAtMost(maxWidth)
        }
        val measuringLabel = createLabel().apply {
            ellipsize = null
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        fun textHeight(text: CharSequence, width: Int, includePad: Boolean, maxLines: Int = Int.MAX_VALUE): Int {
            measuringLabel.includeFontPadding = includePad
            measuringLabel.maxLines = maxLines
            measuringLabel.text = text
            measuringLabel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            return measuringLabel.measuredHeight
        }
        // TextView의 실제 대체 글꼴과 줄 간격도 포함해 큰 글꼴에서 상하가 잘리지 않게 한다.
        val nameHeight = maxOf(dp(DEVICE_ROW_MIN_DP), ceil(label.paint.fontSpacing.toDouble()).toInt(),
            statusLabels.maxOf { textHeight(it, measuredStatusWidth, false, 1) })
        val metrics = label.paint.fontMetricsInt
        val rowHeight = if (stackedRows) {
            nameHeight + gap + statusLabels.maxOf { textHeight(it, statusWidth, false) }
        } else nameHeight
        val headerHeight = maxOf(dp(ICON_SIZE_DP), if (devices?.title != null) {
            textHeight(devices.title, contentWidth, true)
        } else metrics.bottom - metrics.top)
        val contentHeight = if (devices != null) {
            (if (devices.title != null) headerHeight else 0) + rowHeight * devices.rows.size.coerceAtLeast(1)
        } else {
            maxOf(headerHeight, textHeight(message, contentWidth, true, 6))
        }
        return Frame(contentWidth, contentHeight, statusWidth, headerHeight, rowHeight, nameHeight, stackedRows)
    }

    protected fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val SWITCH_MAX_WIDTH_DP = 300
        private const val MESSAGE_MAX_WIDTH_DP = 320
        private const val PADDING_DP = 12
        private const val ICON_SIZE_DP = 24
        private const val ICON_GAP_DP = 10
        private const val STATUS_GAP_DP = 8
        private const val DEVICE_NAME_WIDTH_DP = 100
        private const val DEVICE_ROW_MIN_DP = 18
        const val DURATION_MS = 2_000L
        const val ONGOING_DURATION_MS = 30_000L
    }
}

/**
 * "다른 앱 위에 표시" 권한으로 다른 앱 위에 띄운다. 입력은 아래 앱으로 통과한다.
 * 권한이 없으면 즉시 false를 반환해 앱 화면 안 표시([ForegroundNoticeOverlay])로 넘어가고,
 * 나중에 권한을 허용하면 다음 안내부터 바로 이 창을 쓴다.
 */
class SystemNoticeOverlay(private val context: Context,
    onFailure: (NoticePresenter) -> Unit = {}) : NoticePresenter {
    private val thread = HandlerThread("EasySwitchNotice", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)
    private val caller = Handler(Looper.getMainLooper())
    private val queued = QueuedNoticePresenter(
        post = { action ->
            val queuedAt = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() else 0L
            handler.post {
                val startedAt = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() else 0L
                action()
                ConnectionLog.trace { "NOTICE_WORKER queueMs=${startedAt - queuedAt} " +
                    "workMs=${SystemClock.uptimeMillis() - startedAt}" }
            }
        },
        postToCaller = { caller.post(it) },
        create = { SystemNoticeWindow(context, thread.looper) },
        onFailure = { onFailure(this) },
        onError = { Log.w("AppToast", "안내 전용 스레드의 창 처리 실패", it) },
    )

    override fun show(message: CharSequence, ongoing: Boolean): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        return queued.show(message, ongoing)
    }

    override fun dismiss() = queued.dismiss()

    override fun updateAppearance() = queued.updateAppearance()

    fun close() = queued.close { thread.quitSafely() }
}

/** View와 ViewRoot의 소유 Looper를 함께 유지한다. Activity 안의 대체 안내는 기존 메인 스레드를 쓴다. */
private class SystemNoticeWindow(private val context: Context, looper: Looper) : NoticeOverlay(context, looper) {
    private val manager = requireNotNull(context.getSystemService(WindowManager::class.java))
    private var opacity = 1f

    override fun applyOpacity(view: View, opacity: Float) {
        this.opacity = opacity
        // 소프트웨어 ViewRoot에서도 전체에 반영되도록 창 Surface의 알파를 사용한다.
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        if (params.alpha == opacity) return
        params.alpha = opacity
        manager.updateViewLayout(view, params)
    }

    override fun available(): Boolean = Settings.canDrawOverlays(context)

    override fun attach(view: View) {
        val params = WindowManager.LayoutParams(
            view.measuredWidth,
            view.measuredHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(64)
            alpha = opacity
            title = "EasySwitchNotice"
        }
        manager.addView(view, params)
    }

    override fun relayout(view: View, width: Int, height: Int, bottomOffset: Int) {
        val params = view.layoutParams as WindowManager.LayoutParams
        if (params.width == width && params.height == height && params.y == bottomOffset) return
        params.width = width
        params.height = height
        params.y = bottomOffset
        manager.updateViewLayout(view, params)
    }

    override fun detach(view: View) { manager.removeViewImmediate(view) }
}

/** "다른 앱 위에 표시"를 허용하지 않은 경우에도 현재 앱 화면 안에서는 동일한 안내를 표시한다. */
class ForegroundNoticeOverlay(private val activity: Activity) : NoticeOverlay(activity) {
    private var popup: PopupWindow? = null

    override fun attach(view: View) {
        check(!activity.isFinishing && !activity.isDestroyed)
        val window = PopupWindow(view, view.measuredWidth, view.measuredHeight).apply {
            isFocusable = false
            isTouchable = false
            isOutsideTouchable = false
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        }
        popup = window
        window.showAtLocation(activity.window.decorView, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(64))
    }

    override fun relayout(view: View, width: Int, height: Int, bottomOffset: Int) {
        popup?.update(0, bottomOffset, width, height)
    }

    override fun detach(view: View) {
        popup?.dismiss()
        popup = null
    }
}
