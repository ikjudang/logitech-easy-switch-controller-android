package com.easyswitchcontroller.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import android.view.View
import java.util.WeakHashMap

private object SettingsInputBounds {
    data class Entry(val token: Any, val bounds: Rect)
    val focused = WeakHashMap<View, Entry>()
    fun remove(view: View, token: Any) {
        if (focused[view]?.token === token) focused.remove(view)
    }
}

/** 입력칸 안의 클릭은 유지하고, 설정 화면의 다른 곳을 누르면 입력을 끝낸다. 입력은 소비하지 않는다. */
@Composable
internal fun Modifier.clearSettingsInputFocusOnOutsidePress(): Modifier {
    val view = LocalView.current
    val focus = LocalFocusManager.current
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return onGloballyPositioned { coordinates[0] = it }.pointerInput(view, focus) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val down = event.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                val entry = SettingsInputBounds.focused[view] ?: continue
                val parent = coordinates[0]?.takeIf { it.isAttached } ?: continue
                if (!entry.bounds.contains(parent.localToWindow(down.position))) focus.clearFocus()
            }
        }
    }
}

/** 포커스가 있는 입력칸의 위치만 공유하고, 실제 키보드 Enter도 입력 종료로 처리한다. */
@Composable
internal fun Modifier.dismissibleSettingsInput(): Modifier {
    val view = LocalView.current
    val focus = LocalFocusManager.current
    val token = remember { Any() }
    val bounds = remember { arrayOfNulls<Rect>(1) }
    val focused = remember { booleanArrayOf(false) }
    DisposableEffect(view, token) { onDispose { SettingsInputBounds.remove(view, token) } }
    return onGloballyPositioned {
        bounds[0] = it.boundsInWindow()
        if (focused[0]) SettingsInputBounds.focused[view] = SettingsInputBounds.Entry(token, bounds[0]!!)
    }.onFocusChanged {
        focused[0] = it.isFocused
        val area = bounds[0]
        if (it.isFocused && area != null) SettingsInputBounds.focused[view] = SettingsInputBounds.Entry(token, area)
        else SettingsInputBounds.remove(view, token)
    }.onPreviewKeyEvent {
        if (it.key == Key.Enter || it.key == Key.NumPadEnter) {
            if (it.type == KeyEventType.KeyDown) focus.clearFocus()
            true
        } else false
    }
}

/** 터치·휠 스크롤 때 입력을 끝낸다. 입력칸을 보이게 하는 자동 스크롤은 제외한다. */
@Composable
internal fun Modifier.clearInputFocusOnUserScroll(): Modifier {
    val focus = LocalFocusManager.current
    val connection = remember(focus) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) focus.clearFocus()
                return Offset.Zero
            }
        }
    }
    return nestedScroll(connection)
}
