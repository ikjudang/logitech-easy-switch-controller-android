package com.easyswitchcontroller.floating

import kotlin.math.hypot

/** 입력을 소비하지 않는다. 표시 상태와 무관하게 같은 마우스의 좌우 동시 클릭을 판정한다. */
class MouseChordDetector(
    private val simultaneousWindowMs: Long = 150,
    private val dragSlop: Float = 12f,
    private val maxHoldMs: Long = 5_000,
) {
    data class Sample(
        val deviceId: Int,
        val displayId: Int,
        val eventTimeMs: Long,
        val buttons: Int,
        val x: Float,
        val y: Float,
        val cancelled: Boolean = false,
    )

    data class Summon(val deviceId: Int, val displayId: Int, val x: Float, val y: Float)

    private data class Press(
        val displayId: Int,
        val startedAt: Long,
        var lastAt: Long,
        val firstX: Float,
        val firstY: Float,
        var lastButtons: Int,
        var armed: Boolean = false,
        var rejected: Boolean = false,
    )

    private val presses = mutableMapOf<Int, Press>()
    var enabled: Boolean = true
        set(value) { field = value; if (!value) presses.clear() }

    fun reset() = presses.clear()
    fun removeDevice(deviceId: Int) { presses.remove(deviceId) }

    fun accept(sample: Sample): Summon? {
        if (!enabled) return null
        if (sample.cancelled || !sample.x.isFinite() || !sample.y.isFinite()) {
            presses.remove(sample.deviceId)
            return null
        }
        val previous = presses[sample.deviceId]
        if (previous != null && (sample.displayId != previous.displayId || sample.eventTimeMs < previous.lastAt)) {
            presses.remove(sample.deviceId)
            return null
        }
        if (sample.buttons == 0) {
            val press = presses.remove(sample.deviceId) ?: return null
            return if (press.armed && !press.rejected && sample.eventTimeMs - press.startedAt <= maxHoldMs) {
                Summon(sample.deviceId, sample.displayId, sample.x, sample.y)
            } else null
        }
        val press = previous ?: Press(
            sample.displayId, sample.eventTimeMs, sample.eventTimeMs,
            sample.x, sample.y, lastButtons = 0,
        ).also { presses[sample.deviceId] = it }
        val both = sample.buttons and BOTH == BOTH
        if (sample.buttons and BOTH.inv() != 0 || sample.eventTimeMs - press.startedAt > maxHoldMs) press.rejected = true
        if (!press.armed && hypot(sample.x - press.firstX, sample.y - press.firstY) > dragSlop) press.rejected = true
        // 한쪽을 뗀 뒤 다른 쪽을 누른 경우는 동시 클릭으로 처리하지 않는다.
        if (!press.armed && press.lastButtons and sample.buttons != press.lastButtons) press.rejected = true
        if (both && !press.armed) {
            if (sample.eventTimeMs - press.startedAt <= simultaneousWindowMs && !press.rejected) press.armed = true
            else press.rejected = true
        }
        press.lastButtons = sample.buttons
        press.lastAt = sample.eventTimeMs
        return null
    }

    companion object {
        const val PRIMARY = 1
        const val SECONDARY = 2
        private const val BOTH = PRIMARY or SECONDARY
    }
}
