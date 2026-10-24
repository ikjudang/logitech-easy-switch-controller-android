package com.easyswitchcontroller.hidpp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HidppFrameParserTest {

    @Test
    fun `알 수 없는 report_id는 null`() {
        assertNull(HidppFrameParser.parse(hex("99 FF 00 0A 00 00 00")))
    }

    @Test
    fun `SHORT 리포트가 7바이트보다 짧으면 null`() {
        assertNull(HidppFrameParser.parse(hex("10 FF 00")))
    }

    @Test
    fun `HID++ 2_0 에러 프레임을 감지하고 파싱한다`() {
        // [0x10][0xFF][0xFF(에러)][0x0A(func|sw 에코)][0x05(실패 feature)][0x09(UNSUPPORTED)][0x00]
        val raw = hex("10 FF FF 0A 05 09 00")
        assertTrue(HidppFrameParser.isError(raw))

        val info = HidppFrameParser.parseError(raw)!!
        assertEquals(2, info.protocol)
        assertEquals(0x0A, info.funcSwByte)
        assertEquals(0x05, info.failedFeatureIndex)
        assertEquals(0x09, info.errorCode)
        assertEquals(HidppErrorCode.UNSUPPORTED, info.errorCodeEnum)
    }

    @Test
    fun `HID++ 1_0 에러 마커(0x8F)도 에러로 감지된다`() {
        val raw = hex("10 FF 8F 0A 05 03 00")
        assertTrue(HidppFrameParser.isError(raw))
        assertEquals(1, HidppFrameParser.parseError(raw)!!.protocol)
    }

    @Test
    fun `정상 응답은 에러가 아니다`() {
        val raw = hex("10 FF 00 0A 05 00 04")
        assertFalse(HidppFrameParser.isError(raw))
        assertNull(HidppFrameParser.parseError(raw))
    }
}
