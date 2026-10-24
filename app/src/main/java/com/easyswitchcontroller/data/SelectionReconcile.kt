package com.easyswitchcontroller.data

/** 이름을 잠시 못 읽어도 재등록 승계에 필요한 마지막 실제 이름을 보존한다. */
internal fun selectionNames(
    selected: Set<String>,
    previous: Map<String, String>,
    known: List<BondedDeviceInfo>,
): Map<String, String> {
    val byAddress = known.associateBy { it.address }
    return selected.mapNotNull { address ->
        val name = byAddress[address]?.name?.takeIf { it != address && it.isNotBlank() }
            ?: previous[address] ?: return@mapNotNull null
        address to name
    }.toMap()
}

internal data class SelectionResolution(val active: Set<String>, val remembered: Set<String>)

/** 재등록 전의 공백에서도 선택 의도는 보존하되, 화면의 선택 수에는 현재 본딩만 포함한다. */
internal fun resolveSelection(
    stored: Set<String>,
    names: Map<String, String>,
    snapshot: BondedDeviceSnapshot,
): SelectionResolution {
    if (!snapshot.complete) {
        val visible = snapshot.devices.mapTo(mutableSetOf()) { it.address }
        return SelectionResolution(stored.intersect(visible), stored)
    }
    val active = reconcileSelection(stored, names, snapshot.devices, inventoryComplete = true)
    val successors = snapshot.devices.filter { it.address in active - stored }.mapTo(mutableSetOf()) { it.name }
    val migrated = stored.filterTo(mutableSetOf()) { it !in active && names[it] in successors }
    return SelectionResolution(active, (stored - migrated) + active)
}

/**
 * 저장된 선택(MAC 집합)을 현재 본딩 목록과 대조해 정리한다.
 *
 * 선택은 MAC으로 저장되는데 기기를 지웠다 다시 등록하면 **이름이 같아도 MAC이 바뀐다**.
 * 정리하지 않으면 옛 MAC이 유령으로 남아 하단 CTA의 "N개 선택" 카운트가 부풀고,
 * 유령만 남은 경우 CTA는 눌리지만 홈으로 못 넘어가는 막다른 상태가 된다.
 *
 * - 본딩이 사라진 주소는 버린다.
 * - 단 같은 이름의 새 본딩이 **정확히 하나** 있으면 그쪽으로 승계한다(재등록 시 선택 유지).
 *
 * 순수 함수 — Context 없이 단위테스트한다.
 */
fun reconcileSelection(
    stored: Set<String>,
    storedNames: Map<String, String>,
    bonded: List<BondedDeviceInfo>,
    inventoryComplete: Boolean = false,
): Set<String> {
    // 어댑터 꺼짐·BLUETOOTH_CONNECT 거부 시 목록이 비는데, 이때의 "사라짐"은 근거가 없다.
    // 여기서 정리하면 멀쩡한 선택이 날아가므로 그대로 둔다.
    if (bonded.isEmpty() && !inventoryComplete) return stored

    val bondedAddresses = bonded.mapTo(mutableSetOf()) { it.address }
    val result = stored.filterTo(mutableSetOf()) { it in bondedAddresses }

    for (goneAddress in stored - result) {
        // 이름을 기록하지 않은 구버전 선택(또는 이름을 못 읽어 MAC이 대신 들어간 항목)은 승계 근거가 없다.
        val name = storedNames[goneAddress]?.takeIf { it != goneAddress } ?: continue
        val successor = bonded.singleOrNull {
            it.name == name && it.address !in stored && it.address !in result
        } ?: continue
        result += successor.address
    }
    return result
}
