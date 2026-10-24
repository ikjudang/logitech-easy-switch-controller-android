package com.easyswitchcontroller.inputprobe

import com.easyswitchcontroller.floating.PixelCapturePlan
import com.easyswitchcontroller.floating.PixelRgbaRows

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.easyswitchcontroller.R
import org.json.JSONObject
import java.util.UUID

/** 사용자 동의를 받은 한 세션에서만 실제 화면 픽셀을 수집한다. 자동 재시작하지 않는다. */
class ProbePixelCaptureService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var worker: HandlerThread
    private lateinit var capture: Handler
    private var projection: MediaProjection? = null // 캡처 스레드에서만 접근한다.
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var densityDpi = 0
    private var frameNumber = 0L
    private var lastFrameNs = 0L
    private var lastSavedNs = 0L
    private var plan = PixelCapturePlan()
    private var started = false
    private var captureId = ""
    private var version = ""
    @Volatile private var closing = false
    private val timeout = Runnable { stopCapture("timeout_10min") }
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { main.post { stopCapture("system_stop") } }
        override fun onCapturedContentResize(newWidth: Int, newHeight: Int) {
            if (!closing && newWidth > 0 && newHeight > 0 && (width != newWidth || height != newHeight)) {
                runCatching { resize(newWidth, newHeight) }.onFailure { fail("RESIZE", it) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ProbeLog.attach(this)
        worker = HandlerThread("probe-screen-pixels").apply { start() }
        capture = Handler(worker.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopCapture("user"); return START_NOT_STICKY }
        if (started || closing) return START_NOT_STICKY
        @Suppress("DEPRECATION")
        val consent = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(CONSENT, Intent::class.java)
            else intent?.getParcelableExtra<Intent>(CONSENT)
        if (consent == null || intent?.getIntExtra(RESULT, Activity.RESULT_CANCELED) != Activity.RESULT_OK) {
            stopCapture("missing_consent"); return START_NOT_STICKY
        }
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "화면 픽셀 로그", NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 91, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
            val open = PendingIntent.getActivity(this, 92, Intent(this, InputProbeActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_follow).setContentTitle("EasySwitch 화면 픽셀 로그")
                .setContentText("앱이 플로팅 버튼과 이동 경로의 화면을 자동 저장하고 있습니다.")
                .setContentIntent(open).setOngoing(true).setSilent(true)
                .addAction(0, "픽셀 기록 종료", stop).build()
            // 동의 결과를 받은 뒤 mediaProjection 유형으로 전경 실행하고 토큰을 한 번만 쓴다.
            ServiceCompat.startForeground(this, 91, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
                ?: error("Default display missing")
            val context = createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            width = bounds.width(); height = bounds.height()
            densityDpi = context.resources.configuration.densityDpi
            captureId = UUID.randomUUID().toString()
            version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
            started = true
            ProbePixelLog.start()
            main.postDelayed(timeout, 10 * 60_000L)
            capture.post {
                if (closing) return@post
                try {
                    projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK, consent)
                        ?: error("Projection missing")
                    projection!!.registerCallback(callback, capture)
                    reader = newReader(width, height)
                    virtualDisplay = projection!!.createVirtualDisplay("EasySwitch pixel log", width, height, densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, capture)
                    log("PIXEL_CAPTURE_START id=$captureId version=$version source=MediaProjection display=0 width=$width height=$height densityDpi=$densityDpi fpsLimit=30 tailMs=1000 maxFrames=600 maxBytes=134217728")
                } catch (error: Exception) { fail("START", error) }
            }
        } catch (error: Exception) { fail("FOREGROUND", error) }
        return START_NOT_STICKY
    }

    private fun newReader(w: Int, h: Int): ImageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2).apply {
        setOnImageAvailableListener({ source -> record(source) }, capture)
    }

    private fun resize(w: Int, h: Int) {
        // 같은 동의 토큰으로 가상 화면을 다시 만들지 않고 Surface와 기존 가상 화면만 바꾼다.
        val display = virtualDisplay ?: return
        val replacement = newReader(w, h)
        val old = reader
        display.surface = null
        display.resize(w, h, densityDpi)
        display.surface = replacement.surface
        reader = replacement; width = w; height = h
        old?.setOnImageAvailableListener(null, null); old?.close()
        plan = PixelCapturePlan()
        log("PIXEL_CAPTURE_RESIZE width=$w height=$h")
    }

    private fun record(source: ImageReader) {
        if (closing || source !== reader) return
        var bitmap: Bitmap? = null
        try {
            val image = source.acquireLatestImage() ?: return
            image.use {
                if (closing) return
                val scene = ProbePixelLog.scene ?: return
                val readNs = SystemClock.elapsedRealtimeNanos()
                if (readNs - lastFrameNs < 33_333_333L) return
                lastFrameNs = readNs
                val visible = image.cropRect
                val screen = PixelCapturePlan.Box(visible.left, visible.top, visible.right, visible.bottom)
                val region = plan.sample(readNs / 1_000_000L, screen, scene.windows, scene.state, scene.moving) ?: return
                val plane = image.planes.first()
                val pixels = PixelRgbaRows.crop(plane.buffer, plane.rowStride, plane.pixelStride, region)
                bitmap = Bitmap.createBitmap(region.width, region.height, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(pixels) }
                val wallMs = System.currentTimeMillis()
                frameNumber++
                val metadata = JSONObject().put("wallTimeMs", wallMs).put("time", ProbePixelLog.formattedTime(wallMs))
                    .put("captureId", captureId).put("version", version).put("model", Build.MODEL).put("displayId", 0).put("densityDpi", densityDpi)
                    .put("frameNumber", frameNumber).put("readElapsedNs", readNs).put("imageTimestampNs", image.timestamp)
                    .put("stateSampledNs", scene.sampledNs).put("stateAgeNs", readNs - scene.sampledNs)
                    .put("savedFrameGapMs", if (lastSavedNs == 0L) 0L else (readNs - lastSavedNs) / 1_000_000L)
                    .put("sourceWidth", image.width).put("sourceHeight", image.height)
                    .put("crop", JSONObject().put("left", region.left).put("top", region.top).put("width", region.width).put("height", region.height))
                    .put("overlayState", JSONObject(scene.state))
                ProbePixelLog.save(this, bitmap!!, metadata)
                lastSavedNs = readNs
                log("PIXEL_FRAME id=$captureId n=$frameNumber timeMs=$wallMs imageNs=${image.timestamp} readNs=$readNs crop=$region stateAgeNs=${readNs - scene.sampledNs}")
            }
        } catch (error: Exception) { fail("FRAME", error) }
        catch (error: OutOfMemoryError) { fail("MEMORY", error) }
        finally { bitmap?.recycle() }
    }

    private fun log(message: String) { runCatching { ProbeLog.append(message) } }
    private fun fail(stage: String, error: Throwable) {
        log("PIXEL_${stage}_FAILED ${error.javaClass.simpleName}: ${error.message}")
        main.post { stopCapture("${stage.lowercase()}_failed") }
    }
    private fun stopCapture(reason: String) {
        if (closing) return
        closing = true
        ProbePixelLog.stop()
        log("PIXEL_CAPTURE_STOP reason=$reason")
        stopSelf()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Android 12/13에는 onCapturedContentResize가 없으므로 서비스 설정 변경에서도 크기를 갱신한다.
        if (Build.VERSION.SDK_INT < 34 && started && !closing) {
            val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return
            val context = createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            val dpi = context.resources.configuration.densityDpi
            capture.post { if (!closing) runCatching { densityDpi = dpi; resize(bounds.width(), bounds.height()) }.onFailure { fail("RESIZE", it) } }
        }
    }
    override fun onDestroy() {
        closing = true
        main.removeCallbacks(timeout)
        ProbePixelLog.stop()
        capture.post {
            try {
                fun release(name: String, action: () -> Unit) {
                    runCatching(action).onFailure { log("PIXEL_RELEASE_FAILED resource=$name ${it.javaClass.simpleName}: ${it.message}") }
                }
                release("listener") { reader?.setOnImageAvailableListener(null, null) }
                release("display") { virtualDisplay?.release() }; virtualDisplay = null
                release("reader") { reader?.close() }; reader = null
                release("callback") { projection?.unregisterCallback(callback) }
                release("projection") { projection?.stop() }; projection = null
                log("PIXEL_CAPTURE_RELEASED frames=$frameNumber")
            } finally { worker.quitSafely() }
        }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val CONSENT = "pixel_capture_consent"
        const val RESULT = "pixel_capture_result"
        const val STOP = "com.easyswitchcontroller.inputprobe.PIXEL_STOP"
        private const val CHANNEL = "probe-screen-pixels"
    }
}
