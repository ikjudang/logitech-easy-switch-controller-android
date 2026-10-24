package com.easyswitchcontroller.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.easyswitchcontroller.R
import com.easyswitchcontroller.floating.FineSliderMotion
import com.easyswitchcontroller.floating.FloatingBubbleView
import com.easyswitchcontroller.floating.FloatingPreferences
import com.easyswitchcontroller.floating.FloatingSize
import com.easyswitchcontroller.floating.FloatingAnimationSpeed
import com.easyswitchcontroller.floating.FloatingInputSession
import com.easyswitchcontroller.floating.FloatingMenuGeometry
import com.easyswitchcontroller.service.FollowService
import com.easyswitchcontroller.ui.openOverlaySettings
import com.easyswitchcontroller.ui.theme.AppIcons
import com.easyswitchcontroller.ui.theme.Pretendard
import com.easyswitchcontroller.ui.theme.Tokens
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
internal fun FloatingButtonCard(overlayOn: Boolean, tablet: Boolean) {
    val context = LocalContext.current
    val revision by FloatingPreferences.revision.collectAsState()
    val settings = remember(revision) { FloatingPreferences.load(context) }
    val homeVisible by FollowService.floatingVisible.collectAsState()
    val inUse = settings.enabled && overlayOn && homeVisible
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (tablet) 16.dp else 18.dp)
    val height = cardHeight(tablet)
    val titleSize = if (tablet) 15.sp else 14.5.sp
    val toggleLabel = stringResource(R.string.floating_title)
    val iconSize = if (tablet) 40.dp else 38.dp
    val inactiveIconPaint = remember { inactiveHomeIconPaint() }
    Column(Modifier.fillMaxWidth().clip(shape).background(Tokens.Card, shape).border(1.dp, Tokens.Border, shape)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().border(1.dp, Tokens.Border, shape)) {
            Row(Modifier.weight(1f).height(height).roundedMenuHover(shape).clickable { expanded = !expanded }
                .padding(horizontal = if (tablet) 16.dp else 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                key(iconSize) {
                    AndroidView(modifier = Modifier.size(iconSize), factory = { viewContext ->
                        FloatingBubbleView(viewContext, null, designScale = iconSize.value / 60f).apply {
                            // CLEAR가 아이콘 자체의 비트맵 레이어만 지우도록 분리한다.
                            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                            isClickable = false
                            isFocusable = false
                            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            contentDescription = null
                        }
                    }, update = { view ->
                        view.setLayerPaint(if (settings.enabled && overlayOn) null else inactiveIconPaint)
                        view.invalidate()
                    })
                }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.floating_title), fontSize = titleSize,
                        fontWeight = FontWeight.Bold, color = Tokens.Text, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (inUse) {
                        Text(stringResource(R.string.floating_in_use), fontSize = titleSize,
                            fontWeight = FontWeight.Bold, color = Tokens.Text, maxLines = 1)
                    } else {
                        val actionShape = RoundedCornerShape(8.dp)
                        Box(Modifier.height(36.dp).widthIn(min = 44.dp).roundedMenuHover(actionShape)
                            .clip(actionShape).clickable(role = Role.Button) {
                                when {
                                    !overlayOn -> openOverlaySettings(context)
                                    settings.sizePercent <= 0 || settings.transparency >= 100 -> expanded = true
                                    else -> {
                                        if (!settings.enabled) FloatingPreferences.setEnabled(context, true)
                                        FollowService.summonFloating(context)
                                    }
                                }
                            }.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.floating_turn_on), fontSize = titleSize,
                                fontWeight = FontWeight.Bold, color = Tokens.Text, maxLines = 1)
                        }
                    }
                }
                Icon(if (expanded) AppIcons.ChevronUp else AppIcons.ChevronDown,
                    stringResource(if (expanded) R.string.settings_collapse else R.string.settings_expand),
                    tint = Tokens.Text2, modifier = Modifier.size(16.dp))
            }
            Box(Modifier.height(height).roundedMenuHover(shape).toggleable(settings.enabled && overlayOn, role = Role.Switch) { enabled ->
                if (!settings.enabled && !overlayOn) openOverlaySettings(context)
                else {
                    FloatingPreferences.setEnabled(context, enabled)
                    FollowService.start(context)
                }
            }.semantics {
                contentDescription = toggleLabel
            }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                SizeStableToggle(settings.enabled && overlayOn)
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth()) {
                FloatingPercentRow(stringResource(R.string.floating_transparency), settings.transparency, 100, tablet) {
                    FloatingPreferences.setTransparency(context, it)
                }
                FloatingPercentRow(stringResource(R.string.floating_size), settings.sizePercent, FloatingSize.MAX_PERCENT, tablet) {
                    FloatingPreferences.setSizePercent(context, it)
                }
                FloatingPercentRow(stringResource(R.string.floating_animation_speed), settings.animationSpeedPercent,
                    FloatingAnimationSpeed.MAX_PERCENT, tablet, minimum = FloatingAnimationSpeed.MIN_PERCENT) {
                    FloatingPreferences.setAnimationSpeedPercent(context, it)
                }
                Row(Modifier.fillMaxWidth().height(height).roundedMenuHover(shape)
                    .toggleable(settings.alwaysShowMenu, role = Role.Switch) {
                        FloatingPreferences.setAlwaysShowMenu(context, it)
                    }.padding(start = if (tablet) 18.dp else 16.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.floating_always_show_menu), fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = Tokens.Text, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    SizeStableToggle(settings.alwaysShowMenu)
                }
                Row(Modifier.fillMaxWidth().height(height).roundedMenuHover(shape)
                    .toggleable(settings.bounceEnabled, role = Role.Switch) {
                        FloatingPreferences.setBounceEnabled(context, it)
                    }.padding(start = if (tablet) 18.dp else 16.dp, end = 18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.floating_bounce_animation), fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = Tokens.Text, modifier = Modifier.weight(1f))
                    SizeStableToggle(settings.bounceEnabled)
                }
            }
        }
    }
}

