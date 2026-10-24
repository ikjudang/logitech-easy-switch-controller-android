package com.easyswitchcontroller.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyswitchcontroller.R
import com.easyswitchcontroller.device.FirmwareProbePhase
import com.easyswitchcontroller.ui.channel.ChannelRuntime
import com.easyswitchcontroller.ui.theme.Tokens

/** 폰 로그 화면과 태블릿 로그 페인에 같은 수동 시험 진입점을 제공한다. */
@Composable
fun FirmwareReturnProbeEntry(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val model = remember { ChannelRuntime.get(context) }
    val channels by model.uiState.collectAsState()
    val probe by model.firmwareProbe.collectAsState()
    var open by rememberSaveable { mutableStateOf(false) }
    var address by rememberSaveable { mutableStateOf<String?>(null) }
    var target by rememberSaveable { mutableStateOf<Int?>(null) }
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.clip(shape).background(Tokens.Card, shape).border(1.dp, Tokens.Border, shape)
        .clickable { open = true }.padding(horizontal = 12.dp, vertical = 9.dp)) {
        Text(stringResource(R.string.firmware_probe_title), color = Tokens.Text2, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
    if (!open) return
    LaunchedEffect(Unit) { ChannelRuntime.refresh(context) }
    // 채널 상태 수집으로 연결 변화·목록 변경에도 후보를 다시 계산한다.
    val candidates = remember(channels, probe.phase) { model.firmwareProbeCandidates() }
    LaunchedEffect(candidates.map { it.address }) {
        if (!probe.running && candidates.none { it.address == address }) address = candidates.singleOrNull()?.address
    }
    val selected = candidates.firstOrNull { it.address == address }
    val dismiss = { open = false }
    AlertDialog(
        onDismissRequest = dismiss,
        containerColor = Tokens.Card,
        title = { Text(stringResource(R.string.firmware_probe_title), color = Tokens.Text) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (probe.running) {
                    Text(probe.deviceName, color = Tokens.Text, fontWeight = FontWeight.Bold)
                    Text(stringResource(when (probe.phase) {
                        FirmwareProbePhase.PREPARING -> R.string.firmware_probe_preparing
                        FirmwareProbePhase.VERIFYING -> R.string.firmware_probe_verifying
                        else -> R.string.firmware_probe_observing
                    }), color = Tokens.Text2)
                    Text(stringResource(R.string.firmware_probe_elapsed, probe.elapsedSeconds), color = Tokens.Text2)
                    Text(stringResource(R.string.firmware_probe_running_help), color = Tokens.Text2)
                } else {
                    Text(stringResource(R.string.firmware_probe_instructions), color = Tokens.Text2)
                    if (probe.phase == FirmwareProbePhase.FINISHED) {
                        Text(stringResource(R.string.firmware_probe_finished), color = Tokens.Text)
                    }
                    if (candidates.isEmpty()) Text(stringResource(R.string.firmware_probe_no_mouse), color = Tokens.Text2)
                    candidates.forEach { device ->
                        Row(Modifier.fillMaxWidth().clip(shape)
                            .selectable(selected = address == device.address, role = Role.RadioButton, onClick = { address = device.address; target = null })
                            .padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = address == device.address, onClick = null)
                            Text(device.name, color = Tokens.Text, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Text(stringResource(R.string.firmware_probe_destination), color = Tokens.Text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..3).forEach { channel ->
                            val enabled = selected != null && selected.localHostIndex?.plus(1) != channel
                            Box(Modifier.weight(1f).clip(shape)
                                .background(if (target == channel) Tokens.Point else Tokens.Inset, shape)
                                .selectable(selected = target == channel, enabled = enabled, role = Role.RadioButton, onClick = { target = channel })
                                .padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text("$channel", color = if (enabled) Tokens.Text else Tokens.TextMuted, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (probe.running) TextButton(onClick = { model.cancelFirmwareReturnProbe() }) { Text(stringResource(R.string.firmware_probe_stop)) }
            else TextButton(enabled = selected != null && target != null && !channels.busy,
                onClick = { selected?.let { device -> target?.let { model.startFirmwareReturnProbe(device.address, it) } } }) {
                Text(stringResource(R.string.firmware_probe_start))
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.firmware_probe_close)) } },
    )
}
