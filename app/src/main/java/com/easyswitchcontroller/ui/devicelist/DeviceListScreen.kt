package com.easyswitchcontroller.ui.devicelist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyswitchcontroller.R
import com.easyswitchcontroller.data.BondedDeviceInfo
import com.easyswitchcontroller.ui.components.ScreenHeader
import com.easyswitchcontroller.ui.components.SelectDeviceRow
import com.easyswitchcontroller.ui.theme.AppIcons
import com.easyswitchcontroller.ui.theme.Tokens

/** 기기 선택(핸드오프 C/F1): 선택형 카드 리스트 + 하단 "N개 선택 완료" CTA. */
@Composable
fun DeviceListScreen(
    devices: List<BondedDeviceInfo>,
    selected: Set<String>,
    connectedAddresses: Set<String>,
    cancelEnabled: Boolean,
    tablet: Boolean,
    /** 태블릿에서 좌우 절반만 쓸지. 폴드를 펼친 폭처럼 절반이 너무 좁으면 false — 전체 폭을 쓴다. */
    split: Boolean = true,
    onToggle: (BondedDeviceInfo) -> Unit,
    onRefresh: () -> Unit,
    onProceed: () -> Unit,
    onCancel: () -> Unit,
) {
    // 시스템 뒤로가기 → 메인(홈)으로.
    BackHandler(enabled = cancelEnabled) { onCancel() }

    if (tablet && split) {
        // 설정 좌측 카드 열과 폭을 정확히 일치시킨다: 프레임(AppRoot h32/v30)을 설정과 동일하게
        // weight 1:1 · gap 24dp로 나눠 왼쪽 절반만 쓰고 오른쪽은 빈 Spacer로 둔다.
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            DeviceSelectBody(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                tablet = true,
                devices = devices,
                selected = selected,
                connectedAddresses = connectedAddresses,
                onToggle = onToggle,
                onRefresh = onRefresh,
                onProceed = onProceed,
            )
            Spacer(Modifier.weight(1f))
        }
    } else {
        // 폰, 또는 폴드를 펼친 폭(설정 카드도 1열이라 여기도 전체 폭 — 폭 일치 규칙은 그대로 지켜진다).
        DeviceSelectBody(
            modifier = Modifier.fillMaxSize(),
            tablet = tablet,
            devices = devices,
            selected = selected,
            connectedAddresses = connectedAddresses,
            onToggle = onToggle,
            onRefresh = onRefresh,
            onProceed = onProceed,
        )
    }
}

/** 기기 선택 본문(헤더 + 리스트 + CTA). 폭 프레이밍(폰 전체폭 / 태블릿 반쪽)은 [DeviceListScreen]이 담당한다. */
@Composable
private fun DeviceSelectBody(
    modifier: Modifier,
    tablet: Boolean,
    devices: List<BondedDeviceInfo>,
    selected: Set<String>,
    connectedAddresses: Set<String>,
    onToggle: (BondedDeviceInfo) -> Unit,
    onRefresh: () -> Unit,
    onProceed: () -> Unit,
) {
    Column(modifier) {
        // 헤더: 공용 ScreenHeader(홈·설정과 폰트·정렬 통일) + 서브타이틀 + 새로고침 트레일링
        ScreenHeader(
            title = stringResource(R.string.device_select_title),
            tablet = tablet,
            subtitle = stringResource(R.string.device_select_subtitle),
            modifier = if (tablet) {
                Modifier.padding(bottom = 16.dp)
            } else {
                Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 16.dp)
            },
            trailing = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Tokens.Card, RoundedCornerShape(12.dp))
                        .border(1.dp, Tokens.Border, RoundedCornerShape(12.dp))
                        .clickable { onRefresh() },
                ) {
                    Icon(
                        imageVector = AppIcons.Refresh,
                        contentDescription = stringResource(R.string.cd_refresh),
                        tint = Tokens.Text2,
                        // 사용자 피드백: 25% 확대
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )

        if (devices.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.device_select_empty),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Tokens.Text2,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(
                // 태블릿 카드 간격도 설정 카드 열과 동일(10dp).
                verticalArrangement = Arrangement.spacedBy(if (tablet) 10.dp else 9.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = if (tablet) 0.dp else 16.dp),
            ) {
                items(devices, key = { it.address }) { device ->
                    SelectDeviceRow(
                        info = device,
                        selected = device.address in selected,
                        connected = device.address in connectedAddresses,
                        tablet = tablet,
                        onToggle = { onToggle(device) },
                    )
                }
            }
        }

        // 하단 CTA
        val ctaEnabled = selected.isNotEmpty()
        val ctaShape = RoundedCornerShape(16.dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(
                    start = if (tablet) 0.dp else 24.dp,
                    top = 16.dp,
                    end = if (tablet) 0.dp else 24.dp,
                    bottom = 16.dp,
                )
                // 폰은 아래 하단 내비 바가 시스템 인셋을 처리하므로 CTA엔 중복 적용하지 않는다(틈 방지). 태블릿만 적용.
                .let { if (tablet) it.navigationBarsPadding() else it }
                .fillMaxWidth()
                .alpha(if (ctaEnabled) 1f else 0.4f)
                .let {
                    if (ctaEnabled) {
                        it.shadow(8.dp, ctaShape, ambientColor = Tokens.Point, spotColor = Tokens.Point)
                    } else {
                        it
                    }
                }
                .clip(ctaShape)
                .background(Tokens.Point, ctaShape)
                .clickable(enabled = ctaEnabled) { onProceed() }
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.device_select_confirm, selected.size),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}
