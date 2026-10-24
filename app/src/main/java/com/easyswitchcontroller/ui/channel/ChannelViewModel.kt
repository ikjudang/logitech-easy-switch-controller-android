package com.easyswitchcontroller.ui.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easyswitchcontroller.data.ConnectionLog
import com.easyswitchcontroller.device.DeviceKind
import com.easyswitchcontroller.device.LogiDevice
import com.easyswitchcontroller.device.SwitchResult
import com.easyswitchcontroller.hidpp.ChangeHostFeature
import com.easyswitchcontroller.hidpp.CidInfo
import com.easyswitchcontroller.hidpp.HidppConstants
import com.easyswitchcontroller.hidpp.ReprogKeysFeature
import com.easyswitchcontroller.service.FollowStatus
import com.easyswitchcontroller.transport.ConnectionState
import com.easyswitchcontroller.device.SwitchIndicator
import com.easyswitchcontroller.device.SwitchReport
import com.easyswitchcontroller.device.SwitchReportProgress
import com.easyswitchcontroller.device.DeviceSwitchStatus
import com.easyswitchcontroller.device.SwitchTransactions
import com.easyswitchcontroller.device.FirmwareReturnProbe
import com.easyswitchcontroller.device.FirmwareProbePhase
import com.easyswitchcontroller.device.FirmwareProbeState
import com.easyswitchcontroller.device.FirmwareProbeOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceUiState(
    val name: String,
    val kind: DeviceKind,
    /** UI 표시용 연결 뱃지: 마우스·일반은 ACL 모니터, 키보드는 팔로우 매니저 GATT 상태(FollowStatus) 기반. 참고용. */
    val connected: Boolean = false,
    val connection: ConnectionState = ConnectionState.Disconnected,
    /** 런타임 조회한 0x1814 feature index (null이면 미준비). */
    val featureIndex: Int? = null,
    val lastResult: String? = null,
    /** 이 기기에 연결됐을 때 조회한 슬롯(1-based). connected와 함께 사용한다. */
    val localChannel: Int? = null,
)

data class ChannelUiState(
    val devices: List<DeviceUiState> = emptyList(),
    val channelCount: Int = 3,
    /** 채널(1-based) → 사용자 라벨(예: "회사 PC"). */
    val labels: Map<Int, String> = emptyMap(),
    val busy: Boolean = false,
    /** 실패가 조용히 나는 영역이라 모든 단계를 화면 로그로 노출한다. */
    val log: List<String> = emptyList(),
) {
    val connectedLocalChannels: Set<Int> get() = devices.filter { it.connected }.mapNotNull { it.localChannel }.toSet()
    fun labelOrDefault(channel1Based: Int): String = labels[channel1Based] ?: "Easy Switch $channel1Based"
}

/**
 * 채널 화면 상태 관리(다기기).
 *
 * **연결 판정은 실시간으로** 한다: 준비·전환·진단 시 각 기기에 직접 연결을 시도하고,
 * [com.easyswitchcontroller.ble.GattConnection]의 ACL 가드가 실제 연결 여부를 판정한다
 * (미연결 기기는 즉시 실패해 간섭/연타 없음). [connectedAddresses]는 UI 뱃지 표시용 참고값이며,
 * 우리 GATT 조작으로 흔들릴 수 있어 대상 판정에는 쓰지 않는다.
 */
