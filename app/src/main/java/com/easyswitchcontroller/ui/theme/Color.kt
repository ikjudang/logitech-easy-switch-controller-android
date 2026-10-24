package com.easyswitchcontroller.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 핸드오프 디자인 토큰(다크 테마 고정).
 * 이름은 핸드오프 표 그대로 유지한다 — 디자인 파일과 1:1 대조 가능하게.
 */
object Tokens {
    /** 브랜드 바이올렛(point). 활성 채널 타일, 주 버튼, 런처 배경. */
    val Point = Color(0xFF6C3CE0)

    /** 다크 표면 위 아이콘·링크·활성 내비(point-accent). */
    val PointAccent = Color(0xFFA684F2)

    /** 레일 활성 항목 배경(point-tint-bg). */
    val PointTintBg = Color(0xFF231847)

    /** point-tint-bg 위 텍스트/아이콘(point-tint-text). */
    val PointTintText = Color(0xFFC5B6F7)

    /** 마스터-디테일 선택 행의 아이콘 타일 배경. */
    val PointTintIconBg = Color(0xFF2D2058)

    /** 화면 배경(bg). */
    val Bg = Color(0xFF0F1216)

    /** 태블릿 사이드 레일 배경(bg-rail). */
    val BgRail = Color(0xFF14181D)

    /** 카드·리스트 행·내비 바(card). */
    val Card = Color(0xFF191E24)

    /** 아이콘 타일·키캡·드롭다운 트리거(inset). */
    val Inset = Color(0xFF20262D)

    /** 카드/헤어라인 보더(border). */
    val Border = Color(0xFF262C33)

    /** 드롭다운/키캡 보더(border-strong). */
    val BorderStrong = Color(0xFF333A43)

    /** 본문 텍스트(text). */
    val Text = Color(0xFFEEF1F5)

    /** 보조 텍스트(text-2). */
    val Text2 = Color(0xFF8B939D)

    /** 3차 텍스트·타임스탬프(text-muted). */
    val TextMuted = Color(0xFF6B727C)

    /** 로그 본문 등 강조 본문(#c3c9d2). */
    val TextBody = Color(0xFFC3C9D2)

    /** 유휴 채널 숫자(numeral-idle). */
    val NumeralIdle = Color(0xFF3A424D)

    /** 비연결 행의 글리프 스트로크. */
    val GlyphDisabled = Color(0xFF5B636D)

    /** 연결됨/허용됨(success). */
    val Success = Color(0xFF4ECB83)

    /** 권한 필요(warn). */
    val Warn = Color(0xFFE6A935)

    /** 경고 제목/필 텍스트(warn-text). */
    val WarnText = Color(0xFFF2CF76)

    /** 경고 본문 텍스트(#c9b98a). */
    val WarnBody = Color(0xFFC9B98A)

    /** 경고 배너/카드 배경(warn-card-bg). */
    val WarnCardBg = Color(0xFF2A2210)

    /** 경고 배너/카드 보더(warn-border). */
    val WarnBorder = Color(0xFF6B5A1E)

    /** 터미널 로그 패널 배경(log-bg). */
    val LogBg = Color(0xFF0A0C0F)
}
