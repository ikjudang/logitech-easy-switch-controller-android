package com.easyswitchcontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyswitchcontroller.R
import com.easyswitchcontroller.ui.theme.SpaceMono
import com.easyswitchcontroller.ui.theme.Tokens

/** 앰버 원 안의 "!" — 배너·설정 행이 함께 쓰는 경고 마크. */
@Composable
fun WarnMark(size: androidx.compose.ui.unit.Dp, fontSize: androidx.compose.ui.unit.TextUnit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).background(Tokens.Warn, CircleShape),
    ) {
        Text(
            text = "!",
            fontFamily = SpaceMono,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            color = Tokens.Bg,
        )
    }
}

/** 홈 상단 "다른 앱 위에 표시" 허용 배너(핸드오프 C4 모양): 제목·본문 + 허용 CTA. */
@Composable
fun OverlayBanner(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(Tokens.WarnCardBg, shape)
            .border(1.dp, Tokens.WarnBorder, shape)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        WarnMark(26.dp, 15.sp)
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.perm_banner_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.WarnText,
            )
            Text(
                text = stringResource(R.string.perm_banner_body),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = Tokens.WarnBody,
                modifier = Modifier.padding(top = 3.dp),
            )
            // 따라가기·수동 전환은 이 권한 없이도 동작함을 명시(사용자 확정 사항)
            Text(
                text = stringResource(R.string.perm_banner_manual),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 17.sp,
                color = Tokens.WarnText,
                modifier = Modifier.padding(top = 2.dp),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(top = 11.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Tokens.Warn, RoundedCornerShape(11.dp))
                    .clickable { onOpenSettings() }
                    .padding(vertical = 9.dp),
            ) {
                Text(
                    text = stringResource(R.string.perm_banner_grant),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Tokens.Bg,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
