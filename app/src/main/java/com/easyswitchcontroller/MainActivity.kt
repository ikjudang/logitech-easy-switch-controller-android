package com.easyswitchcontroller

import android.graphics.Color
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.easyswitchcontroller.data.ConnectionLog
import com.easyswitchcontroller.floating.FloatingInputSession
import com.easyswitchcontroller.floating.FloatingPreferences
import kotlinx.coroutines.launch
import com.easyswitchcontroller.service.FollowService
import com.easyswitchcontroller.ui.AppRoot
import com.easyswitchcontroller.ui.AppToast
import com.easyswitchcontroller.ui.notice.ForegroundNoticeOverlay

/**
 * AppCompatActivity 사용 이유: 앱별 언어 선택(AppCompatDelegate.setApplicationLocales)이
 * API 31~32에서 AppCompat 경유로만 동작한다(33+는 시스템이 처리).
 */
class MainActivity : AppCompatActivity() {
    private val notice by lazy { ForegroundNoticeOverlay(this) }
    private val floatingNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        FollowService.start(this)
    }

    private fun requestFloatingNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || !FloatingPreferences.load(this).enabled ||
            !Settings.canDrawOverlays(this) || FloatingPreferences.notificationPermissionRequested(this) ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        FloatingPreferences.markNotificationPermissionRequested(this)
        floatingNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        FloatingInputSession.acceptAppEvent(event, display?.displayId ?: 0)
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        FloatingInputSession.acceptAppEvent(event, display?.displayId ?: 0)
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FloatingInputSession.appWindowFocused = hasFocus
        if (!hasFocus) FloatingInputSession.resetAppInput()
    }

    override fun onResume() {
        super.onResume()
        // 화면에 들어올 때마다 따라가기 서비스를 켠다(이미 돌고 있으면 그대로). 블루투스 권한을
        // 막 허용한 직후에도 권한 창이 닫히며 여기로 돌아오므로 첫 실행에서 바로 켜진다.
        FollowService.start(this)
        requestFloatingNotificationPermission()
    }

    override fun onPostResume() {
        super.onPostResume()
        // 첫 resume의 onPostResume은 창이 WindowManager에 붙기 전에 불려 팝업을 띄울 토큰이 없다.
        // 그때 대기 중이던 안내가 있으면 붙이기에 실패하고 그대로 사라진다(키보드가 붙거나 떨어져
        // 화면이 다시 만들어질 때). 창이 붙은 뒤에 연결한다.
        window.decorView.post {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) AppToast.attachForeground(notice)
        }
    }

    override fun onStop() {
        FloatingInputSession.appWindowFocused = false
        FloatingInputSession.resetAppInput()
        AppToast.detachForeground(notice)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 로그 파일을 연결한다 — 프로세스가 죽었다 살아난 흔적이 남아야 사후 진단이 된다.
        ConnectionLog.attach(this, getString(R.string.log_source_app))
        // 앱이 항상 다크(핸드오프 확정)라 시스템 바 아이콘도 밝은색으로 고정한다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            AppRoot()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                FloatingPreferences.revision.collect { requestFloatingNotificationPermission() }
            }
        }
    }
}
