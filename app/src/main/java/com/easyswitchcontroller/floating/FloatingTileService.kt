package com.easyswitchcontroller.floating

import android.content.ComponentName
import android.content.Context
import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.easyswitchcontroller.R
import com.easyswitchcontroller.data.ConnectionLog

/** 알림 권한과 무관한 외부 소환 경로. 켜기/끄기 대신 저장 위치의 버튼을 다시 표시한다. */
class FloatingTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val settings = FloatingPreferences.load(this)
        val state = FloatingEntryState.resolve(settings.enabled, Settings.canDrawOverlays(this),
            settings.sizePercent, settings.transparency)
        qsTile?.apply {
            label = getString(R.string.floating_title)
            subtitle = getString(if (state == FloatingEntryState.READY) R.string.floating_show else R.string.floating_open_app)
            this.state = if (state == FloatingEntryState.READY) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            contentDescription = getString(if (state == FloatingEntryState.READY) R.string.floating_show else R.string.floating_open_app)
            updateTile()
        }
    }

    // PendingIntent 오버로드는 API 34부터 있으므로 31~33에서만 Intent 경로를 쓴다.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        fun summon() {
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(FloatingSummonActivity.pendingIntent(this))
            else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(FloatingSummonActivity.intent(this))
            }
        }
        if (isLocked) unlockAndRun { summon() } else summon()
    }

    companion object {
        internal fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, FloatingTileService::class.java)) }
                .onFailure { ConnectionLog.append("FLOATING TILE_REFRESH_FAILED ${it.javaClass.simpleName}", ConnectionLog.Level.Dim) }
        }
    }
}
