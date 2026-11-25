package ir.library123057.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import java.util.Locale

class MembershipActivity : AppCompatActivity() {
    companion object { const val EXTRA_URL = "membership_url" }
    private var browser: WebView? = null
    private var bar: ProgressBar? = null
    private var startUrl = ""
    private var earlyViewport = false
    private val viewportScript = """(function(){if(self!==top)return;function fix(){if(!document.head)return false;var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}m.content='width=device-width, initial-scale=1';return true;}if(!fix()){var ob=new MutationObserver(function(){if(fix())ob.disconnect();});ob.observe(document,{childList:true,subtree:true});}document.addEventListener('DOMContentLoaded',fix,{once:true});})()"""
    private fun allowed(uri: Uri): Boolean {
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        return uri.scheme.equals("https",true) && uri.userInfo == null && uri.port in listOf(-1,443) &&
            (host == "samanpl.ir" || host.endsWith(".samanpl.ir"))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (!allowed(Uri.parse(startUrl))) { finish(); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(startUrl))) }
            finish(); return
        }
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(ContextCompat.getColor(this@MembershipActivity,R.color.surface))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8),0,dp(8),0)
        }
        val back = ImageButton(this).apply {
            setImageResource(R.drawable.ic_return_book)
            contentDescription = "بازگشت به برنامه اصلی"
            setBackgroundResource(android.R.color.transparent)
            setOnClickListener { finish() }
        }
        header.addView(back,LinearLayout.LayoutParams(dp(48),dp(52)))
        val title = TextView(this).apply {
            text = "تمدید یا عضویت جدید"
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@MembershipActivity,R.color.primary))
            typeface = Typeface.create(ResourcesCompat.getFont(this@MembershipActivity,R.font.vazir),Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        header.addView(title,LinearLayout.LayoutParams(0,dp(52),1f))
        val refresh = ImageButton(this).apply {
            setImageResource(R.drawable.ic_refresh)
            contentDescription = getString(R.string.refresh)
            setBackgroundResource(android.R.color.transparent)
            setOnClickListener { browser?.reload() }
        }
        header.addView(refresh,LinearLayout.LayoutParams(dp(48),dp(52)))
        root.addView(header)
        val progress = ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max=100
            progressTintList=android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this@MembershipActivity,R.color.accent))
        }
        bar=progress
        root.addView(progress,LinearLayout.LayoutParams(-1,dp(3)))
        val w=WebView(this)
        browser=w
        w.isFocusable=true
        w.isFocusableInTouchMode=true
        w.setOnTouchListener { v,event ->
            if(event.actionMasked==MotionEvent.ACTION_DOWN&&!v.hasFocus())v.requestFocus()
            false
        }
        root.addView(w,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        w.settings.apply {
            javaScriptEnabled=true
            domStorageEnabled=true
            databaseEnabled=true
            cacheMode=WebSettings.LOAD_DEFAULT
            useWideViewPort=true
            loadWithOverviewMode=true
            allowFileAccess=false
            allowContentAccess=false
            allowFileAccessFromFileURLs=false
            allowUniversalAccessFromFileURLs=false
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically=false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture=true
            setGeolocationEnabled(false)
            safeBrowsingEnabled=true
        }
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
            earlyViewport = runCatching { androidx.webkit.WebViewCompat.addDocumentStartJavaScript(w,viewportScript,setOf("https://samanpl.ir","https://*.samanpl.ir")); true }.getOrDefault(false)
        }
        CookieManager.getInstance().apply { setAcceptCookie(true);setAcceptThirdPartyCookies(w,false) }
        w.webChromeClient=object:WebChromeClient(){
            override fun onProgressChanged(view:WebView,newProgress:Int){
                progress.progress=newProgress
                progress.visibility=if(newProgress<100)View.VISIBLE else View.INVISIBLE
            }
        }
        w.webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                if(!request.isForMainFrame)return request.url.scheme?.lowercase(Locale.ROOT) !in setOf("https","about","data","blob")
                if(allowed(request.url))return false
                if(request.hasGesture()&&request.url.scheme.equals("https",true)&&request.url.userInfo==null) {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW,request.url).addCategory(Intent.CATEGORY_BROWSABLE)) }
                }
                return true
            }
            override fun onPageStarted(view:WebView,url:String?,favicon:android.graphics.Bitmap?){
                if(url!=null&&!allowed(Uri.parse(url)))view.stopLoading()
            }
            override fun onPageFinished(view:WebView,url:String?){
                if(!earlyViewport&&url!=null&&allowed(Uri.parse(url))&&view.url==url) {
                    view.evaluateJavascript("(function(){if(self!==top)return;var m=document.querySelector('meta[name=viewport]');if(!m&&document.head){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}if(m)m.content='width=device-width, initial-scale=1';})()",null)
                }
            }
            override fun onReceivedSslError(view:WebView,handler:SslErrorHandler,error:SslError){handler.cancel();Toast.makeText(this@MembershipActivity,R.string.ssl_error_title,Toast.LENGTH_SHORT).show()}
            override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){
                if(request.isForMainFrame)Toast.makeText(this@MembershipActivity,R.string.server_error_title,Toast.LENGTH_SHORT).show()
            }
        }
        onBackPressedDispatcher.addCallback(this,object:OnBackPressedCallback(true){
            override fun handleOnBackPressed(){if(w.canGoBack())w.goBack()else finish()}
        })
        val previous=savedInstanceState?.getBundle("membership_web")
        if(previous==null||w.restoreState(previous)==null)w.loadUrl(startUrl)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (allowed(Uri.parse(url)) && url != startUrl) {
            setIntent(intent)
            startUrl = url
            browser?.loadUrl(url)
        }
    }
    override fun onSaveInstanceState(outState:Bundle){
        val state=Bundle()
        if(browser?.saveState(state)!=null)outState.putBundle("membership_web",state)
        super.onSaveInstanceState(outState)
    }
    override fun onResume(){super.onResume();browser?.onResume()}
    override fun onPause(){browser?.onPause();if(browser!=null)CookieManager.getInstance().flush();super.onPause()}
    override fun onDestroy(){
        browser?.let { w ->
            w.stopLoading();w.webChromeClient=null;w.webViewClient=WebViewClient()
            (w.parent as? ViewGroup)?.removeView(w);w.destroy()
        }
        browser=null;super.onDestroy()
    }
}
