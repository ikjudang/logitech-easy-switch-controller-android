package com.easyswitchcontroller.floating

import android.content.pm.ActivityInfo
import android.content.res.Configuration

/** 입력 기기의 구성만 바뀌면 진행 중인 창과 애니메이션을 유지한다. */
internal class FloatingConfigurationChanges(initial: Configuration) {
    private var previous = Configuration(initial)

    fun reset(current: Configuration) {
        previous = Configuration(current)
    }

    fun update(current: Configuration): Int {
        val changes = previous.diff(current)
        reset(current)
        return changes
    }

    companion object {
        private const val INPUT_CHANGES = ActivityInfo.CONFIG_KEYBOARD or
            ActivityInfo.CONFIG_KEYBOARD_HIDDEN or ActivityInfo.CONFIG_NAVIGATION

        fun remaining(changes: Int): Int = changes and INPUT_CHANGES.inv()
        fun requiresRebuild(changes: Int): Boolean = remaining(changes) != 0
    }
}
