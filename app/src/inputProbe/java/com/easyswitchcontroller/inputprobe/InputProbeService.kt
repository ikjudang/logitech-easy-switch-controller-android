package com.easyswitchcontroller.inputprobe

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.InputDevice
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.easyswitchcontroller.R
import com.easyswitchcontroller.floating.FloatingOverlayController

/** 본 앱과 같은 렌더러로 입력·픽셀만 검증한다. 블루투스 전환은 실행하지 않는다. */
class InputProbeService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var overlay: FloatingOverlayController? = null
    private val timeout = Runnable { ProbeLog.append("AUTO_STOP after=10min"); stopSelf() }

    override fun onCreate() {
        super.onCreate()
        ProbeLog.attach(this)
        ProbeLog.append("SERVICE_START version=${packageManager.getPackageInfo(packageName, 0).versionName}")
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "마우스 입력 검증", NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, Intent(this, InputProbeActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_follow).setContentTitle("EasySwitch 입력 검증")
            .setContentText("앱이 마우스 입력 검증 결과를 기록하고 있습니다.")
            .setContentIntent(open).setOngoing(true).setSilent(true).build()
        try {
            ServiceCompat.startForeground(this, 81, notification,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } catch (error: Exception) {
            ProbeLog.append("FOREGROUND_FAILED ${error.javaClass.simpleName}: ${error.message}")
            stopSelf(); return
        }
        auditPermissions()
        overlay = FloatingOverlayController(this,
            onChannelSelected = { ProbeLog.append("CHANNEL_PROBE_ONLY channel=$it bluetooth_control=false") },
            onAppSelected = {
                startActivity(Intent(this, InputProbeActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                ProbeLog.append("APP_PROBE_OPENED bluetooth_control=false")
            },
            log = ProbeLog::append, testMode = true).also { it.start() }
        ProbePixelLog.sceneProvider = { overlay?.snapshot() ?: error("Overlay stopped") }
        main.postDelayed(timeout, 10 * 60_000L)
    }

    private fun auditPermissions() {
        ProbeLog.append("OVERLAY_GRANTED ${Settings.canDrawOverlays(this)}")
        for (permission in listOf("MONITOR_INPUT", "ACCESSIBILITY_MOTION_EVENT_OBSERVING", "INJECT_EVENTS", "DUMP")) {
            val full = "android.permission.$permission"
            val granted = checkSelfPermission(full) == PackageManager.PERMISSION_GRANTED
            val protection = runCatching { packageManager.getPermissionInfo(full, 0).protectionLevel }.getOrNull()
            ProbeLog.append("PERMISSION $permission granted=$granted protection=$protection")
        }
        InputDevice.getDeviceIds().asSequence().mapNotNull { InputDevice.getDevice(it) }.filter { it.supportsSource(InputDevice.SOURCE_MOUSE) }
            .forEach { ProbeLog.append("MOUSE id=${it.id} name=${it.name} sources=${it.sources} vendor=${it.vendorId} product=${it.productId}") }
        ProbeLog.append("ROUTE public-overlay; no-accessibility; no-injection; no-shizuku; outside-events-are-not-a-complete-click-stream")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = overlay ?: return START_NOT_STICKY
        if (intent?.action == HIDE) controller.hide("user")
        else {
            controller.refreshAccess()
            ProbeSession.resetAppInput()
            main.removeCallbacks(timeout)
            main.postDelayed(timeout, 10 * 60_000L)
        }
        return START_NOT_STICKY
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.hide("configuration_changed")
    }
    override fun onDestroy() {
        main.removeCallbacks(timeout)
        ProbePixelLog.sceneProvider = null
        stopService(Intent(this, ProbePixelCaptureService::class.java))
        overlay?.stop(); overlay = null
        ProbeLog.append("SERVICE_STOP")
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val HIDE = "com.easyswitchcontroller.inputprobe.HIDE"
        private const val CHANNEL = "input-probe"
    }
}
