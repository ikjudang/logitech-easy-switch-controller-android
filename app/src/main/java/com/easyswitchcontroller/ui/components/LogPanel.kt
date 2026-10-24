package com.easyswitchcontroller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyswitchcontroller.R
import com.easyswitchcontroller.data.ConnectionLog
import com.easyswitchcontroller.ui.theme.SpaceMono
import com.easyswitchcontroller.ui.theme.Tokens
import androidx.compose.ui.res.stringResource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 로그 내 hex/채널 토큰(0x1814, ch2 등)은 포인트 색으로 강조한다(핸드오프 로그 팔레트). */
private val HighlightRegex = Regex("""0x[0-9A-Fa-f]+|ch\d""")

/**
 * 터미널풍 로그 패널. 최신이 위(핸드오프: newest first), 패널 내부 스크롤.
 * 폰 전용 페이지와 태블릿 설정의 우측 페인이 공유한다.
 */
@Composable
fun LogPanel(
    entries: List<ConnectionLog.Entry>,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 11.sp,
    radius: Dp = 14.dp,
    innerPadding: Dp = 14.dp,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .background(Tokens.LogBg, shape)
            .border(1.dp, Tokens.Border, shape)
            .padding(innerPadding),
    ) {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.log_empty),
                fontFamily = SpaceMono,
                fontSize = fontSize,
                color = Tokens.TextMuted,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            // 오늘 것은 시각만, 지난 날짜는 날짜까지 — 로그가 며칠에 걸치면 시각만으로는
            // 순서가 거꾸로 보인다(20:18 다음 줄이 08:32면 다음 날인지 알 수 없다).
            val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
            val dateTimeFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) }
            val todayStart = run {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(entries.asReversed()) { entry ->
                    Text(
                        text = formatLine(
                            entry,
                            if (entry.timeMillis >= todayStart) timeFormat else dateTimeFormat,
                        ),
                        fontFamily = SpaceMono,
                        fontSize = fontSize,
                        lineHeight = fontSize * 1.5,
                    )
                }
            }
        }
    }
}

private fun formatLine(entry: ConnectionLog.Entry, timeFormat: SimpleDateFormat): AnnotatedString {
    val baseColor = when (entry.level) {
        ConnectionLog.Level.Success -> Tokens.Success
        ConnectionLog.Level.Dim -> Tokens.Text2
        ConnectionLog.Level.Info -> Tokens.TextBody
    }
    return buildAnnotatedString {
        withStyle(SpanStyle(color = Tokens.TextMuted)) {
            append("[${timeFormat.format(Date(entry.timeMillis))}] ")
        }
        var index = 0
        val message = entry.message
        for (match in HighlightRegex.findAll(message)) {
            if (match.range.first > index) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(message.substring(index, match.range.first))
                }
            }
            withStyle(SpanStyle(color = Tokens.PointAccent)) { append(match.value) }
            index = match.range.last + 1
        }
        if (index < message.length) {
            withStyle(SpanStyle(color = baseColor)) { append(message.substring(index)) }
        }
    }
}
