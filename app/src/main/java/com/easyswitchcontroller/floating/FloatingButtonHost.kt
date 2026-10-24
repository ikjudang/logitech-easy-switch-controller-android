package com.easyswitchcontroller.floating

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import com.easyswitchcontroller.R
import com.easyswitchcontroller.MainActivity
import com.easyswitchcontroller.data.ConnectionLog
import com.easyswitchcontroller.ui.AppToast
import com.easyswitchcontroller.ui.channel.ChannelRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 기존 connectedDevice 서비스에 메뉴를 붙인다. 별도의 접근성·특수 사용 서비스는 추가하지 않는다. */
internal class FloatingButtonHost(private val context: Context, private val scope: CoroutineScope,
    private val onVisibilityChanged: (Boolean) -> Unit = {}) {
    private val configurationChanges = FloatingConfigurationChanges(context.resources.configuration)
    private var overlay: FloatingOverlayController? = null
    private var closing = false

    fun sync(showHome: Boolean = true) {
        val settings = FloatingPreferences.load(context)
        if (!Settings.canDrawOverlays(context)) {
            close()
            return
        }
        if (!settings.enabled) {
            val existing = overlay
            if (existing != null && !closing) {
                closing = true
                existing.dismiss {
                    // 이전 종료 콜백이 새로 켠 버튼을 정리하지 않게 한다.
                    if (overlay === existing) {
                        overlay = null
                        closing = false
                        existing.stop()
                    }
                }
            }
            return
        }
        if (closing) close()
        // 0%는 그림과 입력 창 모두 없앤다. 설정을 올리면 저장 위치에서 다시 표시한다.
        if (settings.sizePercent == 0) {
            val previous = overlay
            previous?.setSizePercent(0) {
                if (overlay === previous && FloatingPreferences.load(context).sizePercent == 0) close()
            }
            return
        }
        val existing = overlay
        if (existing != null) {
            existing.setAnimationSpeedPercent(settings.animationSpeedPercent)
            existing.setBounceEnabled(settings.bounceEnabled)
            existing.setAlwaysShowMenu(settings.alwaysShowMenu)
            existing.setOpacity(settings.opacity)
            existing.setSizePercent(settings.sizePercent)
            existing.refreshAccess()
            return
        }
        configurationChanges.reset(context.resources.configuration)
        overlay = FloatingOverlayController(context,
            onChannelSelected = { channel -> scope.launch(Dispatchers.Main.immediate) {
                runCatching {
                    val channels = ChannelRuntime.refresh(context)
                    if (channels.uiState.value.devices.isEmpty()) AppToast.show(context, R.string.floating_no_devices)
                    else channels.onChannelClicked(channel)
                }.onFailure {
                    if (it is CancellationException) throw it
                    ConnectionLog.append("플로팅 전환 실패: ${it.javaClass.simpleName}: ${it.message}")
                }
            } },
            onAppSelected = {
                runCatching {
                    context.startActivity(Intent(context, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    ConnectionLog.append("FLOATING APP_OPEN_REQUESTED")
                }.onFailure {
                    ConnectionLog.append("플로팅 앱 열기 실패: ${it.javaClass.simpleName}: ${it.message}")
                }
            },
            log = { ConnectionLog.append("FLOATING $it", ConnectionLog.Level.Dim) },
            onHomeMoved = { point ->
                val screen = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
                FloatingPreferences.savePosition(context, point.x / screen.width(), point.y / screen.height())
            },
            onHomeVisibilityChanged = onVisibilityChanged,
        ).also {
            it.setAnimationSpeedPercent(settings.animationSpeedPercent)
            it.setBounceEnabled(settings.bounceEnabled)
            it.setAlwaysShowMenu(settings.alwaysShowMenu)
            it.setOpacity(settings.opacity)
            it.setSizePercent(settings.sizePercent)
            it.start()
            if (showHome) it.showHome(settings.x, settings.y)
        }
    }

    fun configurationChanged(newConfig: Configuration) {
        val changes = configurationChanges.update(newConfig)
        val rebuild = FloatingConfigurationChanges.requiresRebuild(changes)
        ConnectionLog.append("FLOATING CONFIG_DECISION diff=0x${Integer.toHexString(changes)} " +
            "remaining=0x${Integer.toHexString(FloatingConfigurationChanges.remaining(changes))} " +
            "decision=${if (rebuild) "rebuild" else "preserve"} keyboard=${newConfig.keyboard} " +
            "keyboardHidden=${newConfig.keyboardHidden} hardKeyboardHidden=${newConfig.hardKeyboardHidden} " +
            "navigation=${newConfig.navigation} navigationHidden=${newConfig.navigationHidden}", ConnectionLog.Level.Dim)
        if (!rebuild) return
        if (closing) { close(); return }
        val settings = FloatingPreferences.load(context)
        val wasShowing = overlay?.isShowing == true
        overlay?.hide("configuration_changed")
        if (wasShowing) overlay?.showHome(settings.x, settings.y)
    }

    fun summon(requestedAt: Long = SystemClock.uptimeMillis()) {
        val settings = FloatingPreferences.load(context)
        if (!settings.enabled || !Settings.canDrawOverlays(context) || settings.sizePercent <= 0 || settings.opacity <= 0f) return
        // 처음 만드는 경우에도 홈을 한 번만 붙인다.
        sync(showHome = false)
        overlay?.showHome(settings.x, settings.y, requestedAt)
    }

    /** 키보드 출발 신호는 이미 펼쳐진 메뉴의 시각 효과만 실행한다. */
    fun keyboardSwitched(channel: Int) {
        if (!closing) overlay?.keyboardSwitched(channel)
    }

    fun close() {
        val previous = overlay
        overlay = null
        closing = false
        previous?.stop()
        onVisibilityChanged(false)
    }
}
