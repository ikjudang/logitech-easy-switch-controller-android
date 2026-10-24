package com.easyswitchcontroller.diagnostics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.easyswitchcontroller.R
import com.easyswitchcontroller.ui.AppToast

/** debug 빌드와 ADB/system 호출에만 허용되는 오버레이 확인 도구. 전환 명령은 보내지 않는다. */
class ToastProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppToast.show(context, R.string.toast_display_test)
    }
}
