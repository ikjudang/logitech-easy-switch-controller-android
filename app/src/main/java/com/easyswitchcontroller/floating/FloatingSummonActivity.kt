package com.easyswitchcontroller.floating

import android.app.Activity
import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import com.easyswitchcontroller.MainActivity
import com.easyswitchcontroller.service.FollowService
import androidx.core.content.ContextCompat

/** 알림/타일의 사용자 클릭으로 패널을 닫고 소환한다. 별도 화면이나 입력 주입은 없다. */
class FloatingSummonActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val requestedAt = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        val settings = FloatingPreferences.load(this)
        val state = FloatingEntryState.resolve(settings.enabled, Settings.canDrawOverlays(this),
            settings.sizePercent, settings.transparency)
        val canConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (state == FloatingEntryState.READY && canConnect) FollowService.summonFloating(this, requestedAt)
        else startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    companion object {
        internal fun intent(context: Context) = Intent(context, FloatingSummonActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        internal fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(context, 41,
            intent(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
