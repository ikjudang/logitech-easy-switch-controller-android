package com.easyswitchcontroller.inputprobe

import android.view.MotionEvent
import com.easyswitchcontroller.floating.FloatingInputSession

internal object ProbeSession {
    var testArea: FloatingInputSession.TestArea?
        get() = FloatingInputSession.testArea
        set(value) { FloatingInputSession.testArea = value }
    val running get() = FloatingInputSession.running
    fun resetAppInput() = FloatingInputSession.resetAppInput()
    fun acceptAppEvent(event: MotionEvent, displayId: Int) = FloatingInputSession.acceptAppEvent(event, displayId)
}
