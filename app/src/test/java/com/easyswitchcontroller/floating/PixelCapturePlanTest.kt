package com.easyswitchcontroller.floating

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class PixelCapturePlanTest {
    private val screen = PixelCapturePlan.Box(0, 0, 1200, 2400)
    private val old = PixelCapturePlan.Box(100, 300, 200, 400)
    private val far = PixelCapturePlan.Box(900, 1800, 1000, 1900)

    @Test fun longDragIncludesBothTheOldLocationAndTheNewHome() {
        val plan = PixelCapturePlan()
        plan.sample(0, screen, listOf(old), "old", false)
        val crop = plan.sample(100, screen, listOf(far), "far", true)!!
        assertEquals(PixelCapturePlan.Box(92, 292, 1008, 1908), crop)
        assertEquals(PixelCapturePlan.Box(892, 1792, 1008, 1908), plan.sample(1101, screen, listOf(far), "far", true))
    }

    @Test fun stationaryScreenStopsSavingAfterTheTail() {
        val plan = PixelCapturePlan()
        assertNotNull(plan.sample(0, screen, listOf(old), "still", false))
        assertNotNull(plan.sample(1000, screen, listOf(old), "still", false))
        assertNull(plan.sample(1001, screen, listOf(old), "still", false))
        assertNotNull(plan.sample(1002, screen, listOf(old), "hover", false))
    }

    @Test fun hiddenWindowIsStillCapturedAtItsLastLocationThenExpires() {
        val plan = PixelCapturePlan()
        plan.sample(0, screen, listOf(old), "shown", false)
        plan.sample(5000, screen, listOf(old), "shown", false)
        assertNotNull(plan.sample(6000, screen, emptyList(), "hidden", false))
        assertNotNull(plan.sample(6999, screen, emptyList(), "hidden", false))
        assertNull(plan.sample(7001, screen, emptyList(), "hidden", false))
    }

    @Test fun cropsAreClippedToThePhysicalScreenAndIgnoreOffScreenWindows() {
        val plan = PixelCapturePlan()
        val crop = plan.sample(0, screen, listOf(PixelCapturePlan.Box(-100, -200, 50, 60)), "edge", true)
        assertEquals(PixelCapturePlan.Box(0, 0, 58, 68), crop)
        assertNull(PixelCapturePlan().sample(0, screen, listOf(PixelCapturePlan.Box(1300, 0, 1400, 10)), "outside", true))
        assertNull(PixelCapturePlan().sample(0, screen, emptyList(), "empty", false))
    }

    @Test fun rgbaCropExcludesRowPaddingAndPreservesEveryPixelByte() {
        // 4 pixels wide, 2 rows, 4 bytes of padding per row. Crop columns 1 and 2.
        val source = ByteBuffer.wrap(ByteArray(40) { it.toByte() })
        val pixels = PixelRgbaRows.crop(source, 20, 4, PixelCapturePlan.Box(1, 0, 3, 2))
        val actual = ByteArray(pixels.remaining()).also { pixels.get(it) }
        val expected = ((4..11).toList() + (24..31).toList()).map { it.toByte() }.toByteArray()
        assertArrayEquals(expected, actual)
        assertEquals(0, source.position())
        assertEquals(40, source.limit())
    }

    @Test fun rgbaCropSupportsNonzeroBufferPositionAndFinalRowWithoutPadding() {
        val source = ByteBuffer.wrap(ByteArray(39) { it.toByte() }).apply { position(3) }
        val pixels = PixelRgbaRows.crop(source, 20, 4, PixelCapturePlan.Box(2, 1, 4, 2))
        val actual = ByteArray(pixels.remaining()).also { pixels.get(it) }
        assertArrayEquals((31..38).map { it.toByte() }.toByteArray(), actual)
        assertEquals(3, source.position())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedPixelStrideDoesNotSilentlyWriteWrongColors() {
        PixelRgbaRows.crop(ByteBuffer.allocate(16), 8, 2, PixelCapturePlan.Box(0, 0, 1, 1))
    }
}
