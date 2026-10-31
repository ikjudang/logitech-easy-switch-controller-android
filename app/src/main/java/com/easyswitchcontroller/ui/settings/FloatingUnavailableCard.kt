package com.easyswitchcontroller.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyswitchcontroller.R
import com.easyswitchcontroller.ui.theme.Tokens

/** 플로팅 버튼 구현이 들어 있지 않은 빌드(공개 소스에서 만든 빌드)에서 설정 카드 대신 보여 주는 안내. */
@Composable
fun FloatingUnavailableCard() {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = Tokens.Card,
        border = BorderStroke(1.dp, Tokens.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                stringResource(R.string.floating_title),
                color = Tokens.Text, fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.floating_unavailable),
                color = Tokens.Text2, fontSize = 12.5.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
