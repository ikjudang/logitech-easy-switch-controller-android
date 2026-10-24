package com.easyswitchcontroller.ui.notice

/** 기기명과 상태를 분리해, 안내 창을 갱신해도 상태 열의 정렬을 유지한다. */
internal class SwitchNoticeMessage(
    title: String,
    rows: List<DeviceNoticeMessage.Row>,
    val operationId: Long? = null,
) : DeviceNoticeMessage(title, rows)
