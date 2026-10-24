package com.easyswitchcontroller.hidpp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootFeatureTest {

    @Test
    fun `getFeature(0x1814) 요청 바이트가 골든벡터와 일치한다`() {
        // Root(index 0x00), fn 0, sw 0x0A, params=[0x18,0x14]
        // [0x10][0xFF][0x00][0x0A][0x18][0x14][0x00]
        val bytes = RootFeature.buildGetFeature(HidppConstants.FEATURE_CHANGE_HOST)
        assertArrayEquals(hex("10 FF 00 0A 18 14 00"), bytes)
    }

    @Test
    fun `getFeature는 16-bit featureId를 hi_lo로 분해한다`() {
        val bytes = RootFeature.buildGetFeature(HidppConstants.FEATURE_HOSTS_INFO) // 0x1815
        assertArrayEquals(hex("10 FF 00 0A 18 15 00"), bytes)
    }

    @Test
    fun `getFeature 응답에서 feature index를 읽는다`() {
        // 응답 params[0]=0x05(index), [1]=0x00(flags), [2]=0x04(version)
        val response = HidppFrameParser.parse(hex("10 FF 00 0A 05 00 04"))!!
        val lookup = RootFeature.parseGetFeatureResponse(response)
        assertEquals(0x05, lookup.featureIndex)
        assertEquals(0x04, lookup.version)
        assertTrue(lookup.isSupported)
    }

    @Test
    fun `feature index 0은 미지원으로 판정된다`() {
        val response = HidppFrameParser.parse(hex("10 FF 00 0A 00 00 00"))!!
        val lookup = RootFeature.parseGetFeatureResponse(response)
        assertEquals(0, lookup.featureIndex)
        assertFalse(lookup.isSupported)
    }
}
