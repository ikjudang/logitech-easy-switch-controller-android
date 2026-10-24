package com.easyswitchcontroller.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.easyswitchcontroller.R
import com.easyswitchcontroller.ui.theme.Tokens

/** 상하 여백 없이 두 선택 행이 팝업 전체를 채운다. */
@Composable
internal fun LanguageSelectionPopup(
    expanded: Boolean,
    isEnglish: Boolean,
    width: Dp,
    onDismiss: () -> Unit,
    onSelect: (Boolean) -> Unit,
) {
    if (!expanded) return
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val gap = with(LocalDensity.current) { 2.dp.roundToPx() }
    val position = remember(margin, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                return LanguageMenuPlacement.position(anchorBounds, windowSize, layoutDirection,
                    popupContentSize, margin, gap)
            }
        }
    }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)) {
        Surface(shape = RoundedCornerShape(12.dp), color = Tokens.Card,
            border = BorderStroke(1.dp, Tokens.BorderStrong), shadowElevation = 8.dp) {
            Column(Modifier.width(width)) {
                LanguageOption(R.string.settings_lang_ko, !isEnglish,
                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)) { onSelect(false) }
                LanguageOption(R.string.settings_lang_en, isEnglish,
                    RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)) { onSelect(true) }
            }
        }
    }
}

@Composable
private fun LanguageOption(label: Int, selected: Boolean, shape: RoundedCornerShape, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp).roundedMenuHover(shape)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics { this.selected = selected }
        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) Tokens.PointAccent else Tokens.Text,
            maxLines = 1)
    }
}
