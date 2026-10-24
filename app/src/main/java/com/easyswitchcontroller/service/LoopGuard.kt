package com.easyswitchcontroller.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 백그라운드 루프의 한 바퀴를 돌린다. 바퀴 안에서 난 예외가 **루프를 죽이지 않게** 한다.
 *
 * 왜 필요한가: 코루틴은 CancellationException으로 끝나면 크래시도 로그도 없이 조용히 완료된다.
 * 그런데 `withTimeout`이 던지는 TimeoutCancellationException도 CancellationException의 하위라,
 * "취소면 다시 던진다"는 흔한 패턴(`catch (e: CancellationException) { throw e }`)에 걸리면
 * 그저 시간 초과였던 것이 **그 루프를 소리 없이 끝내 버린다.** 따라가기 매니저가 바로 이 패턴이었다
 * (실측 2026-10-06: 4초 간격 시간 초과 두 번 뒤 실패 기록은 0건이고 65초간 연결 시도가 한 번도 없었다).
 *
 * 바깥 스코프가 **진짜로** 취소된 경우(서비스 종료)만 그대로 던져 루프를 끝낸다 — 그 판별은 예외의
 * 종류가 아니라 지금 이 코루틴이 아직 살아 있는지([ensureActive])로 한다.
 */
internal suspend fun survive(onError: (Throwable) -> Unit, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive() // 진짜 취소였다면 여기서 다시 던져진다
        onError(e)
    } catch (e: Exception) {
        onError(e)
    }
}
