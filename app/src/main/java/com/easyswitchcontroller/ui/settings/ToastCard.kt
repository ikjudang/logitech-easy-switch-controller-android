package com.easyswitchcontroller.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyswitchcontroller.R
import com.easyswitchcontroller.ui.AppToast
import com.easyswitchcontroller.ui.components.IconTile
import com.easyswitchcontroller.ui.notice.NoticePreferences
import com.easyswitchcontroller.ui.theme.AppIcons
import com.easyswitchcontroller.ui.theme.Tokens

@Composable
internal fun ToastCard(tablet: Boolean) {
    val context = LocalContext.current
    val revision by NoticePreferences.revision.collectAsStateWithLifecycle()
    val settings = remember(revision) { NoticePreferences.load(context) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (tablet) 16.dp else 18.dp)
    val height = cardHeight(tablet)
    val title = stringResource(R.string.settings_toast)
    Column(Modifier.fillMaxWidth().clip(shape).background(Tokens.Card, shape).border(1.dp, Tokens.Border, shape)) {
        Row(Modifier.fillMaxWidth().border(1.dp, Tokens.Border, shape), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).height(height).roundedMenuHover(shape).clickable { expanded = !expanded }
                .padding(horizontal = if (tablet) 16.dp else 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                IconTile(size = if (tablet) 40.dp else 38.dp, radius = 12.dp,
                    background = if (settings.enabled) Tokens.PointTintIconBg else Tokens.Inset) {
                    Icon(AppIcons.Brand, null, tint = if (settings.enabled) Tokens.PointTintText else Tokens.Text2,
                        modifier = Modifier.size(24.dp))
                }
                Text(title, fontSize = if (tablet) 15.sp else 14.5.sp, fontWeight = FontWeight.Bold,
                    color = Tokens.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(if (expanded) AppIcons.ChevronUp else AppIcons.ChevronDown,
                    stringResource(if (expanded) R.string.settings_collapse else R.string.settings_expand),
                    tint = Tokens.Text2, modifier = Modifier.size(16.dp))
            }
            Box(Modifier.height(height).roundedMenuHover(shape).toggleable(settings.enabled, role = Role.Switch) {
                if (NoticePreferences.setEnabled(context, it)) AppToast.settingsChanged(context)
            }.semantics { contentDescription = title }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                SizeStableToggle(settings.enabled)
            }
        }
        AnimatedVisibility(expanded) {
            FloatingPercentRow(stringResource(R.string.floating_transparency), settings.transparency, 100, tablet) {
                if (NoticePreferences.setTransparency(context, it)) AppToast.settingsChanged(context)
            }
        }
    }
}