/** 투명도·크기·속도는 같은 숫자 입력·미세 조절 슬라이더를 사용한다. */
@Composable
private fun FloatingPercentRow(label: String, value: Int, maximum: Int, tablet: Boolean,
    minimum: Int = 0, onChange: (Int) -> Unit) {
    val focus = LocalFocusManager.current
    val current by rememberUpdatedState(value)
    var number by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { number = value.toString() }
    val numberStyle = LocalTextStyle.current.copy(color = Tokens.Text, fontSize = 13.sp, fontFamily = Pretendard,
        lineHeight = 16.sp, platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        textAlign = TextAlign.End)
    val percentStyle = numberStyle.copy(color = Tokens.Text2, fontSize = 12.sp)
    val textMeasurer = rememberTextMeasurer()
    val hundredSize = textMeasurer.measure("100", style = numberStyle).size
    val twoHundredSize = textMeasurer.measure("200", style = numberStyle).size
    val maximumSize = textMeasurer.measure(maximum.toString(), style = numberStyle).size
    val numberSize = androidx.compose.ui.unit.IntSize(maxOf(hundredSize.width, twoHundredSize.width, maximumSize.width),
        maxOf(hundredSize.height, twoHundredSize.height, maximumSize.height))
    val labelStyle = numberStyle.copy(fontWeight = FontWeight.Bold)
    val labelWidth = with(LocalDensity.current) {
        maxOf(textMeasurer.measure(stringResource(R.string.floating_transparency), style = labelStyle).size.width,
            textMeasurer.measure(stringResource(R.string.floating_size), style = labelStyle).size.width,
            textMeasurer.measure(stringResource(R.string.floating_animation_speed), style = labelStyle).size.width).toDp()
    }
    val percentSize = textMeasurer.measure("%", style = percentStyle).size
    val inputWidth = with(LocalDensity.current) { (numberSize.width + percentSize.width).toDp() } + 20.dp
    val inputHeight = maxOf(28.dp, with(LocalDensity.current) { maxOf(numberSize.height, percentSize.height).toDp() } + 4.dp)
    val resizeArea = if (maximum == FloatingSize.MAX_PERCENT) Modifier.keepFloatingMenuDuringResize() else Modifier
    Row(Modifier.fillMaxWidth().height(cardHeight(tablet)).then(resizeArea)
        .padding(start = if (tablet) 18.dp else 16.dp, end = if (tablet) 16.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tokens.Text, maxLines = 1,
            modifier = Modifier.width(labelWidth))
        BasicTextField(number, onValueChange = { raw ->
            if (raw.all(Char::isDigit) && raw.length <= 3) {
                val parsed = raw.toIntOrNull()
                number = raw
                if (parsed != null && parsed >= minimum) {
                    val bounded = parsed.coerceAtMost(maximum)
                    number = bounded.toString()
                    onChange(bounded)
                }
            }
        }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            cursorBrush = SolidColor(Tokens.Text), textStyle = numberStyle,
            modifier = Modifier.width(inputWidth).height(inputHeight).dismissibleSettingsInput().onFocusChanged {
                if (!it.isFocused) {
                    val committed = number.toIntOrNull()?.coerceIn(minimum, maximum) ?: current
                    number = committed.toString()
                    if (committed != current) onChange(committed)
                }
            }.semantics { contentDescription = label }
                .border(1.dp, Tokens.BorderStrong, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp),
            decorationBox = { field -> Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) { field() }
                Text("%", style = percentStyle)
            } })
        FineTransparencySlider(value, {
            number = it.toString()
            onChange(it)
        }, label, Modifier.weight(1f), maximum, minimum)
    }
}