class ChannelViewModel(
    devices: List<LogiDevice>,
    private val connectedAddresses: StateFlow<Set<String>>,
    initialLabels: Map<Int, String> = emptyMap(),
    channelCount: Int = 3,
    private val onLabelChanged: (Int, String) -> Unit = { _, _ -> },
    private val onSwitchReport: (SwitchReport) -> Unit = {},
    private val verify: suspend (LogiDevice, Int) -> SwitchIndicator = { _, _ -> SwitchIndicator.Pending },
    private val isConnected: (LogiDevice) -> Boolean? = { null },
    private val saveFirmwareProbeLog: suspend (List<String>) -> Unit = {},
    private val firmwareProbeEvents: (LogiDevice) -> kotlinx.coroutines.flow.Flow<Boolean> = { kotlinx.coroutines.flow.emptyFlow() },
) : ViewModel() {
    private var devices = devices.toList()
    private var trackingJob: Job? = null
    private var switchJob: Job? = null
    private var firmwareProbeJob: Job? = null
    private val _firmwareProbe = MutableStateFlow(FirmwareProbeState())
    val firmwareProbe = _firmwareProbe.asStateFlow()
    private val deviceUpdateMutex = Mutex()
    private var updatingDevices = false
    private val preparationJobs = mutableMapOf<String, Job>()
    private val prepareRetryAt = mutableMapOf<String, Long>()

    private val _uiState = MutableStateFlow(
        ChannelUiState(
            devices = devices.map {
                DeviceUiState(it.name, it.kind, connected = it.address in connectedAddresses.value)
            },
            channelCount = channelCount,
            labels = initialLabels,
        ),
    )
    val uiState = _uiState.asStateFlow()

    /** 준비 진행 중인 기기 index. 준비 완료(featureIndex 확보) 후에는 다시 건드리지 않는다(라디오 부하 억제). */
    private val preparing = mutableSetOf<Int>()

    init { trackDevices() }

    /** 선택 변경 시 이전 대상의 수집/준비 작업을 끝내고 같은 ViewModel에 새 목록을 적용한다. */
    suspend fun updateDevices(next: List<LogiDevice>) = deviceUpdateMutex.withLock {
        if (devices.map { it.address } == next.map { it.address } &&
            _uiState.value.devices.map { it.name to it.kind } == next.map { it.name to it.kind }) return@withLock
        updatingDevices = true
        try {
            // 연속 선택 변경이나 화면 재생성으로 호출자가 취소돼도 감시가 반쯤 중단된 채 남지 않는다.
            withContext(NonCancellable) {
                SwitchTransactions.begin()
                trackingJob?.cancelAndJoin()
                switchJob?.cancelAndJoin()
                firmwareProbeJob?.cancelAndJoin()
                val previousPreparations = preparationJobs.values.toList()
                previousPreparations.forEach { it.cancel() }
                previousPreparations.forEach { it.join() }
                preparationJobs.clear()
                preparing.clear()
                prepareRetryAt.clear()
                val previous = devices.zip(_uiState.value.devices).associate { it.first.address to it.second }
                devices = next.toList()
                _uiState.update { state ->
                    state.copy(busy = false, devices = devices.map { device ->
                        (previous[device.address] ?: DeviceUiState(device.name, device.kind)).copy(
                            name = device.name, kind = device.kind,
                            connected = device.address in connectedAddresses.value,
                        )
                    })
                }
                trackDevices()
            }
        } finally { updatingDevices = false }
    }

    private fun trackDevices() {
        val tracked = devices.toList()
        if (tracked.isEmpty()) return
        trackingJob = viewModelScope.launch {
            tracked.forEachIndexed { index, device ->
                launch {
                    device.connectionState.collect { state ->
                        updateDeviceAt(index) { it.copy(connection = state) }
                    }
                }
            }
            launch {
                while (true) {
                    if (!_uiState.value.busy) connect()
                    delay(2_000)
                }
            }
            combine(connectedAddresses, FollowStatus.running, FollowStatus.connectedKeyboards, FollowStatus.localChannels) { acl, running, keyboards, localChannels ->
                tracked.map { device ->
                    val connected = if (device.kind == DeviceKind.KEYBOARD && running) device.address in keyboards
                        else device.address in acl
                    connected to (localChannels[device.address] ?: device.localHostIndex?.plus(1))
                }
            }.collect { flags ->
                _uiState.update { state ->
                    state.copy(devices = state.devices.mapIndexed { index, device ->
                        val observed = flags.getOrNull(index)
                        device.copy(connected = observed?.first == true, localChannel = observed?.second)
                    })
                }
                tracked.forEachIndexed { index, device ->
                    if (flags[index].first) maybePrepare(index, device)
                }
            }
        }
    }

    fun connect() {
        devices.forEachIndexed { index, device ->
            if (device.address in connectedAddresses.value) maybePrepare(index, device)
        }
    }

    private fun maybePrepare(index: Int, device: LogiDevice) {
        // 키보드는 따라가기 서비스(KeyboardFollowManager)가 GATT를 상시 소유한다. UI가 두 번째 GATT를
        // 열면 충돌("writeCharacteristic 큐잉 실패")하므로, 키보드는 준비하지 않고 연결 상태만 표시한다.
        if (device.kind == DeviceKind.KEYBOARD || _uiState.value.busy || SwitchTransactions.firmwareProbeActive) return
        val now = System.nanoTime() / 1_000_000
        if (device.switchGraceActive || now < (prepareRetryAt[device.address] ?: Long.MIN_VALUE)) return
        if (_uiState.value.devices.getOrNull(index)?.featureIndex != null) return
        synchronized(preparing) { if (!preparing.add(index)) return }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            appendLog("${device.name} 준비 중…")
            try {
                device.connect()
                val featureIndex = device.getFeatureIndex(HidppConstants.FEATURE_CHANGE_HOST)
                updateDeviceAt(index) { it.copy(featureIndex = featureIndex) }
                appendLog("${device.name}: 준비됨 (0x1814=0x%02X)".format(featureIndex), ConnectionLog.Level.Success)
                // 본인 채널 확인(사용자 확정): 지금 연결된 기기의 currentHost = 이 기기의 슬롯.
                // 주의 — 슬롯 번호는 기기마다 다를 수 있어(실측), 표시값은 "그 기기 기준" 번호다.
                runCatching {
                    val hostInfo = ChangeHostFeature.parseGetHostInfo(
                        device.request(ChangeHostFeature.buildGetHostInfo(featureIndex)),
                    )
                    device.rememberLocalHost(hostInfo.currentHost)
                    FollowStatus.rememberLocalChannel(device.address, hostInfo.currentHost + 1)
                    appendLog("${device.name}: 현재 호스트 ch${hostInfo.currentHost + 1}")
                }
                currentCoroutineContext().ensureActive()
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                appendLog("${device.name}: 연결 타임아웃 (폰에 없거나 절전일 수 있음)", ConnectionLog.Level.Dim)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                appendLog("${device.name}: ${e.message}")
            } finally {
                // 실패 경로에서도 반드시 닫는다 — 열린 GATT 핸들이 남으면 ACL을 붙잡아
                // 연결 표시가 안 꺼지고(중복 notify), 라디오 간섭 위험도 생긴다.
                withContext(NonCancellable) { runCatching { device.disconnect() } }
                prepareRetryAt[device.address] = System.nanoTime() / 1_000_000 + 5_000
                synchronized(preparing) { preparing.remove(index) }
                preparationJobs.remove(device.address)
            }
        }
        preparationJobs[device.address] = job
        job.start()
    }

    /**
     * UI 채널(1-based) 클릭. 처음부터 이 기기에 연결되지 않은 기기는 마우스든 키보드든 이번 전환 대상에서 뺀다
     * — 키보드도 두 장소 것이 함께 선택돼 있어, 빼지 않으면 없는 쪽이 매번 ×로 찍힌다.
     */
    fun onChannelClicked(channel1Based: Int) {
        if (updatingDevices || _uiState.value.busy || SwitchTransactions.firmwareProbeActive || channel1Based !in 1.._uiState.value.channelCount || devices.isEmpty()) return
        val targets = devices.filter { isConnected(it) != false }
        val host = channel1Based - 1
        val operation = SwitchTransactions.begin()
        val indicators = targets.associateWith { SwitchIndicator.Pending }.toMutableMap()
        val progress = SwitchReportProgress(channel1Based, operation, targets.associate { device ->
            device.address to DeviceSwitchStatus(device.name, device.kind, SwitchIndicator.Pending)
        }) { report ->
            if (SwitchTransactions.isCurrent(operation)) onSwitchReport(report)
        }
        switchJob?.cancel()
        switchJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(busy = true) }
                try {
                    progress.start()
                    preparationJobs.values.toList().forEach { it.cancelAndJoin() }
                    SwitchTransactions.mutex.withLock {
                        SwitchTransactions.startManual(host)
                        try {
                            if (targets.isNotEmpty()) SwitchTransactions.noteManual(host)
                            // 기기별 전송 잠금은 각 transport가 관리한다. 다른 기기의 전송·재시도를
                            // 기다리지 않고 모든 대상의 명령을 시작한 뒤 이번 요청의 결과를 모은다.
                            val results = coroutineScope {
                                targets.map { device -> async {
                                    val result = try {
                                        device.setCurrentHost(host)
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        SwitchResult.Failed(error.message ?: "기기 전환 오류")
                                    }
                                    device to result
                                } }.awaitAll()
                            }
                            for ((device, result) in results) {
                                val message = when (result) {
                                    is SwitchResult.Switched -> {
                                        // 로컬 연결 표시를 내리기 전에 실제로 보낸 목적지를 이력에 기록한다.
                                        if (SwitchTransactions.isCurrent(operation)) FollowStatus.setActiveChannel(channel1Based)
                                        if (device.kind == DeviceKind.KEYBOARD && device.localHostIndex != host)
                                            FollowStatus.setKeyboardConnected(device.address, false)
                                        "전환 명령 전달됨"
                                    }
                                    is SwitchResult.Failed -> {
                                        indicators[device] = SwitchIndicator.Failure
                                        "전환 안 됨: ${result.reason}"
                                    }
                                }
                                appendLog("${device.name}: $message")
                                val index = devices.indexOf(device)
                                if (index >= 0) updateDeviceAt(index) { it.copy(lastResult = message) }
                            }
                        } finally { SwitchTransactions.finishManual(host) }
                    }
                } finally {
                    _uiState.update { it.copy(busy = false) }
                }
                val checks = coroutineScope {
                    targets.map { device -> async {
                        val status = if (indicators[device] == SwitchIndicator.Failure) SwitchIndicator.Failure else {
                            try { verify(device, host) }
                            catch (error: CancellationException) { throw error }
                            catch (error: Exception) {
                                appendLog("${device.name}: 전환 확인 오류: ${error.message}")
                                SwitchIndicator.Pending
                            }
                        }
                        progress.resolved(device.address, status)
                        device to status
                    } }.awaitAll()
                }
                if (SwitchTransactions.isCurrent(operation) && checks.any { it.second == SwitchIndicator.Success }) {
                    FollowStatus.setActiveChannel(channel1Based)
                }
            } finally { progress.finishUnconfirmed() }
        }
    }

    fun firmwareProbeCandidates(): List<LogiDevice> = devices.filter {
        it.kind == DeviceKind.MOUSE && isConnected(it) == true
    }

    /** 연결 로그 화면에서 명시적으로 시작하는 단발 시험. 키보드와 영구 설정은 건드리지 않는다. */
    fun startFirmwareReturnProbe(address: String, targetChannel: Int) {
        if (updatingDevices || _uiState.value.busy || firmwareProbeJob?.isActive == true || targetChannel !in 1..3) return
        val device = firmwareProbeCandidates().firstOrNull { it.address == address } ?: return
        if (!SwitchTransactions.reserveFirmwareProbe(address)) return
        _uiState.update { it.copy(busy = true) }
        _firmwareProbe.value = FirmwareProbeState(FirmwareProbePhase.PREPARING, device.name, targetChannel = targetChannel)
        val records = mutableListOf<String>()
        val timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS XXX")
            .withZone(java.time.ZoneId.systemDefault())
        fun record(line: String) {
            records += "${timestamp.format(java.time.Instant.now())} $line"
            appendLog(line)
        }
        // 화면 수명 종료가 첫 dispatch보다 빨라도 finally가 예약을 해제하게 한다.
        firmwareProbeJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                switchJob?.cancelAndJoin()
                preparationJobs.values.toList().forEach { it.cancelAndJoin() }
                _uiState.update { it.copy(busy = true) }
                SwitchTransactions.mutex.withLock {
                    SwitchTransactions.mouseMaintenanceMutex.withLock {
                        val operation = SwitchTransactions.begin()
                        FirmwareReturnProbe(
                            connected = isConnected,
                            log = ::record,
                            update = { _firmwareProbe.value = it },
                            interrupted = { !SwitchTransactions.isCurrent(operation) },
                            connectionEvents = firmwareProbeEvents(device),
                        ).run(device, targetChannel)
                    }
                }
            } finally {
                if (_firmwareProbe.value.running) {
                    record("[FW-RETURN] RESULT=CANCELLED before observation")
                    _firmwareProbe.update { it.copy(phase = FirmwareProbePhase.FINISHED, outcome = FirmwareProbeOutcome.CANCELLED) }
                }
                SwitchTransactions.releaseFirmwareProbe(address)
                _uiState.update { it.copy(busy = false) }
                withContext(NonCancellable) {
                    try { saveFirmwareProbeLog(records.toList()) }
                    catch (error: Exception) { appendLog("[FW-RETURN] 시험 파일 저장 실패: ${error.message}") }
                }
            }
        }
    }

    fun cancelFirmwareReturnProbe() { firmwareProbeJob?.cancel() }

    /**
     * 0x1B04 진단: 폰에 연결된 키보드의 컨트롤(cid/task/flags) 목록을 덤프한다.
     * Easy-Switch 키(cid 0xD1~D3)는 divert 불가(flags=0x04)로 실측 확정 — 재검증·타 기기 확인용.
     * 여러 키보드가 등록돼 있으면 실제 연결되는 것을 순차로 찾는다(연결 판정도 실시간).
     */
    fun probeReprogKeys() {
        val keyboards = devices.filter { it.kind == DeviceKind.KEYBOARD }
        if (keyboards.isEmpty()) {
            appendLog("[0x1B04] 대상에 키보드가 없어요 (기기 선택을 확인하세요)")
            return
        }
        viewModelScope.launch {
            var keyboard: LogiDevice? = null
            for (kb in keyboards) {
                appendLog("[0x1B04] ${kb.name} 연결 시도")
                try {
                    kb.connect()
                    keyboard = kb
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    appendLog("[0x1B04] ${kb.name}: ${e.message}")
                }
            }
            val kb = keyboard ?: run {
                appendLog("[0x1B04] 연결된 키보드가 없어요 (키보드가 폰에 붙어 있어야 함)")
                return@launch
            }
            try {
                val idx = kb.getFeatureIndex(HidppConstants.FEATURE_REPROG_KEYS)
                appendLog("[0x1B04] ${kb.name} index = 0x%02X".format(idx))
                val count = ReprogKeysFeature.parseGetCount(kb.request(ReprogKeysFeature.buildGetCount(idx)))
                appendLog("[0x1B04] 컨트롤 수 = $count")
                for (i in 0 until count) {
                    val info: CidInfo =
                        ReprogKeysFeature.parseCidInfo(kb.request(ReprogKeysFeature.buildGetCidInfo(idx, i)))
                    appendLog(
                        "[%d] cid=0x%04X task=0x%04X flags=0x%02X%s".format(
                            i, info.cid, info.taskId, info.flags, if (info.divertable) " DIVERTABLE" else "",
                        ),
                    )
                }
                appendLog("[0x1B04] 완료 — DIVERTABLE 표시된 cid가 Easy-Switch 후보")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                appendLog("[0x1B04] 조회 실패: ${e.message}")
            } finally {
                runCatching { kb.disconnect() }
            }
        }
    }

    /**
     * 이벤트 감시 모드: 키보드에 연결을 유지한 채 [seconds]초 동안 모든 notify를 로그로 찍는다.
     *
     * 키보드가 전환 직전 보내는 ChangeHost(0x1814) 이벤트(funcSw=0x00, params[1]=target)는
     * 실측 확정됐고 [com.easyswitchcontroller.service.KeyboardFollowManager]가 사용 중이다.
     * 이 모드는 그 이벤트 재확인·신규 기기 프레임 디버깅용 진단 도구다.
     * 감시 중 사용자가 Easy-Switch 키를 누르면, 링크가 끊기기 전 도착하는 프레임이 전부 기록된다.
     */
    fun watchKeyboardEvents(seconds: Int = 30) {
        val keyboards = devices.filter { it.kind == DeviceKind.KEYBOARD }
        if (keyboards.isEmpty()) {
            appendLog("[감시] 대상에 키보드가 없어요")
            return
        }
        viewModelScope.launch {
            var keyboard: LogiDevice? = null
            for (kb in keyboards) {
                try {
                    kb.connect()
                    keyboard = kb
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    appendLog("[감시] ${kb.name}: ${e.message}")
                }
            }
            val kb = keyboard ?: run {
                appendLog("[감시] 연결된 키보드가 없어요 (키보드를 깨운 뒤 다시)")
                return@launch
            }
            appendLog("[감시] ${kb.name} 연결 유지 — 지금 Easy-Switch 키를 눌러보세요 (${seconds}초)")
            val collectJob = launch {
                kb.notifications.collect { frame ->
                    appendLog(
                        "[EVT] feat=0x%02X funcSw=0x%02X params=%s".format(
                            frame.featureIndex,
                            frame.funcSwByte,
                            frame.params.joinToString(" ") { "%02X".format(it) },
                        ),
                    )
                }
            }
            try {
                delay(seconds * 1000L)
            } finally {
                collectJob.cancel()
                runCatching { kb.disconnect() }
            }
            appendLog("[감시] 종료")
        }
    }

    fun setLabel(channel1Based: Int, label: String) {
        val trimmed = label.trim()
        _uiState.update {
            val newLabels = if (trimmed.isEmpty()) it.labels - channel1Based else it.labels + (channel1Based to trimmed)
            it.copy(labels = newLabels)
        }
        onLabelChanged(channel1Based, trimmed)
    }

    private fun updateDeviceAt(index: Int, transform: (DeviceUiState) -> DeviceUiState) {
        _uiState.update { state ->
            state.copy(
                devices = state.devices.mapIndexed { i, du -> if (i == index) transform(du) else du },
            )
        }
    }

    private fun appendLog(line: String, level: ConnectionLog.Level = ConnectionLog.Level.Info) {
        android.util.Log.d("HidppApp", line)
        // VM 로컬 로그(테스트 관측용)와 전역 로그(연결 로그 화면·프로세스 수명)에 함께 남긴다.
        ConnectionLog.append(line, level)
        _uiState.update { it.copy(log = (it.log + line).takeLast(200)) }
    }
}
