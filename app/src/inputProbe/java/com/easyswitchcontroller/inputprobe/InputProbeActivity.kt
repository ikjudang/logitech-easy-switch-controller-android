package com.easyswitchcontroller.inputprobe

import com.easyswitchcontroller.floating.FloatingMenuGeometry
import com.easyswitchcontroller.floating.FloatingInputSession

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.net.toUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InputProbeActivity : Activity() {
    private var inputTarget: View? = null
    private var pixelButton: Button? = null
    private val pixelStatusRefresh = object : Runnable {
        override fun run() {
            updatePixelButton()
            window.decorView.postDelayed(this, 500L)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProbeLog.attach(this)
        ProbeLog.append("ACTIVITY_CREATED")
        window.statusBarColor = Color.rgb(15, 18, 22)
        window.navigationBarColor = Color.rgb(15, 18, 22)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
            setBackgroundColor(Color.rgb(15, 18, 22))
        }
        fun text(message: String, size: Float = 14f) = TextView(this).apply {
            text = message; textSize = size; setTextColor(Color.rgb(238, 241, 245))
            setPadding(0, 0, 0, dp(16))
            content.addView(this)
        }
        fun button(title: String, action: () -> Unit) = Button(this).apply {
            text = title; isAllCaps = false; setOnClickListener { action() }
            content.addView(this, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(8) })
        }
        text("EasySwitch Input Probe", 23f)
        text("이 앱은 입력 검증용 별도 설치본입니다. 앱이 검증 결과를 파일에 자동 기록합니다. 아래 순서로 시험한 뒤 로그를 저장해 주세요.")
        button("1. 다른 앱 위에 표시 허용") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
        }
        button("2. 입력 검증 시작") {
            ProbeLog.append("USER_START overlay=${Settings.canDrawOverlays(this)}")
            runCatching { startForegroundService(Intent(this, InputProbeService::class.java)) }
                .onFailure { ProbeLog.append("SERVICE_START_FAILED ${it.javaClass.simpleName}: ${it.message}") }
        }
        pixelButton = button("화면 픽셀 로그 시작") {
            if (ProbePixelLog.active) {
                stopService(Intent(this, ProbePixelCaptureService::class.java))
            } else if (ProbeSession.running) {
                val manager = getSystemService(MediaProjectionManager::class.java)
                val intent = if (Build.VERSION.SDK_INT >= 34) manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                    else manager.createScreenCaptureIntent()
                startActivityForResult(intent, PIXEL_CONSENT)
            }
            updatePixelButton()
        }
        text("픽셀 로그가 필요하면 입력 검증을 시작한 뒤 위 버튼을 누르고 화면 캡처를 허용해 주세요. 앱이 플로팅 버튼과 최근 이동 경로의 실제 화면을 자동 저장합니다. 해당 영역의 다른 앱 내용도 이미지에 포함됩니다. 기록은 10분 후 자동 종료됩니다. 최근 이미지 최대 600장·128MB를 보관합니다.")
        text("앱 화면 시험: 아래 시험 영역에서 좌우 버튼을 동시에 누르고 모두 떼세요. 표시된 버튼을 숨긴 뒤 같은 동작을 반복해 주세요. 채널 버튼을 누르면 앱이 선택만 기록합니다.")
        val target = button("마우스 입력 시험 영역") { ProbeLog.append("APP_LEFT_CLICK_DELIVERED") }
        inputTarget = target
        target.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateTestArea() }
        target.setOnGenericMotionListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS && event.actionButton == MotionEvent.BUTTON_SECONDARY) {
                ProbeLog.append("APP_SECONDARY_EVENT_DELIVERED buttons=${event.buttonState}")
            }
            false
        }
        // 다른 버튼의 고정 높이(54dp)를 상속하므로 최소 높이뿐 아니라 실제 배치 높이도 바꾼다.
        target.layoutParams = target.layoutParams.apply { height = dp(162) }
        target.minHeight = dp(162)
        target.background = GradientDrawable().apply { setColor(Color.rgb(32, 38, 45)); cornerRadius = dp(16).toFloat() }
        text("홈·다른 앱 시험: 검증을 시작한 뒤 홈 화면이나 다른 앱에서 좌우 동시 클릭을 반복하세요. 그다음 이 앱으로 돌아와 로그를 저장해 주세요.")
        button("플로팅 버튼 숨기기") {
            if (ProbeSession.running) startService(Intent(this, InputProbeService::class.java).setAction(InputProbeService.HIDE))
        }
        button("검증 로그 저장") {
            ProbeLog.append("EXPORT_REQUEST")
            val time = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.ROOT).format(Date())
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "easyswitch-inputprobe-${Build.MODEL}-$time.txt")
            }, EXPORT)
        }
        button("픽셀 이미지와 로그 ZIP 저장") {
            stopService(Intent(this, ProbePixelCaptureService::class.java))
            ProbeLog.append("PIXEL_EXPORT_REQUEST")
            val time = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.ROOT).format(Date())
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, "easyswitch-inputprobe-pixels-${Build.MODEL}-$time.zip")
            }, EXPORT_PIXELS)
        }
        button("입력 검증 종료") { stopService(Intent(this, InputProbeService::class.java)) }
        setContentView(ScrollView(this).apply {
            addView(content)
            viewTreeObserver.addOnScrollChangedListener { updateTestArea() }
        })
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        updateTestArea()
        ProbeSession.acceptAppEvent(event, display?.displayId ?: 0)
        return super.dispatchTouchEvent(event)
    }
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        updateTestArea()
        ProbeSession.acceptAppEvent(event, display?.displayId ?: 0)
        return super.dispatchGenericMotionEvent(event)
    }
    @Deprecated("Activity result compatibility for this isolated probe")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PIXEL_CONSENT) {
            if (resultCode == RESULT_OK && data != null && ProbeSession.running) {
                runCatching {
                    startForegroundService(Intent(this, ProbePixelCaptureService::class.java)
                        .putExtra(ProbePixelCaptureService.CONSENT, data).putExtra(ProbePixelCaptureService.RESULT, resultCode))
                }.onFailure { ProbeLog.append("PIXEL_SERVICE_FAILED ${it.javaClass.simpleName}: ${it.message}") }
            } else ProbeLog.append("PIXEL_CONSENT_CANCELLED result=$resultCode probeRunning=${ProbeSession.running}")
            return
        }
        if (requestCode == EXPORT_PIXELS && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val context = applicationContext
            // PNG를 묶거나 Drive 문서 공급자에 쓸 때 입력·애니메이션 스레드를 막지 않는다.
            Thread({
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { ProbePixelLog.export(context, it) }
                        ?: error("No output stream")
                }.onSuccess { ProbeLog.append("PIXEL_EXPORT_WRITTEN") }
                    .onFailure { ProbeLog.append("PIXEL_EXPORT_FAILED ${it.javaClass.simpleName}: ${it.message}") }
            }, "probe-pixel-export").start()
            return
        }
        if (requestCode == EXPORT && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(ProbeLog.snapshot().toByteArray(Charsets.UTF_8)) }
                    ?: error("No output stream")
            }.onSuccess { ProbeLog.append("EXPORT_WRITTEN") }
                .onFailure { ProbeLog.append("EXPORT_FAILED ${it.javaClass.simpleName}: ${it.message}") }
        }
    }
    override fun onResume() {
        super.onResume(); ProbeLog.append("ACTIVITY_RESUMED")
        window.decorView.removeCallbacks(pixelStatusRefresh)
        pixelStatusRefresh.run()
    }
    override fun onPause() {
        window.decorView.removeCallbacks(pixelStatusRefresh)
        ProbeLog.append("ACTIVITY_PAUSED"); ProbeSession.testArea = null; ProbeSession.resetAppInput(); super.onPause()
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) ProbeSession.resetAppInput()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun updatePixelButton() {
        val title = if (ProbePixelLog.active) "화면 픽셀 로그 종료" else "화면 픽셀 로그 시작"
        if (pixelButton?.text != title) pixelButton?.text = title
        pixelButton?.isEnabled = ProbeSession.running || ProbePixelLog.active
    }
    private fun updateTestArea() {
        val target = inputTarget ?: return
        val visible = Rect()
        val area = if (target.getLocalVisibleRect(visible) && visible.width() >= dp(144) && visible.height() >= dp(144)) {
            val location = IntArray(2)
            target.getLocationOnScreen(location)
            FloatingInputSession.TestArea(display?.displayId ?: 0, FloatingMenuGeometry.Bounds(
                (location[0] + visible.left).toFloat(), (location[1] + visible.top).toFloat(),
                (location[0] + visible.right).toFloat(), (location[1] + visible.bottom).toFloat()))
        } else null
        if (ProbeSession.testArea != area) {
            ProbeSession.testArea = area
            ProbeLog.append("TEST_AREA_BOUNDS display=${area?.displayId} bounds=${area?.bounds}")
        }
    }
    companion object {
        private const val EXPORT = 81
        private const val PIXEL_CONSENT = 82
        private const val EXPORT_PIXELS = 83
    }
}
