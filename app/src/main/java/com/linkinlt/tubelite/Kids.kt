package com.linkinlt.tubelite

import android.net.Uri
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager

/**
 * Đường dự phòng cho video "dành cho trẻ em": cách lấy luồng thường bị YouTube trả về
 * "video không có sẵn" với loại video này, nên hỏi lại YouTube như trình phát nhúng (embed).
 */
object Kids {
    class Fmt(val url: String, val height: Int, val fps: Int, val mime: String, val bitrate: Int)

    class Video(
        val id: String,
        val title: String,
        val author: String,
        val duration: Long,
        val muxed: List<Fmt>,
        val videos: List<Fmt>,
        val audios: List<Fmt>
    ) {
        fun heights(): List<Int> = (videos.filter { ok(it) } + muxed).map { it.height }
            .filter { it in 1..Device.maxHeight }.distinct().sortedDescending()
        fun item() = VItem("https://www.youtube.com/watch?v=$id", title, author, Yt.thumbFor("https://www.youtube.com/watch?v=$id", null), duration)
    }

    private class Client(val name: String, val id: String, val version: String, val ua: String, val extra: Map<String, Any>)

    // Video trẻ em: client VR/visionOS bị chặn, nhưng client ANDROID / IOS vẫn trả về luồng (đã thử 11/2026)
    private val clients = listOf(
        Client("ANDROID", "3", "20.10.38",
            "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip",
            mapOf("androidSdkVersion" to 30, "osName" to "Android", "osVersion" to "11")),
        Client("IOS", "5", "20.10.4",
            "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            mapOf("deviceMake" to "Apple", "deviceModel" to "iPhone16,2", "osName" to "iPhone", "osVersion" to "18.3.2.22D82"))
    )

    private fun request(c: Client, id: String): JSONObject {
        val client = JSONObject().put("clientName", c.name).put("clientVersion", c.version).put("hl", "vi").put("gl", "VN")
        c.extra.forEach { (k, v) -> client.put(k, v) }
        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", id)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        val req = okhttp3.Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header("User-Agent", c.ua)
            .header("X-YouTube-Client-Name", c.id)
            .header("X-YouTube-Client-Version", c.version)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        DownloaderImpl.client.newCall(req).execute().use { r -> return JSONObject(r.body?.string() ?: "{}") }
    }

    fun fetch(url: String): Video {
        val id = Yt.videoId(url) ?: throw IllegalArgumentException("Link không hợp lệ")
        var last: Exception = IllegalStateException("Video không xem được")
        for (c in clients) {
            try {
                val resp = request(c, id)
                val status = resp.optJSONObject("playabilityStatus")
                if (status != null && !status.optString("status").equals("OK", true))
                    throw IllegalStateException("Video không xem được (${status.optString("status")})")
                val sd = resp.optJSONObject("streamingData") ?: throw IllegalStateException("Không có luồng phát")
                val details = resp.optJSONObject("videoDetails") ?: JSONObject()
                val muxed = parse(id, sd.optJSONArray("formats"))
                val adaptive = parse(id, sd.optJSONArray("adaptiveFormats"))
                val videos = adaptive.filter {
                    (it.mime.startsWith("video/mp4") && it.mime.contains("avc1")) || (it.mime.startsWith("video/webm") && it.mime.contains("vp9"))
                }
                val audios = adaptive.filter { it.mime.startsWith("audio/mp4") }
                if (muxed.isEmpty() && (videos.isEmpty() || audios.isEmpty())) throw IllegalStateException("Không có luồng phát")
                return Video(id, details.optString("title"), details.optString("author"),
                    details.optString("lengthSeconds").toLongOrNull() ?: 0L, muxed, videos, audios)
            } catch (e: Exception) { last = e }
        }
        throw last
    }

    private fun parse(id: String, arr: JSONArray?): List<Fmt> {
        if (arr == null) return emptyList()
        val out = ArrayList<Fmt>()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            var u = f.optString("url")
            if (u.isEmpty()) {
                val cipher = f.optString("signatureCipher").ifEmpty { f.optString("cipher") }
                if (cipher.isEmpty()) continue
                val q = Uri.parse("http://x/?$cipher")
                val s = q.getQueryParameter("s") ?: continue
                val sp = q.getQueryParameter("sp") ?: "signature"
                val base = q.getQueryParameter("url") ?: continue
                u = try {
                    base + "&" + sp + "=" + Uri.encode(YoutubeJavaScriptPlayerManager.deobfuscateSignature(id, s))
                } catch (_: Exception) { continue }
            }
            u = try { YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(id, u) } catch (_: Exception) { u }
            out.add(Fmt(u, f.optInt("height"), f.optInt("fps"), f.optString("mimeType"), f.optInt("bitrate")))
        }
        return out
    }

    class Built(val source: MediaSource, val height: Int)

    fun ok(f: Fmt) = Device.codecOk(f.mime.contains("vp9"), f.mime.contains("avc1"), f.height, f.fps)

    private val rangeDs by lazy { YoutubeHttpDataSource.Factory().setRangeParameterEnabled(true).setRnParameterEnabled(true) }
    private val plainDs by lazy { YoutubeHttpDataSource.Factory().setRangeParameterEnabled(false).setRnParameterEnabled(true) }

    fun build(v: Video, maxH: Int, muxedOnly: Boolean): Built {
        if (!muxedOnly && v.videos.isNotEmpty() && v.audios.isNotEmpty()) {
            val good = v.videos.filter { it.height in 1..maxH && ok(it) }
            val video = good.sortedWith(compareByDescending<Fmt> { it.height }
                .thenBy { if (it.mime.contains("avc1")) 0 else 1 }
                .thenBy { if (it.fps in 1..30) 0 else 1 }).firstOrNull()
                ?: v.videos.filter { it.mime.contains("avc1") }.minByOrNull { it.height } ?: v.videos.minByOrNull { it.height }!!
            val audio = v.audios.filter { it.bitrate in 1..170_000 }.maxByOrNull { it.bitrate } ?: v.audios.minByOrNull { it.bitrate }!!
            val f = ProgressiveMediaSource.Factory(rangeDs)
            val src = MergingMediaSource(true,
                f.createMediaSource(MediaItem.fromUri(video.url)),
                f.createMediaSource(MediaItem.fromUri(audio.url)))
            return Built(src, video.height)
        }
        val m = v.muxed.filter { it.height in 1..maxH }.maxByOrNull { it.height } ?: v.muxed.minByOrNull { it.height }
            ?: throw IllegalStateException("Không có luồng phát")
        return Built(ProgressiveMediaSource.Factory(plainDs).createMediaSource(MediaItem.fromUri(m.url)), m.height)
    }
}
