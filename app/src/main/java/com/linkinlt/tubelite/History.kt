package com.linkinlt.tubelite

import android.content.Context
import org.json.JSONArray

/** Lịch sử xem (tối đa 100 video), lưu cả vị trí đang xem để xem tiếp. */
object History {
    private const val MAX = 100
    private fun prefs() = App.instance.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun all(): MutableList<VItem> {
        val out = ArrayList<VItem>()
        try {
            val arr = JSONArray(prefs().getString("list", "[]"))
            for (i in 0 until arr.length()) out.add(VItem.fromJson(arr.getJSONObject(i)))
        } catch (_: Exception) {}
        return out
    }

    private fun save(list: List<VItem>) {
        val arr = JSONArray()
        list.take(MAX).forEach { arr.put(it.toJson()) }
        prefs().edit().putString("list", arr.toString()).apply()
    }

    fun add(item: VItem) {
        val list = all()
        val old = list.firstOrNull { it.url == item.url }
        list.removeAll { it.url == item.url }
        if (item.position == 0L && old != null) item.position = old.position
        list.add(0, item)
        save(list)
    }

    fun position(url: String): Long = all().firstOrNull { it.url == url }?.position ?: 0L

    fun setPosition(url: String, posMs: Long) {
        val list = all()
        val it = list.firstOrNull { v -> v.url == url } ?: return
        it.position = posMs
        save(list)
    }

    fun remove(url: String) = save(all().filter { it.url != url })

    fun clear() = save(emptyList())
}
