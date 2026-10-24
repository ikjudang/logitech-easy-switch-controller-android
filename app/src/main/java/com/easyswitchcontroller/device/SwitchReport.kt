package com.easyswitchcontroller.device

enum class SwitchIndicator(val symbol: String) {
    Success("○"), Failure("×"), Pending("…"), NotSelected("—"),
}

data class DeviceSwitchStatus(
    val name: String,
    val kind: DeviceKind,
    val indicator: SwitchIndicator,
)

data class SwitchReport(
    val channel: Int,
    val devices: List<DeviceSwitchStatus>,
    val inProgress: Boolean = false,
    val operationId: Long? = null,
    val targetsConfirmed: Boolean = true,
) {
    val keyboard: SwitchIndicator get() = summarizeSwitchIndicators(
        devices.filter { it.kind == DeviceKind.KEYBOARD }.map { it.indicator },
    )
    val mouse: SwitchIndicator get() = summarizeSwitchIndicators(
        devices.filter { it.kind == DeviceKind.MOUSE }.map { it.indicator },
    )
}

fun summarizeSwitchIndicators(values: List<SwitchIndicator>): SwitchIndicator = when {
    values.isEmpty() -> SwitchIndicator.NotSelected
    SwitchIndicator.Failure in values -> SwitchIndicator.Failure
    values.all { it == SwitchIndicator.Success } -> SwitchIndicator.Success
    else -> SwitchIndicator.Pending
}

/** 알 수 없는 연결 상태를 성공/실패로 단정하지 않는다. 현재 채널 재선택은 연결 유지가 정상이다. */
fun confirmedSwitchIndicator(localHost: Int?, targetHost: Int, connected: Boolean?): SwitchIndicator = when {
    localHost == targetHost && connected == true -> SwitchIndicator.Success
    connected == false -> SwitchIndicator.Success
    localHost == null -> SwitchIndicator.Pending
    connected == true -> SwitchIndicator.Failure
    else -> SwitchIndicator.Pending
}
