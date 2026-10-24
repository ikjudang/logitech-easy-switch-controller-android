package com.easyswitchcontroller.ui.notice

import com.easyswitchcontroller.device.SwitchIndicator

/** 연결 안내와 전환 안내에서 기기명·상태 열을 공유한다. 연결 안내에는 헤더를 추가하지 않는다. */
internal open class DeviceNoticeMessage(
    val title: String?,
    rows: List<Row>,
) : CharSequence {
    data class Row(val name: String, val status: String, val indicator: SwitchIndicator)

    val rows = rows.toList()
    private val text = buildList {
        title?.let(::add)
        this@DeviceNoticeMessage.rows.forEach { add("${it.name}\t${it.status}") }
    }.joinToString("\n")

    override val length: Int get() = text.length
    override fun get(index: Int): Char = text[index]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = text.subSequence(startIndex, endIndex)
    override fun toString(): String = text
}
