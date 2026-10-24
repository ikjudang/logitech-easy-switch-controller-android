package com.easyswitchcontroller.ui

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.easyswitchcontroller.data.BondedDeviceRepository
import com.easyswitchcontroller.data.SelectionStore
import com.easyswitchcontroller.device.DeviceKind
import com.easyswitchcontroller.di.ServiceLocator
import com.easyswitchcontroller.ui.channel.ChannelRuntime
import com.easyswitchcontroller.service.FollowStatus
import com.easyswitchcontroller.ui.components.AppTab
import com.easyswitchcontroller.ui.components.BottomNavBar
import com.easyswitchcontroller.ui.components.SideRail
import com.easyswitchcontroller.ui.components.SideRailWidth
import com.easyswitchcontroller.ui.devicelist.DeviceListScreen
import com.easyswitchcontroller.ui.home.HomeScreen
import com.easyswitchcontroller.ui.home.HomeTabletContent
import com.easyswitchcontroller.ui.log.LogScreen
import com.easyswitchcontroller.ui.permissions.BluetoothConnectGate
import com.easyswitchcontroller.ui.settings.SettingsScreen
import com.easyswitchcontroller.ui.settings.SettingsTabletContent
import com.easyswitchcontroller.ui.theme.EasySwitchControllerTheme
import com.easyswitchcontroller.ui.theme.Tokens

/**
 * 태블릿 화면(설정·기기 선택)을 좌우 절반으로 쪼갤 수 있는 최소 콘텐츠 폭(사이드레일을 뺀 폭).
 *
 * 이 폭을 절반으로 나누면 카드 하나에 약 350dp가 남는데, 설정 카드 안은 아이콘 40 + 상태 필 약 75 +
 * 간격·패딩 58을 먼저 가져가므로 그보다 좁아지면 제목·설명이 들어갈 자리가 사라진다.
 * Z Fold 6를 펼치면 사이드레일을 뺀 폭이 세로 550dp·가로 632dp(실측)라 정확히 그 구간이다.
 */
private val SplitPanesMinWidth = 800.dp

/**
 * 최상위 컴포저블: 권한 게이트 → 홈/기기 선택/설정(+연결 로그).
 *
 * 반응형(핸드오프 확정): sw600dp 미만 = 폰(하단 내비, 단일 열, 로그는 전용 페이지),
 * 이상 = 태블릿(좌측 레일, 다열, 설정 마스터-디테일 로그).
 *
 * 선택은 [SelectionStore]에 영속화된다. 연결 뱃지는 전역 모니터(마우스·일반)와
 * [FollowStatus](키보드 — 팔로우 매니저의 GATT 상태)로 표시하고, 전환 대상 판정은
 * 각 기기 연결 시점의 GattConnection **실시간 ACL 가드**가 담당한다(미연결은 즉시 실패).
 */
@Composable
fun AppRoot() {
    EasySwitchControllerTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Tokens.Bg) {
            BluetoothConnectGate {
                AppScaffold()
            }
        }
    }
}

