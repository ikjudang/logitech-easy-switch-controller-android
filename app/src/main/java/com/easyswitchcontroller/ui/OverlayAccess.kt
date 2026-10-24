package com.easyswitchcontroller.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlinx.coroutines.delay

/**
 * "다른 앱 위에 표시" 허용 여부를 주기 폴링(1.5초)하는 상태.
 * 시스템 설정에 갔다 돌아오면 자동 갱신된다. 홈 배너·설정 필·내비 배지가 함께 쓴다.
 * 마우스 따라가기 자체는 이 권한과 무관하다 — 다른 앱 위의 전환 안내에만 필요하다.
 */
@Composable
fun rememberOverlayGranted(): State<Boolean> {
    val context = LocalContext.current
    val granted = remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            granted.value = Settings.canDrawOverlays(context)
            delay(1500)
        }
    }
    return granted
}

/** 이 앱의 "다른 앱 위에 표시" 설정 페이지를 연다. 패키지 지정이 안 되는 기기에서는 전체 목록으로 연다. */
fun openOverlaySettings(context: Context) {
    val direct = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(direct) }.isSuccess) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
