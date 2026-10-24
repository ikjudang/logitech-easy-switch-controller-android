package com.easyswitchcontroller.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 기기 재등록(같은 이름, 새 MAC) 시 선택 정리·승계 규칙. */
class SelectionReconcileTest {

    private fun bonded(vararg pairs: Pair<String, String>) =
        pairs.map { (address, name) -> BondedDeviceInfo(name, address, likelyLogitech = true) }

    private val mouse = "AA:BB:CC:DD:EE:01" to "MX Master 4"
    private val keyboard = "AA:BB:CC:DD:EE:02" to "MX MCHNCL"

    @Test
    fun `본딩에 남아 있는 선택은 그대로 유지된다`() {
        val result = reconcileSelection(
            stored = setOf(mouse.first, keyboard.first),
            storedNames = mapOf(mouse.first to mouse.second, keyboard.first to keyboard.second),
            bonded = bonded(mouse, keyboard),
        )
        assertEquals(setOf(mouse.first, keyboard.first), result)
    }

    @Test
    fun `같은 이름으로 재등록하면 새 주소로 승계된다`() {
        val reRegistered = "AA:BB:CC:DD:EE:99" to "MX Master 4"
        val result = reconcileSelection(
            stored = setOf(mouse.first, keyboard.first),
            storedNames = mapOf(mouse.first to mouse.second, keyboard.first to keyboard.second),
            bonded = bonded(reRegistered, keyboard),
        )
        assertEquals(setOf(reRegistered.first, keyboard.first), result)
    }

    @Test
    fun `승계 대상이 없으면 사라진 선택은 버린다`() {
        val result = reconcileSelection(
            stored = setOf(mouse.first, keyboard.first),
            storedNames = mapOf(mouse.first to mouse.second, keyboard.first to keyboard.second),
            bonded = bonded(keyboard),
        )
        assertEquals(setOf(keyboard.first), result)
    }

    @Test
    fun `이름을 기록하지 않은 구버전 선택은 승계 없이 버린다`() {
        val result = reconcileSelection(
            stored = setOf(mouse.first),
            storedNames = emptyMap(),
            bonded = bonded("AA:BB:CC:DD:EE:99" to "MX Master 4"),
        )
        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `같은 이름 후보가 둘이면 애매하므로 승계하지 않는다`() {
        val result = reconcileSelection(
            stored = setOf(mouse.first),
            storedNames = mapOf(mouse.first to mouse.second),
            bonded = bonded(
                "AA:BB:CC:DD:EE:98" to "MX Master 4",
                "AA:BB:CC:DD:EE:99" to "MX Master 4",
            ),
        )
        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `이미 선택된 기기는 승계 대상이 되지 않는다`() {
        // 옛 주소와 이름이 같은 기기가 이미 선택돼 있으면 중복 승계로 카운트가 부풀지 않아야 한다.
        val alreadySelected = "AA:BB:CC:DD:EE:98" to "MX Master 4"
        val result = reconcileSelection(
            stored = setOf(mouse.first, alreadySelected.first),
            storedNames = mapOf(
                mouse.first to mouse.second,
                alreadySelected.first to alreadySelected.second,
            ),
            bonded = bonded(alreadySelected),
        )
        assertEquals(setOf(alreadySelected.first), result)
    }

    @Test
    fun `본딩 목록이 비면 어댑터 꺼짐일 수 있으므로 선택을 건드리지 않는다`() {
        val stored = setOf(mouse.first, keyboard.first)
        val result = reconcileSelection(
            stored = stored,
            storedNames = mapOf(mouse.first to mouse.second),
            bonded = emptyList(),
        )
        assertEquals(stored, result)
    }

    @Test
    fun `이름 자리에 MAC이 들어간 항목은 승계 근거로 쓰지 않는다`() {
        // BondedDeviceRepository는 name을 못 읽으면 주소를 대신 넣는다 — 이걸로 매칭하면 안 된다.
        val noName = "AA:BB:CC:DD:EE:01"
        val result = reconcileSelection(
            stored = setOf(noName),
            storedNames = mapOf(noName to noName),
            bonded = bonded("AA:BB:CC:DD:EE:99" to noName),
        )
        assertEquals(emptySet<String>(), result)
    }
}
