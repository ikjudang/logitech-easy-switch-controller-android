package com.easyswitchcontroller.floating

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal object FloatingPreferences {
    data class State(val enabled: Boolean, val transparency: Int, val x: Float, val y: Float,
        val sizePercent: Int = FloatingSize.DEFAULT_PERCENT, val bounceEnabled: Boolean = true,
        val animationSpeedPercent: Int = FloatingAnimationSpeed.DEFAULT_PERCENT, val alwaysShowMenu: Boolean = false) {
        val opacity: Float get() = 1f - transparency / 100f
    }
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("floating_button", Context.MODE_PRIVATE)
    fun load(context: Context): State = prefs(context).let {
        State(it.getBoolean("enabled", false), it.getInt("transparency", 0).coerceIn(0, 100),
            it.getFloat("x", .88f).coerceIn(0f, 1f), it.getFloat("y", .58f).coerceIn(0f, 1f),
            FloatingSize.percent(it.getInt("size_percent", FloatingSize.DEFAULT_PERCENT)),
            it.getBoolean("bounce_enabled", true),
            FloatingAnimationSpeed.percent(it.getInt("animation_speed_percent", FloatingAnimationSpeed.DEFAULT_PERCENT)),
            it.getBoolean("always_show_menu", false))
    }
    fun setEnabled(context: Context, value: Boolean) {
        val preferences = prefs(context)
        preferences.edit().putBoolean("enabled", value).apply {
            if (value && !preferences.getBoolean("enabled", false)) putBoolean("notification_permission_requested", false)
        }.apply()
        changes.update { it + 1 }
    }
    fun setTransparency(context: Context, value: Int) {
        prefs(context).edit().putInt("transparency", value.coerceIn(0, 100)).apply()
        changes.update { it + 1 }
    }
    fun setSizePercent(context: Context, value: Int) {
        prefs(context).edit().putInt("size_percent", FloatingSize.percent(value)).apply()
        changes.update { it + 1 }
    }
    fun setBounceEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("bounce_enabled", value).apply()
        changes.update { it + 1 }
    }
    fun setAlwaysShowMenu(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean("always_show_menu", value) }
        changes.update { it + 1 }
    }
    fun setAnimationSpeedPercent(context: Context, value: Int) {
        prefs(context).edit { putInt("animation_speed_percent", FloatingAnimationSpeed.percent(value)) }
        changes.update { it + 1 }
    }
    fun notificationPermissionRequested(context: Context) = prefs(context).getBoolean("notification_permission_requested", false)
    fun markNotificationPermissionRequested(context: Context) {
        prefs(context).edit().putBoolean("notification_permission_requested", true).apply()
    }
    fun savePosition(context: Context, x: Float, y: Float) {
        prefs(context).edit().putFloat("x", x.coerceIn(0f, 1f)).putFloat("y", y.coerceIn(0f, 1f)).apply()
    }
}
