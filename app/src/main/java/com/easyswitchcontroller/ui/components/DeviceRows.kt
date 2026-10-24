package com.easyswitchcontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyswitchcontroller.R
import com.easyswitchcontroller.data.BondedDeviceInfo
import com.easyswitchcontroller.device.DeviceKind
import com.easyswitchcontroller.di.ServiceLocator
import com.easyswitchcontroller.ui.theme.AppIcons
import com.easyswitchcontroller.ui.theme.Tokens

/** 기기 종류 글리프(마우스/키보드 외곽선). */
@Composable
fun DeviceGlyph(kind: DeviceKind, connected: Boolean, size: Dp) {
    Icon(
        imageVector = if (kind == DeviceKind.KEYBOARD) AppIcons.Keyboard else AppIcons.Mouse,
        contentDescription = stringResource(
            if (kind == DeviceKind.KEYBOARD) R.string.device_role_keyboard else R.string.device_role_mouse,
        ),
        tint = if (connected) Tokens.PointAccent else Tokens.GlyphDisabled,
        modifier = Modifier.size(size),
    )
}

/**
 * 홈의 연결 기기 행. 상태 문구·칩은 실제 상태에서 유도한다:
 * 마우스는 0x1814 준비 여부, 키보드는 팔로우 매니저 가동 여부.
 * [showChip]=false는 태블릿 홈(핸드오프 E — 칩 없음).
 */
@Composable
fun HomeDeviceRow(
    name: String,
    kind: DeviceKind,
    connected: Boolean,
    followRunning: Boolean,
    featureReady: Boolean,
    showChip: Boolean = true,
    tablet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val statusText = when {
        !connected -> stringResource(R.string.device_not_connected)
        kind == DeviceKind.KEYBOARD && followRunning -> stringResource(R.string.device_kbd_following)
        kind == DeviceKind.KEYBOARD ->
            "${stringResource(R.string.device_role_keyboard)}: ${stringResource(R.string.device_connected)}"
        featureReady -> stringResource(R.string.device_mouse_ready)
        else -> "${stringResource(R.string.device_role_mouse)}: ${stringResource(R.string.device_connected)}"
    }
    val chipText = when {
        !connected -> "—"
        kind == DeviceKind.KEYBOARD && followRunning -> stringResource(R.string.chip_detect)
        kind == DeviceKind.KEYBOARD -> "—"
        featureReady -> stringResource(R.string.chip_ready)
        else -> "—"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (connected) 1f else 0.6f)
            .background(Tokens.Card, RoundedCornerShape(if (tablet) 14.dp else 16.dp))
            .border(1.dp, Tokens.Border, RoundedCornerShape(if (tablet) 14.dp else 16.dp))
            .padding(horizontal = 13.dp, vertical = if (tablet) 11.dp else 10.dp),
    ) {
        IconTile(size = 32.dp, radius = 10.dp) {
            DeviceGlyph(kind, connected, 18.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (connected) Tokens.Text else Tokens.Text2,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                StatusDot(if (connected) Tokens.Success else Tokens.NumeralIdle)
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    color = if (connected) Tokens.Text2 else Tokens.TextMuted,
                )
            }
        }
        if (showChip) {
            MonoChip(chipText, textColor = if (connected) Tokens.Text2 else Tokens.GlyphDisabled)
        }
    }
}

/**
 * 표시용 글리프 종류 판별. **마우스/키보드가 아닌 기기(차량·버즈·노트북 등)는 null** —
 * 글리프를 비운다(사용자 확정: 목업에 비-로지텍 기기 참조가 없었고 마우스 글리프는 오해 유발).
 * 전환 대상 판정용 [ServiceLocator.guessKind]와 달리 CoD가 주변기기가 아니면 추측하지 않는다.
 */
fun displayKindOf(info: BondedDeviceInfo): DeviceKind? {
    val dc = info.deviceClass
    if ((dc and 0x1F00) == 0x0500) { // major = PERIPHERAL
        if (dc and 0x40 != 0) return DeviceKind.KEYBOARD
        if (dc and 0x80 != 0) return DeviceKind.MOUSE
        return null
    }
    if (dc != 0) return null // 오디오·컴퓨터·차량 등 — 글리프 없음
    // CoD 미상: 로지텍 추정 기기에 한해 이름 휴리스틱
    if (!info.likelyLogitech) return null
    return ServiceLocator.guessKind(info)
}

/** 기기 선택 화면의 선택형 카드 행. 선택 여부(보더·체크)와 연결 여부(점·문구)는 독립 표시. */
@Composable
fun SelectDeviceRow(
    info: BondedDeviceInfo,
    selected: Boolean,
    connected: Boolean,
    onToggle: () -> Unit,
    tablet: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val displayKind = displayKindOf(info)
    // 태블릿은 설정 카드와 반경·높이를 맞춘다(radius 16dp, height 70dp = cardHeight(tablet)).
    val shape = RoundedCornerShape(if (tablet) 16.dp else 18.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        modifier = modifier
            .fillMaxWidth()
            .let { if (tablet) it.height(70.dp) else it }
            .alpha(if (selected) 1f else 0.62f)
            .clip(shape)
            .background(Tokens.Card, shape)
            .let {
                if (selected) it.border(1.5.dp, Tokens.PointAccent, shape)
                else it.border(1.dp, Tokens.Border, shape)
            }
            .clickable { onToggle() }
            .padding(13.dp),
    ) {
        // 체크박스(22dp, radius 7)
        val checkShape = RoundedCornerShape(7.dp)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .let {
                    if (selected) it.background(Tokens.Point, checkShape)
                    else it.border(2.dp, Tokens.NumeralIdle, checkShape)
                },
        ) {
            if (selected) {
                Icon(
                    imageVector = AppIcons.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        IconTile(size = 34.dp, radius = 11.dp) {
            // 마우스/키보드가 아니면 빈 타일(정렬 유지, 글리프 없음)
            if (displayKind != null) {
                DeviceGlyph(displayKind, connected = selected, size = 19.dp)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = info.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Tokens.Text else Tokens.Text2,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 3.dp),
            ) {
                StatusDot(if (connected) Tokens.Success else Tokens.NumeralIdle, 7.dp)
                val roleSuffix = when (displayKind) {
                    DeviceKind.KEYBOARD -> " · " + stringResource(R.string.device_role_keyboard)
                    DeviceKind.MOUSE -> " · " + stringResource(R.string.device_role_mouse)
                    null -> ""
                }
                Text(
                    text = if (connected) {
                        stringResource(R.string.device_connected) + roleSuffix
                    } else {
                        stringResource(R.string.device_not_connected)
                    },
                    fontSize = 12.sp,
                    color = if (connected) Tokens.Text2 else Tokens.TextMuted,
                )
            }
        }
    }
}
