package com.easyswitchcontroller.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.easyswitchcontroller.R
import com.easyswitchcontroller.ui.AppToast
import com.easyswitchcontroller.ui.theme.Tokens

/** BLUETOOTH_CONNECT 권한 게이트. 허용되면 [content]를, 아니면 요청 화면을 보여준다. */
@Composable
fun BluetoothConnectGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var requested by rememberSaveable { mutableStateOf(false) }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // 거부의 종류를 가르기 위한 기록 — 뒤로가기로 권한 창을 닫아도 결과는 '거부'로 온다.
    var rationaleSeen by rememberSaveable { mutableStateOf(false) }
    var silentDenials by rememberSaveable { mutableStateOf(0) }
    fun rationale() = (context as? Activity)?.shouldShowRequestPermissionRationale(
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == true
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        requested = true
        if (!result) { if (rationale()) rationaleSeen = true else silentDenials++ }
    }
    // 권한 창을 더 못 띄우는 경우만 설정 화면으로 보낸다: 설명 표시가 뜬 뒤 다시 거부됐거나(다시 묻지 않음),
    // 설명 없이 두 번 거부됐을 때(처음부터 막혀 있던 경우)다. 창을 한 번 닫은 것만으로는 보내지 않는다.
    val openSettings = requested && !rationale() && (rationaleSeen || silentDenials >= 2)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (granted) {
        content()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.bt_perm_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Tokens.Text,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(if (openSettings) R.string.bt_perm_settings_body else R.string.bt_perm_body),
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = Tokens.Text2,
                modifier = Modifier.widthIn(max = 360.dp),
            )
            Spacer(Modifier.height(16.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .widthIn(min = 200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Tokens.Point, RoundedCornerShape(16.dp))
                    .clickable {
                        if (openSettings) {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.onFailure { AppToast.show(context, R.string.bt_perm_settings_failed) }
                        } else launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            ) {
                Text(
                    text = stringResource(if (openSettings) R.string.bt_perm_open_settings else R.string.bt_perm_grant),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}
