package com.easyswitchcontroller.floating

/** 픽셀 기록은 검증 앱이 사용자 동의 후에만 수행한다. 본 앱은 좌표 상태만 제공한다. */
internal data class FloatingScene(val sampledNs: Long, val windows: List<PixelCapturePlan.Box>, val state: String, val moving: Boolean)
