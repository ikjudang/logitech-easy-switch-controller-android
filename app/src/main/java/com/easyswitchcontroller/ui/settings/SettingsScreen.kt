package com.easyswitchcontroller.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.easyswitchcontroller.R
import com.easyswitchcontroller.data.ConnectionLog
import com.easyswitchcontroller.data.LogFolderSink
import com.easyswitchcontroller.data.SelectionStore
import com.easyswitchcontroller.ui.AppToast
import com.easyswitchcontroller.ui.components.AppVersionLabel
import com.easyswitchcontroller.ui.components.IconTile
import com.easyswitchcontroller.ui.components.LogPanel
import com.easyswitchcontroller.ui.log.ExportLogButton
import com.easyswitchcontroller.ui.log.FirmwareReturnProbeEntry
import com.easyswitchcontroller.ui.log.exportConnectionLogToFolder
import com.easyswitchcontroller.ui.components.ScreenHeader
import com.easyswitchcontroller.ui.components.StatusDot
import com.easyswitchcontroller.ui.components.WarnMark
import com.easyswitchcontroller.ui.openOverlaySettings
import com.easyswitchcontroller.ui.theme.AppIcons
import com.easyswitchcontroller.ui.theme.SpaceMono
import com.easyswitchcontroller.ui.theme.Tokens

/** 설정(폰, 핸드오프 C0/C5): 다른 앱 위에 표시·연결 로그·언어 카드 + 하단 버전. */
@Composable
fun SettingsScreen(
    overlayOn: Boolean,
    onOpenLog: () -> Unit,
) {
    Column(Modifier.fillMaxSize().clearSettingsInputFocusOnOutsidePress()) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            tablet = false,
            modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 16.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .clearInputFocusOnUserScroll()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            OverlayCard(overlayOn = overlayOn, tablet = false)
            FloatingButtonCard(overlayOn = overlayOn, tablet = false)
            KeepMouseCard(tablet = false)
            LogCard(tablet = false, selectedOnTablet = false, onClick = onOpenLog)
            LogFolderCard(tablet = false)
            LanguageCard(tablet = false)
            // 폰엔 사이드 레일이 없어 버전을 마지막 카드에서 일정 거리에 둔다(사용자 확정) — 카드와 함께 스크롤된다.
            AppVersionLabel(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = FOOTER_GAP, bottom = 14.dp)
                    .navigationBarsPadding(),
            )
        }
    }
}

/**
 * 설정(태블릿, 핸드오프 E): 좌측 설정 카드 열 + [logOpen]이면 우측에 연결 로그 페인.
 * 연결 로그 카드를 탭하면 페이지 이동 대신 우측 페인이 토글된다(마스터-디테일).
 *
 * [split]이 false면(폴드를 펼친 폭) 좌우로 쪼개지 않고 카드 열이 전체 폭을 쓴다. 이때 연결 로그는
 * 우측 페인이 아니라 폰과 같은 하위 화면으로 열리므로, 로그 카드는 선택 강조를 하지 않는다.
 */
