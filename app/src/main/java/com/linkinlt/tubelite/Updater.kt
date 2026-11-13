package com.linkinlt.tubelite

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import org.json.JSONObject
import java.io.File

/**
 * Cập nhật: đọc version.json trên repo công khai Linkinlt/TubeLite-update,
 * nếu số build lớn hơn bản đang cài thì tải TubeLite.apk và cài đè.
 */
object Updater {
    private const val BASE = "https://raw.githubusercontent.com/Linkinlt/TubeLite-update/main/"

    class Info(val build: Int, val name: String, val notes: String)

    fun apkFile(ctx: Context) = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "update.apk")

    private fun get(url: String): okhttp3.Response =
        DownloaderImpl.client.newCall(okhttp3.Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute()

    fun fetch(): Info? {
        get(BASE + "version.json?t=" + System.currentTimeMillis()).use { r ->
            if (!r.isSuccessful) return null
            val o = JSONObject(r.body?.string() ?: return null)
            return Info(o.optInt("build"), o.optString("name"), o.optString("notes"))
        }
    }

    /** Kiểm tra ngầm khi mở app: chỉ báo về có bản mới hay không. */
    fun checkSilent(done: (Info) -> Unit) {
        Bg.run({ fetch() }) { res -> res.getOrNull()?.let { if (it.build > BuildConfig.VERSION_CODE) done(it) } }
    }

    /** Nút "Kiểm tra cập nhật" trong mục Giới thiệu. */
    fun check(act: Activity) {
        Toast.makeText(act, "Đang kiểm tra…", Toast.LENGTH_SHORT).show()
        Bg.run({ fetch() }) { res ->
            if (act.isFinishing) return@run
            val info = res.getOrNull()
            when {
                info == null -> Toast.makeText(act, "Không kiểm tra được, thử lại sau", Toast.LENGTH_LONG).show()
                info.build <= BuildConfig.VERSION_CODE ->
                    Toast.makeText(act, "Đang dùng bản mới nhất (build ${BuildConfig.VERSION_CODE})", Toast.LENGTH_LONG).show()
                else -> AlertDialog.Builder(act)
                    .setTitle("Có bản mới: build ${info.build}")
                    .setMessage(info.notes.ifEmpty { "Cập nhật ngay?" })
                    .setPositiveButton("Cập nhật") { _, _ -> download(act) }
                    .setNegativeButton("Để sau", null)
                    .show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun download(act: Activity) {
        val pd = ProgressDialog(act).apply {
            setMessage("Đang tải bản mới…")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }
        val out = apkFile(act)
        Bg.run({
            get(BASE + "TubeLite.apk?t=" + System.currentTimeMillis()).use { r ->
                if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}")
                val body = r.body ?: throw IllegalStateException("empty")
                val total = body.contentLength()
                out.delete()
                body.byteStream().use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(32 * 1024)
                        var done = 0L; var last = -1
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            o.write(buf, 0, n); done += n
                            if (total > 0) {
                                val p = (done * 100 / total).toInt()
                                if (p != last) { last = p; Bg.main.post { pd.progress = p } }
                            }
                        }
                    }
                }
            }
            out.setReadable(true, false)
        }) { res ->
            try { pd.dismiss() } catch (_: Exception) {}
            if (res.isFailure) { Toast.makeText(act, "Tải bản mới lỗi, thử lại sau", Toast.LENGTH_LONG).show(); return@run }
            install(act)
        }
    }

    private fun install(act: Activity) {
        try {
            val i = if (Build.VERSION.SDK_INT >= 24)
                Intent(Intent.ACTION_VIEW).setDataAndType(ApkProvider.uri(act), "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            else
                Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(apkFile(act)), "application/vnd.android.package-archive")
            act.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(act, "Không mở được trình cài đặt", Toast.LENGTH_LONG).show()
        }
    }
}
