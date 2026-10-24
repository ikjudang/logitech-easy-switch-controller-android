package com.easyswitchcontroller.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 전환 후보 판별 규칙: CoD 주변기기는 남기고, 비주변기기 확실(오디오 등)은 빼고, 미상은 이름으로. */
class SwitchCandidateTest {

    private fun info(deviceClass: Int, likelyLogitech: Boolean = false) =
        BondedDeviceInfo("이름", "AA:BB:CC:DD:EE:01", likelyLogitech, deviceClass)

    @Test
    fun `주변기기 CoD는 이름과 무관하게 남는다`() {
        assertTrue(isSwitchCandidate(info(0x0540))) // keyboard
        assertTrue(isSwitchCandidate(info(0x0580))) // pointing
        assertTrue(isSwitchCandidate(info(0x05C0))) // keyboard+pointing 콤보
    }

    @Test
    fun `비주변기기 CoD는 로지텍 추정 이름이어도 뺀다`() {
        assertFalse(isSwitchCandidate(info(0x0404))) // audio(버즈·헤드셋)
        assertFalse(isSwitchCandidate(info(0x0404, likelyLogitech = true)))
        assertFalse(isSwitchCandidate(info(0x010C))) // computer(노트북)
        assertFalse(isSwitchCandidate(info(0x0408))) // car audio
    }

    @Test
    fun `CoD 미상이면 로지텍 추정 이름일 때만 남는다`() {
        assertTrue(isSwitchCandidate(info(0, likelyLogitech = true)))
        assertFalse(isSwitchCandidate(info(0))) // 버즈의 동반 LE 본딩(CoD 없음) 등
    }

    @Test
    fun `CoD 미분류(0x1F00)도 미상과 같이 이름으로 거른다`() {
        assertTrue(isSwitchCandidate(info(0x1F00, likelyLogitech = true)))
        assertFalse(isSwitchCandidate(info(0x1F00)))
    }
}
