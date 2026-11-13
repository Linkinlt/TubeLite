package com.linkinlt.tubelite

import android.net.Uri
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.ByteArrayInputStream

/** Chọn luồng video (tối đa 720p, ưu tiên H.264 ≤30fps cho máy yếu) + âm thanh */
object Media {
    class Built(val source: MediaSource, val height: Int, val adaptive: Boolean)

    private fun ytFactory(range: Boolean, rn: Boolean) =
        YoutubeHttpDataSource.Factory().setRangeParameterEnabled(range).setRnParameterEnabled(rn)

    private val dashDs by lazy { ytFactory(true, true) }
    private val progDs by lazy { ytFactory(false, true) }
    private val hlsDs by lazy { ytFactory(false, false) }
    private val dashF by lazy { DashMediaSource.Factory(DefaultDashChunkSource.Factory(dashDs), dashDs) }
    private val progF by lazy { ProgressiveMediaSource.Factory(progDs) }
    private val hlsF by lazy { HlsMediaSource.Factory(hlsDs) }

    fun height(v: VideoStream): Int {
        if (v.height > 0) return v.height
        return v.resolution?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    }

    private fun playable(s: Stream) = s.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP ||
        s.deliveryMethod == DeliveryMethod.DASH || s.deliveryMethod == DeliveryMethod.HLS

    fun heights(info: StreamInfo): List<Int> =
        (info.videoOnlyStreams + info.videoStreams).filter(::playable).map(::height)
            .filter { it in 1..720 }.distinct().sortedDescending()

    fun build(info: StreamInfo, maxH: Int, muxedOnly: Boolean): Built {
        if (!muxedOnly) {
            val video = pickVideo(info.videoOnlyStreams.filter(::playable), maxH)
            val audio = pickAudio(info.audioStreams.filter(::playable))
            if (video != null && audio != null) {
                val src = MergingMediaSource(true, source(video, info), source(audio, info))
                return Built(src, height(video), true)
            }
        }
        val muxed = info.videoStreams.filter(::playable).filter { height(it) in 1..maxH }
            .sortedByDescending(::height).firstOrNull()
            ?: info.videoStreams.filter(::playable).minByOrNull(::height)
        if (muxed != null) return Built(source(muxed, info), height(muxed), false)
        val hls = info.hlsUrl
        if (!hls.isNullOrEmpty()) return Built(hlsF.createMediaSource(MediaItem.fromUri(hls)), maxH, false)
        throw IllegalStateException("Không có luồng phát phù hợp")
    }

    private fun pickVideo(list: List<VideoStream>, maxH: Int): VideoStream? {
        val ok = list.filter { height(it) in 1..maxH }
        if (ok.isEmpty()) return list.minByOrNull(::height)
        val avc = ok.filter { it.format == MediaFormat.MPEG_4 || (it.codec ?: "").startsWith("avc") }
        val pool = avc.ifEmpty { ok }
        return pool.sortedWith(
            compareByDescending<VideoStream> { height(it) }
                .thenBy { if (it.fps in 1..30) 0 else 1 }
        ).first()
    }

    private fun pickAudio(list: List<AudioStream>): AudioStream? {
        if (list.isEmpty()) return null
        val orig = list.filter { it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL }.ifEmpty { list }
        val m4a = orig.filter { it.format == MediaFormat.M4A }.ifEmpty { orig }
        val br = { a: AudioStream -> if (a.averageBitrate > 0) a.averageBitrate else a.bitrate / 1000 }
        return m4a.filter { br(it) in 1..160 }.maxByOrNull(br) ?: m4a.minByOrNull(br)
    }

    private fun source(s: Stream, info: StreamInfo): MediaSource {
        val item = MediaItem.Builder().setUri(Uri.parse(s.content)).build()
        val separate = (s is VideoStream && s.isVideoOnly) || s is AudioStream
        return when (s.deliveryMethod) {
            DeliveryMethod.PROGRESSIVE_HTTP -> {
                val itag = itag(s)
                if (separate && itag != null) {
                    try {
                        val mpd = YoutubeProgressiveDashManifestCreator
                            .fromProgressiveStreamingUrl(s.content, itag, info.duration)
                        dashF.createMediaSource(parse(mpd, s), item)
                    } catch (e: Exception) {
                        progF.createMediaSource(item)
                    }
                } else progF.createMediaSource(item)
            }
            DeliveryMethod.DASH -> {
                val mpd = YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(s.content, itag(s)!!, info.duration)
                dashF.createMediaSource(parse(mpd, s), item)
            }
            DeliveryMethod.HLS -> hlsF.createMediaSource(item)
            else -> throw IllegalStateException("Kiểu luồng không hỗ trợ: ${s.deliveryMethod}")
        }
    }

    private fun itag(s: Stream) = when (s) {
        is VideoStream -> s.itagItem
        is AudioStream -> s.itagItem
        else -> null
    }

    private fun parse(mpd: String, s: Stream) =
        DashManifestParser().parse(Uri.parse(s.content), ByteArrayInputStream(mpd.toByteArray(Charsets.UTF_8)))
}
