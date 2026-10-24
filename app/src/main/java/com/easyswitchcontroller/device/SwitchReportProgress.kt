package com.easyswitchcontroller.device

/** 같은 전환의 기기별 확인 결과를 누적한다. 대상 조회 중에는 단독 완료로 판단하지 않는다. */
internal class SwitchReportProgress(
    private val channel: Int,
    private val operationId: Long,
    initialTargets: Map<String, DeviceSwitchStatus>,
    targetsKnown: Boolean = true,
    private val onReport: (SwitchReport) -> Unit,
) {
    private val states = LinkedHashMap(initialTargets)
    private val waiting = initialTargets.keys.toMutableSet()
    private var discoveryFinished = targetsKnown
    private var targetsConfirmed = targetsKnown
    private var closed = false

    @Synchronized fun start() { if (!closed) publish() }

    @Synchronized fun discover(targets: Map<String, DeviceSwitchStatus>, confirmed: Boolean) {
        if (closed) return
        targets.forEach { (address, status) ->
            if (address !in states) { states[address] = status; waiting += address }
        }
        discoveryFinished = true
        targetsConfirmed = confirmed
        publish()
    }

    @Synchronized fun resolved(address: String, indicator: SwitchIndicator) {
        if (closed || !waiting.remove(address)) return
        states[address] = states.getValue(address).copy(indicator = indicator)
        publish()
    }

    @Synchronized fun finishUnconfirmed() {
        if (closed) return
        discoveryFinished = true
        targetsConfirmed = false
        waiting.clear()
        publish()
    }

    private fun publish() {
        val pending = !discoveryFinished || waiting.isNotEmpty()
        if (!pending) closed = true
        // 콜백 순서도 같은 잠금 안에서 보존한다. UI 전달은 호출자가 메인 스레드로 예약한다.
        onReport(SwitchReport(channel, states.values.toList(), pending, operationId, targetsConfirmed))
    }
}
