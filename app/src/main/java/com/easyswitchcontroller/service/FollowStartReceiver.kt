package com.easyswitchcontroller.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 재부팅·앱 업데이트 뒤 앱 화면을 열지 않아도 따라가기가 다시 켜지게 한다.
 * 접근성 서비스는 시스템이 알아서 다시 붙였지만, 포그라운드 서비스는 누군가 시작해야 한다.
 * 두 브로드캐스트 모두 백그라운드에서 포그라운드 서비스를 시작해도 되는 예외에 속한다.
 */
class FollowStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> FollowService.start(context)
        }
    }
}
