package com.easyswitchcontroller.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.easyswitchcontroller.R
import com.easyswitchcontroller.device.DeviceKind
import com.easyswitchcontroller.service.FollowStatus
import com.easyswitchcontroller.ui.channel.ChannelViewModel
import com.easyswitchcontroller.ui.components.OverlayBanner
import com.easyswitchcontroller.ui.components.ChannelTileGrid
import com.easyswitchcontroller.ui.components.HomeDeviceRow
import com.easyswitchcontroller.ui.components.ScreenHeader
import com.easyswitchcontroller.ui.components.SectionLabel
import com.easyswitchcontroller.ui.components.StatusDot
import com.easyswitchcontroller.ui.openOverlaySettings
import com.easyswitchcontroller.ui.settings.roundedMenuHover
import com.easyswitchcontroller.ui.theme.Tokens

/**
 * 홈(폰): 채널 전환 + 연결 기기 한눈 보기(핸드오프 C1).
 * "다른 앱 위에 표시"가 꺼져 있으면 경고 배너를 띄운다(C4 모양). 채널 버튼은 항상 활성.
 */
@Composable
fun HomeScreen(
    viewModel: ChannelViewModel,
    overlayOn: Boolean,
    onOpenDevices: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val followRunning by FollowStatus.running.collectAsState()
    val activeChannel by FollowStatus.activeChannel.collectAsState()
    var editingChannel by remember { mutableStateOf<Int?>(null) }
    var renameAnchor by remember { mutableStateOf(IntRect.Zero) }
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.connect() }

    Column(Modifier.fillMaxSize()) {
        // 타이틀(공용 헤더 — 기기 선택·설정과 폰트·정렬 통일)
        ScreenHeader(
            title = stringResource(R.string.app_name),
            tablet = false,
            modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 16.dp),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!overlayOn) {
                OverlayBanner(
                    onOpenSettings = { openOverlaySettings(context) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }

            // 전환할 채널
            SectionLabel(
                text = stringResource(R.string.home_section_channels),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            ChannelTileGrid(
                channelCount = state.channelCount,
                activeChannel = activeChannel,
                connectedLocalChannels = state.connectedLocalChannels,
                labels = state.labels,
                // 수동 전환(BLE)은 권한과 무관하게 동작 — 항상 활성(사용자 확정).
                // busy 가드는 VM(onChannelClicked)이 담당한다.
                enabled = true,
                modifier = Modifier.padding(horizontal = 16.dp),
                onTap = { if (editingChannel == null) viewModel.onChannelClicked(it) },
                onLongPress = { channel, bounds -> renameAnchor = bounds; editingChannel = channel },
            )

            // 연결된 기기 — 수동 전환이 항상 가능하므로 권한 여부와 무관하게 표시
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 6.dp),
            ) {
                SectionLabel(
                    stringResource(R.string.home_section_connected, state.devices.count { it.connected }),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.home_link_select_devices),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Tokens.PointAccent,
                    modifier = Modifier.roundedMenuHover(RoundedCornerShape(8.dp))
                        .clickable { onOpenDevices() }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                state.devices.forEach { device ->
                    HomeDeviceRow(
                        name = device.name,
                        kind = device.kind,
                        connected = device.connected,
                        followRunning = followRunning,
                        featureReady = device.featureIndex != null,
                    )
                }
            }
        }

        // 하단 상태 줄
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(start = 20.dp, end = 20.dp, bottom = 10.dp, top = 8.dp),
        ) {
            StatusDot(if (followRunning) Tokens.Success else Tokens.Warn)
            Text(
                text = stringResource(
                    if (followRunning) R.string.status_footer_on else R.string.status_footer_off,
                ),
                fontSize = 11.sp,
                color = if (followRunning) Tokens.Text2 else Tokens.WarnBody,
            )
        }
    }

    RenameChannelPopup(
        editingChannel = editingChannel,
        anchorBounds = renameAnchor,
        currentLabel = { state.labels[it] ?: context.getString(R.string.channel_label, it) },
        onSave = { channel, label -> viewModel.setLabel(channel, label) },
        onDismiss = { editingChannel = null },
    )
}

/**
 * 홈(태블릿, 핸드오프 E): 좌측 기기 열(300dp) + 우측 채널 그리드.
 * 레일·타이틀은 AppRoot의 태블릿 스캐폴드가 그린다.
 *
 * [split]이 false면(폴드를 펼친 폭) 기기 열 300dp를 떼고 남는 폭에 채널 타일을 놓을 수 없으니,
 * 폰과 같은 순서로 위아래에 쌓는다 — 채널 그리드 → 연결 기기 목록. 타일·행 스타일은 태블릿 그대로.
 */
