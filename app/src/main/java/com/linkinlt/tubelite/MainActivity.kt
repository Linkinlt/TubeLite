package com.linkinlt.tubelite

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.EditText
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler

class MainActivity : Activity() {

    private enum class Tab { SEARCH, TRENDING, HISTORY }

    /** Giữ kết quả từng tab để chuyển qua lại không phải tải lại. */
    private class State {
        val items = ArrayList<VItem>()
        var next: Page? = null
        var title = ""
        var scroll = 0
    }

    private lateinit var navSearch: TextView
    private lateinit var navTrending: TextView
    private lateinit var navHistory: TextView
    private lateinit var navUpdate: TextView
    private lateinit var searchBar: View
    private lateinit var searchInput: EditText
    private lateinit var chipScroll: HorizontalScrollView
    private lateinit var chips: LinearLayout
    private lateinit var pageTitle: TextView
    private lateinit var grid: GridView
    private lateinit var status: TextView
    private val adapter = VideoAdapter()

    private var tab: Tab? = null
    private val states = mapOf(Tab.SEARCH to State(), Tab.TRENDING to State(), Tab.HISTORY to State())
    private var loading = false
    private var token = 0

    private var searchHandler: SearchQueryHandler? = null
    private var kiosks: List<Yt.Kiosk> = emptyList()
    private var kioskId: String? = null
    private var trendHandler: SearchQueryHandler? = null
    private var lastBack = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        navSearch = findViewById(R.id.navSearch)
        navTrending = findViewById(R.id.navTrending)
        navHistory = findViewById(R.id.navHistory)
        navUpdate = findViewById(R.id.navUpdate)
        searchBar = findViewById(R.id.searchBar)
        searchInput = findViewById(R.id.searchInput)
        chipScroll = findViewById(R.id.chipScroll)
        chips = findViewById(R.id.chips)
        pageTitle = findViewById(R.id.pageTitle)
        grid = findViewById(R.id.grid)
        status = findViewById(R.id.status)

        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> open(adapter.items[pos]) }
        grid.setOnItemLongClickListener { _, _, pos, _ -> longPress(pos); true }
        grid.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}
            override fun onScroll(v: AbsListView?, first: Int, visible: Int, total: Int) {
                if (total > 0 && first + visible >= total - 4) loadMore()
            }
        })
        grid.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos >= adapter.count - 4) loadMore()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        })

        // Chuyển tab khi di chuyển con trỏ lên mục (giống SmartTube)
        val navs = mapOf(navSearch to Tab.SEARCH, navTrending to Tab.TRENDING, navHistory to Tab.HISTORY)
        for ((v, t) in navs) {
            v.setOnFocusChangeListener { _, has -> if (has) switchTab(t) }
            v.setOnClickListener {
                switchTab(t)
                if (t == Tab.SEARCH) searchInput.requestFocus() else if (adapter.count > 0) grid.requestFocus()
            }
        }

        navUpdate.text = "Giới thiệu"
        navUpdate.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("TubeLite")
                .setMessage("Ứng dụng YouTube nhẹ cho các thiết bị Android 5.\n\nPhiên bản: build ${BuildConfig.VERSION_CODE}\n\nTB")
                .setPositiveButton("Kiểm tra cập nhật") { _, _ -> Updater.check(this) }
                .setNegativeButton("Đóng", null)
                .show()
        }
        // Mở app thì kiểm tra ngầm, có bản mới thì báo ở mục Giới thiệu
        Updater.checkSilent { navUpdate.text = "Giới thiệu  •  Có bản mới" }

        searchInput.setOnEditorActionListener { _, actionId, ev ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
                ev?.keyCode == KeyEvent.KEYCODE_ENTER) { doSearch(); true } else false
        }
        findViewById<View>(R.id.btnGo).setOnClickListener { doSearch() }
        val mic = findViewById<View>(R.id.btnMic)
        val voice = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
        if (packageManager.queryIntentActivities(voice, 0).isEmpty()) mic.visibility = View.GONE
        mic.setOnClickListener {
            try { startActivityForResult(voice, REQ_VOICE) } catch (_: Exception) {
                Toast.makeText(this, "Máy không hỗ trợ giọng nói", Toast.LENGTH_SHORT).show()
            }
        }

        switchTab(Tab.TRENDING)
        navTrending.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (tab == Tab.HISTORY) showHistory()
    }

    // ---------------- Tabs ----------------
    private fun switchTab(t: Tab) {
        if (t == tab) return
        tab?.let { states[it]!!.scroll = grid.firstVisiblePosition }
        tab = t
        token++
        loading = false
        navSearch.isActivated = t == Tab.SEARCH
        navTrending.isActivated = t == Tab.TRENDING
        navHistory.isActivated = t == Tab.HISTORY
        searchBar.visibility = if (t == Tab.SEARCH) View.VISIBLE else View.GONE
        chipScroll.visibility = if (t == Tab.TRENDING) View.VISIBLE else View.GONE
        val st = states[t]!!
        adapter.set(st.items)
        grid.setSelection(st.scroll)
        pageTitle.text = st.title
        when (t) {
            Tab.SEARCH -> {
                if (st.items.isEmpty()) showStatus("Bấm vào ô tìm kiếm ở trên, nhập tên video rồi bấm OK") else hideStatus()
                pageTitle.text = st.title
            }
            Tab.TRENDING -> {
                if (kiosks.isEmpty()) loadKiosks()
                else if (st.items.isEmpty()) kioskId?.let { loadKiosk(it) } else hideStatus()
            }
            Tab.HISTORY -> showHistory()
        }
    }

    private fun showStatus(msg: String) { status.text = msg; status.visibility = View.VISIBLE }
    private fun hideStatus() { status.visibility = View.GONE }

    private fun fail(t: Throwable) {
        loading = false
        val msg = Errors.message(t)
        if (adapter.count == 0) showStatus(msg) else Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    // ---------------- Tìm kiếm ----------------
    private fun doSearch() {
        val q = searchInput.text.toString().trim()
        if (q.isEmpty()) return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchInput.windowToken, 0)
        val st = states[Tab.SEARCH]!!
        st.items.clear(); st.next = null; st.scroll = 0
        st.title = "Kết quả: $q"
        pageTitle.text = st.title
        adapter.set(emptyList())
        showStatus("Đang tìm…")
        val my = ++token
        loading = true
        Bg.run({
            val h = Yt.searchHandler(q)
            h to Yt.search(h)
        }) { res ->
            if (my != token) return@run
            loading = false
            res.onSuccess { (h, page) ->
                searchHandler = h
                st.items.addAll(page.items); st.next = page.next
                adapter.set(st.items)
                if (st.items.isEmpty()) showStatus("Không tìm thấy video nào") else { hideStatus(); grid.requestFocus(); grid.setSelection(0) }
            }.onFailure { fail(it) }
        }
    }

    // ---------------- Thịnh hành ----------------
    private fun loadKiosks() {
        showStatus("Đang tải…")
        val my = ++token
        Bg.run({ Yt.kiosks() }) { res: Result<List<Yt.Kiosk>> ->
            if (my != token) return@run
            res.onSuccess { list ->
                kiosks = list
                chips.removeAllViews()
                for (k in list) {
                    val c = TextView(this).apply {
                        text = k.name
                        setTextColor(0xFFFFFFFF.toInt())
                        textSize = 15f
                        setPadding(dp(18), dp(8), dp(18), dp(8))
                        setBackgroundResource(R.drawable.chip)
                        isFocusable = true
                        tag = k.id
                        setOnClickListener { selectKiosk(k.id) }
                        setOnFocusChangeListener { _, has -> if (has) selectKiosk(k.id) }
                    }
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.rightMargin = dp(10)
                    chips.addView(c, lp)
                }
                list.firstOrNull()?.let { selectKiosk(it.id) }
            }.onFailure { fail(it) }
        }
    }

    private fun selectKiosk(id: String) {
        if (id == kioskId && states[Tab.TRENDING]!!.items.isNotEmpty()) return
        kioskId = id
        for (i in 0 until chips.childCount) chips.getChildAt(i).let { it.isActivated = it.tag == id }
        loadKiosk(id)
    }

    private fun loadKiosk(id: String) {
        val st = states[Tab.TRENDING]!!
        st.items.clear(); st.next = null; st.scroll = 0
        st.title = kiosks.firstOrNull { it.id == id }?.name ?: ""
        pageTitle.text = st.title
        adapter.set(emptyList())
        showStatus("Đang tải…")
        val my = ++token
        loading = true
        Bg.run({
            val h = Yt.searchHandler(id)
            h to Yt.search(h)
        }) { res ->
            if (my != token || tab != Tab.TRENDING) return@run
            loading = false
            res.onSuccess { (h, page) ->
                trendHandler = h
                st.items.addAll(page.items); st.next = page.next
                adapter.set(st.items)
                if (st.items.isEmpty()) showStatus("Chưa có video") else hideStatus()
            }.onFailure { fail(it) }
        }
    }

    // ---------------- Tải thêm khi cuộn ----------------
    private fun loadMore() {
        val t = tab ?: return
        if (loading || t == Tab.HISTORY) return
        val st = states[t]!!
        val next = st.next ?: return
        val my = token
        loading = true
        Bg.run({
            when (t) {
                Tab.SEARCH -> Yt.searchMore(searchHandler!!, next)
                else -> Yt.searchMore(trendHandler!!, next)
            }
        }) { res ->
            if (my != token || tab != t) return@run
            loading = false
            res.onSuccess { p ->
                st.next = p.next
                adapter.add(p.items)
                st.items.clear(); st.items.addAll(adapter.items)
            }.onFailure { st.next = null }
        }
    }

    // ---------------- Lịch sử ----------------
    private fun showHistory() {
        val st = states[Tab.HISTORY]!!
        st.items.clear(); st.items.addAll(History.all())
        st.title = "Lịch sử xem"
        pageTitle.text = st.title
        adapter.set(st.items)
        if (st.items.isEmpty()) showStatus("Chưa xem video nào") else hideStatus()
    }

    private fun longPress(pos: Int) {
        if (tab != Tab.HISTORY) return
        val item = adapter.items.getOrNull(pos) ?: return
        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setItems(arrayOf("Xoá video này khỏi lịch sử", "Xoá toàn bộ lịch sử")) { _, which ->
                if (which == 0) History.remove(item.url) else History.clear()
                showHistory()
            }.show()
    }

    // ---------------- Mở video ----------------
    private fun open(item: VItem) {
        startActivity(Intent(this, PlayerActivity::class.java).putExtra(PlayerActivity.EXTRA_URL, item.url))
    }

    // ---------------- Phím điều khiển ----------------
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && tab == Tab.HISTORY && grid.hasFocus()) {
            longPress(grid.selectedItemPosition); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val sideFocused = navSearch.hasFocus() || navTrending.hasFocus() || navHistory.hasFocus() || navUpdate.hasFocus()
        if (!sideFocused) {
            when (tab) {
                Tab.SEARCH -> navSearch.requestFocus()
                Tab.HISTORY -> navHistory.requestFocus()
                else -> navTrending.requestFocus()
            }
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastBack < 2000) { finish(); return }
        lastBack = now
        Toast.makeText(this, "Bấm Back lần nữa để thoát", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return
            searchInput.setText(text)
            doSearch()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object { private const val REQ_VOICE = 7 }
}