@Composable
fun SettingsTabletContent(
    overlayOn: Boolean,
    logOpen: Boolean,
    split: Boolean,
    onToggleLog: () -> Unit,
) {
    val entries by ConnectionLog.entries.collectAsState()
    Column(Modifier.fillMaxSize().clearSettingsInputFocusOnOutsidePress()) {
        ScreenHeader(
            title = stringResource(R.string.settings_title),
            tablet = true,
            modifier = Modifier.padding(bottom = 22.dp),
        )
        if (!split) {
            SettingsCardColumn(
                overlayOn = overlayOn,
                logSelected = false,
                onToggleLog = onToggleLog,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            return@Column
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.weight(1f),
        ) {
            // 좌측 열은 로그 펼침 여부와 무관하게 항상 절반(weight 1:1) — 카드 폭이 변하지 않고
            // 태블릿 크기에 적응형(사용자 확정). 우측은 접힘 상태에서 빈 슬롯.
            SettingsCardColumn(
                overlayOn = overlayOn,
                logSelected = logOpen,
                onToggleLog = onToggleLog,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            if (logOpen) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.log_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Tokens.Text,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.settings_log_count, entries.size),
                            fontFamily = SpaceMono,
                            fontSize = 11.sp,
                            color = Tokens.TextMuted,
                        )
                        Spacer(Modifier.width(10.dp))
                        ExportLogButton()
                    }
                    FirmwareReturnProbeEntry(Modifier.padding(bottom = 12.dp))
                    LogPanel(
                        entries = entries,
                        fontSize = 12.sp,
                        radius = 16.dp,
                        innerPadding = 16.dp,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            } else {
                // 접힘: 우측 절반은 빈 슬롯 — 좌측 카드 폭을 고정하기 위한 자리 차지
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 태블릿·폴드 공용 설정 카드 열 — 좌우 분할이든 1열이든 카드 구성과 순서는 같다. */
@Composable
private fun SettingsCardColumn(
    overlayOn: Boolean,
    logSelected: Boolean,
    onToggleLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 넘치면 열 전체가 스크롤된다. 버전 표시는 태블릿에선 사이드 레일 맨 아래에 있다(사용자 확정).
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.clearInputFocusOnUserScroll().verticalScroll(rememberScrollState()),
    ) {
        OverlayCard(overlayOn = overlayOn, tablet = true)
        FloatingButtonCard(overlayOn = overlayOn, tablet = true)
        KeepMouseCard(tablet = true)
        LogCard(tablet = true, selectedOnTablet = logSelected, onClick = onToggleLog)
        LogFolderCard(tablet = true)
        LanguageCard(tablet = true)
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * 로그 저장 카드: 저장 켜기·끄기, 저장 폴더 선택·해제, 내보내기.
 *
 * 저장을 끄면 앱이 로그를 어떤 파일에도 쓰지 않는다(내부 파일·지정 폴더 모두) — 다른 사람에게 앱을 줄 때
 * 기록이 저절로 쌓이지 않게 하려는 것이다(사용자 확정, 새 설치는 꺼짐으로 시작). 로그 화면에는 이번 실행의
 * 기록만 보인다. 이 카드의 내보내기는 지정 폴더에 지금 바로 쓰고(폴더가 없으면 먼저 고르게 한다), 저장을
 * 꺼 둬도 사용자가 누른 내보내기는 쓴다. 로그 보기의 내보내기는 저장 창으로 따로 고른 파일에 쓴다.
 *
 * 폴더를 고르면 앱이 그 폴더에 연결 로그를 상시 저장한다. 안드로이드에서 구글 드라이브는 선택기에
 * 저장소처럼 붙으므로, 드라이브 폴더를 고르면 그 파일이 PC로 그대로 내려온다.
 */
@Composable
private fun LogFolderCard(tablet: Boolean) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(ConnectionLog.isSavingEnabled(context)) }
    var folderUri by remember { mutableStateOf(LogFolderSink.getFolder(context)) }
    var label by remember { mutableStateOf<String?>(null) }
    // 폴더 이름은 저장소 앱(드라이브 등)에 물어봐야 해서 메인 스레드 밖에서 읽는다.
    LaunchedEffect(folderUri) {
        label = if (folderUri == null) null else withContext(Dispatchers.IO) { LogFolderSink.folderLabel(context) }
    }
    // 폴더 없이 '로그 내보내기'를 누르면 폴더부터 고르게 하고, 고르면 바로 내보낸다(사용자 확정).
    // 키보드가 붙거나 떨어지면 액티비티가 다시 만들어지므로, 선택 창에 다녀오는 동안 잊지 않게 저장한다.
    var exportAfterPick by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val export = exportAfterPick
        exportAfterPick = false
        if (uri == null) return@rememberLauncherForActivityResult
        if (LogFolderSink.setFolder(context, uri)) {
            folderUri = LogFolderSink.getFolder(context)
            if (export) scope.launch { exportConnectionLogToFolder(context) }
            else if (saving) scope.launch {
                if (!ConnectionLog.snapshotNow().await()) AppToast.show(context, R.string.log_save_failed)
            }
        } else {
            AppToast.show(context, R.string.settings_log_folder_failed)
        }
    }
    val shape = RoundedCornerShape(if (tablet) 16.dp else 18.dp)
    val chosen = folderUri != null
    val toggleLabel = stringResource(R.string.settings_log_save)
    val titleSize = if (tablet) 15.sp else 14.5.sp
    val descSize = if (tablet) 12.sp else 11.5.sp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tokens.Card, shape)
            .border(1.dp, Tokens.Border, shape),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().border(1.dp, Tokens.Border, shape)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                modifier = Modifier.weight(1f).height(cardHeight(tablet))
                    .roundedMenuHover(shape).clickable { expanded = !expanded }.padding(cardPadding(tablet)),
            ) {
                IconTile(size = if (tablet) 40.dp else 38.dp, radius = 12.dp,
                    background = if (saving) Tokens.PointTintIconBg else Tokens.Inset) {
                    Icon(AppIcons.Folder, null, tint = if (saving) Tokens.PointTintText else Tokens.Text2,
                        modifier = Modifier.size(20.dp))
                }
                Text(stringResource(R.string.settings_log_save), fontSize = titleSize,
                    fontWeight = FontWeight.Bold, color = Tokens.Text, modifier = Modifier.weight(1f))
                Icon(if (expanded) AppIcons.ChevronUp else AppIcons.ChevronDown,
                    stringResource(if (expanded) R.string.settings_collapse else R.string.settings_expand),
                    tint = Tokens.Text2, modifier = Modifier.size(16.dp))
            }
            Box(Modifier.height(cardHeight(tablet)).roundedMenuHover(shape).toggleable(saving, role = Role.Switch) { enabled ->
                saving = enabled
                ConnectionLog.setSavingEnabled(context, saving)
            }.semantics { contentDescription = toggleLabel }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                SizeStableToggle(on = saving)
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                // 2행: 저장 폴더 — 선택(변경)·해제.
                LogSubRow(tablet) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_log_folder),
                            fontSize = titleSize,
                            fontWeight = FontWeight.Bold,
                            color = Tokens.Text,
                        )
                        // 설명 문장 없이 고른 폴더 이름만 보인다(사용자 확정) — 맞는 폴더인지 확인하는 용도다.
                        Text(
                            text = if (chosen) label ?: "…" else stringResource(R.string.settings_log_folder_unset),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            fontSize = descSize,
                            color = if (chosen && saving) Tokens.PointTintText else Tokens.Text2,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    CardActionButton(
                        text = stringResource(if (chosen) R.string.settings_log_folder_change else R.string.settings_log_folder_pick),
                        onClick = { picker.launch(null) },
                    )
                    if (chosen) {
                        CardActionButton(
                            text = stringResource(R.string.settings_log_folder_clear),
                            onClick = {
                                LogFolderSink.clearFolder(context)
                                folderUri = null
                                AppToast.show(context, R.string.settings_log_folder_cleared)
                            },
                        )
                    }
                }
                // 3행: 내보내기 — 지정 폴더에 지금 바로 쓴다. 저장을 꺼 둬도 이번 실행의 기록은 꺼낼 수 있다.
                LogSubRow(tablet) {
                    Text(
                        text = stringResource(R.string.settings_log_export),
                        fontSize = titleSize,
                        fontWeight = FontWeight.Bold,
                        color = Tokens.Text,
                        modifier = Modifier.weight(1f),
                    )
                    CardActionButton(
                        text = stringResource(R.string.log_export),
                        onClick = {
                            if (folderUri != null) scope.launch { exportConnectionLogToFolder(context) }
                            else {
                                exportAfterPick = true
                                picker.launch(null)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * 로그 카드의 아래 행 — 맨 윗줄(로그 자동 저장)과 높이·여백을 맞추고, 아이콘 자리만큼 들여
 * 세 행의 제목이 같은 세로선에서 시작하게 한다(사용자 확정).
 */
@Composable
private fun LogSubRow(tablet: Boolean, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight(tablet))
            .padding(cardPadding(tablet)),
    ) {
        // 아이콘(40·38dp) + 맨 윗줄의 간격(13dp)에서 이 행의 간격(8dp)을 뺀 만큼 비운다.
        Spacer(Modifier.width((if (tablet) 40.dp else 38.dp) + 5.dp))
        content()
    }
}

@Composable
private fun CardDivider() {
    Box(Modifier.fillMaxWidth().heightIn(min = 1.dp, max = 1.dp).background(Tokens.Border))
}

/** 카드 안의 작은 동작 버튼 — 로그 내보내기 버튼과 같은 모양이다. */
@Composable
private fun CardActionButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(Tokens.Card, shape)
            .border(1.dp, Tokens.Border, shape)
            .roundedMenuHover(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Tokens.Text2,
        )
    }
}

