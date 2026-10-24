package com.easyswitchcontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.easyswitchcontroller.ui.theme.SpaceMono
import com.easyswitchcontroller.ui.theme.Tokens

/** 상태 점(연결·경고 표시). */
@Composable
fun StatusDot(color: Color, size: Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** 둥근 사각 아이콘 타일(기기 글리프·설정 행 리딩 아이콘 배경). */
@Composable
fun IconTile(
    size: Dp,
    radius: Dp,
    background: Color = Tokens.Inset,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.size(size).background(background, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 섹션 라벨(12sp/600, text-muted, 자간 +0.02em). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.02.em,
        color = Tokens.TextMuted,
        modifier = modifier,
    )
}

/**
 * 최상위 화면(홈·기기 선택·설정) 공용 메인 헤더 — 세 화면 타이틀의 폰트·굵기·자간·정렬을 통일한다.
 * [tablet]이면 26sp, 폰이면 24sp / ExtraBold / -0.015em / 좌측 정렬.
 * [subtitle](기기 선택의 부제)·[trailing](예: 새로고침 버튼)은 선택 항목이다.
 * 바깥 여백은 호출부가 [modifier]로 준다(화면별 프레임에 맞춤).
 */
@Composable
fun ScreenHeader(
    title: String,
    tablet: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = if (tablet) 26.sp else 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.015).em,
                color = Tokens.Text,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Tokens.Text2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) trailing()
    }
}

/** 모노 배지(기기 행 우측 hex/감지 칩). */
@Composable
fun MonoChip(text: String, textColor: Color = Tokens.Text2, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Tokens.Inset, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontFamily = SpaceMono,
            fontSize = 10.sp,
            color = textColor,
        )
    }
}
