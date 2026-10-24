package com.easyswitchcontroller.device

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.sync.Mutex

/** 수동 전환과 따라가기의 중복 명령, 이전 전환의 늦은 토스트를 막는다. */
object SwitchTransactions {
    val mutex = Mutex()
    /** 복귀 시험은 이 잠금을 보유해 마우스 상시 연결의 재접속을 관찰 구간에서 제외한다. */
    internal val mouseMaintenanceMutex = Mutex()
    private val firmwareProbe = AtomicReference<String?>(null)
    val firmwareProbeActive: Boolean get() = firmwareProbe.get() != null
    internal fun reserveFirmwareProbe(address: String): Boolean = firmwareProbe.compareAndSet(null, address)
    internal fun releaseFirmwareProbe(address: String) { firmwareProbe.compareAndSet(address, null) }
    private val sequence = AtomicLong()
    private val manualRequests = ConcurrentHashMap<Int, Long>()
    @Volatile private var activeManualTarget: Int? = null
    fun begin(): Long = sequence.incrementAndGet()
    fun isCurrent(id: Long): Boolean = sequence.get() == id
    fun noteManual(target: Int) { manualRequests[target] = System.nanoTime() }
    fun startManual(target: Int) { activeManualTarget = target; noteManual(target) }
    fun finishManual(target: Int) { noteManual(target); activeManualTarget = null }
    fun isRecentManual(target: Int): Boolean = activeManualTarget == target || manualRequests[target]?.let {
        System.nanoTime() - it < 2_000_000_000L
    } ?: false
}
