package com.easyswitchcontroller.device

enum class DeviceKind { MOUSE, KEYBOARD }

/** 채널 전환 시도의 결과. */
sealed interface SwitchResult {
    /** setCurrentHost write가 나갔음(응답을 기다리지 않는다). 직후 링크 끊김은 정상이다. */
    data object Switched : SwitchResult

    data class Failed(
        val reason: String,
        /**
         * 현재 안드로이드 기기에 연결되지 않아 명령을 보내지 못한 경우.
         * 백그라운드 감시 재시도 잡음과 실제 요청의 전환 실패를 구분하는 데 쓴다.
         */
        val notConnected: Boolean = false,
    ) : SwitchResult
}
