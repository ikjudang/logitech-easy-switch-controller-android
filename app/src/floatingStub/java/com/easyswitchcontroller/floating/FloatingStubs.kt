package com.easyswitchcontroller.floating

import android.content.Context
import android.graphics.Canvas
import android.view.View

/*
 * 플로팅 버튼 구현의 빈 자리표시.
 *
 * 공개 저장소에는 플로팅 버튼의 그리기·애니메이션 구현이 없다. 이 파일은 그 구현이 없는 소스(예: 공개 저장소를 복제한
 * 환경)에서도 나머지 앱이 컴파일되게 하는 최소한의 형태만 담고, 아무것도 그리거나 표시하지 않는다.
 * 실제 구현(FloatingOverlayController.kt 등)이 소스에 있으면 app/build.gradle.kts가 이 폴더를 빌드에서 뺀다.
 * 이 빌드에서는 BuildConfig.FLOATING_IMPL이 false이고, 플로팅 버튼은 켜지지 않는다.
 * 플로팅 버튼이 필요하면 릴리즈에 첨부된 별도 빌드 APK를 쓴다.
 */

internal object FloatingSize {
    const val DEFAULT_PERCENT = 100
    const val MAX_PERCENT = 200
    fun percent(value: Int) = value.coerceIn(0, MAX_PERCENT)
}

internal object FloatingAnimationSpeed {
    const val DEFAULT_PERCENT = 100
    const val MIN_PERCENT = 25
    const val MAX_PERCENT = 300
    fun percent(value: Int) = value.coerceIn(MIN_PERCENT, MAX_PERCENT)
}

internal object FloatingMenuGeometry {
    data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)
    data class Point(val x: Float, val y: Float)
}

/** 아무것도 그리지 않는 자리표시 뷰. */
internal class FloatingBubbleView(context: Context, val channel: Int?, designScale: Float = 1f) : View(context) {
    override fun onDraw(canvas: Canvas) = Unit
}

/** 아무것도 표시하지 않는 자리표시 컨트롤러. */
internal class FloatingOverlayController(
    context: Context,
    onChannelSelected: (Int) -> Unit,
    onAppSelected: () -> Unit,
    log: (String) -> Unit,
    onHomeMoved: (FloatingMenuGeometry.Point) -> Unit = {},
    testMode: Boolean = false,
    onHomeVisibilityChanged: (Boolean) -> Unit = {},
) {
    val isShowing: Boolean get() = false
    fun start() = Unit
    fun stop() = Unit
    fun showHome(xFraction: Float = .88f, yFraction: Float = .58f, requestedAt: Long? = null) = Unit
    fun hide(reason: String = "user") = Unit
    fun dismiss(onFinished: () -> Unit) = onFinished()
    fun keyboardSwitched(channel: Int): Boolean = false
    fun refreshAccess() = Unit
    fun setOpacity(value: Float) = Unit
    fun setSizePercent(value: Int, onHidden: () -> Unit = {}) = onHidden()
    fun setAnimationSpeedPercent(value: Int) = Unit
    fun setBounceEnabled(value: Boolean) = Unit
    fun setAlwaysShowMenu(value: Boolean) = Unit
}
