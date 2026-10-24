package com.easyswitchcontroller.ui.channel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.easyswitchcontroller.data.BondedDeviceRepository
import com.easyswitchcontroller.data.SelectionStore
import com.easyswitchcontroller.device.currentAclConnection
import com.easyswitchcontroller.device.verifySwitch
import com.easyswitchcontroller.di.ServiceLocator
import com.easyswitchcontroller.ui.AppToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** 홈과 플로팅 메뉴가 같은 전환 작업·준비 작업을 사용한다. Context는 애플리케이션만 보관한다. */
internal object ChannelRuntime : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private var repository: BondedDeviceRepository? = null

    private fun repository(context: Context) = repository ?: BondedDeviceRepository(context.applicationContext).also { repository = it }

    fun get(context: Context): ChannelViewModel {
        val app = context.applicationContext
        return ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val store = SelectionStore(app)
                val snapshot = repository(app).snapshot()
                val selected = store.reconcile(snapshot)
                return ChannelViewModel(
                    devices = snapshot.candidates(selected).filter { it.address in selected }.map {
                        ServiceLocator.device(app, it, ServiceLocator.guessKind(it))
                    },
                    connectedAddresses = ServiceLocator.connectedDevicesMonitor(app).connectedAddresses,
                    initialLabels = store.loadLabels(3),
                    onLabelChanged = { channel, label -> store.saveLabel(channel, label) },
                    onSwitchReport = { AppToast.showSwitchResult(app, it) },
                    verify = { device, target -> verifySwitch(app, device, target) },
                    isConnected = { currentAclConnection(app, it.address) },
                    // 수동으로 시작한 시험 파일은 자동 로그 저장 설정을 바꾸지 않고 별도로 남긴다.
                    saveFirmwareProbeLog = { lines ->
                        withContext(Dispatchers.IO) {
                            val folder = java.io.File(app.filesDir, "firmware-probes").apply { mkdirs() }
                            val stamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss-SSS")
                                .format(java.time.LocalDateTime.now())
                            java.io.File(folder, "firmware-return-$stamp.txt").writeText(lines.joinToString("\n", postfix = "\n"))
                        }
                        com.easyswitchcontroller.data.ConnectionLog.append("[FW-RETURN] 수동 시험 파일 저장 완료")
                    },
                    firmwareProbeEvents = { device -> ServiceLocator.connectedDevicesMonitor(app).aclEvents
                        .filter { it.address == device.address }.map { it.connected } },
                ) as T
            }
        })["channels", ChannelViewModel::class.java]
    }

    suspend fun refresh(context: Context): ChannelViewModel {
        val app = context.applicationContext
        val devices = withContext(Dispatchers.IO) {
            val snapshot = repository(app).snapshot()
            val selected = SelectionStore(app).reconcile(snapshot)
            if (!snapshot.complete) null else snapshot.candidates(selected).filter { it.address in selected }.map {
                ServiceLocator.device(app, it, ServiceLocator.guessKind(it))
            }
        }
        return withContext(Dispatchers.Main.immediate) { get(app).also { if (devices != null) it.updateDevices(devices) } }
    }
}
