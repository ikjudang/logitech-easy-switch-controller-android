package com.easyswitchcontroller.inputprobe

import com.easyswitchcontroller.floating.FloatingScene

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 실제 화면 캡처와 입력 로그를 연결하고, 오버레이 창과 이동 경로를 잘라 저장한다. */
internal object ProbePixelLog {
    var sceneProvider: (() -> FloatingScene)? = null // 메인 스레드에서만 View를 읽는다.
    @Volatile var scene: FloatingScene? = null
        private set
    @Volatile var active = false
        private set
    private val main = Handler(Looper.getMainLooper())
    private val filesLock = Any()
    private data class SavedFrame(val png: File, val json: File, val bytes: Long)
    private var retained: ArrayDeque<SavedFrame>? = null
    private var retainedBytes = 0L
    private val sample = object : Runnable {
        override fun run() {
            if (!active) return
            scene = sceneProvider?.invoke() ?: FloatingScene(SystemClock.elapsedRealtimeNanos(), emptyList(), "{}", false)
            main.postDelayed(this, 16L)
        }
    }

    fun start() {
        scene = null
        active = true
        main.removeCallbacks(sample)
        main.post(sample)
    }
    fun stop() {
        active = false
        main.removeCallbacks(sample)
        scene = null
    }

    private fun directory(context: Context) = File(context.filesDir, "pixel-log").apply { mkdirs() }

    fun save(context: Context, bitmap: Bitmap, metadata: JSONObject) = synchronized(filesLock) {
        val root = directory(context)
        val id = "${metadata.getLong("wallTimeMs")}-${metadata.getLong("frameNumber")}-${metadata.getLong("readElapsedNs")}"
        val png = File(root, "$id.png")
        val json = File(root, "$id.json")
        try {
            val frames = retained ?: ArrayDeque(root.listFiles().orEmpty().filter { it.extension == "png" }.sortedBy { it.name }.map { image ->
                val sidecar = File(root, "${image.nameWithoutExtension}.json")
                SavedFrame(image, sidecar, image.length() + sidecar.length())
            }).also { retained = it; retainedBytes = it.sumOf { saved -> saved.bytes } }
            png.outputStream().buffered().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            json.writeText(metadata.put("image", png.name).toString(), Charsets.UTF_8)
            val saved = SavedFrame(png, json, png.length() + json.length())
            frames.addLast(saved); retainedBytes += saved.bytes
            while (frames.size > 600 || retainedBytes > 128L * 1024 * 1024) {
                val oldest = frames.first()
                check(!oldest.png.exists() || oldest.png.delete()) { "Cannot remove expired pixel frame" }
                check(!oldest.json.exists() || oldest.json.delete()) { "Cannot remove expired pixel metadata" }
                frames.removeFirst(); retainedBytes -= oldest.bytes
            }
        } catch (error: Exception) {
            png.delete(); json.delete()
            retained = null
            throw error
        }
    }

    fun export(context: Context, output: OutputStream) = synchronized(filesLock) {
        ZipOutputStream(output.buffered()).use { zip ->
            fun entry(name: String, write: (ZipOutputStream) -> Unit) {
                zip.putNextEntry(ZipEntry(name)); write(zip); zip.closeEntry()
            }
            entry("input-log.txt") { it.write(ProbeLog.snapshot().toByteArray(Charsets.UTF_8)) }
            entry("pixel-log-info.txt") { it.write((
                "Actual MediaProjection screen pixels; PNG, original pixel resolution, no rescaling.\n" +
                "Only the overlay windows and their last 1000 ms movement region are saved.\n" +
                "Background content inside that region is included; wide drags or edge targets can span the screen.\n" +
                "No separate uncropped master image is stored.\n" +
                "Frames are sampled at up to 30 fps; encoding load or unchanged screen may reduce this.\n" +
                "Retention: latest 600 frames within 128 MiB, across capture sessions.\n" +
                "Coordinates are physical screen pixels. Each PNG has a JSON sidecar with crop origin,\n" +
                "source size, image timestamp, millisecond wall time and latest overlay state at image read.\n" +
                "State is sampled independently of composition; stateAgeNs reports its age at image read.\n" +
                "Projection pixels come from Android composition; panel-only artifacts may not appear.\n"
            ).toByteArray(Charsets.UTF_8)) }
            val root = directory(context)
            root.listFiles().orEmpty().filter { it.extension == "png" }.sortedBy { it.name }.forEach { png ->
                val json = File(root, "${png.nameWithoutExtension}.json")
                if (json.exists()) {
                    entry("pixels/${png.name}") { sink -> png.inputStream().use { it.copyTo(sink) } }
                    entry("pixels/${json.name}") { sink -> json.inputStream().use { it.copyTo(sink) } }
                }
            }
        }
    }

    fun formattedTime(millis: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT).format(Date(millis))
}