/**
 * 크기가 변하지 않는 토글 스위치.
 *
 * Material3 `Switch`를 쓰지 않는다 — M3는 동그라미 지름을 손잡이 **위치에 따라 보간**해서
 * 꺼짐 16dp ↔ 켜짐 24dp로 **크기가 변한다.** `thumbContent`를 넘겨 고정하는 우회가 있지만
 * 그건 내부 구현에 기댄 것이라 실제로 먹지 않았다(실측). 직접 그리면 변할 여지가 없다.
 *
 * 켜짐·꺼짐은 **크기가 아니라 흰색의 진하기로만** 구분한다(사용자 확정).
 * 클릭은 받지 않는다 — 카드 전체가 클릭을 받으므로 여기서 또 받으면 이중 토글이 된다.
 */
@Composable
internal fun SizeStableToggle(on: Boolean) {
    val trackWidth = 46.dp
    val trackHeight = 28.dp
    val thumbSize = 22.dp // 켜짐·꺼짐 공통. 큰 쪽 기준이다.
    val gap = 3.dp

    val thumbOffset by animateDpAsState(
        targetValue = if (on) trackWidth - thumbSize - gap else gap,
        label = "toggleThumbOffset",
    )
    val trackColor by animateColorAsState(
        targetValue = if (on) Tokens.Point else Tokens.Inset,
        label = "toggleTrackColor",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (on) Color.White else Color.White.copy(alpha = 0.38f),
        label = "toggleThumbColor",
    )
    val borderColor by animateColorAsState(
        targetValue = if (on) Tokens.Point else Tokens.BorderStrong,
        label = "toggleBorderColor",
    )
    val trackShape = RoundedCornerShape(trackHeight / 2)

    Box(
        modifier = Modifier
            .size(width = trackWidth, height = trackHeight)
            .clip(trackShape)
            .background(trackColor, trackShape)
            .border(1.dp, borderColor, trackShape),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(thumbSize)
                .background(thumbColor, CircleShape),
        )
    }
}

