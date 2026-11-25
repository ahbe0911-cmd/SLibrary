package ir.library123057.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ir.library123057.app.databinding.ActivityMainBinding
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var optimizer: WebViewOptimizer
    private lateinit var tools: LibraryTools
    private val homeHost = Uri.parse(AppConfig.HOME_URL).host.orEmpty()
    private var lastBack = 0L
    private var toast: Toast? = null
    private var activeUrl: String? = null
    private var failedUrl: String? = null
    private var sslWarning = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var uploadOrigin: String? = null
    private var pendingDownload: DownloadInfo? = null
    private data class DownloadInfo(val url: String, val agent: String, val disposition: String, val mime: String)
    private data class Nav(val id: Int, val url: String?)

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileCallback
        val origin = uploadOrigin
        fileCallback = null
        uploadOrigin = null
        if (callback != null) {
            val selected = linkedSetOf<Uri>()
            if (result.resultCode == Activity.RESULT_OK && origin != null && sameUrl(origin, binding.webView.url)) {
                val data = result.data
                data?.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { if (validUpload(it)) selected.add(it) }
                }
                data?.data?.let { if (validUpload(it)) selected.add(it) }
            }
            callback.onReceiveValue(selected.takeIf { it.isNotEmpty() }?.toTypedArray())
        }
    }
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val info = pendingDownload
        pendingDownload = null
        if (granted && info != null) enqueueDownload(info)
        else if (!granted) message("برای ذخیره فایل، مجوز حافظه لازم است.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        optimizer = WebViewOptimizer(this)
        tools = LibraryTools(this, binding, ::trusted, ::loadPage, ::message)
        splash.setOnExitAnimationListener { p ->
            p.view.animate().alpha(0f).setDuration(220).withEndAction { p.remove() }.start()
        }
        if (Build.VERSION.SDK_INT < 26) window.navigationBarColor = ContextCompat.getColor(this, R.color.primary_dark)
        applyFont(binding.root, ResourcesCompat.getFont(this, R.font.vazir) ?: Typeface.DEFAULT)
        setupWebView()
        tools.setup()
        setupNavigation()
        binding.headerRefresh.setOnClickListener { retry() }
        binding.retryButton.setOnClickListener { retry() }
        binding.swipeRefresh.setColorSchemeResources(R.color.primary, R.color.accent)
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ -> binding.webView.canScrollVertically(-1) }
        binding.swipeRefresh.setOnRefreshListener { retry() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (tools.cancelScan()) return
                if (binding.webView.canGoBack()) {
                    lastBack = 0
                    beginLoading()
                    binding.webView.goBack()
                } else {
                    val now = SystemClock.elapsedRealtime()
                    if (lastBack != 0L && now - lastBack <= 2000) finish()
                    else { lastBack = now; message(getString(R.string.exit_message)) }
                }
            }
        })
        savedInstanceState?.getBundle("download")?.let {
            pendingDownload = DownloadInfo(it.getString("url").orEmpty(), it.getString("agent").orEmpty(), it.getString("disposition").orEmpty(), it.getString("mime").orEmpty())
        }
        val restored = savedInstanceState?.getBundle("web")?.let { binding.webView.restoreState(it) }
        if (restored == null) loadPage(AppConfig.HOME_URL)
        else {
            activeUrl = binding.webView.url
            updateNavigation(activeUrl)
            savedInstanceState?.let { state ->
                state.getString("errorUrl")?.let { u ->
                    showError(state.getString("errorTitle") ?: getString(R.string.server_error_title),
                        state.getString("errorDescription") ?: getString(R.string.server_error_description), u)
                }
            }
        }
    }

    private fun applyFont(v: View, font: Typeface) {
        if (v is TextView) v.typeface = Typeface.create(font, v.typeface?.style ?: Typeface.NORMAL)
        if (v is ViewGroup) for (i in 0 until v.childCount) applyFont(v.getChildAt(i), font)
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun setupWebView() {
        WebView.setWebContentsDebuggingEnabled(false)
        val w = binding.webView
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 95
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            defaultTextEncodingName = "UTF-8"
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            setGeolocationEnabled(false)
            if (Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
        }
        // Keep the normal hardware-accelerated WebView compositor; do not force a bitmap layer.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            w.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
        }
        w.isHorizontalScrollBarEnabled = false
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(w, false)
        }
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return request.url.scheme?.lowercase(Locale.ROOT) !in setOf("https","about","data","blob")
                if (request.url.scheme == "slibrary-scan") {
                    if (request.hasGesture() && view.url?.let { trusted(Uri.parse(it)) } == true) tools.requestScan()
                    return true
                }
                return navigate(request.url)
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                tools.cancelScan()
                activeUrl = url
                lastBack = 0
                beginLoading()
                updateNavigation(url)
                if (url != null && !trusted(Uri.parse(url))) {
                    view.stopLoading()
                    if (secure(Uri.parse(url))) launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    showError(getString(R.string.access_denied), "برای امنیت، صفحات خارجی در مرورگر باز می‌شوند.", AppConfig.HOME_URL)
                }
            }
            override fun onPageCommitVisible(view: WebView, url: String?) {
                super.onPageCommitVisible(view, url)
                if (failedUrl == null && sameUrl(view.url, url)) optimizer.apply(view, url)
            }
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (failedUrl != null) { finishLoading(); return }
                if (!sameUrl(view.url, url)) return
                optimizer.apply(view, url)
                finishLoading()
                updateNavigation(url)
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (failedUrl == null) updateNavigation(url)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                val url = request.url.toString()
                if (error.errorCode == ERROR_FAILED_SSL_HANDSHAKE) sslError(url)
                else if (error.errorCode == ERROR_REDIRECT_LOOP) showError("خطا در تغییر مسیر", "لطفاً دوباره تلاش کنید.", url)
                else connectionError(url)
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (!request.isForMainFrame) return
                val url = request.url.toString()
                when (errorResponse.statusCode) {
                    401 -> message("برای دسترسی وارد حساب شوید.")
                    403 -> message(getString(R.string.access_denied))
                    404 -> showError(getString(R.string.page_not_found), getString(R.string.page_not_found_description), url)
                    in 500..599 -> showError(getString(R.string.server_error_title), getString(R.string.server_error_description), url)
                }
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                if (sameUrl(error.url, activeUrl) || sameUrl(error.url, view.url)) sslError(error.url)
                else if (!sslWarning) { sslWarning = true; message("یکی از منابع ناامن صفحه مسدود شد.") }
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.pageProgress.progress = newProgress.coerceIn(0,100)
                binding.pageProgress.visibility = if (failedUrl == null && newProgress < 100) View.VISIBLE else View.INVISIBLE
            }
            override fun onShowFileChooser(webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = null
                uploadOrigin = null
                val url = webView.url
                if (url == null || !trusted(Uri.parse(url)) || params.mode !in setOf(FileChooserParams.MODE_OPEN, FileChooserParams.MODE_OPEN_MULTIPLE)) {
                    callback.onReceiveValue(null)
                    message("این نوع آپلود مجاز نیست.")
                    return true
                }
                val types = mimeTypes(params.acceptTypes)
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                    type = if (types.size == 1) types.first() else "*/*"
                    if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
                }
                fileCallback = callback
                uploadOrigin = url
                try { picker.launch(intent) } catch (e: Exception) {
                    fileCallback = null
                    uploadOrigin = null
                    callback.onReceiveValue(null)
                    message("امکان انتخاب فایل وجود ندارد.")
                }
                return true
            }
        }
        w.setDownloadListener { url, agent, disposition, mime, _ ->
            requestDownload(DownloadInfo(url.orEmpty(),agent.orEmpty(),disposition.orEmpty(),mime.orEmpty()))
        }
    }

    private fun secure(u: Uri): Boolean = u.scheme.equals("https",true) && !u.host.isNullOrBlank() && u.userInfo == null
    private fun trusted(u: Uri): Boolean = secure(u) && u.port in listOf(-1,443) && (u.host.equals(homeHost,true) || (this::tools.isInitialized && tools.isSavedOrigin(u)))
    private fun sameUrl(a: String?, b: String?): Boolean = a != null && b != null && Uri.parse(a).buildUpon().fragment(null).build() == Uri.parse(b).buildUpon().fragment(null).build()
    private fun navItems() = listOf(
        Nav(R.id.navHome, AppConfig.HOME_URL),
        Nav(R.id.navStatus, tools.target(0)),
        Nav(R.id.navLoan, tools.target(1)),
        Nav(R.id.navReturn, tools.target(2))
    )

    private fun openTab(id: Int): Boolean {
        val item = navItems().firstOrNull { it.id == id } ?: return false
        val target = item.url
        if (target.isNullOrBlank()) {
            val index = when (id) { R.id.navStatus -> 0; R.id.navLoan -> 1; R.id.navReturn -> 2; else -> return false }
            tools.edit(index)
            return false
        }
        if (!trusted(Uri.parse(target))) { message("آدرس صفحه مجاز نیست."); return false }
        loadPage(target)
        return true
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { openTab(it.itemId) }
        binding.bottomNavigation.setOnItemReselectedListener { openTab(it.itemId) }
        updateNavigation(AppConfig.HOME_URL)
    }

    private fun updateNavigation(url: String?) {
        val match = navItems().firstOrNull { sameUrl(it.url, url) } ?: return
        val item = binding.bottomNavigation.menu.findItem(match.id)
        if (!item.isChecked) item.isChecked = true
    }

    private fun loadPage(url: String) {
        if (!trusted(Uri.parse(url))) { message("آدرس صفحه مجاز نیست."); return }
        // Ignore repeated taps, including a navigation already in progress.
        if (failedUrl == null && (sameUrl(binding.webView.url, url) ||
                (binding.pageProgress.visibility == View.VISIBLE && sameUrl(activeUrl, url)))) return
        binding.webView.stopLoading()
        activeUrl = url
        beginLoading()
        updateNavigation(url)
        binding.webView.loadUrl(url)
    }
    private fun retry() {
        failedUrl?.let { loadPage(it); return }
        val u = binding.webView.url
        if (u == null || !trusted(Uri.parse(u))) loadPage(AppConfig.HOME_URL)
        else { beginLoading(); binding.webView.reload() }
    }
    private fun beginLoading() {
        failedUrl = null; sslWarning = false
        binding.offlineScreen.visibility = View.GONE
        binding.swipeRefresh.visibility = View.VISIBLE
        binding.pageProgress.progress = 0
        binding.pageProgress.visibility = View.VISIBLE
    }
    private fun finishLoading() {
        binding.swipeRefresh.isRefreshing = false
        binding.pageProgress.progress = 100
        binding.pageProgress.visibility = View.INVISIBLE
    }
    private fun showError(title: String, description: String, url: String?) {
        failedUrl = url?.takeIf { trusted(Uri.parse(it)) } ?: activeUrl?.takeIf { trusted(Uri.parse(it)) } ?: AppConfig.HOME_URL
        finishLoading()
        binding.offlineTitle.text = title
        binding.offlineDescription.text = description
        binding.swipeRefresh.visibility = View.INVISIBLE
        binding.offlineScreen.visibility = View.VISIBLE
    }
    private fun connectionError(url: String?) {
        val cm = getSystemService(ConnectivityManager::class.java)
        val online = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        if (online) showError(getString(R.string.server_error_title),getString(R.string.server_error_description),url)
        else showError(getString(R.string.offline_title),getString(R.string.offline_description),url)
    }
    private fun sslError(url: String?) = showError(getString(R.string.ssl_error_title),getString(R.string.ssl_error_description),url)

    private fun domain(host: String, expected: String) = host == expected || host.endsWith(".$expected")
    private fun messenger(u: Uri): List<String> {
        val s = u.scheme.orEmpty().lowercase(Locale.ROOT)
        val h = u.host.orEmpty().lowercase(Locale.ROOT).trimEnd('.')
        return when {
            s in setOf("tg","telegram") || listOf("t.me","telegram.me","telegram.dog").any { domain(h,it) } -> listOf("org.telegram.messenger","org.telegram.messenger.web")
            s == "eitaa" || listOf("eitaa.com","eitaa.ir").any { domain(h,it) } -> listOf("ir.eitaa.messenger")
            s in setOf("bale","ble") || listOf("ble.ir","bale.ai","bale.ir").any { domain(h,it) } -> listOf("ir.nasim")
            s in setOf("rubika","rubino") || listOf("rubika.ir","rubino.ir").any { domain(h,it) } -> listOf("app.rbmain.a","ir.resaneh1.iptv")
            else -> emptyList()
        }
    }
    private fun launch(intent: Intent): Boolean = try { startActivity(intent); true } catch (e: Exception) { false }
    private fun external(u: Uri): Boolean {
        for (pkg in messenger(u)) {
            if (launch(Intent(Intent.ACTION_VIEW,u).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(pkg))) return true
        }
        return launch(Intent(Intent.ACTION_VIEW,u).addCategory(Intent.CATEGORY_BROWSABLE))
    }
    private fun navigate(u: Uri): Boolean {
        val scheme = u.scheme.orEmpty().lowercase(Locale.ROOT)
        when (scheme) {
            "https" -> {
                if (!secure(u)) message("آدرس لینک معتبر نیست.")
                else if (trusted(u)) return false
                else if (!external(u)) message("برنامه مناسب برای باز کردن لینک پیدا نشد.")
            }
            "http" -> {
                val secureUri = u.buildUpon().scheme("https").build()
                if (messenger(secureUri).isNotEmpty()) {
                    if (!external(secureUri)) message("پیام‌رسان در دسترس نیست.")
                } else message("لینک ناامن HTTP مسدود شد.")
            }
            "tel" -> if (!launch(Intent(Intent.ACTION_DIAL,u))) message("شماره‌گیر در دسترس نیست.")
            "mailto","sms","smsto" -> if (!launch(Intent(Intent.ACTION_SENDTO,u))) message("برنامه مناسب پیدا نشد.")
            "tg","telegram","bale","ble","eitaa","rubika","rubino" -> if (!external(u)) message("پیام‌رسان نصب نیست یا لینک پشتیبانی نمی‌شود.")
            "intent" -> {
                try {
                    val parsed = Intent.parseUri(u.toString(),Intent.URI_INTENT_SCHEME)
                    val target = parsed.data
                    val allowed = setOf("https","tg","telegram","bale","ble","eitaa","rubika","rubino")
                    val ok = target != null && target.scheme?.lowercase(Locale.ROOT) in allowed && messenger(target).isNotEmpty() && (target.scheme != "https" || secure(target)) && external(target)
                    if (!ok) {
                        val fallback = parsed.getStringExtra("browser_fallback_url")?.let { Uri.parse(it) }
                        if (fallback == null || !secure(fallback) || messenger(fallback).isEmpty() || !external(fallback)) message("لینک خارجی ناشناس یا نامعتبر است.")
                    }
                } catch (e: Exception) { message("خطا در باز کردن لینک خارجی.") }
            }
            else -> message("این نوع لینک پشتیبانی نمی‌شود.")
        }
        return true
    }
    private fun validUpload(uri: Uri): Boolean {
        val authority = uri.authority ?: return false
        if (uri.scheme != "content" || authority == packageName || authority.startsWith("$packageName.")) return false
        val provider = packageManager.resolveContentProvider(authority,0)
        if (provider?.packageName == packageName) return false
        return checkUriPermission(uri,android.os.Process.myPid(),android.os.Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
    private fun mimeTypes(values: Array<String>?): List<String> {
        val types = values.orEmpty().flatMap { it.split(",") }.map { it.trim().lowercase(Locale.ROOT) }.mapNotNull {
            when { it.startsWith(".") -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.drop(1))
                it.contains("/") -> it
                else -> null }
        }.distinct()
        return if (types.isEmpty() || "*/*" in types) listOf("*/*") else types
    }
    private fun requestDownload(info: DownloadInfo) {
        if (!secure(Uri.parse(info.url))) { message("دانلود مستقیم این لینک پشتیبانی نمی‌شود."); return }
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (pendingDownload != null) { message("درخواست قبلی را تکمیل کنید."); return }
            pendingDownload = info
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else enqueueDownload(info)
    }
    private fun enqueueDownload(info: DownloadInfo) {
        try {
            val uri = Uri.parse(info.url)
            val guessed = URLUtil.guessFileName(info.url, info.disposition, info.mime)
            val clean = guessed.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""),"_").trim().trim('.').ifBlank { "download_file" }
            val ext = clean.substringAfterLast(".","")
            val name = if (clean.length <= 120) clean else if (ext.length in 1..12) clean.substringBeforeLast(".").take(100)+"."+ext else clean.take(120)
            val request = DownloadManager.Request(uri)
                .setTitle(name)
                .setDescription("در حال دریافت فایل از کتابخانه")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,UUID.randomUUID().toString().take(8)+"_"+name)
            if (info.mime.isNotBlank()) request.setMimeType(info.mime)
            if (info.agent.isNotBlank()) request.addRequestHeader("User-Agent",info.agent)
            if (trusted(uri)) CookieManager.getInstance().getCookie(info.url)?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("Cookie",it) }
            val manager = getSystemService(DownloadManager::class.java) ?: error("DownloadManager unavailable")
            manager.enqueue(request)
            message("دانلود آغاز شد. وضعیت دریافت را در اعلان‌ها بررسی کنید.")
        } catch (e: Exception) { message("دانلود آغاز نشد. دوباره تلاش کنید.") }
    }
    private fun message(text: String) {
        toast?.cancel()
        toast = Toast.makeText(this,text,Toast.LENGTH_SHORT)
        toast?.show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        val web = Bundle()
        if (binding.webView.saveState(web) != null) outState.putBundle("web",web)
        pendingDownload?.let { d -> outState.putBundle("download",Bundle().apply { putString("url",d.url); putString("agent",d.agent); putString("disposition",d.disposition); putString("mime",d.mime) }) }
        failedUrl?.let { u -> outState.putString("errorUrl",u); outState.putString("errorTitle",binding.offlineTitle.text.toString()); outState.putString("errorDescription",binding.offlineDescription.text.toString()) }
        super.onSaveInstanceState(outState)
    }
    override fun onResume() { super.onResume(); binding.webView.onResume(); tools.resumeCamera() }
    override fun onPause() { tools.pauseCamera(); binding.webView.onPause(); CookieManager.getInstance().flush(); super.onPause() }
    override fun onDestroy() {
        tools.cancelScan()
        toast?.cancel()
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        binding.webView.stopLoading()
        binding.webView.setDownloadListener(null)
        binding.webView.webChromeClient = null
        binding.webView.webViewClient = WebViewClient()
        binding.swipeRefresh.removeView(binding.webView)
        binding.webView.destroy()
        super.onDestroy()
    }
}