@Composable
private fun AppScaffold() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember { BondedDeviceRepository(context) }
    val store = remember { SelectionStore(context) }
    // 프로세스 전역 싱글톤 — 재생성돼도 캐시된 ViewModel과 항상 같은 인스턴스를 본다.
    val monitor = remember { ServiceLocator.connectedDevicesMonitor(appContext) }
    val connectedAddresses by monitor.connectedAddresses.collectAsState()
    val followRunning by FollowStatus.running.collectAsState()
    val followKeyboards by FollowStatus.connectedKeyboards.collectAsState()
    val overlayOn by rememberOverlayGranted()

    val initialSnapshot = remember { repository.snapshot() }
    // 본딩이 사라진 주소(기기 재등록 시 MAC이 바뀜)를 걸러낸다 — 안 하면 유령이 "N개 선택"을 부풀린다.
    val initialSelected = remember { store.reconcile(initialSnapshot) }
    val initialDevices = remember { initialSnapshot.candidates(initialSelected) }

    var devices by remember { mutableStateOf(initialDevices) }
    var selected by remember { mutableStateOf(initialSelected) }
    // 저장된 선택이 유효하면 홈에서, 아니면(최초 실행) 기기 선택에서 시작한다.
    var tab by rememberSaveable {
        mutableStateOf(
            if (initialSelected.isNotEmpty() && initialDevices.any { it.address in initialSelected }) {
                AppTab.Home
            } else {
                AppTab.Devices
            },
        )
    }
    // 폰: 연결 로그 전용 페이지 표시 여부. 태블릿: 설정 우측 페인 펼침 여부.
    var logOpen by rememberSaveable { mutableStateOf(false) }

    fun refreshDevices() {
        val snapshot = repository.snapshot()
        selected = store.reconcile(snapshot)
        // 권한/어댑터 일시 오류에는 마지막으로 확인한 목록도 보존한다.
        if (snapshot.complete) devices = snapshot.candidates(selected)
        monitor.refresh()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshDevices()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        SelectionStore.revision.collect { refreshDevices() }
    }

    // OS에서 기기를 지우거나 다시 등록하면(본딩 변화) 목록·선택을 즉시 다시 읽는다 —
    // 목록은 앱 시작 때 한 번만 읽으므로, 이게 없으면 삭제된 기기가 유령으로 남고
    // 재등록된 새 MAC은 수동 새로고침 전까지 안 보인다(실측 버그). 액티비티가 죽어 있는
    // 동안의 변화는 재생성 시의 초기 읽기가 자연히 반영한다.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                refreshDevices()
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothDevice.ACTION_CLASS_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        // 시스템 브로드캐스트라 EXPORTED로 등록해야 수신된다(Android 14+ 필수 플래그).
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // 키보드는 팔로우 매니저가 GATT를 상시 소유해 ACL이 항상 연결됨으로 나오므로,
    // 매니저가 켜져 있으면 그의 실제 GATT 상태로 판정한다(홈 화면과 동일 기준).
    val effectiveConnected = devices.mapNotNull { info ->
        val isKeyboard = ServiceLocator.guessKind(info) == DeviceKind.KEYBOARD
        val connected =
            if (isKeyboard && followRunning) info.address in followKeyboards
            else info.address in connectedAddresses
        info.address.takeIf { connected }
    }.toSet()

    val hasSelection = selected.isNotEmpty() && devices.any { it.address in selected }

    // 홈과 플로팅 버튼은 한 전환 실행기를 공유한다.
    val chosen = devices.filter { it.address in selected }
    val channels = remember { ChannelRuntime.get(appContext) }
    LaunchedEffect(chosen) {
        channels.updateDevices(chosen.map { ServiceLocator.device(appContext, it, ServiceLocator.guessKind(it)) })
    }
    val channelViewModel = channels.takeIf { hasSelection }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600

    // 폴드를 펼친 폭은 태블릿 기준(600dp)을 넘지만, 사이드레일을 빼고 다시 좌우로 쪼개면
    // 카드 하나에 230dp 남짓이라 글자가 잘린다. 그래서 "쪼갤지"는 기기 종류가 아니라
    // 사이드레일을 뺀 실제 콘텐츠 폭으로 판단한다 — 폴드 펼침은 1열, 태블릿은 종전대로 좌우 분할.
    // 설정·기기 선택이 같은 판정을 쓴다(두 화면의 카드 폭을 일치시키는 규칙 때문).
    val splitPanes = isTablet &&
        configuration.screenWidthDp.dp - SideRailWidth >= SplitPanesMinWidth

    // 뒤로가기: 태블릿은 로그 페인 닫기 → 홈 순서. 폰의 로그 페이지는 LogScreen이 자체 처리(→설정).
    BackHandler(enabled = tab == AppTab.Settings && (isTablet || !logOpen)) {
        if (isTablet && logOpen) logOpen = false else tab = AppTab.Home
    }

    val deviceList: @Composable (Boolean) -> Unit = { tablet ->
        DeviceListScreen(
            devices = devices,
            selected = selected,
            connectedAddresses = effectiveConnected,
            cancelEnabled = hasSelection,
            tablet = tablet,
            split = splitPanes,
            onCancel = { tab = AppTab.Home },
            onToggle = { info ->
                val next = if (info.address in selected) {
                    selected - info.address
                } else {
                    selected + info.address
                }
                selected = next
                store.saveSelected(next, devices)
            },
            onRefresh = {
                refreshDevices()
            },
            onProceed = { if (selected.isNotEmpty()) tab = AppTab.Home },
        )
    }

    // 어떤 방향이든 상태바·컷아웃·측면 시스템바 영역으로 서식이 연장되지 않게(사용자 확정).
    // 하단은 내비 바/푸터가 navigationBarsPadding으로 개별 처리한다.
    val safeTopSides = Modifier.windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    )

    if (isTablet) {
        Row(Modifier.fillMaxSize().then(safeTopSides)) {
            SideRail(
                current = tab,
                settingsBadge = !overlayOn,
                followRunning = followRunning,
                onSelect = { selectedTab ->
                    // 로그가 하위 화면으로 열리는 폭에서는 내비로 옮길 때 로그를 닫는다(폰과 동일).
                    // 좌우 분할 태블릿에서는 페인 상태를 유지한다 — 설정으로 돌아오면 그대로 펼쳐져 있다.
                    if (!splitPanes) logOpen = false
                    tab = selectedTab
                },
            )
            Column(Modifier.weight(1f)) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        // 기기 선택도 홈·설정과 같은 좌측 정렬 프레임(사용자 확정). 폭은 DeviceListScreen에서 캡.
                        AppTab.Devices -> Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp, vertical = 30.dp),
                        ) {
                            deviceList(true)
                        }
                        // 좌우로 쪼갤 수 없는 폭(폴드 펼침)에서는 연결 로그를 우측 페인이 아니라
                        // 하위 화면으로 띄운다 — 사이드레일은 그대로 두고 뒤로가기로 설정에 돌아온다(사용자 확정).
                        AppTab.Settings -> if (logOpen && !splitPanes) {
                            LogScreen(onBack = { logOpen = false })
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 32.dp, vertical = 30.dp),
                            ) {
                                SettingsTabletContent(
                                    overlayOn = overlayOn,
                                    logOpen = logOpen,
                                    split = splitPanes,
                                    onToggleLog = { logOpen = !logOpen },
                                )
                            }
                        }
                        AppTab.Home -> Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp, vertical = 30.dp),
                        ) {
                            if (channelViewModel != null) {
                                HomeTabletContent(
                                    viewModel = channelViewModel,
                                    overlayOn = overlayOn,
                                    split = splitPanes,
                                    onOpenDevices = { tab = AppTab.Devices },
                                )
                            } else {
                                deviceList(true)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().then(safeTopSides)) {
            Box(Modifier.weight(1f)) {
                when {
                    logOpen -> LogScreen(onBack = { logOpen = false })
                    tab == AppTab.Devices -> deviceList(false)
                    tab == AppTab.Settings -> SettingsScreen(
                        overlayOn = overlayOn,
                        onOpenLog = { logOpen = true },
                    )
                    else -> {
                        if (channelViewModel != null) {
                            HomeScreen(
                                viewModel = channelViewModel,
                                overlayOn = overlayOn,
                                onOpenDevices = { tab = AppTab.Devices },
                            )
                        } else {
                            deviceList(false)
                        }
                    }
                }
            }
            // 하단 내비 바는 항상 유지한다 — 내비로 "기기 선택"에 진입했는데 바가 사라지지 않도록.
            BottomNavBar(
                current = if (logOpen) AppTab.Settings else tab,
                settingsBadge = !overlayOn,
                onSelect = { selectedTab ->
                    logOpen = false
                    tab = selectedTab
                },
            )
        }
    }
}
