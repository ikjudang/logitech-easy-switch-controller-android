package com.easyswitchcontroller.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceInventoryRegressionTest {
    @Test
    fun `삭제 후 재등록 사이에 빈 목록을 읽어도 선택을 새 주소로 복원한다`() {
        val names = mapOf("OLD" to "MX Mechanical")
        val removed = resolveSelection(setOf("OLD"), names, BondedDeviceSnapshot(emptyList(), true))
        assertEquals(emptySet<String>(), removed.active)
        assertEquals(setOf("OLD"), removed.remembered)
        val registered = BondedDeviceInfo("MX Mechanical", "NEW", true, 0x0540)
        val restored = resolveSelection(removed.remembered, names, BondedDeviceSnapshot(listOf(registered), true))
        assertEquals(setOf("NEW"), restored.active)
        assertEquals(setOf("NEW"), restored.remembered)
    }

    @Test
    fun `조회 실패는 선택 의도를 삭제하지 않으며 보이지 않는 주소를 선택 수에 포함하지 않는다`() {
        val result = resolveSelection(setOf("OLD"), emptyMap(), BondedDeviceSnapshot(emptyList(), false))
        assertEquals(setOf("OLD"), result.remembered)
        assertEquals(emptySet<String>(), result.active)
    }
    private val mouse = BondedDeviceInfo("MX Master 4", "M", true, 0x0580)
    private val keyboard = BondedDeviceInfo("My keyboard", "K", false, 0, deviceType = 0)

    @Test
    fun `선택된 기기는 일시적인 종류와 이름 판별 실패로 후보에서 사라지지 않는다`() {
        val snapshot = BondedDeviceSnapshot(listOf(mouse, keyboard), true)
        assertEquals(setOf("M", "K"), snapshot.candidates(setOf("K")).map { it.address }.toSet())
    }

    @Test
    fun `선택 삭제는 후보 필터가 아니라 전체 본딩 목록을 기준으로 한다`() {
        val snapshot = BondedDeviceSnapshot(listOf(mouse, keyboard), true)
        val stored = setOf("M", "K")
        val selected = reconcileSelection(stored, emptyMap(), snapshot.devices, inventoryComplete = true)
        assertEquals(stored, selected)
    }

    @Test
    fun `블루투스 타입 미상이어도 키보드 CoD가 있으면 후보에 남는다`() {
        val snapshot = BondedDeviceSnapshot(listOf(keyboard.copy(deviceClass = 0x0540)), true)
        assertEquals(listOf("K"), snapshot.candidates().map { it.address })
    }

    @Test
    fun `본딩 전체가 실제로 삭제되었으면 오래된 선택도 정리한다`() {
        assertEquals(emptySet<String>(), reconcileSelection(setOf("M"), emptyMap(), emptyList(), true))
    }

    @Test
    fun `일시적으로 이름 대신 주소를 읽어도 마지막 기기 이름을 보존한다`() {
        assertEquals(mapOf("K" to "MX Mechanical"), selectionNames(
            setOf("K"), mapOf("K" to "MX Mechanical"), listOf(keyboard.copy(name = "K")),
        ))
    }
}
