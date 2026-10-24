package com.easyswitchcontroller.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.easyswitchcontroller.R

/** UI 본문 폰트(핸드오프 지정). KO/EN 모두 커버. */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
)

/** 모노 폰트 — 채널 숫자·hex·타임스탬프·로그·버전·작은 배지 전용(핸드오프 지정). */
val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

/** M3 기본 스케일에 Pretendard만 입힌다. 화면별 크기·굵기는 컴포저블에서 직접 지정한다. */
val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = Pretendard),
        displayMedium = t.displayMedium.copy(fontFamily = Pretendard),
        displaySmall = t.displaySmall.copy(fontFamily = Pretendard),
        headlineLarge = t.headlineLarge.copy(fontFamily = Pretendard),
        headlineMedium = t.headlineMedium.copy(fontFamily = Pretendard),
        headlineSmall = t.headlineSmall.copy(fontFamily = Pretendard),
        titleLarge = t.titleLarge.copy(fontFamily = Pretendard),
        titleMedium = t.titleMedium.copy(fontFamily = Pretendard),
        titleSmall = t.titleSmall.copy(fontFamily = Pretendard),
        bodyLarge = t.bodyLarge.copy(fontFamily = Pretendard),
        bodyMedium = t.bodyMedium.copy(fontFamily = Pretendard),
        bodySmall = t.bodySmall.copy(fontFamily = Pretendard),
        labelLarge = t.labelLarge.copy(fontFamily = Pretendard),
        labelMedium = t.labelMedium.copy(fontFamily = Pretendard),
        labelSmall = t.labelSmall.copy(fontFamily = Pretendard),
    )
}
