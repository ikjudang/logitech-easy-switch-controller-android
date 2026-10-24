package com.easyswitchcontroller.ui.log

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.easyswitchcontroller.R
import com.easyswitchcontroller.data.ConnectionLog
import com.easyswitchcontroller.ui.AppToast
import com.easyswitchcontroller.ui.components.LogPanel
import com.easyswitchcontroller.ui.theme.AppIcons
import com.easyswitchcontroller.ui.theme.SpaceMono
import com.easyswitchcontroller.ui.theme.Tokens

/** 연결 로그 전용 페이지(폰, 핸드오프 C3): 뒤로 + 타이틀 + 건수 + 터미널 패널. */
@Composable
fun LogScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val entries by ConnectionLog.entries.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 18.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Tokens.Card, RoundedCornerShape(11.dp))
                    .border(1.dp, Tokens.Border, RoundedCornerShape(11.dp))
                    .clickable { onBack() },
            ) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Tokens.Text,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = stringResource(R.string.log_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.015).em,
                color = Tokens.Text,
                modifier = Modifier.padding(start = 12.dp),
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
        FirmwareReturnProbeEntry(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        LogPanel(
            entries = entries,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
    }
}

/**
 * 로그 보기의 내보내기 버튼: 저장 창을 띄워, 사용자가 고른 위치·이름의 파일에 로그를 쓴다.
 * adb 없이 기록을 꺼낼 수 있는 경로라 로그가 보이는 화면마다 둔다. 자동 저장 폴더 설정은 건드리지 않는다.
 */
@Composable
fun ExportLogButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch { saveConnectionLog(context, uri) }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(Tokens.Card, shape)
            .border(1.dp, Tokens.Border, shape)
            .clickable {
                if (ConnectionLog.entries.value.isEmpty()) AppToast.show(context, R.string.log_export_empty)
                else saver.launch(ConnectionLog.exportFileName())
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = stringResource(R.string.log_export),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Tokens.Text2,
        )
    }
}
