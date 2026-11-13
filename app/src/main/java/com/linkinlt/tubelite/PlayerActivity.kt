package com.linkinlt.tubelite

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.video.VideoSize
import org.schabi.newpipe.extractor.stream.StreamInfo

class PlayerActivity : Activity() {

    private lateinit var player: ExoPlayer
    private lateinit var aspect: AspectFrame
    private lateinit var loadingView: ProgressBar
    private lateinit var msg: TextView
    private lateinit var overlay: View
    private lateinit var vTitle: TextView
    private lateinit var vSub: TextView
    private lateinit var tPos: TextView
    private lateinit var tDur: TextView
    private lateinit var seek: SeekBar
    private lateinit var ctrls: LinearLayout
    private lateinit var bPlay: TextView
    private lateinit var bQuality: TextView
    private lateinit var bSpeed: TextView
    private lateinit var relTitle: TextView
    private lateinit var relScroll: HorizontalScrollView
    private lateinit var relBox: LinearLayout

    private var info: StreamInfo? = null
    private var kids: Kids.Video? = null
    private var related: List<VItem> = emptyList()
    private val back = ArrayList<String>()   // video đã xem trước đó (nút Trước)
    private var curUrl = ""
    private var token = 0
    private var curHeight = 0
    private var triedMuxed = false
    private var speed = 1f
    private var userSeeking = false

    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val maxQuality get() = prefs.getInt("quality", Device.defaultQuality).coerceAtMost(Device.maxHeight)

    private val hideRunnable = Runnable { hideOverlay() }
    private val tick = object : Runnable {
        override fun run() {
            updateTime()
            Bg.main.postDelayed(this, 1000)
        }
    }
    private var saveCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        aspect = findViewById(R.id.aspect)
        loadingView = findViewById(R.id.loading)
        msg = findViewById(R.id.msg)
        overlay = findViewById(R.id.overlay)
        vTitle = findViewById(R.id.vTitle)
        vSub = findViewById(R.id.vSub)
        tPos = findViewById(R.id.tPos)
        tDur = findViewById(R.id.tDur)
        seek = findViewById(R.id.seek)
        ctrls = findViewById(R.id.ctrls)
        bPlay = findViewById(R.id.bPlay)
        bQuality = findViewById(R.id.bQuality)
        bSpeed = findViewById(R.id.bSpeed)
        relTitle = findViewById(R.id.relTitle)
        relScroll = findViewById(R.id.relScroll)
        relBox = findViewById(R.id.related)

