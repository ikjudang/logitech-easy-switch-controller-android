package com.easyswitchcontroller.hidpp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidppFrameTest {

    @Test
    fun `SHORT 프레임은 7바이트, 헤더+파라미터 배치가 정확하다`() {
        val frame = HidppFrame.build(
            reportType = ReportType.SHORT,
            deviceIndex = 0xFF,
            featureIndex = 0x05,
            functionId = 0x01,
            softwareId = 0x0A,
            params = byteArrayOf(0x02),
        )
        // [0x10][0xFF][0x05][func1|sw0A=0x1A][0x02][0x00][0x00]
        assertArrayEquals(hex("10 FF 05 1A 02 00 00"), frame.toBytes())
        assertEquals(7, frame.toBytes().size)
    }

    @Test
    fun `LONG 프레임은 20바이트로 0 패딩된다`() {
        val frame = HidppFrame.build(
            reportType = ReportType.LONG,
            featureIndex = 0x00,
            functionId = 0x00,
            softwareId = 0x0A,
            params = byteArrayOf(0x18, 0x14),
        )
        val bytes = frame.toBytes()
        assertEquals(20, bytes.size)
        assertEquals(0x11.toByte(), bytes[0])
        assertArrayEquals(hex("18 14"), bytes.copyOfRange(4, 6))
    }

    @Test
    fun `func_sw 바이트에서 functionId 상위, softwareId 하위 nibble이 분리된다`() {
        val frame = HidppFrame.build(featureIndex = 0x05, functionId = 0x03, softwareId = 0x0A)
        assertEquals(0x3A, frame.funcSwByte)
        assertEquals(0x03, frame.functionId)
        assertEquals(0x0A, frame.softwareId)
    }

    @Test
    fun `toBytes-parse 왕복이 동일하다`() {
        val original = HidppFrame.build(
            featureIndex = 0x07,
            functionId = 0x01,
            softwareId = 0x0A,
            params = byteArrayOf(0x01),
        )
        val parsed = HidppFrameParser.parse(original.toBytes())
        assertEquals(original, parsed)
    }

    @Test
    fun `echoes는 device_feature_funcSw가 모두 일치할 때만 참`() {
        val response = HidppFrame.build(featureIndex = 0x00, functionId = 0x00, softwareId = 0x0A)
        // response.deviceIndex는 기본값 DEVICE_INDEX_BLE(0xFF)
        assertTrue(response.echoes(deviceIndex = 0xFF, featureIndex = 0x00, funcSwByte = 0x0A))
        assertFalse(response.echoes(deviceIndex = 0xFF, featureIndex = 0x00, funcSwByte = 0x0B))
        assertFalse(response.echoes(deviceIndex = 0x00, featureIndex = 0x00, funcSwByte = 0x0A))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `softwareId 0은 거부된다`() {
        HidppFrame.build(featureIndex = 0x05, functionId = 0x01, softwareId = 0x00)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `functionId nibble 초과는 거부된다`() {
        HidppFrame.build(featureIndex = 0x05, functionId = 0x10, softwareId = 0x0A)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `params가 SHORT 용량을 초과하면 거부된다`() {
        HidppFrame.build(
            reportType = ReportType.SHORT,
            featureIndex = 0x05,
            functionId = 0x01,
            softwareId = 0x0A,
            params = ByteArray(4), // SHORT paramLength는 3
        )
    }
}
