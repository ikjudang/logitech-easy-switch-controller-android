package com.easyswitchcontroller.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CancellationException
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 프로세스 전역 연결 로그 스토어.
 *
 * 기존에는 로그가 [com.easyswitchcontroller.ui.channel.ChannelViewModel] 안에만 있어서
 * 기기 선택이 바뀌면(=VM 재생성) 날아갔고, 따라가기 서비스 쪽 이벤트(따라가기)는 토스트로만
 * 보여 기록이 남지 않았다. 연결 로그 화면(설정 > 연결 로그)은 VM·서비스 양쪽이 쓰는
 * 이 싱글톤을 읽는다.
 *
 * **파일로도 남긴다([attach]).** 메모리에만 두면 프로세스가 죽는 순간 기록이 통째로 사라져,
 * 사후에 "따라가기 서비스가 죽은 것인지, 살아서 재연결만 실패한 것인지"를 **구분할 수 없다** —
 * 정작 그 구분이 필요한 상황에서 증거가 없어진다. 그래서 줄마다 파일에 붙이고 다음 실행 때
 * 읽어와 이어 붙인다. 시작 마커가 함께 남으므로 로그에 끊긴 구간이 보이면 프로세스가 죽은 것이다.
 */
object ConnectionLog {

    enum class Level {
        /** 일반 진행 로그. */
        Info,

        /** 성공(전환됨·연결됨 등) — 로그 화면에서 초록으로 강조. */
        Success,

        /** 끊김·재시도 등 소극 이벤트 — 흐리게 표시. */
        Dim,
    }

    data class Entry(
        val timeMillis: Long,
        val message: String,
        val level: Level,
    )

    /** 화면에 들고 있을 줄 수. */
    private const val MAX_ENTRIES = 300

    /** 파일 상한. 넘으면 오래된 쪽부터 버린다. */
    private const val MAX_FILE_BYTES = 256L * 1024

    private const val FILE_NAME = "connection-log.tsv"

    private const val PREFS = "easy_switch_log"
    private const val KEY_SAVING = "log_saving_enabled"

    /** 지정 폴더 스냅샷을 몰아 쓰는 간격. */
    private const val SNAPSHOT_DEBOUNCE_MS = 20_000L
    private const val SEPARATOR = '\t'

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    // 파일 입출력은 화면의 로그 예약·설정 잠금을 붙들지 않는다.
    private val fileLock = Any()

    @Volatile
    private var sink: File? = null

    /** 지정 폴더 스냅샷을 쓰려면 프로세스 어디서든 Context가 필요하다(따라가기 서비스도 쓴다). */
    @Volatile
    private var appContext: Context? = null

    private var snapshotJob: Job? = null

    /**
     * 파일 영속화를 켜고 시작 마커를 남긴다. 프로세스당 여러 번 불려도 안전하다(액티비티·서비스가
     * 각각 부른다) — 파일 연결은 한 번만 하고, 마커는 [startedBy] 이름으로 매번 남긴다.
     *
     * @param startedBy 누가 시작했는지("앱 화면", "따라가기 서비스"). 로그에서 재시작 지점을 읽는 근거다.
     */
    @Synchronized
    fun attach(context: Context, startedBy: String) {
        appContext = context.applicationContext
        if (sink == null && isSavingEnabled(context)) openSink(context)
        // 버전을 함께 남긴다 — 업데이트하면 서비스가 다시 시작되므로, 어느 빌드에서 생긴 일인지 로그만으로 가른다.
        append("— $startedBy 시작 (v${appVersion(context)}) —", Level.Dim)
    }

    private fun appVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

    /**
     * 로그를 파일(앱 내부 파일·지정 폴더)로 남기는지. 꺼져 있으면 메모리에만 두고 앱이 다시 시작되면 사라진다.
     *
     * 처음 값: 이미 로그를 쌓던 기기(내부 로그 파일이나 저장 폴더가 있음)는 켜짐으로 이어 가고,
     * 새로 설치한 기기는 꺼짐으로 시작한다 — 다른 사람에게 줄 때 기록이 저절로 쌓이지 않게(사용자 확정).
     */
    fun isSavingEnabled(context: Context): Boolean {
        val prefs = prefs(context)
        if (prefs.contains(KEY_SAVING)) return prefs.getBoolean(KEY_SAVING, false)
        val app = context.applicationContext
        val existing = File(app.filesDir, FILE_NAME).length() > 0 || LogFolderSink.getFolder(app) != null
        prefs.edit().putBoolean(KEY_SAVING, existing).apply()
        return existing
    }

