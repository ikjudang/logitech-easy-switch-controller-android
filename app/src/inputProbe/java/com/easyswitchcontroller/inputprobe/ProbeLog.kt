package com.easyswitchcontroller.inputprobe

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 검증 결과는 화면 안내 대신 밀리초 시각을 포함한 파일에 기록한다. */
object ProbeLog {
    private var file: File? = null
    @Synchronized fun attach(context: Context) {
        if (file != null) return
        file = File(context.filesDir, "mouse-input-probe.txt")
        append("SESSION model=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} package=${context.packageName}")
    }
    @Synchronized fun append(message: String) {
        val target = file ?: return
        if (target.length() > 2_000_000) target.writeText(target.readText().takeLast(500_000))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT).format(Date())
        target.appendText("$time\t${message.replace('\n', ' ')}\n")
    }
    @Synchronized fun snapshot(): String = file?.takeIf { it.exists() }?.readText().orEmpty()
}
