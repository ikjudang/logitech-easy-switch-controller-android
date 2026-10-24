package com.easyswitchcontroller.ui.log

import android.content.Context
import android.net.Uri
import com.easyswitchcontroller.R
import com.easyswitchcontroller.data.ConnectionLog
import com.easyswitchcontroller.ui.AppToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val FOLDER_SAVE_WAIT_MS = 5_000L

/**
 * 연결 로그를 꺼내는 두 길. adb를 쓸 수 없는 상황(원격 근무 중, 기기만 손에 있는 경우)에서 증상 기록을 꺼낼
 * 경로다. 스크린샷은 화면에 보이는 몇 줄만 담기고 시간대도 잘려서, 재연결이 몇 초 간격으로 몇 번 반복됐는지
 * 같은 **간격 정보**가 사라진다 — 원인 판정은 대개 그 간격에서 갈린다.
 *
 *  - 로그 보기의 내보내기: 저장 창에서 사용자가 고른 위치·이름의 파일에 한 번 쓴다([saveConnectionLog]).
 *  - 로그 저장 카드의 내보내기: 지정 폴더의 하루 파일에 지금 바로 쓴다([exportConnectionLogToFolder]).
 *
 * 예전의 공유 창(다른 앱으로 넘기기)은 없앴다(사용자 확정, 2026-10-20).
 */
suspend fun saveConnectionLog(context: Context, target: Uri) {
    val text = ConnectionLog.exportAllText().await()
    if (text.isBlank()) {
        AppToast.show(context, R.string.log_export_empty)
        return
    }
    val saved = withContext(Dispatchers.IO) {
        runCatching {
            // "wt" = 기존 내용을 잘라내고 쓴다. 사용자가 이미 있는 파일을 고르면 옛 꼬리가 남지 않게 한다.
            context.contentResolver.openOutputStream(target, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
        }.getOrDefault(false)
    }
    AppToast.show(context, if (saved) R.string.log_saved else R.string.log_save_file_failed)
}

/**
 * 지정 폴더에 지금 바로 한 편 남긴다 — 평소의 몰아쓰기(20초)를 기다리지 않는다. 사용자가 누른 내보내기라
 * 자동 저장을 꺼 둬도 쓴다. 폴더가 없으면 부르는 쪽이 먼저 고르게 한다.
 */
suspend fun exportConnectionLogToFolder(context: Context) {
    if (ConnectionLog.entries.value.isEmpty()) {
        AppToast.show(context, R.string.log_export_empty)
        return
    }
    // 드라이브 쓰기가 늦어도 화면을 붙잡지 않는다. 시간 안에 못 끝낸 저장은 뒤에서 마저 진행된다.
    val saved = withTimeoutOrNull(FOLDER_SAVE_WAIT_MS) { ConnectionLog.snapshotNow(explicit = true).await() }
    AppToast.show(context, when (saved) {
        true -> R.string.log_saved
        false -> R.string.log_save_failed
        null -> R.string.log_save_pending
    })
}
