package com.easyswitchcontroller.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.easyswitchcontroller.R
import com.easyswitchcontroller.ui.settings.LanguageMenuPlacement
import com.easyswitchcontroller.ui.settings.roundedMenuHover
import com.easyswitchcontroller.ui.theme.Pretendard
import com.easyswitchcontroller.ui.theme.Tokens

/** Windows 채널 편집기와 같은 작은 인라인 팝업. 우클릭과 기존 롱프레스가 함께 연다. */
@Composable
internal fun RenameChannelPopup(
    editingChannel: Int?,
    anchorBounds: IntRect,
    currentLabel: (Int) -> String,
    onSave: (Int, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val channel = editingChannel ?: return
    var text by remember(channel) {
        val label = currentLabel(channel)
        mutableStateOf(TextFieldValue(label, TextRange(0, label.length)))
    }
    var finished by remember(channel) { mutableStateOf(false) }
    val focus = remember(channel) { FocusRequester() }
    fun finish(save: Boolean) {
        if (finished) return
        finished = true
        if (save) onSave(channel, text.text)
        onDismiss()
    }
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val gap = with(LocalDensity.current) { 2.dp.roundToPx() }
    val position = remember(anchorBounds, margin, gap) {
        val targetBounds = anchorBounds
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
                LanguageMenuPlacement.position(targetBounds, windowSize, layoutDirection,
                    popupContentSize, margin, gap)
        }
    }
    val width = minOf(274.dp, (LocalConfiguration.current.screenWidthDp - 16).coerceAtLeast(1).dp)
    val inputShape = RoundedCornerShape(8.dp)
    val inputStyle = TextStyle(fontFamily = Pretendard, fontSize = 13.sp, lineHeight = 18.sp,
        color = Tokens.Text, platformStyle = PlatformTextStyle(includeFontPadding = false))
    Popup(popupPositionProvider = position, onDismissRequest = { finish(false) },
        properties = PopupProperties(focusable = true)) {
        Surface(shape = RoundedCornerShape(12.dp), color = Tokens.Card,
            border = BorderStroke(1.dp, Tokens.BorderStrong)) {
            Column(Modifier.width(width).padding(15.dp).onPreviewKeyEvent { event ->
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> { if (event.type == KeyEventType.KeyUp) finish(true); true }
                    Key.Escape -> { if (event.type == KeyEventType.KeyUp) finish(false); true }
                    else -> false
                }
            }) {
                Text(stringResource(R.string.rename_title), fontFamily = Pretendard,
                    fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
                    color = Tokens.Text, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(value = text, onValueChange = { text = it }, singleLine = true,
                        textStyle = inputStyle, cursorBrush = SolidColor(Tokens.Text),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { finish(true) }),
                        modifier = Modifier.weight(1f).heightIn(min = 32.dp)
                            .background(Tokens.Inset, inputShape).border(1.dp, Tokens.BorderStrong, inputShape)
                            .focusRequester(focus),
                        decorationBox = { field ->
                            Box(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp),
                                contentAlignment = Alignment.CenterStart) { field() }
                        })
                    Box(Modifier.width(48.dp).heightIn(min = 32.dp)
                        .border(1.dp, Tokens.Border, inputShape).roundedMenuHover(inputShape)
                        .clickable(role = Role.Button) { finish(true) }.padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.rename_confirm), style = inputStyle.copy(fontSize = 12.sp))
                    }
                }
            }
        }
        LaunchedEffect(channel) { focus.requestFocus() }
    }
}