    /** 켜면 이번 실행에서 메모리에만 있던 줄까지 파일에 남기고, 끄면 그 순간부터 어떤 파일에도 쓰지 않는다. */
    @Synchronized
    fun setSavingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SAVING, enabled).apply()
        if (!enabled) {
            sink = null
            snapshotJob?.cancel()
            return
        }
        if (sink != null) return
        val file = openSink(context)
        val recent = _entries.value
        io.launch {
            runCatching {
                val persisted = readAll(file, limit = Int.MAX_VALUE)
                unwrittenEntries(persisted, recent).forEach { writeLine(file, it) }
            }
        }
        scheduleSnapshot()
    }

    /** 내부 로그 파일을 연결하고 이전 실행의 기록을 화면 목록에 되살린다. */
    private fun openSink(context: Context): File {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        sink = file
        val restored = runCatching { readAll(file) }.getOrDefault(emptyList())
        if (restored.isNotEmpty()) {
            _entries.update { mergeLogEntries(restored, it).takeLast(MAX_ENTRIES) }
        }
        return file
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun append(message: String, level: Level = Level.Info) {
        // 개행이 들어오면 파일의 한 줄 = 한 항목 규칙이 깨진다.
        val safe = message.replace('\n', ' ').replace('\r', ' ').replace(SEPARATOR, ' ')
        val entry = Entry(System.currentTimeMillis(), safe, level)
        _entries.update { (it + entry).takeLast(MAX_ENTRIES) }

        val file = sink ?: return
        io.launch { runCatching { writeLine(file, entry) } }
        scheduleSnapshot()
    }

    /**
     * 폴더를 막 지정했을 때처럼, 기다리지 않고 지금 한 편 남겨야 하는 경우.
     *
     * @param explicit 사용자가 누른 내보내기면 true — 자동 저장을 꺼 둬도 쓴다. 그때는 파일에 쌓인 기록이 없으니
     *   이번 실행의 기록만 담긴다.
     */
    fun snapshotNow(explicit: Boolean = false): Deferred<Boolean> {
        val context = appContext ?: return CompletableDeferred(false)
        return io.async {
            try { writeSnapshot(context, explicit) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { false }
        }
    }

    /**
     * 지정 폴더 스냅샷을 예약한다. 전환 한 번에 로그가 여러 줄 붙는데 줄마다 쓰면 드라이브
     * 동기화가 그만큼 돈다 — 조용해질 때까지 기다렸다 한 번만 쓴다.
     */
    @Synchronized private fun scheduleSnapshot() {
        val context = appContext ?: return
        if (LogFolderSink.getFolder(context) == null) return // 폴더 미지정이면 비용 0
        snapshotJob?.cancel()
        snapshotJob = io.launch {
            delay(SNAPSHOT_DEBOUNCE_MS)
            runCatching { writeSnapshot(context) }
        }
    }

    private fun writeSnapshot(context: Context, explicit: Boolean = false): Boolean {
        if ((!explicit && !isSavingEnabled(context)) || LogFolderSink.getFolder(context) == null) return false
        val all = allEntries()
        if (all.isEmpty()) return false
        return LogFolderSink.writeSnapshot(context, dailyFileName(), format(all))
    }

    private fun allEntries(): List<Entry> {
        // 화면에 남은 줄보다 파일에 더 많이 있다 — 스냅샷·내보내기는 파일 쪽 전체를 담는다.
        val persisted = sink?.let { runCatching { readAll(it, limit = Int.MAX_VALUE) }.getOrDefault(emptyList()) }
            ?: emptyList()
        // 파일 쓰기가 실패하거나 아직 대기 중이어도 화면의 최신 기록을 빠뜨리지 않는다.
        return mergeLogEntries(persisted, entries.value)
    }

    /** 기기 모델을 넣어 기기마다 파일을 가른다 — 태블릿과 휴대폰이 같은 폴더를 써도 서로 덮어쓰지 않는다. */
    private fun dailyFileName(): String =
        "easyswitch-${modelTag()}-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".txt"

    /** 저장 창에 미리 넣을 파일 이름. 하루 파일과 겹치지 않게 시각까지 넣는다. */
    fun exportFileName(): String =
        "easyswitch-${modelTag()}-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date()) + ".txt"

    private fun modelTag(): String =
        Build.MODEL.orEmpty().replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { "device" }

    private fun format(list: List<Entry>): String {
        // 밀리초까지 남긴다 — 키보드가 붙은 뒤 신호를 받기까지의 빈틈은 1초 안쪽에서 갈린다.
        val formatter = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
        return list.joinToString(separator = "\n") { entry ->
            "${formatter.format(Date(entry.timeMillis))} [${entry.level.name}] ${entry.message}"
        }
    }

    /** 저장 창으로 내보낼 평문 — 폴더 스냅샷과 같은 내용이다. 며칠에 걸친 기록이라 날짜까지 넣는다. */
    fun exportAllText(): Deferred<String> = io.async { format(allEntries()) }

    private fun writeLine(file: File, entry: Entry) = synchronized(fileLock) {
        file.appendText("${entry.timeMillis}$SEPARATOR${entry.level.name}$SEPARATOR${entry.message}\n")
        if (file.length() > MAX_FILE_BYTES) {
            val kept = file.readLines().takeLast(MAX_ENTRIES * 2)
            file.writeText(kept.joinToString("\n", postfix = "\n"))
        }
    }

    private fun readAll(file: File, limit: Int = MAX_ENTRIES): List<Entry> = synchronized(fileLock) {
        if (!file.exists()) return@synchronized emptyList()
        file.readLines().mapNotNull { line ->
            val parts = line.split(SEPARATOR, limit = 3)
            if (parts.size < 3) return@mapNotNull null
            val time = parts[0].toLongOrNull() ?: return@mapNotNull null
            val level = runCatching { Level.valueOf(parts[1]) }.getOrDefault(Level.Info)
            Entry(time, parts[2], level)
        }.takeLast(limit)
    }
}

/** 디스크와 메모리에서 겹치는 항목만 합친다. 같은 밀리초에 발생한 동일 로그도 개수를 보존한다. */
internal fun mergeLogEntries(
    persisted: List<ConnectionLog.Entry>,
    recent: List<ConnectionLog.Entry>,
): List<ConnectionLog.Entry> = (persisted + unwrittenEntries(persisted, recent)).sortedBy { it.timeMillis }

/** [recent] 중 [persisted]에 아직 없는 항목. 같은 밀리초에 발생한 동일 로그도 개수를 보존한다. */
internal fun unwrittenEntries(
    persisted: List<ConnectionLog.Entry>,
    recent: List<ConnectionLog.Entry>,
): List<ConnectionLog.Entry> {
    val remaining = persisted.groupingBy { it }.eachCount().toMutableMap()
    return recent.filter { entry ->
        val count = remaining[entry] ?: 0
        if (count == 0) true else {
            remaining[entry] = count - 1
            false
        }
    }
}
