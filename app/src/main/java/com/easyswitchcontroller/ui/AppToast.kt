package com.easyswitchcontroller.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.LocaleList
import android.os.SystemClock
import android.util.Log
import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.easyswitchcontroller.R
import com.easyswitchcontroller.device.SwitchReport
import com.easyswitchcontroller.device.SwitchIndicator
import com.easyswitchcontroller.device.SwitchTransactions
import com.easyswitchcontroller.data.ConnectionLog
import com.easyswitchcontroller.ui.notice.NoticeDispatcher
import com.easyswitchcontroller.ui.notice.NoticePresenter
import com.easyswitchcontroller.ui.notice.NoticeIndicatorSpan
import com.easyswitchcontroller.ui.notice.DeviceNoticeMessage
import com.easyswitchcontroller.ui.notice.SwitchNoticeMessage

/** 앱 전체의 안내를 같은 오버레이에 표시한다. 모든 표시 상태는 메인 스레드에서 관리한다. */
object AppToast {
    private val main = Handler(Looper.getMainLooper())
    private val dispatcher = NoticeDispatcher(SystemClock::uptimeMillis)

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
    }

    fun attachSystem(presenter: NoticePresenter) = onMain { dispatcher.attachSystem(presenter) }
    fun detachSystem(presenter: NoticePresenter) = onMain { dispatcher.detachSystem(presenter) }
    fun attachForeground(presenter: NoticePresenter) = onMain { dispatcher.attachForeground(presenter) }
    fun detachForeground(presenter: NoticePresenter) = onMain { dispatcher.detachForeground(presenter) }
    internal fun presentationFailed(presenter: NoticePresenter) = onMain { dispatcher.presentationFailed(presenter) }

    private fun messageContext(context: Context): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        return context.createConfigurationContext(configuration)
    }

    fun switchMessage(context: Context, report: SwitchReport): CharSequence {
        val resources = messageContext(context)
        if (report.devices.isEmpty()) return resources.getString(R.string.toast_switch_no_devices, report.channel)
        val complete = resources.getString(R.string.toast_switch_status_complete)
        val failed = resources.getString(R.string.toast_switch_status_failed)
        val switching = resources.getString(R.string.toast_switch_status_switching)
        val unconfirmed = resources.getString(R.string.toast_switch_status_unconfirmed)
        return SwitchNoticeMessage(
            title = resources.getString(R.string.toast_switch_title, report.channel),
            rows = report.devices.map { device ->
                val status = when (device.indicator) {
                    SwitchIndicator.Success -> complete
                    SwitchIndicator.Failure -> failed
                    SwitchIndicator.Pending -> if (report.inProgress) switching else unconfirmed
                    SwitchIndicator.NotSelected -> device.indicator.symbol
                }
                DeviceNoticeMessage.Row(device.name, status, device.indicator)
            },
            operationId = report.operationId,
        )
    }

    private fun styleIndicators(message: CharSequence): CharSequence = SpannableString(message).apply {
        message.forEachIndexed { index, symbol ->
            val indicator = when (symbol) {
                '○' -> NoticeIndicatorSpan(successful = true)
                '×' -> NoticeIndicatorSpan(successful = false)
                else -> null
            }
            if (indicator != null) setSpan(
                indicator, index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    fun showSwitchResult(context: Context, report: SwitchReport) {
        onMain {
            if (report.operationId?.let { !SwitchTransactions.isCurrent(it) } == true) return@onMain
            show(context, switchMessage(context, report), ongoing = report.inProgress)
        }
    }

    fun showDeviceConnected(context: Context, names: List<String>) {
        val resources = messageContext(context)
        show(context, DeviceNoticeMessage(null, names.map {
            DeviceNoticeMessage.Row(it, resources.getString(R.string.device_connected), SwitchIndicator.NotSelected)
        }))
    }

    fun show(context: Context, @StringRes messageRes: Int) {
        show(context, messageContext(context).getString(messageRes))
    }

    /** 서비스의 작업 스레드에서도 호출할 수 있으며, 새 안내가 이전 안내를 대체한다. */
    fun show(context: Context, message: CharSequence) = show(context, message, ongoing = false)

    private fun show(context: Context, message: CharSequence, ongoing: Boolean) {
        val snapshot = if (message is DeviceNoticeMessage) message else SpannedString.valueOf(styleIndicators(message))
        onMain {
            Log.i("AppToast", "오버레이 표시 요청: $snapshot")
            if (!dispatcher.show(snapshot, ongoing)) {
                ConnectionLog.append("안내 표시 대기: 다른 앱 위에 표시를 허용하거나 앱 화면을 열어야 해요.", ConnectionLog.Level.Dim)
            }
        }
    }
}
