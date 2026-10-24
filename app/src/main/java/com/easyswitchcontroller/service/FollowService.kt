package com.easyswitchcontroller.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.easyswitchcontroller.MainActivity
import com.easyswitchcontroller.R
import com.easyswitchcontroller.data.ConnectionLog
import com.easyswitchcontroller.device.SwitchReport
import com.easyswitchcontroller.floating.FloatingButtonHost
import com.easyswitchcontroller.floating.FloatingPreferences
import com.easyswitchcontroller.floating.FloatingEntryState
import com.easyswitchcontroller.floating.FloatingSummonActivity
import com.easyswitchcontroller.floating.FloatingTileService
import com.easyswitchcontroller.ui.AppToast
import com.easyswitchcontroller.ui.notice.SystemNoticeOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Easy-Switch 따라가기(핵심 기능, [KeyboardFollowManager])를 상시 구동하는 포그라운드 서비스.
 *
 * 0.3.0까지는 접근성 서비스가 이 역할을 했지만, 스토어 밖에서 받은 앱이 접근성을 선언하면
 * Play 프로텍트가 첫 설치를 "기기 보호를 위해 앱 차단됨"으로 막아 포그라운드 서비스로 옮겼다.
 * 앱 화면 진입·재부팅·앱 업데이트 때 [start]가 다시 불리며, 이미 돌고 있으면 아무 일도 없다.
 */
