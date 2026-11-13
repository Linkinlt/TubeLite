package com.linkinlt.tubelite

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class VideoAdapter : BaseAdapter() {
    val items = ArrayList<VItem>()

    fun set(list: List<VItem>) { items.clear(); items.addAll(list); notifyDataSetChanged() }
    fun add(list: List<VItem>) {
        val seen = items.mapTo(HashSet()) { it.url }
        items.addAll(list.filter { seen.add(it.url) })
        notifyDataSetChanged()
    }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    private class H(v: View) {
        val thumb: ImageView = v.findViewById(R.id.thumb)
        val dur: TextView = v.findViewById(R.id.duration)
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.sub)
        val watched: View = v.findViewById(R.id.watched)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false).also { it.tag = H(it) }
        val h = v.tag as H
        val it = items[position]
        h.title.text = it.title
        h.sub.text = it.uploader
        val d = fmtTime(it.duration)
        h.dur.text = d
        h.dur.visibility = if (d.isEmpty()) View.GONE else View.VISIBLE
        // Vạch đỏ đã xem bao nhiêu (trong Lịch sử)
        val lp = h.watched.layoutParams
        if (it.position > 0 && it.duration > 0) {
            val w = parent.width.takeIf { p -> p > 0 } ?: 0
            lp.width = ((it.position / 1000f / it.duration).coerceIn(0f, 1f) * (w / 4f)).toInt()
            h.watched.visibility = View.VISIBLE
        } else {
            h.watched.visibility = View.GONE
        }
        h.watched.layoutParams = lp
        ImageLoader.load(h.thumb, it.thumb)
        return v
    }
}
