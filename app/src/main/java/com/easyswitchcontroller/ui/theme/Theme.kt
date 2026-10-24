package com.easyswitchcontroller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 핸드오프 확정: 앱은 항상 다크 테마로 출시한다(라이트 보드는 참고용).
 * 다이내믹 컬러(Material You)도 쓰지 않는다 — 토큰이 그대로 최종 색이다.
 */
private val DarkScheme = darkColorScheme(
    primary = Tokens.Point,
    onPrimary = Color.White,
    primaryContainer = Tokens.PointTintBg,
    onPrimaryContainer = Tokens.PointTintText,
    secondary = Tokens.PointAccent,
    onSecondary = Tokens.Bg,
    background = Tokens.Bg,
    onBackground = Tokens.Text,
    surface = Tokens.Card,
    onSurface = Tokens.Text,
    surfaceVariant = Tokens.Inset,
    onSurfaceVariant = Tokens.Text2,
    outline = Tokens.Border,
    outlineVariant = Tokens.BorderStrong,
    error = Tokens.Warn,
    onError = Tokens.Bg,
    errorContainer = Tokens.WarnCardBg,
    onErrorContainer = Tokens.WarnText,
)

@Composable
fun EasySwitchControllerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = AppTypography,
        content = content,
    )
}