/**
 * 마우스 상시 연결 토글(실험).
 *
 * 켜면 앱이 선택된 마우스와 BLE 연결을 유지하고 feature index까지 미리 받아 둬서, Easy-Switch
 * 키를 눌렀을 때 전환이 write 한 번으로 끝난다. 링크를 계속 붙잡는 것이 마우스 입력에 영향을
 * 주는지 확인된 바가 없어 기본은 꺼짐이고, 사용자가 직접 켜고 끄며 비교하도록 노출한다.
 * 저장 즉시 따라가기 서비스의 팔로우 매니저가 반영한다([SelectionStore.revision]).
 */
@Composable
private fun KeepMouseCard(tablet: Boolean) {
    val context = LocalContext.current
    val store = remember { SelectionStore(context) }
    var on by remember { mutableStateOf(store.loadKeepMouseConnected()) }
    val shape = RoundedCornerShape(if (tablet) 16.dp else 18.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight(tablet))
            .clip(shape)
            .background(Tokens.Card, shape)
            .border(1.dp, Tokens.Border, shape)
            .roundedMenuHover(shape)
            .clickable {
                on = !on
                store.saveKeepMouseConnected(on)
            }
            .padding(cardPadding(tablet)),
    ) {
        IconTile(
            size = if (tablet) 40.dp else 38.dp,
            radius = 12.dp,
            background = if (on) Tokens.PointTintIconBg else Tokens.Inset,
        ) {
            Icon(
                imageVector = AppIcons.Mouse,
                contentDescription = null,
                tint = if (on) Tokens.PointTintText else Tokens.Text2,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_keep_mouse),
                fontSize = if (tablet) 15.sp else 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.Text,
            )
        }
        // 클릭은 카드 전체(Row)가 받는다 — 토글 자체는 그림만 그린다(이중 토글 방지).
        SizeStableToggle(on = on)
    }
}

