package com.linkinlt.tubelite

import org.json.JSONObject

data class VItem(
    val url: String,
    val title: String,
    val uploader: String,
    val thumb: String,
    val duration: Long,          // giây
    var position: Long = 0L      // ms đã xem (cho Lịch sử)
) {
    fun toJson(): JSONObject = JSONObject()
        .put("u", url).put("t", title).put("c", uploader)
        .put("i", thumb).put("d", duration).put("p", position)

    companion object {
        fun fromJson(o: JSONObject) = VItem(
            o.optString("u"), o.optString("t"), o.optString("c"),
            o.optString("i"), o.optLong("d"), o.optLong("p")
        )
    }
}

fun fmtTime(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
