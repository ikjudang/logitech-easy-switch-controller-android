package com.easyswitchcontroller.hidpp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChangeHostFeatureTest {
    @Test
    fun `host flags preserve unknown bits and read enhanced switch bit independently`() {
        val frame = HidppFrame.build(featureIndex = 5, functionId = 0, params = byteArrayOf(3, 2, 0x81.toByte()))
        val info = ChangeHostFeature.parseGetHostInfo(frame)
        assertEquals(0x81, info.flags)
        assertEquals(true, info.enhancedHostSwitch)
        assertEquals(false, info.copy(flags = 0x80).enhancedHostSwitch)
    }

    @Test
    fun `cookie diagnostics send function2 only and decode unsigned bytes`() {
        assertArrayEquals(hex("10 FF 05 2A 00 00 00"), ChangeHostFeature.buildGetCookies(5))
        val frame = HidppFrame.build(featureIndex = 5, functionId = 2, params = byteArrayOf(0, 0x80.toByte(), 0xFF.toByte()))
        assertEquals(listOf(0, 128, 255), ChangeHostFeature.parseCookies(frame, 3))
    }

    @Test
    fun `잡음의 잘못된 채널은 마우스 전환을 만들지 않는다`() {
        val invalidChannel = HidppFrameParser.parse(hex("11 FF 09 00 00 FF 00 00 00 00 00 00 00 00 00 00 00 00 00 00"))!!
        // 실측 잡음(MX MCHNCL, 2026-07-22 logcat): params=[4B, 08]
        val measuredNoise = HidppFrameParser.parse(hex("11 FF 09 00 4B 08 00 00 00 00 00 00 00 00 00 00 00 00 00 00"))!!
        assertNull(ChangeHostFeature.parseHostChangeEvent(invalidChannel, 9))
        assertNull(ChangeHostFeature.parseHostChangeEvent(measuredNoise, 9))
    }

    @Test
    fun `params0이 0이 아니어도 목적지는 params1로 읽는다`() {
        // params[0]으로 거르던 0.2.17~0.2.19에서 MX KEYS S의 전환 감지가 0건이었다(2026-10-19).
        val frame = HidppFrameParser.parse(hex("11 FF 0A 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"))!!
        assertEquals(0, ChangeHostFeature.parseHostChangeEvent(frame, changeHostFeatureIndex = 0x0A))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `잘못된 현재 호스트 정보는 기본 채널 1로 오인하지 않는다`() {
        val invalid = HidppFrameParser.parse(hex("11 FF 09 0A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"))!!
        ChangeHostFeature.parseGetHostInfo(invalid)
    }

    @Test
    fun `setCurrentHost 채널1(host 0) 바이트가 골든벡터와 일치한다`() {
        // featureIndex 0x05, fn 1, sw 0x0A, hostIndex 0
        // [0x10][0xFF][0x05][fn1|sw0A=0x1A][0x00][0x00][0x00]
        val bytes = ChangeHostFeature.buildSetCurrentHost(featureIndex = 0x05, hostIndex = 0)
        assertArrayEquals(hex("10 FF 05 1A 00 00 00"), bytes)
    }

    @Test
    fun `setCurrentHost 채널3(host 2) 바이트`() {
        val bytes = ChangeHostFeature.buildSetCurrentHost(featureIndex = 0x05, hostIndex = 2)
        assertArrayEquals(hex("10 FF 05 1A 02 00 00"), bytes)
    }

    @Test
    fun `feature index가 다르면 세번째 바이트가 그에 맞게 바뀐다`() {
        val bytes = ChangeHostFeature.buildSetCurrentHost(featureIndex = 0x0C, hostIndex = 1)
        assertArrayEquals(hex("10 FF 0C 1A 01 00 00"), bytes)
    }

    @Test
    fun `getHostInfo 바이트(fn 0)`() {
        val bytes = ChangeHostFeature.buildGetHostInfo(featureIndex = 0x05)
        // fn 0 | sw 0x0A = 0x0A
        assertArrayEquals(hex("10 FF 05 0A 00 00 00"), bytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `음수 hostIndex는 거부된다`() {
        ChangeHostFeature.buildSetCurrentHost(featureIndex = 0x05, hostIndex = -1)
    }

    @Test
    fun `getHostInfo 응답에서 호스트 수와 현재 호스트를 파싱한다`() {
        // params[0]=3(호스트 수), params[1]=1(현재 host, 0-based)
        val frame = HidppFrameParser.parse(hex("11 FF 09 0A 03 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00"))!!
        val info = ChangeHostFeature.parseGetHostInfo(frame)
        assertEquals(3, info.hostCount)
        assertEquals(1, info.currentHost)
    }

    // ---- parseHostChangeEvent (MX MCHNCL 실측 골든벡터: 전환 직전 이벤트) ----

    @Test
    fun `전환 이벤트에서 목적지 host를 파싱한다`() {
        // 실측: feat=0x09(0x1814 index), funcSw=0x00, params=[00, target]
        val frame = HidppFrameParser.parse(hex("11 FF 09 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00"))!!
        assertEquals(1, ChangeHostFeature.parseHostChangeEvent(frame, changeHostFeatureIndex = 0x09))

        val frame2 = HidppFrameParser.parse(hex("11 FF 09 00 00 02 00 00 00 00 00 00 00 00 00 00 00 00 00 00"))!!
        assertEquals(2, ChangeHostFeature.parseHostChangeEvent(frame2, changeHostFeatureIndex = 0x09))
    }

    @Test
    fun `다른 feature의 이벤트는 무시한다`() {
        // 실측 잡음: 배터리(feat=0x07) 이벤트
        val frame = HidppFrameParser.parse(hex("11 FF 07 00 64 08 00 00 00 00 00 00 00 00 00 00 00 00 00 00"))!!
        assertNull(ChangeHostFeature.parseHostChangeEvent(frame, changeHostFeatureIndex = 0x09))
    }

    @Test
    fun `swId가 에코된 응답(이벤트 아님)은 무시한다`() {
        // funcSw=0x0A(우리 요청의 에코)는 이벤트가 아니다
        val frame = HidppFrameParser.parse(hex("11 FF 09 0A 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00"))!!
        assertNull(ChangeHostFeature.parseHostChangeEvent(frame, changeHostFeatureIndex = 0x09))
    }
}
