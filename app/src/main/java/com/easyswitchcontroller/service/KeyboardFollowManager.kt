package com.easyswitchcontroller.service

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.easyswitchcontroller.ble.toHex
import com.easyswitchcontroller.data.AclEvent
import com.easyswitchcontroller.data.BondedDeviceInfo
import com.easyswitchcontroller.data.BondedDeviceRepository
import com.easyswitchcontroller.data.ConnectionLog
import com.easyswitchcontroller.data.DeviceNotConnectedException
import com.easyswitchcontroller.data.SelectionStore
import com.easyswitchcontroller.device.DeviceKind
import com.easyswitchcontroller.device.LogiDevice
import com.easyswitchcontroller.device.SwitchIndicator
import com.easyswitchcontroller.device.SwitchReport
import com.easyswitchcontroller.device.SwitchReportProgress
import com.easyswitchcontroller.device.DeviceSwitchStatus
import com.easyswitchcontroller.device.SwitchResult
import com.easyswitchcontroller.device.SwitchTransactions
import com.easyswitchcontroller.device.verifySwitch
import com.easyswitchcontroller.device.currentAclConnection
import com.easyswitchcontroller.di.ServiceLocator
import com.easyswitchcontroller.hidpp.ChangeHostFeature
import com.easyswitchcontroller.hidpp.HidppConstants
import com.easyswitchcontroller.hidpp.HidppFrame
import com.easyswitchcontroller.transport.ConnectionState
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 선택된 각 키보드의 ChangeHost 이벤트를 구독하고 선택된 마우스를 같은 채널로 전환한다. */
class KeyboardFollowManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onStatus: (SwitchReport) -> Unit,
    private val onKeyboardSwitch: (Int) -> Unit = {},
) {
    private val worker = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private val store = SelectionStore(context)
    private val repository = BondedDeviceRepository(context)
    private val monitor = ServiceLocator.connectedDevicesMonitor(context)
    private data class Watch(
        val device: LogiDevice,
        val phoneHost: Int,
        val stateJob: Job,
        val eventJob: Job,
    )
    private val watches = ConcurrentHashMap<String, Watch>()
    private val heldMice = ConcurrentHashMap<String, LogiDevice>()
    private val ensureMutex = sharedEnsureMutex
    private val miceMutex = SwitchTransactions.mouseMaintenanceMutex
    private val failStreaks = ConcurrentHashMap<String, Int>()
    private val lastFailLogAt = ConcurrentHashMap<String, Long>()
    private val lastEvents = ConcurrentHashMap<String, Pair<Int, Long>>()
    /** 키보드별 마지막 OS 연결 알림 시각과 마지막 감시 준비 시각(elapsedRealtime) — 준비 전에 떠난 경우를 가른다. */
    private val arrivedAt = ConcurrentHashMap<String, Long>()
    private val readyAt = ConcurrentHashMap<String, Long>()
    /** 기기별 마지막 OS 연결·끊김 알림 시각 — 1초 뒤 연결 판정 대조가 그사이 온 알림을 오경보로 찍지 않게 한다. */
    private val aclNoticeAt = ConcurrentHashMap<String, Long>()
    @Volatile private var started = false
    @Volatile private var lastLoopErrorAt = 0L
    /** 마지막 [ensureWatching]이 감시하려 한 키보드 주소. 재시도 루프가 목록 조회 없이 빈 감시를 가른다. */
    @Volatile private var wantedKeyboards: Set<String> = emptySet()

    private fun isWatching(address: String) =
        watches[address]?.device?.connectionState?.value == ConnectionState.ServicesReady

    fun start() {
        if (started) return
        started = true
        FollowStatus.setRunning(true)
        worker.launch {
            monitor.connectedAddresses.collect {
                survive(::noteLoopError) { ensureWatching(); ensureMiceReady() }
            }
        }
        worker.launch {
            SelectionStore.revision.collect {
                survive(::noteLoopError) { ensureWatching(); ensureMiceReady() }
            }
        }
        worker.launch {
            // 재시도 계기(ACL 알림·선택 변경)를 놓쳐도 스스로 회복하는 안전망이다. 이 기기에 연결(ACL)돼 있는데
            // 감시가 없는 키보드가 있을 때만 잡으러 가고, 실패가 이어지면 간격을 늘린다(0.4초에서 최대 5초).
            // 다른 장소의 키보드는 연결 판정 한 번으로 건너뛴다 — 감시가 실패한 직후에는 곧바로 다시 잡아야
            // 키보드가 그새 떠나며 보내는 전환 신호를 덜 놓친다(2026-10-19 13:20 실측: 실패 뒤 15초 공백).
            var backoff = RETRY_MIN_MS
            while (true) {
                delay(backoff)
                val wanted = wantedKeyboards
                val missing = wanted.filter { !isWatching(it) && currentAclConnection(context, it) != false }
                if (wanted.isNotEmpty() && missing.isEmpty()) {
                    backoff = RETRY_MIN_MS
                    continue
                }
                survive(::noteLoopError) { ensureWatching() }
                backoff = if (missing.isNotEmpty() && missing.all { isWatching(it) }) RETRY_MIN_MS
                else (backoff * 2).coerceAtMost(RETRY_MAX_MS)
            }
        }
        worker.launch {
            while (true) {
                survive(::noteLoopError) { ensureMiceReady() }
                delay(MOUSE_KEEP_MS)
            }
        }
        worker.launch {
            monitor.aclEvents.collect { event -> survive(::noteLoopError) { onAclEvent(event) } }
        }
    }

    fun stop() {
        started = false
        worker.cancel()
        FollowStatus.setRunning(false)
        // onDestroy가 부모 스코프를 곧 취소해도 GATT 정리는 끝까지 실행한다.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                ensureMutex.withLock {
                    watches.values.forEach { watch ->
                        watch.device.heldByService = false
                        runCatching { watch.device.disconnect(force = true) }
                    }
                    watches.clear()
                }
                miceMutex.withLock {
                    heldMice.values.forEach { device ->
                        device.heldByService = false
                        runCatching { device.disconnect(force = true) }
                    }
                    heldMice.clear()
                }
            }
        }
    }

    private suspend fun ensureWatching() = ensureMutex.withLock {
        currentCoroutineContext().ensureActive()
        val snapshot = repository.snapshot()
        if (!snapshot.complete) return@withLock
        val selected = store.reconcile(snapshot)
        val candidates = snapshot.candidates(selected).filter {
            it.address in selected && ServiceLocator.guessKind(it) == DeviceKind.KEYBOARD
        }
        val wanted = candidates.mapTo(mutableSetOf()) { it.address }
        wantedKeyboards = wanted
        watches.keys.filter { it !in wanted }.forEach { removeWatch(it) }

        // 한 기기의 GATT가 살아 있다는 이유로 나머지 선택 키보드의 감시를 생략하지 않는다.
        for (info in candidates) {
            val existing = watches[info.address]
            if (existing?.device?.connectionState?.value == ConnectionState.ServicesReady) continue
            val name = info.name
            removeWatch(info.address)
            val device = ServiceLocator.device(context.applicationContext, info, DeviceKind.KEYBOARD)
            if (device.switchGraceActive) continue
            // 구독은 연결 전에 건다. 키보드가 붙자마자 다시 떠나면 전환 신호는 현재 슬롯 조회가 끝나기 전에 온다
            // (2026-10-20 12:43 실측: 연결 0.9초, 조회 단계에서 실패) — 조회 뒤에 구독하면 그 신호는 버려진다.
            // 슬롯을 알기 전에 온 프레임은 붙잡아 뒀다가 처리한다. 지난번에 확인한 슬롯이 있으면 그걸로 바로 처리한다
            // — 슬롯을 모른 채 해석하면 도착 공지를 출발로 오인할 수 있다.
            val known = device.cachedFeatureIndex(HidppConstants.FEATURE_CHANGE_HOST)?.let { index ->
                device.localHostIndex?.let { EarlySignalGate.Route(index, it) }
            }
            val gate = EarlySignalGate<HidppFrame>(known)
            val eventJob = worker.launch(start = CoroutineStart.UNDISPATCHED) {
                device.notifications.collect { frame ->
                    // 아래 조회의 응답도 같은 흐름으로 온다. 응답은 요청의 software id를 에코하고 기기가 스스로
                    // 보낸 이벤트는 0이다 — 응답은 요청한 쪽이 받으니 여기서는 신호로 세지 않는다.
                    if (frame.softwareId != 0) return@collect
                    val route = gate.offer(frame) ?: return@collect
                    if (!gate.ready && frame.featureIndex == route.featureIndex) {
                        ConnectionLog.append(
                            "$name: 감시 준비 중에 신호를 받았어요(지난번에 확인한 이 기기 ch${route.localHost + 1} 기준으로 처리해요)",
                            ConnectionLog.Level.Dim,
                        )
                    }
                    survive(::noteLoopError) { handleFrame(device, route.featureIndex, route.localHost, frame) }
                }
            }
            // 실패 로그에 어느 단계였는지 남긴다 — 같은 1초 시간 초과도 CCCD(연결 단계)와 응답 대기는 원인이 다르다.
            var step = "연결"
            try {
                device.connect()
                // 여기서부터 신호를 받을 수 있다(알림 켜짐). 키보드가 돌아온 시각과의 차이가 남은 빈틈이다.
                val listeningAt = SystemClock.elapsedRealtime()
                step = "0x1814 조회"
                val index = device.getFeatureIndex(HidppConstants.FEATURE_CHANGE_HOST)
                step = "현재 호스트 조회"
                val host = ChangeHostFeature.parseGetHostInfo(device.request(ChangeHostFeature.buildGetHostInfo(index)))
                device.rememberLocalHost(host.currentHost)
                FollowStatus.rememberLocalChannel(info.address, host.currentHost + 1)
                currentCoroutineContext().ensureActive()
                if (known != null && known.localHost != host.currentHost) {
                    ConnectionLog.append(
                        "$name: 이 기기 슬롯이 바뀌었어요(ch${known.localHost + 1} → ch${host.currentHost + 1})",
                        ConnectionLog.Level.Dim,
                    )
                }
                // 붙잡아 둔 프레임 중 0x1814 것만 신호다(배터리 등 다른 기능의 이벤트도 함께 쌓인다).
                val held = gate.open(EarlySignalGate.Route(index, host.currentHost)).filter { it.featureIndex == index }
                if (held.isNotEmpty()) {
                    ConnectionLog.append("$name: 감시 준비 중에 받은 신호 ${held.size}개를 처리해요", ConnectionLog.Level.Dim)
                    held.forEach { frame -> survive(::noteLoopError) { handleFrame(device, index, host.currentHost, frame) } }
                }
                device.heldByService = true
                val stateJob = worker.launch {
                    var previous: ConnectionState? = null
                    device.connectionState.collect { state ->
                        FollowStatus.setKeyboardConnected(info.address, state == ConnectionState.ServicesReady)
                        // 감시 GATT가 닫히는 순간을 남긴다(잡힌 순간은 "준비됨"이 남긴다). OS 끊김 알림 없이
                        // 이 줄만 있으면 ACL은 그대로인데 GATT만 닫혀 전환 신호를 못 받던 공백이다.
                        if (state == ConnectionState.Disconnected && previous != null && previous != state) {
                            ConnectionLog.append("${info.name}: GATT 재연결 대기…", ConnectionLog.Level.Dim)
                        }
                        previous = state
                    }
                }
                watches[info.address] = Watch(device, host.currentHost, stateJob, eventJob)
                gate.ready = true
                FollowStatus.setKeyboardConnected(info.address, true)
                val streak = failStreaks.remove(info.address) ?: 0
                lastFailLogAt.remove(info.address)
                val previousReady = readyAt.put(info.address, SystemClock.elapsedRealtime()) ?: 0L
                // 돌아온 뒤 처음 준비될 때만 잰다. 실패한 시도가 있었다면 그 시간까지 포함한 실제 빈틈이다.
                val gap = arrivedAt[info.address]?.takeIf { it > previousReady }
                    ?.let { ", 연결 알림 뒤 %.2f초부터 신호 수신".format((listeningAt - it) / 1000.0) }.orEmpty()
                val ready = "$name: 키보드 전환 감지 준비됨 (0x1814=0x%02X, 이 기기 ch%d, 재시도 %d%s)"
                    .format(index, host.currentHost + 1, streak, gap)
                ConnectionLog.append(ready, ConnectionLog.Level.Success)
                // 자동 재연결마다 대기 토스트를 반복해 전환 결과를 덮지 않는다.
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
                noteWatchFailure(info, error, step)
            } catch (error: Exception) {
                noteWatchFailure(info, error, step)
            } finally {
                if (!watches.containsKey(info.address)) {
                    eventJob.cancel()
                    // 슬롯을 끝내 몰라 처리하지 못한 프레임 — 마우스가 안 따라간 이유를 로그에서 바로 찾게 남긴다.
                    // 이 기기에서 처음 잡는 감시라 0x1814 index도 몰라서, 기능을 가리지 않고 센다.
                    val dropped = gate.drain()
                    if (dropped.isNotEmpty()) {
                        val raw = dropped.first().rawHex()
                        ConnectionLog.append(
                            "$name: 감시 준비 중에 받은 프레임 ${dropped.size}개를 처리하지 못했어요(이 기기 슬롯을 몰라요, 첫 프레임 $raw)",
                            ConnectionLog.Level.Dim,
                        )
                    }
                    device.heldByService = false
                    withContext(NonCancellable) { runCatching { device.disconnect(force = true) } }
                }
            }
        }
    }

    private suspend fun removeWatch(address: String) {
        val watch = watches.remove(address) ?: return
        watch.eventJob.cancel()
        watch.stateJob.cancel()
        FollowStatus.setKeyboardConnected(address, false)
        watch.device.heldByService = false
        watch.device.disconnect(force = true)
        lastEvents.remove(address)
    }

    private fun noteWatchFailure(info: BondedDeviceInfo, error: Exception, step: String) {
        if (error is DeviceNotConnectedException) return
        val streak = (failStreaks[info.address] ?: 0) + 1
        failStreaks[info.address] = streak
        val now = SystemClock.elapsedRealtime()
        // 처음 몇 번은 모두 남긴다 — 실패 이유가 바뀌는지가 원인을 가른다. 그 뒤로는 1분에 한 번만.
        // (다른 장소의 키보드는 위에서 걸러져 여기 오지 않으므로 소음이 되지 않는다.)
        if (streak > FAIL_LOG_BURST && now - (lastFailLogAt[info.address] ?: 0L) < FAIL_LOG_INTERVAL_MS) return
        lastFailLogAt[info.address] = now
        val name = info.name
        ConnectionLog.append("$name: 감시 연결 실패 ($streak 회, $step 단계): $error", ConnectionLog.Level.Dim)
    }

    private fun noteLoopError(error: Throwable) {
        val now = SystemClock.elapsedRealtime()
        if (lastLoopErrorAt != 0L && now - lastLoopErrorAt < FAIL_LOG_INTERVAL_MS) return
        lastLoopErrorAt = now
        Log.w(TAG, "감시 오류: $error")
        ConnectionLog.append("감시 오류. 앱이 다시 시도해요: $error", ConnectionLog.Level.Dim)
    }

    private suspend fun onAclEvent(event: AclEvent) {
        val info = repository.bondedDevices().firstOrNull {
            it.address == event.address && it.address in store.loadSelected()
        } ?: return
        val now = SystemClock.elapsedRealtime()
        aclNoticeAt[info.address] = now
        if (event.connected) {
            ServiceLocator.device(context.applicationContext, info, ServiceLocator.guessKind(info)).endSwitchGrace()
            // OS 알림과 앱의 연결 판정(숨은 API 리플렉션)을 1초 뒤 대조한다. 마우스 대상 선정·전환 확인·
            // 수동 전환이 모두 그 판정을 쓰므로, 어긋나면 그 판정이 틀린 것이라 그대로 남긴다.
            worker.launch {
                delay(ACL_CROSSCHECK_DELAY_MS)
                // 그사이 다른 알림(끊김·재연결)이 왔으면 이 대조는 낡았다. 끊김 뒤의 '없음'은 판정이 맞은 것이다
                // (2026-10-20 12:43:36: 0.9초 만에 떠난 키보드가 '판정 불일치'로 잘못 찍혔다).
                if (aclNoticeAt[info.address] != now) return@launch
                if (currentAclConnection(context, info.address) == false) {
                    val name = info.name
                    ConnectionLog.append(
                        "$name: 판정 불일치 — OS는 연결됐다고 알렸지만 앱의 연결 판정은 '없음'입니다",
                        ConnectionLog.Level.Dim,
                    )
                }
            }
        }
        if (ServiceLocator.guessKind(info) != DeviceKind.KEYBOARD) return
        val name = info.name
        val state = if (event.connected) "연결됨" else "연결 끊김"
        ConnectionLog.append("$name: 블루투스 $state (OS 알림)")
        if (event.connected) {
            arrivedAt[info.address] = now
            ensureWatching()
            return
        }
        // 돌아온 뒤 감시가 한 번도 준비되지 않은 채 떠났다면, 그사이 누른 전환 신호는 받을 수 없었다.
        // 사용자가 "가끔 놓친다"고 느낀 순간을 로그에서 바로 찾게 남긴다.
        val arrived = arrivedAt[info.address] ?: return
        // 준비가 끝나기 전이라도 신호를 받아 처리했다면(감시 준비 중 신호) 놓친 것이 아니다.
        val caught = (lastEvents[info.address]?.second ?: 0L) > arrived
        if ((readyAt[info.address] ?: 0L) < arrived && !caught) {
            val seconds = "%.1f".format((now - arrived) / 1000.0)
            ConnectionLog.append(
                "$name: 감시가 준비되기 전에 연결이 끊겼어요(연결 ${seconds}초). 이때 누른 전환은 마우스가 따라가지 못했을 수 있어요.",
                ConnectionLog.Level.Dim,
            )
        }
    }

    private fun handleFrame(keyboard: LogiDevice, index: Int, phoneHost: Int, frame: HidppFrame) {
        if (frame.featureIndex == index) {
            // 키보드마다 전환 신호의 바이트 형식이 다를 수 있어(MX KEYS S 미실측) 해석 전에 원본을 남긴다.
            ConnectionLog.append("${keyboard.name}: 0x1814 신호 수신 (${frame.rawHex()})", ConnectionLog.Level.Dim)
        }
        val target = ChangeHostFeature.parseHostChangeEvent(frame, index) ?: return
        if (target == phoneHost) {
            FollowStatus.setKeyboardConnected(keyboard.address, true)
            FollowStatus.setActiveChannel(target + 1)
            keyboard.endSwitchGrace()
            if (!SwitchTransactions.isRecentManual(target)) SwitchTransactions.begin()
            return
        }
        // 연결 표시를 내리기 전에 관측한 목적지를 먼저 기록한다. 로컬 슬롯은 전환 이력이 아니다.
        FollowStatus.setActiveChannel(target + 1)
        // 전환 신호를 받은 순간부터 이 기기에 연결 중이라고 표시하지 않는다.
        FollowStatus.setKeyboardConnected(keyboard.address, false)
        if (SwitchTransactions.isRecentManual(target)) return
        val now = SystemClock.elapsedRealtime()
        val previous = lastEvents[keyboard.address]
        if (previous?.first == target && now - previous.second < DEBOUNCE_MS) return
        lastEvents[keyboard.address] = target to now
        keyboard.noteSwitchStarted()
        val operation = SwitchTransactions.begin()
        val channel = target + 1
        FollowStatus.setActiveChannel(channel)
        ConnectionLog.append("키보드 전환 감지: 채널 $channel")
        // 도착 공지·수동 전환 에코·중복 신호를 거른 출발만 시각 피드백으로 알린다.
        onKeyboardSwitch(channel)
        // 구독의 수명과 분리한다. 키보드가 떠나 재구독되어도 이미 시작한 마우스 전환은 끝낸다.
        worker.launch {
            survive(::noteLoopError) {
                val progress = SwitchReportProgress(channel, operation, mapOf(
                    keyboard.address to DeviceSwitchStatus(keyboard.name, keyboard.kind, SwitchIndicator.Pending),
                ), targetsKnown = false) { report ->
                    if (started && SwitchTransactions.isCurrent(operation)) {
                        onStatus(report)
                    }
                }
                // 전환 신호를 받으면 연결/재시도를 기다리지 않고 진행 안내부터 표시한다.
                progress.start()
                val earlyKeyboardCheck = worker.launch {
                    // 빠른 성공 확인만 먼저 표시한다. 최종 실패·미확인 판정 시점은 그대로 둔다.
                    if (verifySwitch(context, keyboard, target) == SwitchIndicator.Success) {
                        progress.resolved(keyboard.address, SwitchIndicator.Success)
                    }
                }
                try {
                    val sent = SwitchTransactions.mutex.withLock {
                        // 마우스를 움직이지 않고 끝나는 길은 모두 사유를 남긴다 — 조용히 끝나면 "신호를 못 받았다"와
                        // 구분이 안 된다(0.2.17~0.2.19의 params[0] 무시가 그렇게 사흘 숨어 있었다).
                        val skipped = when {
                            !SwitchTransactions.isCurrent(operation) -> "더 최근 전환이 시작됐어요"
                            SwitchTransactions.isRecentManual(target) -> "앱에서 같은 채널로 전환하는 중이에요"
                            else -> null
                        }
                        if (skipped != null) {
                            ConnectionLog.append("채널 $channel 마우스 전환을 건너뛰었어요: $skipped", ConnectionLog.Level.Dim)
                            return@withLock null
                        }
                        val latest = repository.snapshot()
                        val chosen = store.reconcile(latest)
                        if (keyboard.address !in chosen) {
                            val name = keyboard.name
                            ConnectionLog.append(
                                "채널 $channel 마우스 전환을 건너뛰었어요: 선택에서 빠진 키보드($name)의 신호예요",
                                ConnectionLog.Level.Dim,
                            )
                            return@withLock null
                        }
                        val (present, absent) = latest.candidates(chosen).filter {
                            it.address in chosen && ServiceLocator.guessKind(it) == DeviceKind.MOUSE
                        }.partition { currentAclConnection(context, it.address) != false }
                        if (absent.isNotEmpty()) {
                            // 다른 장소의 마우스는 명령 없이 뺀다. 연결 판정이 틀린 경우와 가를 수 있게 이름을 남긴다.
                            val names = absent.joinToString(", ") { it.name }
                            ConnectionLog.append("이 기기에 연결돼 있지 않아 전환에서 뺀 마우스: $names", ConnectionLog.Level.Dim)
                        }
                        val mice = present.map { ServiceLocator.device(context.applicationContext, it, DeviceKind.MOUSE) }
                        progress.discover(mice.associate { mouse ->
                            mouse.address to DeviceSwitchStatus(mouse.name, mouse.kind, SwitchIndicator.Pending)
                        }, confirmed = latest.complete)
                        mice.map { mouse ->
                            worker.async {
                                val result = try { mouse.setCurrentHost(target) }
                                catch (error: CancellationException) { throw error }
                                catch (error: Exception) { SwitchResult.Failed(error.message ?: "기기 전환 오류") }
                                val name = mouse.name
                                when (result) {
                                    is SwitchResult.Switched -> ConnectionLog.append("$name: 전환 명령 전달됨")
                                    is SwitchResult.Failed -> {
                                        val reason = result.reason
                                        ConnectionLog.append("$name: 전환 실패. $reason", ConnectionLog.Level.Dim)
                                    }
                                }
                                mouse to result
                            }
                        }.awaitAll()
                    } ?: return@survive
                    // 더 최근 전환이 시작돼도 이미 보낸 명령의 확인 결과는 로그에 남긴다. 안내(report)만 최신 전환에 양보한다.
                    earlyKeyboardCheck.cancel()
                    val keyboardCheck = worker.async {
                        verifySwitch(context, keyboard, target).also { progress.resolved(keyboard.address, it) }
                    }
                    val mouseChecks = sent.map { (mouse, result) ->
                        worker.async {
                            val status = if (result is SwitchResult.Failed) SwitchIndicator.Failure
                                else verifySwitch(context, mouse, target)
                            progress.resolved(mouse.address, status)
                            status
                        }
                    }.awaitAll()
                    val keyboardState = keyboardCheck.await()
                    if (sent.isEmpty()) ConnectionLog.append("연결된 마우스가 없어 마우스 전환을 생략했어요.")
                    // 키보드 쪽 확인도 남긴다 — 안내에 "일부 실패"가 떴을 때 어느 기기였는지 로그로 가른다.
                    val keyboardName = keyboard.name
                    ConnectionLog.append("$keyboardName: 채널 $channel 전환 확인 ${keyboardState.symbol}")
                    sent.zip(mouseChecks).forEach { (entry, status) ->
                        val name = entry.first.name
                        val symbol = status.symbol
                        ConnectionLog.append("$name: 채널 $channel 전환 확인 $symbol")
                    }
                } finally {
                    earlyKeyboardCheck.cancel()
                    progress.finishUnconfirmed()
                }
            }
        }
    }

    private suspend fun ensureMiceReady() = miceMutex.withLock {
        val keep = store.loadKeepMouseConnected()
        // 상시 연결이 꺼져 있고 붙잡은 마우스도 없으면 할 일이 없다 — 2초마다 기기 목록을 읽지 않는다.
        if (!keep && heldMice.isEmpty()) return@withLock
        val snapshot = repository.snapshot()
        if (!snapshot.complete) return@withLock
        val selected = store.reconcile(snapshot)
        val mice = snapshot.candidates(selected).filter {
            it.address in selected && ServiceLocator.guessKind(it) == DeviceKind.MOUSE
        }
        val wanted = if (keep) mice.mapTo(mutableSetOf()) { it.address } else emptySet()
        heldMice.keys.filter { it !in wanted }.forEach { address ->
            heldMice.remove(address)?.let { device ->
                device.heldByService = false
                device.disconnect(force = true)
            }
        }
        if (!keep) return@withLock
        for (info in mice) {
            val device = ServiceLocator.device(context.applicationContext, info, DeviceKind.MOUSE)
            if (device.switchGraceActive) continue
            if (device.connectionState.value == ConnectionState.ServicesReady) {
                device.heldByService = true
                heldMice[info.address] = device
                continue
            }
            try {
                device.connect()
                device.getFeatureIndex(HidppConstants.FEATURE_CHANGE_HOST)
                device.heldByService = true
                heldMice[info.address] = device
            } catch (error: CancellationException) {
                currentCoroutineContext().ensureActive()
            } catch (error: Exception) {
                Log.d(TAG, "마우스 상시 연결 대기: $error")
                if (!device.heldByService) device.disconnect()
            }
        }
    }

    /** 연결 로그용 원본 바이트: feature index·func|sw와 params 앞 6바이트. */
    private fun HidppFrame.rawHex(): String =
        (byteArrayOf(featureIndex.toByte(), funcSwByte.toByte()) + params.copyOf(6)).toHex()

    private companion object {
        val sharedEnsureMutex = Mutex()
        const val TAG = "HidppFollow"
        const val DEBOUNCE_MS = 250L
        const val RETRY_MIN_MS = 400L
        const val RETRY_MAX_MS = 5_000L
        const val MOUSE_KEEP_MS = 2_000L
        const val FAIL_LOG_INTERVAL_MS = 60_000L
        const val FAIL_LOG_BURST = 10
        const val ACL_CROSSCHECK_DELAY_MS = 1_000L
    }
}
