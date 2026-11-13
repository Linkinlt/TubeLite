package com.linkinlt.tubelite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors

/** Bộ tải ảnh siêu nhẹ: RGB_565, cache nhỏ, không thư viện ngoài. */
object ImageLoader {
    private val pool = Executors.newFixedThreadPool(2)
    private val cache = object : LruCache<String, Bitmap>(
        // ~1/16 bộ nhớ app, tối đa 4MB
        minOf(4 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 16).toInt())
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun clear() = cache.evictAll()

    fun load(iv: ImageView, url: String) {
        iv.tag = url
        if (url.isEmpty()) { iv.setImageDrawable(null); return }
        val hit = cache.get(url)
        if (hit != null) { iv.setImageBitmap(hit); return }
        iv.setImageDrawable(null)
        pool.execute {
            val bmp = try { fetch(url) } catch (_: Throwable) { null } ?: return@execute
            cache.put(url, bmp)
            Bg.main.post { if (iv.tag == url) iv.setImageBitmap(bmp) }
        }
    }

    private fun fetch(url: String): Bitmap? {
        val req = okhttp3.Request.Builder().url(url).build()
        DownloaderImpl.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            val bytes = r.body?.bytes() ?: return null
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            var sample = 1
            while (o.outWidth / (sample * 2) >= 320) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
    }
}
