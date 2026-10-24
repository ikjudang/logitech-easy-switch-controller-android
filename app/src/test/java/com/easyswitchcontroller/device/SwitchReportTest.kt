package com.easyswitchcontroller.device

import org.junit.Assert.assertEquals
import org.junit.Test

class SwitchReportTest {
    @Test fun `같은 종류의 여러 기기도 이름과 결과를 각각 보존한다`() {
        val report = SwitchReport(2, listOf(
            DeviceSwitchStatus("MX Master 3S", DeviceKind.MOUSE, SwitchIndicator.Failure),
            DeviceSwitchStatus("MX Master 4", DeviceKind.MOUSE, SwitchIndicator.Success),
        ))
        assertEquals(listOf("MX Master 3S", "MX Master 4"), report.devices.map { it.name })
        assertEquals(listOf(SwitchIndicator.Failure, SwitchIndicator.Success), report.devices.map { it.indicator })
        assertEquals(SwitchIndicator.Failure, report.mouse)
        assertEquals(SwitchIndicator.NotSelected, report.keyboard)
    }

    @Test fun `한 마우스라도 실패하면 전체 마우스 표시는 실패다`() {
        assertEquals(SwitchIndicator.Failure, summarizeSwitchIndicators(listOf(SwitchIndicator.Success, SwitchIndicator.Failure)))
    }
    @Test fun `전송이 끝났어도 확인하지 못한 기기는 성공으로 표시하지 않는다`() {
        assertEquals(SwitchIndicator.Pending, summarizeSwitchIndicators(listOf(SwitchIndicator.Success, SwitchIndicator.Pending)))
        assertEquals(SwitchIndicator.Pending, confirmedSwitchIndicator(0, 1, null))
    }
    @Test fun `선택하지 않은 종류는 실패가 아니다`() {
        assertEquals(SwitchIndicator.NotSelected, summarizeSwitchIndicators(emptyList()))
    }
    @Test fun `현재 채널을 다시 선택해 연결이 유지되어도 실패가 아니다`() {
        assertEquals(SwitchIndicator.Success, confirmedSwitchIndicator(1, 1, true))
    }
    @Test fun `다른 채널 명령 뒤 연결이 계속 남으면 실패다`() {
        assertEquals(SwitchIndicator.Failure, confirmedSwitchIndicator(0, 1, true))
        assertEquals(SwitchIndicator.Success, confirmedSwitchIndicator(0, 1, false))
    }
}
