package com.easyswitchcontroller.hidpp

/** 테스트용 헥스 헬퍼. "10 FF 00 0A ..." 형태의 문자열을 바이트 배열로. */
fun hex(spaced: String): ByteArray =
    spaced.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .map { it.toInt(16).toByte() }
        .toByteArray()

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
