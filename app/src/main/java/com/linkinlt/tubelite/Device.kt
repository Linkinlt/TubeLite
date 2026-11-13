package com.linkinlt.tubelite

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList

/**
 * Đo khả năng của máy để chọn chất lượng cao nhất mà máy chạy nổi:
 * - RAM: máy ≤ ~1GB giữ tối đa 720p
 * - Bộ giải mã phần cứng H.264 / VP9: kiểm tra 1080p, 1440p, 4K có giải mã được không
 */
object Device {
    private val sizes = listOf(2160 to 3840, 1440 to 2560, 1080 to 1920, 720 to 1280, 480 to 854, 360 to 640)

    val ramMb: Long by lazy {
        try {
            val am = App.instance.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
            mi.totalMem / (1024 * 1024)
        } catch (_: Exception) { 1024L }
    }

    /** Chiều cao tối đa theo RAM. */
    private val ramCap: Int get() = when {
        ramMb < 1400 -> 720
        ramMb < 2600 -> 1080
        else -> 2160
    }

    private fun hwDecoders(mime: String): List<MediaCodecInfo> {
        return try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(mime, true) } &&
                    !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
            }
        } catch (_: Throwable) { emptyList() }
    }

    private fun maxFor(mime: String, fps: Double): Int {
        for (codec in hwDecoders(mime)) {
            val vc = try { codec.getCapabilitiesForType(mime).videoCapabilities } catch (_: Throwable) { null } ?: continue
            for ((h, w) in sizes) {
                val ok = try { vc.areSizeAndRateSupported(w, h, fps) } catch (_: Throwable) { false }
                if (ok) return h
            }
        }
        return 0
    }

    /** H.264 phần cứng (YouTube chỉ có H.264 tới 1080p). Không đo được thì coi như 720p. */
    val avcMax: Int by lazy { maxFor("video/avc", 30.0).let { if (it == 0) 720 else it }.coerceAtMost(1080) }

    /** VP9 phần cứng (cần cho 1440p / 4K). 0 = không có. */
    val vp9Max: Int by lazy { maxFor("video/x-vnd.on2.vp9", 30.0) }

    /** Chất lượng cao nhất máy cho phép. */
    val maxHeight: Int by lazy { minOf(ramCap, maxOf(avcMax, vp9Max)).coerceAtLeast(360) }

    /** Cho phép 60 khung hình/giây khi máy đủ khoẻ. */
    val allow60: Boolean by lazy {
        ramMb >= 2000 && maxFor("video/avc", 60.0) >= minOf(1080, maxHeight)
    }

    /** Mặc định: máy yếu 720p, máy khoẻ 1080p (không mặc định 4K cho đỡ tốn mạng). */
    val defaultQuality: Int get() = minOf(maxHeight, if (ramMb < 1400) 720 else 1080)

    fun maxWidth(h: Int) = sizes.firstOrNull { it.first == h }?.second ?: (h * 16 / 9)

    /** Bộ đệm theo RAM. */
    val bufferBytes: Int get() = when {
        ramMb < 1400 -> 8 * 1024 * 1024
        ramMb < 2600 -> 16 * 1024 * 1024
        else -> 32 * 1024 * 1024
    }

    fun codecOk(isVp9: Boolean, isAvc: Boolean, height: Int, fps: Int): Boolean {
        if (height > maxHeight) return false
        if (fps > 30 && !allow60) return false
        return when {
            isAvc -> height <= avcMax
            isVp9 -> vp9Max > 0 && height <= vp9Max
            else -> false
        }
    }
}