/** "다른 앱 위에 표시" 상태 카드: 허용됨(그린 필) / 허용 필요(앰버 경고 카드). 탭하면 시스템 설정. */
@Composable
private fun OverlayCard(overlayOn: Boolean, tablet: Boolean) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(if (tablet) 16.dp else 18.dp)
    val cardBg = if (overlayOn) Tokens.Card else Tokens.WarnCardBg
    val cardBorder = if (overlayOn) Tokens.Border else Tokens.WarnBorder
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight(tablet))
            .clip(shape)
            .background(cardBg, shape)
            .border(1.dp, cardBorder, shape)
            .roundedMenuHover(shape)
            .clickable { openOverlaySettings(context) }
            .padding(cardPadding(tablet)),
    ) {
        IconTile(
            size = if (tablet) 40.dp else 38.dp,
            radius = 12.dp,
            background = if (overlayOn) Tokens.Success.copy(alpha = 0.14f) else Tokens.Warn.copy(alpha = 0.16f),
        ) {
            if (overlayOn) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(22.dp).background(Tokens.Success, CircleShape),
                ) {
                    Icon(
                        imageVector = AppIcons.Check,
                        contentDescription = null,
                        tint = Tokens.Bg,
                        modifier = Modifier.size(12.dp),
                    )
                }
            } else {
                WarnMark(22.dp, 13.sp)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_overlay),
                fontSize = if (tablet) 15.sp else 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.Text,
            )
        }
        // 상태 필
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .background(
                    if (overlayOn) Tokens.Success.copy(alpha = 0.14f) else Tokens.Warn.copy(alpha = 0.16f),
                    RoundedCornerShape(20.dp),
                )
                .padding(
                    horizontal = if (tablet) 12.dp else 11.dp,
                    vertical = if (tablet) 6.dp else 5.dp,
                ),
        ) {
            StatusDot(if (overlayOn) Tokens.Success else Tokens.Warn)
            Text(
                text = stringResource(
                    if (overlayOn) R.string.settings_state_granted else R.string.settings_state_needed,
                ),
                fontSize = if (tablet) 12.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (overlayOn) Tokens.Success else Tokens.WarnText,
            )
        }
    }
}

/** 연결 로그 카드. 태블릿에서 [selectedOnTablet]이면 선택 강조(우측 페인 표시 중). */
@Composable
private fun LogCard(tablet: Boolean, selectedOnTablet: Boolean, onClick: () -> Unit) {
    val entries by ConnectionLog.entries.collectAsState()
    val shape = RoundedCornerShape(if (tablet) 16.dp else 18.dp)
    val selected = tablet && selectedOnTablet
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight(tablet))
            .clip(shape)
            .background(if (selected) Tokens.PointTintBg else Tokens.Card, shape)
            .let {
                if (selected) it.border(1.5.dp, Tokens.Point, shape)
                else it.border(1.dp, Tokens.Border, shape)
            }
            .roundedMenuHover(shape)
            .clickable { onClick() }
            .padding(cardPadding(tablet)),
    ) {
        IconTile(
            size = if (tablet) 40.dp else 38.dp,
            radius = 12.dp,
            background = if (selected) Tokens.PointTintIconBg else Tokens.Inset,
        ) {
            Icon(
                imageVector = AppIcons.Clock,
                contentDescription = null,
                tint = if (selected) Tokens.PointTintText else Tokens.Text2,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_log),
                fontSize = if (tablet) 15.sp else 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.Text,
            )
        }
        Text(
            text = stringResource(R.string.settings_log_count, entries.size),
            fontFamily = SpaceMono,
            fontSize = if (tablet) 11.sp else 10.sp,
            color = if (selected) Color(0xFFA99BD6) else Tokens.TextMuted,
        )
        Icon(
            // 페인 열림 = 닫기 방향(<), 닫힘 = 열기 방향(>)
            imageVector = if (selected) AppIcons.Back else AppIcons.ChevronRight,
            contentDescription = null,
            tint = if (selected) Tokens.PointTintText else Tokens.Text2,
            modifier = Modifier.size(12.dp),
        )
    }
}