@Composable
private fun Modifier.keepFloatingMenuDuringResize(): Modifier {
    val view = LocalView.current
    val token = remember { Any() }
    DisposableEffect(view, token) { onDispose { FloatingInputSession.clearResizeControlArea(token) } }
    return onGloballyPositioned { coordinates ->
        val area = coordinates.boundsInWindow()
        val screen = IntArray(2); val window = IntArray(2)
        view.getLocationOnScreen(screen); view.getLocationInWindow(window)
        val dx = (screen[0] - window[0]).toFloat(); val dy = (screen[1] - window[1]).toFloat()
        FloatingInputSession.resizeControlArea(token, FloatingInputSession.TestArea(view.display?.displayId ?: 0,
            FloatingMenuGeometry.Bounds(area.left + dx, area.top + dy, area.right + dx, area.bottom + dy)))
    }
}

/** 같은 홈 버튼의 윤곽·베벨을 유지하고, 배경과 밝은 문양을 기존 비활성 색에 맞춘다. */
private fun inactiveHomeIconPaint(): android.graphics.Paint {
    val baseLuma = .299f * 108f + .587f * 60f + .114f * 224f
    val low = floatArrayOf(Tokens.Inset.red, Tokens.Inset.green, Tokens.Inset.blue)
    val high = floatArrayOf(Tokens.Text2.red, Tokens.Text2.green, Tokens.Text2.blue)
    val matrix = FloatArray(20)
    for (channel in 0..2) {
        val gain = (high[channel] - low[channel]) * 255f / (255f - baseLuma)
        val row = channel * 5
        matrix[row] = .299f * gain
        matrix[row + 1] = .587f * gain
        matrix[row + 2] = .114f * gain
        matrix[row + 4] = low[channel] * 255f - gain * baseLuma
    }
    matrix[18] = 1f
    return android.graphics.Paint().apply {
        colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix(matrix))
    }
}

/** 손잡이만 빛나며, 실제 터치 이동의 2/3만큼 값이 움직인다. */
@Composable
private fun FineTransparencySlider(value: Int, onChange: (Int) -> Unit, label: String, modifier: Modifier,
    maximum: Int, minimum: Int = 0) {
    val current by rememberUpdatedState(value)
    val changed by rememberUpdatedState(onChange)
    var width by remember { mutableStateOf(0) }
    var hovered by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val halo by animateFloatAsState(if (hovered || pressed) 1f else 0f, label = "transparencyThumbHalo")
    val point = Tokens.PointTintText
    val track = Tokens.BorderStrong
    val interaction = modifier.height(48.dp).onSizeChanged { width = it.width }
        .semantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), minimum.toFloat()..maximum.toFloat(), maximum - minimum - 1)
            setProgress { changed(it.roundToInt().coerceIn(minimum, maximum)); true }
        }
        .pointerInput(minimum, maximum) {
            val pad = 20.dp.toPx()
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val at = event.changes.firstOrNull { it.type == PointerType.Mouse }?.position
                    val x = pad + (width - 2 * pad).coerceAtLeast(1f) * FineSliderMotion.fraction(current, minimum, maximum)
                    hovered = event.type != PointerEventType.Exit && at != null &&
                        hypot(at.x - x, at.y - size.height / 2f) <= 12.dp.toPx()
                }
            }
        }
        .pointerInput(minimum, maximum) {
            val pad = 20.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown()
                val length = (size.width - 2 * pad).coerceAtLeast(1f)
                var fraction = FineSliderMotion.fraction(current, minimum, maximum)
                val thumb = pad + length * fraction
                if (kotlin.math.abs(down.position.x - thumb) > 16.dp.toPx()) {
                    fraction = ((down.position.x - pad) / length).coerceIn(0f, 1f)
                    changed(FineSliderMotion.valueAt(fraction, minimum, maximum))
                }
                pressed = true
                down.consume()
                try {
                    drag(down.id) { motion ->
                        fraction = FineSliderMotion.move(fraction, motion.positionChange().x, length)
                        changed(FineSliderMotion.valueAt(fraction, minimum, maximum))
                        motion.consume()
                    }
                } finally { pressed = false }
            }
        }
    Canvas(interaction) {
        val pad = 20.dp.toPx()
        val end = (size.width - pad).coerceAtLeast(pad)
        val x = pad + (end - pad) * FineSliderMotion.fraction(value, minimum, maximum)
        val y = size.height / 2f
        drawLine(track, Offset(pad, y), Offset(end, y), 4.dp.toPx(), StrokeCap.Round)
        drawLine(point, Offset(pad, y), Offset(x, y), 4.dp.toPx(), StrokeCap.Round)
        if (halo > 0f) drawCircle(point.copy(alpha = .18f * halo), 16.dp.toPx(), Offset(x, y))
        drawCircle(point, 8.dp.toPx(), Offset(x, y))
    }
}
