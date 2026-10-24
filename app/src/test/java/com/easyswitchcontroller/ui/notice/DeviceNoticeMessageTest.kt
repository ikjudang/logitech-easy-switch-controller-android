package com.easyswitchcontroller.ui.notice

import com.easyswitchcontroller.device.SwitchIndicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceNoticeMessageTest {
    @Test fun connectedDevicesHaveSeparateNameAndStatusRowsWithoutAnExtraHeader() {
        val message = DeviceNoticeMessage(null, listOf(
            DeviceNoticeMessage.Row("MX Keys", "연결됨", SwitchIndicator.NotSelected),
            DeviceNoticeMessage.Row("MX Master", "연결됨", SwitchIndicator.NotSelected),
        ))
        assertNull(message.title)
        assertEquals(listOf("MX Keys", "MX Master"), message.rows.map { it.name })
        assertEquals(listOf("연결됨", "연결됨"), message.rows.map { it.status })
        assertEquals("MX Keys\t연결됨\nMX Master\t연결됨", message.toString())
    }

    @Test fun aConnectionMessageKeepsItsRowsWhenTheCallersListChanges() {
        val input = mutableListOf(DeviceNoticeMessage.Row("MX Keys", "연결됨", SwitchIndicator.NotSelected))
        val message = DeviceNoticeMessage(null, input)
        input.clear()
        assertEquals(1, message.rows.size)
    }

    @Test fun aSwitchKeepsItsOperationIdentityWhenStatusesChange() {
        fun message(status: String, indicator: SwitchIndicator) = SwitchNoticeMessage("채널 2 전환",
            listOf(DeviceNoticeMessage.Row("MX Keys", status, indicator)), operationId = 42L)
        val before = message("전환 중", SwitchIndicator.Pending)
        val after = message("완료", SwitchIndicator.Success)
        assertEquals(before.operationId, after.operationId)
        assertEquals(before.title, after.title)
        assertEquals(before.rows.map { it.name }, after.rows.map { it.name })
        assertEquals("채널 2 전환\nMX Keys\t완료", after.toString())
    }
}
