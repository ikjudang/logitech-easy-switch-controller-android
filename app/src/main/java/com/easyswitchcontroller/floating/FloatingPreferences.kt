package com.easyswitchcontroller.floating

import android.content.Context
import androidx.core.content.edit
import com.easyswitchcontroller.BuildConfig
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
        State(it.getBoolean("enabled", false) && BuildConfig.FLOATING_IMPL, it.getInt("transparency", 0).coerceIn(0, 100),
            it.getFloat("x", .88f).coerceIn(0f, 1f), it.getFloat("y", .58f).coerceIn(0f, 1f),
            FloatingSize.percent(it.getInt("size_percent", FloatingSize.DEFAULT_PERCENT)),
            it.getBoolean("bounce_enabled", true),
            FloatingAnimationSpeed.percent(it.getInt("animation_speed_percent", FloatingAnimationSpeed.DEFAULT_PERCENT)),
            it.getBoolean("always_show_menu", false))
    }
    fun setEnabled(context: Context, value: Boolean) {
        val preferences = prefs(context)
        if (preferences.getBoolean("enabled", false) == value) return
        preferences.edit().putBoolean("enabled", value).apply {
            if (value && !preferences.getBoolean("enabled", false)) putBoolean("notification_permission_requested", false)
        }.apply()
        changes.update { it + 1 }
    }
    fun setTransparency(context: Context, value: Int) {
        val preferences = prefs(context)
        val bounded = value.coerceIn(0, 100)
        if (preferences.getInt("transparency", 0) == bounded) return
        preferences.edit().putInt("transparency", bounded).apply()
        changes.update { it + 1 }
    }
    fun setSizePercent(context: Context, value: Int) {
        val preferences = prefs(context)
        val bounded = FloatingSize.percent(value)
        if (preferences.getInt("size_percent", FloatingSize.DEFAULT_PERCENT) == bounded) return
        preferences.edit().putInt("size_percent", bounded).apply()
        changes.update { it + 1 }
    }
    fun setBounceEnabled(context: Context, value: Boolean) {
        val preferences = prefs(context)
        if (preferences.getBoolean("bounce_enabled", true) == value) return
        preferences.edit().putBoolean("bounce_enabled", value).apply()
        changes.update { it + 1 }
    }
    fun setAlwaysShowMenu(context: Context, value: Boolean) {
        val preferences = prefs(context)
        if (preferences.getBoolean("always_show_menu", false) == value) return
        preferences.edit { putBoolean("always_show_menu", value) }
        changes.update { it + 1 }
    }
    fun setAnimationSpeedPercent(context: Context, value: Int) {
        val preferences = prefs(context)
        val bounded = FloatingAnimationSpeed.percent(value)
        if (preferences.getInt("animation_speed_percent", FloatingAnimationSpeed.DEFAULT_PERCENT) == bounded) return
        preferences.edit { putInt("animation_speed_percent", bounded) }
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
