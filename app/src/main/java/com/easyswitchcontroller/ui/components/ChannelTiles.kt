package com.easyswitchcontroller.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.sp
import com.easyswitchcontroller.R
import com.easyswitchcontroller.ui.theme.SpaceMono
import com.easyswitchcontroller.ui.theme.Tokens
import kotlin.math.roundToInt

/**
 * 채널 3-업 그리드. [tablet]이면 태블릿 치수(큰 숫자·패딩)를 쓴다.
 * [enabled]가 꺼지면(비활성 상태) 전체를 흐리게(alpha .4) + 배지/힌트 없이 표시한다(핸드오프 C4).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelTileGrid(
    channelCount: Int,
    activeChannel: Int?,
    labels: Map<Int, String>,
    enabled: Boolean,
    tablet: Boolean = false,
    /** true면 타일이 부모 높이를 채운다(태블릿 홈 — 기기 열과 높이 일치). 세로 패딩 대신 중앙 정렬. */
    fillHeight: Boolean = false,
    connectedLocalChannels: Set<Int> = emptySet(),
    modifier: Modifier = Modifier,
    onTap: (Int) -> Unit,
    onLongPress: (Int, IntRect) -> Unit,
) {
    val gap = if (tablet) 14.dp else 10.dp
    val radius = if (tablet) 20.dp else 18.dp
    val verticalPadding = if (tablet) 22.dp else 9.dp
    val innerGap = if (tablet) 9.dp else 4.dp
    val numeralSize = if (tablet) 44.sp else 30.sp
    val labelSize = if (tablet) 14.sp else 12.5.sp

    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        (1..channelCount).forEach { channel ->
            var bounds by remember(channel) { mutableStateOf(IntRect.Zero) }
            val requestRename by rememberUpdatedState { anchor: IntRect -> onLongPress(channel, anchor) }
            val badge = ChannelBadgeState.resolve(channel, activeChannel, connectedLocalChannels)
            val active = enabled && badge == ChannelBadgeState.CONNECTED_HERE
            val shape = RoundedCornerShape(radius)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(innerGap, Alignment.CenterVertically),
                modifier = Modifier
                    .weight(1f)
                    .let { if (fillHeight) it.fillMaxHeight() else it }
                    .let { if (active) it.shadow(10.dp, shape, ambientColor = Tokens.Point, spotColor = Tokens.Point) else it }
                    // 리플이 둥근 모서리를 벗어나지 않게 클릭 이전에 클립
                    .clip(shape)
                    .background(if (active) Tokens.Point else Tokens.Card, shape)
                    .let { if (active) it else it.border(1.dp, Tokens.Border, shape) }
                    .onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInWindow()
                        val left = position.x.roundToInt()
                        val top = position.y.roundToInt()
                        bounds = IntRect(left, top, left + coordinates.size.width, top + coordinates.size.height)
                    }
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            if (currentEvent.buttons.isSecondaryPressed) {
                                down.consume()
                                // UP까지 소비한 뒤 열어 우클릭을 채널 전환이나 바깥 클릭으로 넘기지 않는다.
                                waitForUpOrCancellation(pass = PointerEventPass.Initial)?.let { up ->
                                    up.consume()
                                    val x = (bounds.left + up.position.x).roundToInt()
                                    val y = (bounds.top + up.position.y).roundToInt()
                                    requestRename(IntRect(x, y, x, y))
                                }
                            }
                        }
                    }
                    .combinedClickable(
                        enabled = enabled,
                        onClick = { onTap(channel) },
                        onLongClick = { requestRename(bounds) },
                    )
                    // fillHeight면 높이는 부모가 정하므로 세로 패딩 없이 중앙 정렬만
                    .padding(vertical = if (fillHeight) 0.dp else verticalPadding, horizontal = 8.dp),
            ) {
                Text(
                    text = "$channel",
                    fontFamily = SpaceMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = numeralSize,
                    lineHeight = numeralSize,
                    color = if (active) Color.White else Tokens.NumeralIdle,
                )
                Text(
                    text = labels[channel] ?: stringResource(R.string.channel_label, channel),
                    fontSize = labelSize,
                    fontWeight = FontWeight.Bold,
                    color = if (active) Color.White else Tokens.Text,
                    maxLines = 1,
                )
                // 고정 높이 슬롯 — 배지(활성)와 힌트(유휴)의 높이 차로 타일 크기가 변하지 않게
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.height(32.dp),
                ) {
                    when {
                        enabled && badge != ChannelBadgeState.NONE -> ActiveBadge(tablet, badge == ChannelBadgeState.CONNECTED_HERE)
                        enabled -> Text(
                            text = stringResource(R.string.channel_tap),
                            fontSize = if (tablet) 11.sp else 9.5.sp,
                            color = Tokens.TextMuted,
                        )
                        // 비활성: 배지·힌트 없음(C4) — 슬롯만 유지
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveBadge(tablet: Boolean, connectedHere: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (tablet) 5.dp else 4.dp),
        modifier = Modifier
            .background(if (connectedHere) Color(0x38FFFFFF) else Tokens.Point,
                RoundedCornerShape(if (tablet) 14.dp else 12.dp))
            .padding(
                horizontal = if (tablet) 11.dp else 8.dp,
                vertical = if (tablet) 4.dp else 3.dp,
            ),
    ) {
        StatusDot(Color.White, if (tablet) 6.dp else 5.dp)
        val badgeSize = if (tablet) 10.5.sp else 9.5.sp
        Text(
            text = stringResource(if (connectedHere) R.string.channel_connected_here else R.string.channel_active),
            fontSize = badgeSize,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            // 점과의 세로 정렬: 기본 lineHeight(스타일 상속 24sp)가 글리프를 박스 안에서
            // 밀어내므로 글자 크기에 맞춰 고정하고 폰트 패딩을 끈다.
            lineHeight = badgeSize,
            style = LocalTextStyle.current.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}