/** 언어 카드: 리스트 선택 드롭다운(한글/English) — 토글 아님(핸드오프 확정). */
@Composable
private fun LanguageCard(tablet: Boolean) {
    val shape = RoundedCornerShape(if (tablet) 16.dp else 18.dp)
    var menuOpen by remember { mutableStateOf(false) }
    // 명시 선택이 없으면(최초) 현재 리소스 로케일로 표시
    val configLang = LocalConfiguration.current.locales[0].language
    val appLocales = AppCompatDelegate.getApplicationLocales()
    val isEnglish = if (!appLocales.isEmpty) {
        appLocales[0]?.language == "en"
    } else {
        configLang == "en"
    }
    val textMeasurer = rememberTextMeasurer()
    val englishLabel = stringResource(R.string.settings_lang_en)
    val textStyle = LocalTextStyle.current
    val selectedEnglishWidth = textMeasurer.measure(englishLabel,
        style = textStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)).size.width
    val optionEnglishWidth = textMeasurer.measure(englishLabel,
        style = textStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)).size.width
    val selectorWidth = with(LocalDensity.current) {
        // 버튼: 양옆 12dp + 간격 9dp + 화살표 10dp. 목록: 양옆 16dp.
        maxOf(selectedEnglishWidth + 43.dp.roundToPx(), optionEnglishWidth + 32.dp.roundToPx()).toDp()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight(tablet))
            .background(Tokens.Card, shape)
            .border(1.dp, Tokens.Border, shape)
            .padding(cardPadding(tablet)),
    ) {
        IconTile(size = if (tablet) 40.dp else 38.dp, radius = 12.dp) {
            Icon(
                imageVector = AppIcons.Globe,
                contentDescription = null,
                tint = Tokens.Text2,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = stringResource(R.string.settings_language),
            fontSize = if (tablet) 15.sp else 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = Tokens.Text,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(selectorWidth)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Tokens.Inset, RoundedCornerShape(10.dp))
                    .border(1.dp, Tokens.BorderStrong, RoundedCornerShape(10.dp))
                    .roundedMenuHover(RoundedCornerShape(10.dp))
                    .clickable { menuOpen = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    text = stringResource(if (isEnglish) R.string.settings_lang_en else R.string.settings_lang_ko),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Tokens.TextBody,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (menuOpen) AppIcons.ChevronUp else AppIcons.ChevronDown,
                    contentDescription = null,
                    tint = Tokens.Text2,
                    modifier = Modifier.size(10.dp),
                )
            }
            LanguageSelectionPopup(
                expanded = menuOpen,
                isEnglish = isEnglish,
                width = selectorWidth,
                onDismiss = { menuOpen = false },
                onSelect = { english ->
                    menuOpen = false
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(if (english) "en" else "ko"),
                    )
                },
            )
        }
    }
}

/** 상위 설정·펼친 하위 메뉴의 공통 행 높이. */
internal fun cardHeight(tablet: Boolean) = if (tablet) 70.dp else 64.dp

/** 카드 내부 패딩 — 세로는 최소 높이+중앙 정렬이 담당하므로 좁게 유지한다. */
private fun cardPadding(tablet: Boolean) = PaddingValues(
    horizontal = if (tablet) 16.dp else 14.dp,
    vertical = 8.dp,
)

/** 폰에서 마지막 카드와 버전 표기 사이 추가 간격 — 카드 사이 간격(10dp)에 더해져 24dp가 된다. */
private val FOOTER_GAP = 14.dp