        // Bộ đệm nhỏ cho máy RAM 1GB
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000, 25_000, 1_500, 3_000)
            .setTargetBufferBytes(Device.bufferBytes)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
        val selector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setMaxVideoSize(Device.maxWidth(Device.maxHeight), Device.maxHeight))
        }
        player = ExoPlayer.Builder(this, DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setTrackSelector(selector)
            .setLoadControl(load)
            .build()
        player.setVideoSurfaceView(findViewById<SurfaceView>(R.id.surface))
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                loadingView.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                if (state == Player.STATE_READY) {
                    val d = player.duration
                    if (d > 0) { seek.max = (d / 1000).toInt(); tDur.text = fmtTime(d / 1000) }
                }
                if (state == Player.STATE_ENDED) {
                    History.setPosition(curUrl, 0)
                    playNext(auto = true)
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlayButton()
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.height > 0) aspect.ratio = size.width * size.pixelWidthHeightRatio / size.height
            }
            override fun onPlayerError(error: PlaybackException) = onError(error)
        })

        seek.keyProgressIncrement = 10
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) { player.seekTo(p * 1000L); tPos.text = fmtTime(p.toLong()).ifEmpty { "0:00" }; showOverlay(null) }
            }
            override fun onStartTrackingTouch(sb: SeekBar) { userSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar) { userSeeking = false }
        })

        findViewById<View>(R.id.bPrev).setOnClickListener { playPrev() }
        findViewById<View>(R.id.bRew).setOnClickListener { seekBy(-10_000) }
        bPlay.setOnClickListener { togglePlay() }
        findViewById<View>(R.id.bFwd).setOnClickListener { seekBy(10_000) }
        findViewById<View>(R.id.bNext).setOnClickListener { playNext(auto = false) }
        bQuality.setOnClickListener { chooseQuality() }
        bSpeed.setOnClickListener { chooseSpeed() }
        relTitle.setOnClickListener { openRelated() }

        val url = intent.getStringExtra(EXTRA_URL)
        if (url == null) { finish(); return }
        play(url, remember = false)
        Bg.main.post(tick)
    }

    // ---------------- Phát video ----------------
    private fun play(url: String, remember: Boolean) {
        if (remember && curUrl.isNotEmpty()) { savePosition(); back.add(curUrl) }
        curUrl = url
        triedMuxed = false
        val my = ++token
        player.stop()
        msg.visibility = View.GONE
        loadingView.visibility = View.VISIBLE
        relBox.removeAllViews(); related = emptyList()
        relScroll.visibility = View.GONE
        vTitle.text = ""; vSub.text = ""
        tPos.text = "0:00"; tDur.text = "0:00"; seek.progress = 0
        info = null; kids = null
        Bg.run({ Yt.stream(url) }) { res ->
            if (my != token || isFinishing) return@run
            res.onSuccess { i ->
                info = i
                vTitle.text = i.name
                vSub.text = i.uploaderName
                val item = Yt.toItem(i)
                val resume = History.position(i.url)
                History.add(item)
                related = Yt.related(i)
                buildRelated()
                start(i, if (resume > 30_000 && (i.duration <= 0 || resume < i.duration * 1000 - 30_000)) resume else 0L, false)
                showOverlay(bPlay)
            }.onFailure { t ->
                if (t is org.schabi.newpipe.extractor.exceptions.ReCaptchaException) {
                    loadingView.visibility = View.GONE
                    showMsg(Errors.message(t))
                } else playKids(url, my, t)
            }
        }
    }

    /** Video "dành cho trẻ em": thử lấy luồng theo kiểu trình phát nhúng. */
    private fun playKids(url: String, my: Int, first: Throwable) {
        Bg.run({
            val v = Kids.fetch(url)
            val rel = try {
                Yt.search(Yt.searchHandler(v.author.ifEmpty { v.title })).items.filter { Yt.videoId(it.url) != v.id }.take(20)
            } catch (_: Exception) { emptyList() }
            v to rel
        }) { res ->
            if (my != token || isFinishing) return@run
            res.onSuccess { (v, rel) ->
                kids = v
                vTitle.text = v.title
                vSub.text = v.author
                val item = v.item()
                val resume = History.position(item.url)
                History.add(item)
                related = rel
                buildRelated()
                startKids(v, if (resume > 30_000 && (v.duration <= 0 || resume < v.duration * 1000 - 30_000)) resume else 0L, false)
                showOverlay(bPlay)
            }.onFailure { second ->
                loadingView.visibility = View.GONE
                // Kèm chi tiết lỗi (chữ nhỏ) để dễ tìm nguyên nhân
                showMsg(Errors.message(first) + "\n\n" + detail("1", first) + "\n" + detail("2", second))
            }
        }
    }

    private fun detail(tag: String, t: Throwable): String {
        var e: Throwable? = t
        val parts = ArrayList<String>()
        while (e != null && parts.size < 3) {
            parts.add(e.javaClass.simpleName + ": " + (e.message ?: "").take(120))
            e = e.cause
        }
        return "[$tag] " + parts.joinToString(" <- ")
    }

    private fun startKids(v: Kids.Video, posMs: Long, muxedOnly: Boolean) {
        try {
            val b = Kids.build(v, maxQuality, muxedOnly)
            curHeight = b.height
            bQuality.text = if (b.height > 0) "${b.height}p" else "Chất lượng"
            player.setMediaSource(b.source, posMs)
            player.setPlaybackSpeed(speed)
            player.prepare()
            player.playWhenReady = true
        } catch (t: Throwable) {
            loadingView.visibility = View.GONE
            showMsg(Errors.message(t))
        }
    }

    private fun start(i: StreamInfo, posMs: Long, muxedOnly: Boolean) {
        try {
            val b = Media.build(i, maxQuality, muxedOnly)
            curHeight = b.height
            bQuality.text = if (b.height > 0) "${b.height}p" else "Chất lượng"
            player.setMediaSource(b.source, posMs)
            player.setPlaybackSpeed(speed)
            player.prepare()
            player.playWhenReady = true
        } catch (t: Throwable) {
            loadingView.visibility = View.GONE
            showMsg(Errors.message(t))
        }
    }

    private fun onError(e: PlaybackException) {
        kids?.let { v ->
            if (!triedMuxed && v.muxed.isNotEmpty()) {
                triedMuxed = true
                startKids(v, player.currentPosition.coerceAtLeast(0), true)
                return
            }
            loadingView.visibility = View.GONE
            showMsg("Không phát được video này (${e.errorCodeName})")
            return
        }
        val i = info ?: return
        if (!triedMuxed) {
            // Thử lại bằng luồng gộp sẵn (nhẹ hơn, ít lỗi hơn)
            triedMuxed = true
            start(i, player.currentPosition.coerceAtLeast(0), true)
            return
        }
        loadingView.visibility = View.GONE
        showMsg("Không phát được video này (${e.errorCodeName})")
    }

    private fun showMsg(text: String) {
        msg.text = text
        msg.visibility = View.VISIBLE
        showOverlay(bPlay)
    }

    private fun playNext(auto: Boolean) {
        val next = related.firstOrNull { it.url != curUrl }
        if (next == null) { if (!auto) Toast.makeText(this, "Không có video tiếp theo", Toast.LENGTH_SHORT).show(); return }
        if (auto) Toast.makeText(this, "Phát tiếp: ${next.title}", Toast.LENGTH_SHORT).show()
        play(next.url, remember = true)
    }

    private fun playPrev() {
        if (back.isEmpty()) { Toast.makeText(this, "Không có video trước", Toast.LENGTH_SHORT).show(); return }
        savePosition()
        val url = back.removeAt(back.size - 1)
        play(url, remember = false)
    }

    private fun togglePlay() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.playWhenReady = !player.playWhenReady
        if (!player.playWhenReady) savePosition()
        updatePlayButton()
    }

    private fun seekBy(ms: Long) {
        val d = player.duration
        var p = player.currentPosition + ms
        if (p < 0) p = 0
        if (d > 0 && p > d - 1000) p = d - 1000
        player.seekTo(p)
        updateTime()
    }

    private fun updatePlayButton() {
        val playing = player.playWhenReady
        bPlay.setCompoundDrawablesWithIntrinsicBounds(0, if (playing) R.drawable.ic_pause else R.drawable.ic_play, 0, 0)
        bPlay.text = if (playing) "Tạm dừng" else "Phát"
        if (!playing) Bg.main.removeCallbacks(hideRunnable) else scheduleHide()
    }

    private fun updateTime() {
        if (isFinishing) return
        val pos = player.currentPosition
        if (overlay.visibility == View.VISIBLE && !userSeeking && !seek.isFocused) {
            seek.progress = (pos / 1000).toInt()
            seek.secondaryProgress = (player.bufferedPosition / 1000).toInt()
        }
        if (overlay.visibility == View.VISIBLE) tPos.text = fmtTime(pos / 1000).ifEmpty { "0:00" }
        if (player.isPlaying && ++saveCounter >= 15) { saveCounter = 0; savePosition() }
    }

    private fun savePosition() {
        if (curUrl.isEmpty() || player.duration <= 0) return
        val url = info?.url ?: kids?.item()?.url ?: curUrl
        val pos = player.currentPosition
        Bg.exec { History.setPosition(url, pos) }
    }

    // ---------------- Chất lượng / tốc độ ----------------
    private fun chooseQuality() {
        kids?.let { v ->
            val hs = v.heights().ifEmpty { listOf(360) }
            val labels = hs.map { if (it == curHeight) "✓  ${it}p" else "    ${it}p" }.toTypedArray()
            AlertDialog.Builder(this).setTitle("Chất lượng (máy này tối đa ${Device.maxHeight}p)")
                .setItems(labels) { _, w ->
                    prefs.edit().putInt("quality", hs[w]).apply()
                    triedMuxed = false
                    startKids(v, player.currentPosition, false)
                }.show()
            showOverlay(bQuality)
            return
        }
        val i = info ?: return
        val hs = Media.heights(i).ifEmpty { listOf(360) }
        val labels = hs.map { if (it == curHeight) "✓  ${it}p" else "    ${it}p" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Chất lượng (máy này tối đa ${Device.maxHeight}p)")
            .setItems(labels) { _, w ->
                prefs.edit().putInt("quality", hs[w]).apply()
                triedMuxed = false
                start(i, player.currentPosition, false)
            }.show()
        showOverlay(bQuality)
    }

    private fun chooseSpeed() {
        val sp = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val labels = sp.map { (if (it == speed) "✓  " else "    ") + fmtSpeed(it) }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Tốc độ phát")
            .setItems(labels) { _, w ->
                speed = sp[w]
                player.setPlaybackSpeed(speed)
                bSpeed.text = "Tốc độ ${fmtSpeed(speed)}"
            }.show()
        showOverlay(bSpeed)
    }

    private fun fmtSpeed(f: Float) = (if (f == f.toInt().toFloat()) f.toInt().toString() else f.toString()) + "x"

    // ---------------- Video liên quan ----------------
    private fun buildRelated() {
        relBox.removeAllViews()
        relTitle.visibility = if (related.isEmpty()) View.GONE else View.VISIBLE
        val inf = LayoutInflater.from(this)
        val w = dp(200)
        for (r in related) {
            val v = inf.inflate(R.layout.item_video, relBox, false)
            v.layoutParams = LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(10) }
            v.findViewById<View>(R.id.thumbBox).layoutParams.height = dp(112)
            v.setBackgroundResource(R.drawable.card_focus)
            v.isFocusable = true
            v.findViewById<TextView>(R.id.title).text = r.title
            v.findViewById<TextView>(R.id.sub).text = r.uploader
            val d = fmtTime(r.duration)
            v.findViewById<TextView>(R.id.duration).apply { text = d; visibility = if (d.isEmpty()) View.GONE else View.VISIBLE }
            v.findViewById<View>(R.id.watched).visibility = View.GONE
            v.tag = r.url
            v.setOnClickListener { play(r.url, remember = true) }
            relBox.addView(v)
        }
    }

    private fun loadRelatedThumbs() {
        for (k in 0 until relBox.childCount) {
            val v = relBox.getChildAt(k)
            val r = related.getOrNull(k) ?: continue
            ImageLoader.load(v.findViewById<ImageView>(R.id.thumb), r.thumb)
        }
    }

    private fun openRelated() {
        if (relBox.childCount == 0) return
        relScroll.visibility = View.VISIBLE
        relTitle.text = "Video liên quan  ▲"
        loadRelatedThumbs()     // chỉ tải ảnh khi mở ra, đỡ RAM
        relBox.getChildAt(0).requestFocus()
        scheduleHide()
    }

    private fun closeRelated() {
        relScroll.visibility = View.GONE
        relTitle.text = "Video liên quan  ▼"
        // Giải phóng ảnh khi đóng
        for (k in 0 until relBox.childCount) relBox.getChildAt(k).findViewById<ImageView>(R.id.thumb).setImageDrawable(null)
        bPlay.requestFocus()
    }

    // ---------------- Hiện/ẩn thanh điều khiển ----------------
    private fun showOverlay(focus: View?) {
        val wasHidden = overlay.visibility != View.VISIBLE
        overlay.visibility = View.VISIBLE
        if (wasHidden) updateTime()
        if (focus != null) focus.requestFocus()
        else if (wasHidden || currentFocus == null) bPlay.requestFocus()
        scheduleHide()
    }

    private fun scheduleHide() {
        Bg.main.removeCallbacks(hideRunnable)
        if (player.playWhenReady && msg.visibility != View.VISIBLE)
            Bg.main.postDelayed(hideRunnable, if (relScroll.visibility == View.VISIBLE) 10_000 else 5_000)
    }

    private fun hideOverlay() {
        if (relScroll.visibility == View.VISIBLE) closeRelated()
        overlay.visibility = View.GONE
        findViewById<View>(R.id.root).requestFocus()
    }

    private fun inRelated() = relScroll.visibility == View.VISIBLE && relBox.findFocus() != null
    private fun inCtrls() = ctrls.findFocus() != null

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val code = e.keyCode
        // Phím media luôn hoạt động
        if (e.action == KeyEvent.ACTION_DOWN) {
            when (code) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> { togglePlay(); showOverlay(bPlay); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { if (!player.playWhenReady) togglePlay(); return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (player.playWhenReady) togglePlay(); showOverlay(bPlay); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(10_000); showOverlay(null); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-10_000); showOverlay(null); return true }
                KeyEvent.KEYCODE_MEDIA_NEXT -> { playNext(false); return true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { playPrev(); return true }
            }
        }
        if (code == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e)

        val hidden = overlay.visibility != View.VISIBLE
        if (hidden) {
            if (e.action != KeyEvent.ACTION_DOWN) return true
            when (code) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { togglePlay(); showOverlay(bPlay) }
                KeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-10_000); showOverlay(seek) }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(10_000); showOverlay(seek) }
                else -> showOverlay(bPlay)
            }
            return true
        }

        scheduleHide()
        if (e.action == KeyEvent.ACTION_DOWN) {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN && inCtrls()) { openRelated(); return true }
            if (code == KeyEvent.KEYCODE_DPAD_UP && inRelated()) { closeRelated(); return true }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN && inRelated()) return true
        }
        return super.dispatchKeyEvent(e)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            relScroll.visibility == View.VISIBLE -> closeRelated()
            overlay.visibility == View.VISIBLE && player.playWhenReady && msg.visibility != View.VISIBLE -> hideOverlay()
            else -> { savePosition(); finish() }
        }
    }

    override fun onPause() {
        super.onPause()
        savePosition()
        player.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        token++
        Bg.main.removeCallbacks(tick)
        Bg.main.removeCallbacks(hideRunnable)
        player.release()
        ImageLoader.clear()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object { const val EXTRA_URL = "url" }
}
