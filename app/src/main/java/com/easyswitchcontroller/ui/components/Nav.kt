package com.easyswitchcontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.easyswitchcontroller.R
import com.easyswitchcontroller.ui.theme.AppIcons
import com.easyswitchcontroller.ui.theme.SpaceMono
import com.easyswitchcontroller.ui.theme.Tokens

/** 최상위 목적지. 홈/기기 선택/설정 — 폰은 하단 바, 태블릿은 사이드 레일. */
enum class AppTab { Home, Devices, Settings }

/** 앱 버전 표기(개발자 이름은 넣지 않는다 — 사용자 확정). 실제 설치된 versionName을 읽는다. */
@Composable
fun AppVersionLabel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    Text(
        text = stringResource(R.string.settings_version, versionName),
        fontFamily = SpaceMono,
        fontSize = 11.sp,
        color = Tokens.GlyphDisabled,
        modifier = modifier,
    )
}

@Composable
private fun tabLabel(tab: AppTab): String = stringResource(
    when (tab) {
        AppTab.Home -> R.string.nav_home
        AppTab.Devices -> R.string.nav_devices
        AppTab.Settings -> R.string.nav_settings
    },
)

private fun tabIcon(tab: AppTab): ImageVector = when (tab) {
    AppTab.Home -> AppIcons.Home
    AppTab.Devices -> AppIcons.Devices
    AppTab.Settings -> AppIcons.Gear
}

/** 폰 하단 내비 바. [settingsBadge]가 켜지면 설정 아이콘에 앰버 점을 단다(다른 앱 위에 표시 허용 필요). */
@Composable
fun BottomNavBar(
    current: AppTab,
    settingsBadge: Boolean,
    onSelect: (AppTab) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Tokens.Card)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp)
                .navigationBarsPadding(),
        ) {
            AppTab.entries.forEach { tab ->
                val active = tab == current
                val tint = if (active) Tokens.PointAccent else Tokens.TextMuted
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 2.dp),
                ) {
                    Box {
                        Icon(
                            imageVector = tabIcon(tab),
                            contentDescription = tabLabel(tab),
                            tint = tint,
                            // 사용자 피드백: 기본 대비 ~25% 확대
                            modifier = Modifier.size(25.dp),
                        )
                        if (tab == AppTab.Settings && settingsBadge) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .size(7.dp)
                                    .drawBehind {
                                        // 내비 바 배경색 테두리로 아이콘과 분리(디자인 1.5px border)
                                        drawCircle(Tokens.Card, radius = size.minDimension / 2 + 1.5.dp.toPx(), center = center)
                                        drawCircle(Tokens.Warn, radius = size.minDimension / 2, center = center)
                                    },
                            )
                        }
                    }
                    Text(
                        text = tabLabel(tab),
                        fontSize = 12.5.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        color = tint,
                    )
                }
            }
        }
    }
}

/** 사이드레일 고정 폭 — 콘텐츠에 남는 폭을 계산하는 쪽(AppRoot)에서도 참조한다. */
val SideRailWidth = 200.dp

/** 태블릿 좌측 레일: 브랜드 락업 + 내비 + 하단 따라가기 상태. */
@Composable
fun SideRail(
    current: AppTab,
    settingsBadge: Boolean,
    followRunning: Boolean,
    onSelect: (AppTab) -> Unit,
) {
    Row(Modifier.fillMaxHeight()) {
        Column(
            Modifier
                .width(SideRailWidth)
                .fillMaxHeight()
                .background(Tokens.BgRail)
                .padding(horizontal = 16.dp, vertical = 26.dp),
        ) {
            // 브랜드 락업
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                modifier = Modifier.padding(horizontal = 6.dp),
            ) {
                IconTile(size = 34.dp, radius = 10.dp, background = Tokens.Point) {
                    // 런처 아이콘과 동일한 아트(사용자 확정)
                    Icon(
                        imageVector = AppIcons.Brand,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.01).em,
                    color = Tokens.Text,
                )
            }
            Spacer(Modifier.height(34.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppTab.entries.forEach { tab ->
                    val active = tab == current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (active) Tokens.PointTintBg else Color.Transparent,
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { onSelect(tab) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        Box {
                            Icon(
                                imageVector = tabIcon(tab),
                                contentDescription = null,
                                tint = if (active) Tokens.PointAccent else Tokens.Text2,
                                modifier = Modifier.size(18.dp),
                            )
                            if (tab == AppTab.Settings && settingsBadge) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .size(7.dp)
                                        .background(Tokens.Warn, CircleShape),
                                )
                            }
                        }
                        Text(
                            text = tabLabel(tab),
                            fontSize = 13.5.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (active) Tokens.PointTintText else Tokens.Text2,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // 하단 따라가기 상태
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 6.dp),
            ) {
                StatusDot(if (followRunning) Tokens.Success else Tokens.Warn)
                Text(
                    text = stringResource(
                        if (followRunning) R.string.follow_status_on else R.string.follow_status_off,
                    ),
                    fontSize = 11.sp,
                    color = Tokens.Text2,
                )
            }
            // 앱 버전 — 설정 열이 길어져도 스크롤에 밀리지 않게 레일 맨 아래에 둔다(사용자 확정, 폰은 설정 카드 아래).
            AppVersionLabel(Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp))
        }
    }
}
