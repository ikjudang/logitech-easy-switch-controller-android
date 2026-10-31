package com.easyswitchcontroller.ui.notice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal object NoticePreferences {
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()
    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences("toast_appearance", Context.MODE_PRIVATE)

    fun load(context: Context): NoticeAppearance = prefs(context).let {
        NoticeAppearance(it.getBoolean("enabled", true), it.getInt("transparency", 0).coerceIn(0, 100))
    }

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val preferences = prefs(context)
        if (preferences.getBoolean("enabled", true) == enabled) return false
        preferences.edit().putBoolean("enabled", enabled).apply()
        changes.update { it + 1 }
        return true
    }

    fun setTransparency(context: Context, transparency: Int): Boolean {
        val preferences = prefs(context)
        val bounded = transparency.coerceIn(0, 100)
        if (preferences.getInt("transparency", 0) == bounded) return false
        preferences.edit().putInt("transparency", bounded).apply()
        changes.update { it + 1 }
        return true
    }
}
