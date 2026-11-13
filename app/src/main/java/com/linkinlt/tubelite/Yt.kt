package com.linkinlt.tubelite

import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Mọi lời gọi lấy dữ liệu YouTube nằm ở đây. Gọi từ luồng nền. */
object Yt {
    private val yt get() = ServiceList.YouTube

    class PageResult(val items: List<VItem>, val next: Page?)

    // ---------- Tìm kiếm ----------
    fun searchHandler(q: String): SearchQueryHandler =
        yt.searchQHFactory.fromQuery(q, listOf("videos"), "")

    fun search(h: SearchQueryHandler): PageResult {
        val info = SearchInfo.getInfo(yt, h)
        return PageResult(convert(info.relatedItems), info.nextPage)
    }

    fun searchMore(h: SearchQueryHandler, page: Page): PageResult {
        val p = SearchInfo.getMoreItems(yt, h, page)
        return PageResult(convert(p.items), p.nextPage)
    }

    // ---------- Thịnh hành: các chủ đề hay xem ở Việt Nam ----------
    class Kiosk(val id: String, val name: String)

    fun kiosks(): List<Kiosk> = listOf(
        Kiosk("nhạc trẻ mới nhất", "Nhạc Việt"),
        Kiosk("bolero trữ tình hay nhất", "Bolero"),
        Kiosk("nhạc remix tiktok việt", "Remix"),
        Kiosk("phim hay thuyết minh", "Phim"),
        Kiosk("tin tức thời sự hôm nay", "Tin tức"),
        Kiosk("bóng đá việt nam highlight", "Bóng đá"),
        Kiosk("hài việt nam mới nhất", "Hài"),
        Kiosk("phim hoạt hình thiếu nhi tiếng việt", "Thiếu nhi"),
        Kiosk("nấu ăn món ngon việt nam", "Nấu ăn"),
        Kiosk("du lịch việt nam", "Du lịch")
    )

    // ---------- Video ----------
    fun stream(url: String): StreamInfo = StreamInfo.getInfo(yt, url)

    fun related(info: StreamInfo): List<VItem> = convert(info.relatedItems).take(20)

    fun toItem(info: StreamInfo): VItem =
        VItem(info.url, info.name ?: "", info.uploaderName ?: "", thumbFor(info.url, null), info.duration)

    // ---------- Tiện ích ----------
    fun convert(items: List<InfoItem>?): List<VItem> {
        if (items == null) return emptyList()
        val out = ArrayList<VItem>(items.size)
        for (it in items) {
            if (it !is StreamInfoItem) continue
            if (it.streamType == StreamType.LIVE_STREAM || it.streamType == StreamType.AUDIO_LIVE_STREAM) continue
            if (it.isShortFormContent) continue
            val fallback = it.thumbnails.minByOrNull { img ->
                val h = if (img.height > 0) img.height else 999
                kotlin.math.abs(h - 180)
            }?.url
            out.add(VItem(it.url, it.name ?: "", it.uploaderName ?: "", thumbFor(it.url, fallback), it.duration))
        }
        return out
    }

    private val idRe = Regex("(?:v=|youtu\\.be/|/shorts/|/embed/)([A-Za-z0-9_-]{11})")

    fun videoId(url: String): String? = idRe.find(url)?.groupValues?.get(1)

    /** Ảnh nhỏ 320x180 cho nhẹ RAM. */
    fun thumbFor(url: String, fallback: String?): String {
        val id = videoId(url)
        return if (id != null) "https://i.ytimg.com/vi/$id/mqdefault.jpg" else (fallback ?: "")
    }
}
