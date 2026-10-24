package com.easyswitchcontroller.hidpp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReprogKeysFeatureTest {

    @Test
    fun `getCount 바이트(fn0)`() {
        // featureIndex 0x0C, fn0|sw0A=0x0A
        assertArrayEquals(hex("10 FF 0C 0A 00 00 00"), ReprogKeysFeature.buildGetCount(0x0C))
    }

    @Test
    fun `getCidInfo 바이트(fn1, index)`() {
        // fn1|sw0A=0x1A, index 2
        assertArrayEquals(hex("10 FF 0C 1A 02 00 00"), ReprogKeysFeature.buildGetCidInfo(0x0C, 2))
    }

    @Test
    fun `setCidReporting divert on 바이트(fn3, cid, flags=0x03)`() {
        // fn3|sw0A=0x3A, cid 0x00C3, dvalid|divert=0x03
        assertArrayEquals(
            hex("10 FF 0C 3A 00 C3 03"),
            ReprogKeysFeature.buildSetCidReporting(0x0C, 0x00C3, divert = true),
        )
    }

    @Test
    fun `setCidReporting divert off 바이트(flags=0x02)`() {
        assertArrayEquals(
            hex("10 FF 0C 3A 00 C3 02"),
            ReprogKeysFeature.buildSetCidReporting(0x0C, 0x00C3, divert = false),
        )
    }

    @Test
    fun `parseGetCount는 params 첫 바이트를 읽는다`() {
        val resp = HidppFrameParser.parse(hex("11 FF 0C 0A 18 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"))!!
        assertEquals(0x18, ReprogKeysFeature.parseGetCount(resp))
    }

    @Test
    fun `parseCidInfo는 cid_task_flags를 뽑고 divertable을 판정한다`() {
        // cid=0x00C3, task=0x0009, flags=0x31 (divertable 0x20 포함)
        val resp = HidppFrameParser.parse(hex("11 FF 0C 1A 00 C3 00 09 31 00 00 00 00 00 00 00 00 00 00 00"))!!
        val info = ReprogKeysFeature.parseCidInfo(resp)
        assertEquals(0x00C3, info.cid)
        assertEquals(0x0009, info.taskId)
        assertEquals(0x31, info.flags)
        assertTrue(info.divertable)
    }

    @Test
    fun `divertable 플래그가 없으면 false`() {
        // flags=0x11 (divertable 0x20 미포함)
        val resp = HidppFrameParser.parse(hex("11 FF 0C 1A 00 50 00 01 11 00 00 00 00 00 00 00 00 00 00 00"))!!
        assertFalse(ReprogKeysFeature.parseCidInfo(resp).divertable)
    }

    @Test
    fun `divertedButtonsEvent에서 눌린 cid들을 파싱한다`() {
        // 눌린 cid 0x00C3 하나, 나머지 0
        val resp = HidppFrameParser.parse(hex("11 FF 0C 00 00 C3 00 00 00 00 00 00 00 00 00 00 00 00 00 00"))!!
        assertEquals(listOf(0x00C3), ReprogKeysFeature.parseDivertedButtons(resp))
    }

    @Test
    fun `모두 뗀 이벤트(cid 전부 0)는 빈 리스트`() {
        val resp = HidppFrameParser.parse(hex("11 FF 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"))!!
        assertTrue(ReprogKeysFeature.parseDivertedButtons(resp).isEmpty())
    }
}
