package com.easyswitchcontroller.floating

import java.nio.ByteBuffer

/** 화면 좌표를 그대로 사용한다. 이동 전 위치도 잠시 남겨 합성 잔상을 함께 캡처한다. */
internal class PixelCapturePlan(private val tailMs: Long = 1_000L) {
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
        fun union(other: Box) = Box(minOf(left, other.left), minOf(top, other.top),
            maxOf(right, other.right), maxOf(bottom, other.bottom))
        fun intersect(other: Box): Box? = Box(maxOf(left, other.left), maxOf(top, other.top),
            minOf(right, other.right), minOf(bottom, other.bottom)).takeIf { it.width > 0 && it.height > 0 }
    }
    private data class Trail(val time: Long, val box: Box)
    private val trail = ArrayDeque<Trail>()
    private var previousKey: String? = null
    private var lastBox: Box? = null
    private var activeUntil = Long.MIN_VALUE

    fun sample(now: Long, screen: Box, windows: List<Box>, visualKey: String, moving: Boolean): Box? {
        val current = windows.mapNotNull { it.intersect(screen) }.reduceOrNull(Box::union)
        val changed = visualKey != previousKey
        if (current != null && (changed || moving) || current == null && changed && lastBox != null) activeUntil = now + tailMs
        previousKey = visualKey
        while (trail.isNotEmpty() && now - trail.first().time > tailMs) trail.removeFirst()
        // 오랫동안 정지한 창이 즉시 제거되어도 마지막 위치의 잔상을 기록한다.
        if (current == null && changed) lastBox?.let { trail.addLast(Trail(now, it)) }
        if (current != null) trail.addLast(Trail(now, current))
        lastBox = current
        if (now > activeUntil) return null
        val region = trail.map { it.box }.reduceOrNull(Box::union) ?: return null
        // 그림자와 안티앨리어싱 바깥쪽도 포함한다. 화면 밖과 행 끝 여백은 저장하지 않는다.
        return Box(region.left - 8, region.top - 8, region.right + 8, region.bottom + 8).intersect(screen)
    }
}

/** ImageReader의 행 끝 여백을 제외하고, 요청 영역의 RGBA 픽셀만 모은다. */
internal object PixelRgbaRows {
    fun crop(buffer: ByteBuffer, rowStride: Int, pixelStride: Int, box: PixelCapturePlan.Box): ByteBuffer {
        require(pixelStride == 4 && box.left >= 0 && box.top >= 0 && box.width > 0 && box.height > 0)
        require(rowStride >= box.right * pixelStride)
        val packed = ByteBuffer.allocateDirect(Math.multiplyExact(Math.multiplyExact(box.width, box.height), 4))
        val source = buffer.duplicate()
        val base = source.position()
        for (y in box.top until box.bottom) {
            val start = base + y * rowStride + box.left * pixelStride
            source.limit(buffer.limit())
            source.position(start)
            source.limit(start + box.width * pixelStride)
            packed.put(source)
        }
        return packed.apply { flip() }
    }
}
