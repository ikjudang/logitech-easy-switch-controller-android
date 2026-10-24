package com.easyswitchcontroller.floating

import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent

/** 앱이 실제로 전달받은 입력만 판정한다. 다른 앱의 입력 수신으로 확대 해석하지 않는다. */
internal object FloatingInputSession {
    data class TestArea(val displayId: Int, val bounds: FloatingMenuGeometry.Bounds)
    var testArea: TestArea? = null
    var appWindowFocused = false
    private var resizeControl: Pair<Any, TestArea>? = null
    fun resizeControlArea(token: Any, area: TestArea) { resizeControl = token to area }
    fun clearResizeControlArea(token: Any) { if (resizeControl?.first === token) resizeControl = null }
    fun isResizeControl(point: FloatingMenuGeometry.Point, displayId: Int?) = appWindowFocused && resizeControl?.second?.let {
        it.displayId == displayId && point.x in it.bounds.left..it.bounds.right && point.y in it.bounds.top..it.bounds.bottom
    } == true
    var logger: (String) -> Unit = {}
    private val detector = MouseChordDetector()
    private val main = Handler(Looper.getMainLooper())
    var onSummon: ((MouseChordDetector.Summon) -> Unit)? = null
    var running = false
        set(value) { field = value; detector.reset() }

    fun resetAppInput() = detector.reset()
    fun removeDevice(deviceId: Int) = detector.removeDevice(deviceId)

    fun acceptAppEvent(event: MotionEvent, displayId: Int) {
        if (!running || !event.isFromSource(InputDevice.SOURCE_MOUSE) || event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)) return
        val buttons = if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) 0 else event.buttonState
        if (event.actionMasked != MotionEvent.ACTION_HOVER_MOVE && event.actionMasked != MotionEvent.ACTION_MOVE) {
            logger("APP_WINDOW action=${event.actionMasked} device=${event.deviceId} display=$displayId buttons=$buttons x=${event.rawX} y=${event.rawY}")
        }
        val summon = detector.accept(MouseChordDetector.Sample(event.deviceId, displayId, event.eventTime,
            buttons, event.rawX, event.rawY, cancelled = event.actionMasked == MotionEvent.ACTION_CANCEL)) ?: return
        logger("SUMMON source=APP_WINDOW device=${summon.deviceId} display=${summon.displayId} x=${summon.x} y=${summon.y}")
        main.post { if (running) onSummon?.invoke(summon) }
    }
}