class FollowService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var followManager: KeyboardFollowManager? = null
    private var notice: SystemNoticeOverlay? = null
    private var floating: FloatingButtonHost? = null
    private var notificationKey: Pair<FloatingEntryState, Boolean>? = null

    override fun onCreate() {
        super.onCreate()
        // 서비스 시작을 로그에 남긴다 — 이 마커가 로그 중간에 다시 찍혀 있으면 서비스가 죽었다
        // 살아난 것이고, 안 찍혀 있는데 재연결만 반복됐으면 서비스는 살아 있던 것이다.
        ConnectionLog.attach(this, getString(R.string.log_source_service))
        val promoted = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        }
        promoted.exceptionOrNull()?.let { error ->
            // 백그라운드 시작 제한·권한 회수 등. 로그로만 남긴다(진단 문구는 화면에 띄우지 않는다).
            ConnectionLog.append(getString(R.string.log_service_start_failed, error.javaClass.simpleName))
            stopSelf()
            return
        }
        notice = SystemNoticeOverlay(this, AppToast::presentationFailed).also { AppToast.attachSystem(it) }
        // Easy-Switch 키 따라가기(0x1814 이벤트 수신)를 켠다.
        followManager = KeyboardFollowManager(this, scope, ::toast, onKeyboardSwitch = { channel ->
            scope.launch(Dispatchers.Main.immediate) {
                runCatching { floating?.keyboardSwitched(channel) }.onFailure {
                    if (it is CancellationException) throw it
                    ConnectionLog.append("FLOATING KEYBOARD_FEEDBACK_FAILED ${it.javaClass.simpleName}", ConnectionLog.Level.Dim)
                }
            }
        }).also { it.start() }
        floating = FloatingButtonHost(this, scope) { floatingVisibleState.value = it }
        activeInstance = this
        scope.launch(Dispatchers.Main.immediate) {
            FloatingPreferences.revision.collect {
                floating?.sync()
                updateFloatingNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESTORE_FLOATING_NOTIFICATION) {
            // 삭제 후 도착한 요청도 현재 토글을 다시 확인한다. 홈 버튼의 표시 상태는 바꾸지 않는다.
            if (FloatingPreferences.load(this).enabled) updateFloatingNotification(force = true)
            return START_STICKY
        }
        if (intent?.action == ACTION_SUMMON_FLOATING) {
            floating?.summon(intent.getLongExtra(EXTRA_SUMMON_REQUESTED_AT, SystemClock.uptimeMillis()))
        } else floating?.sync()
        updateFloatingNotification()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ConnectionLog.append("FLOATING CONFIG_CHANGED keyboard=${newConfig.keyboard} keyboardHidden=${newConfig.keyboardHidden} " +
            "hardKeyboardHidden=${newConfig.hardKeyboardHidden} navigation=${newConfig.navigation} " +
            "navigationHidden=${newConfig.navigationHidden} orientation=${newConfig.orientation} widthDp=${newConfig.screenWidthDp} " +
            "heightDp=${newConfig.screenHeightDp} densityDpi=${newConfig.densityDpi}", ConnectionLog.Level.Dim)
        floating?.configurationChanged(newConfig)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 앱 화면과 같은 오버레이 경로로 전환 안내를 표시한다. */
    private fun toast(report: SwitchReport) {
        AppToast.showSwitchResult(this, report)
    }

    private fun entryState(): FloatingEntryState {
        val settings = FloatingPreferences.load(this)
        return FloatingEntryState.resolve(settings.enabled, Settings.canDrawOverlays(this), settings.sizePercent, settings.transparency)
    }

    private fun updateFloatingNotification(force: Boolean = false) {
        val manager = getSystemService(NotificationManager::class.java)
        val key = entryState() to manager.areNotificationsEnabled()
        if (!force && notificationKey == key) return
        notificationKey = key
        FloatingTileService.refresh(this)
        if (key.second) runCatching {
            manager.notify(NOTIFICATION_ID, buildNotification())
            if (force) ConnectionLog.append("FLOATING NOTIFICATION_REPOSTED_AFTER_DISMISS", ConnectionLog.Level.Dim)
        }
            .onFailure { ConnectionLog.append("FLOATING NOTIFICATION_UPDATE_FAILED ${it.javaClass.simpleName}", ConnectionLog.Level.Dim) }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val state = entryState()
        val floatingEnabled = state != FloatingEntryState.OFF
        val channelId = if (floatingEnabled) FLOATING_CHANNEL_ID else CHANNEL_ID
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                getString(if (floatingEnabled) R.string.floating_notification_channel else R.string.follow_notification_channel),
                if (floatingEnabled) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_MIN,
            ).apply {
                if (!floatingEnabled) description = getString(R.string.follow_notification_channel_desc)
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val show = FloatingSummonActivity.pendingIntent(this)
        val ready = state == FloatingEntryState.READY
        val action = if (ready) show else open
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(if (floatingEnabled) R.drawable.ic_stat_floating else R.drawable.ic_stat_follow)
            .setContentTitle(getString(if (floatingEnabled) R.string.floating_title else R.string.follow_notification_title))
            .setContentText(getString(when (state) {
                FloatingEntryState.OFF -> R.string.follow_notification_text
                FloatingEntryState.ZERO_SIZE -> R.string.floating_notification_zero_size
                FloatingEntryState.TRANSPARENT -> R.string.floating_notification_transparent
                FloatingEntryState.NEEDS_PERMISSION -> R.string.floating_permission
                FloatingEntryState.READY -> R.string.floating_notification_text
            }))
            .setContentIntent(if (floatingEnabled) action else open)
            .apply {
                if (floatingEnabled) {
                    addAction(R.drawable.ic_floating_tile,
                        getString(if (ready) R.string.floating_show else R.string.floating_open_app), action)
                    setDeleteIntent(PendingIntent.getForegroundService(this@FollowService, 1,
                        Intent(this@FollowService, FollowService::class.java).setAction(ACTION_RESTORE_FLOATING_NOTIFICATION),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
            }
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
        // 정상 종료는 여기에 남는다. 이 줄 없이 시작 마커만 다시 찍혔다면 시스템이 죽인 것이다.
        if (followManager != null) {
            ConnectionLog.append(getString(R.string.log_service_stopped), ConnectionLog.Level.Dim)
        }
        followManager?.stop()
        followManager = null
        notice?.let { AppToast.detachSystem(it); it.close() }
        notice = null
        floating?.close()
        floating = null
        scope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "follow"
        private const val FLOATING_CHANNEL_ID = "floating_button"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_SUMMON_FLOATING = "com.easyswitchcontroller.SHOW_FLOATING_BUTTON"
        private const val ACTION_RESTORE_FLOATING_NOTIFICATION = "com.easyswitchcontroller.RESTORE_FLOATING_NOTIFICATION"
        private const val EXTRA_SUMMON_REQUESTED_AT = "summon_requested_at"
        // 서비스와 알림 진입 Activity는 같은 프로세스에서 메인 스레드로 호출한다.
        private var activeInstance: FollowService? = null
        private val floatingVisibleState = MutableStateFlow(false)
        internal val floatingVisible = floatingVisibleState.asStateFlow()

        /**
         * 따라가기 서비스를 시작한다. 블루투스 권한이 없으면 시작하지 않는다 — connectedDevice
         * 포그라운드 서비스는 Android 14+에서 BLUETOOTH_CONNECT가 있어야 승격되고, 권한 없이는
         * 키보드에 붙을 수도 없다. 권한을 받으면 앱 화면이 다시 부른다.
         */
        fun start(context: Context) = start(context, null)

        fun summonFloating(context: Context, requestedAt: Long = SystemClock.uptimeMillis()) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED) return
            val host = if (Looper.myLooper() == Looper.getMainLooper()) activeInstance?.floating else null
            if (host != null) {
                // 실행 중인 서비스는 시스템의 서비스 시작 큐를 다시 거치지 않는다.
                host.summon(requestedAt)
            } else start(context, ACTION_SUMMON_FLOATING, requestedAt)
        }

        private fun start(context: Context, action: String?, requestedAt: Long? = null) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
            runCatching {
                val intent = Intent(context, FollowService::class.java).setAction(action)
                requestedAt?.let { intent.putExtra(EXTRA_SUMMON_REQUESTED_AT, it) }
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { error ->
                ConnectionLog.append(context.getString(R.string.log_service_start_failed, error.javaClass.simpleName))
            }
        }
    }
}
