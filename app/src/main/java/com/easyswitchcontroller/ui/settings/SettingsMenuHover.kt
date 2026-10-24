package com.easyswitchcontroller.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.easyswitchcontroller.ui.theme.Tokens

/** 호버와 클릭 효과를 각 터치 영역의 둥근 윤곽 안에서 그린다. */
@Composable
internal fun Modifier.roundedMenuHover(shape: Shape): Modifier {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val color by animateColorAsState(
        if (hovered) Tokens.Text.copy(alpha = .07f) else Color.Transparent,
        animationSpec = tween(100), label = "settingsMenuHover",
    )
    return clip(shape).background(color).hoverable(interactions)
}