@Composable
fun HomeTabletContent(
    viewModel: ChannelViewModel,
    overlayOn: Boolean,
    split: Boolean,
    onOpenDevices: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val followRunning by FollowStatus.running.collectAsState()
    val activeChannel by FollowStatus.activeChannel.collectAsState()
    var editingChannel by remember { mutableStateOf<Int?>(null) }
    var renameAnchor by remember { mutableStateOf(IntRect.Zero) }
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.connect() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.nav_home),
            tablet = true,
            modifier = Modifier.padding(bottom = 22.dp),
        )
        if (!overlayOn) {
            OverlayBanner(
                onOpenSettings = { openOverlaySettings(context) },
                modifier = Modifier
                    .width(480.dp)
                    .padding(bottom = 20.dp),
            )
        }
        if (!split) {
            // 폴드를 펼친 폭: 위아래 스택. 채널 타일 높이는 "기기 2줄" 규칙 대신 자체 패딩(fillHeight=false).
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionLabel(
                    text = stringResource(R.string.home_section_channels),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                ChannelTileGrid(
                    channelCount = state.channelCount,
                    activeChannel = activeChannel,
                    connectedLocalChannels = state.connectedLocalChannels,
                    labels = state.labels,
                    // 수동 전환(BLE)은 권한과 무관하게 동작 — 항상 활성(사용자 확정).
                    enabled = true,
                    tablet = true,
                    onTap = { if (editingChannel == null) viewModel.onChannelClicked(it) },
                    onLongPress = { channel, bounds -> renameAnchor = bounds; editingChannel = channel },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 12.dp),
                ) {
                    SectionLabel(
                        stringResource(R.string.home_section_connected, state.devices.count { it.connected }),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.home_link_select_devices),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Tokens.PointAccent,
                        modifier = Modifier.roundedMenuHover(RoundedCornerShape(8.dp))
                            .clickable { onOpenDevices() }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    state.devices.forEach { device ->
                        HomeDeviceRow(
                            name = device.name,
                            kind = device.kind,
                            connected = device.connected,
                            followRunning = followRunning,
                            featureReady = device.featureIndex != null,
                            showChip = false,
                            tablet = true,
                        )
                    }
                }
            }
            return@Column
        }
        // 헤더 줄(좌: 연결 기기 라벨+링크 / 우: 채널 라벨)
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.width(300.dp),
            ) {
                SectionLabel(
                    stringResource(R.string.home_section_connected, state.devices.count { it.connected }),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.home_link_select_devices),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Tokens.PointAccent,
                    modifier = Modifier.roundedMenuHover(RoundedCornerShape(8.dp))
                        .clickable { onOpenDevices() }.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Box(Modifier.weight(1f)) {
                SectionLabel(stringResource(R.string.home_section_channels))
            }
        }
        Spacer(Modifier.height(12.dp))
        // 기기 열을 먼저 측정하고 채널 그리드에 그 높이를 강제한다
        // (타일 높이 = 기기 2줄 구간 높이, 사용자 확정)
        // 선택 기기가 많아 화면을 넘으면 이 영역이 스크롤된다(사용자 확정). 높이는 아래 측정이 직접 정하므로
        // 스크롤의 무한 높이 제약과 충돌하지 않는다.
        Layout(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    state.devices.forEach { device ->
                        HomeDeviceRow(
                            name = device.name,
                            kind = device.kind,
                            connected = device.connected,
                            followRunning = followRunning,
                            featureReady = device.featureIndex != null,
                            showChip = false,
                            tablet = true,
                        )
                    }
                }
                ChannelTileGrid(
                    channelCount = state.channelCount,
                    activeChannel = activeChannel,
                    connectedLocalChannels = state.connectedLocalChannels,
                    labels = state.labels,
                    // 수동 전환(BLE)은 권한과 무관하게 동작 — 항상 활성(사용자 확정).
                    // busy 가드는 VM(onChannelClicked)이 담당한다.
                    enabled = true,
                    tablet = true,
                    fillHeight = true,
                    onTap = { if (editingChannel == null) viewModel.onChannelClicked(it) },
                    onLongPress = { channel, bounds -> renameAnchor = bounds; editingChannel = channel },
                )
                // [2] 높이 기준(측정 전용, 배치 안 함): 기기 행 2줄. 실제 기기 수와 무관하게
                // 채널 그리드를 "기기 2개 선택 상태" 높이로 고정하기 위한 레퍼런스(사용자 확정).
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    repeat(2) {
                        HomeDeviceRow(
                            name = "",
                            kind = DeviceKind.MOUSE,
                            connected = false,
                            followRunning = false,
                            featureReady = false,
                            showChip = false,
                            tablet = true,
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            val gap = 24.dp.roundToPx()
            val devicesWidth = 300.dp.roundToPx().coerceAtMost(constraints.maxWidth)
            val devices = measurables[0].measure(
                Constraints(minWidth = devicesWidth, maxWidth = devicesWidth),
            )
            val gridWidth = (constraints.maxWidth - devicesWidth - gap).coerceAtLeast(0)
            // 채널 그리드 높이는 실제 기기 수와 무관하게 "기기 행 2줄" 높이로 고정한다(사용자 확정).
            // measurables[2] = 기기 행 2줄 레퍼런스(측정 전용). 극단적 폰트 배율에서 타일 내용이
            // 더 커지는 경우에도 안 잘리게 그리드 intrinsic을 안전 바닥값으로 함께 둔다.
            val twoRowsHeight = measurables[2]
                .measure(Constraints(minWidth = devicesWidth, maxWidth = devicesWidth)).height
            val gridHeight = maxOf(twoRowsHeight, measurables[1].minIntrinsicHeight(gridWidth))
            val grid = measurables[1].measure(Constraints.fixed(gridWidth, gridHeight))
            // weight(1f)가 준 최소 높이(남은 공간 전체)보다 작게 보고하면 Compose가 내용을 세로 가운데에 놓아
            // 제목 줄과 목록 사이가 벌어진다(태블릿 실측 약 120dp). 최소 높이까지 채워 보고하고 위에서부터 놓는다.
            val height = maxOf(devices.height, grid.height).coerceAtLeast(constraints.minHeight)
            layout(constraints.maxWidth, height) {
                devices.placeRelative(0, 0)
                grid.placeRelative(devicesWidth + gap, 0)
                // measurables[2]는 배치하지 않음(높이 측정 전용)
            }
        }
    }

    RenameChannelPopup(
        editingChannel = editingChannel,
        anchorBounds = renameAnchor,
        currentLabel = { state.labels[it] ?: context.getString(R.string.channel_label, it) },
        onSave = { channel, label -> viewModel.setLabel(channel, label) },
        onDismiss = { editingChannel = null },
    )
}
