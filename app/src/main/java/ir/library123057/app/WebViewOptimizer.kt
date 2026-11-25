package ir.library123057.app

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.util.Locale
import java.util.WeakHashMap

class WebViewOptimizer(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("library_tools", Context.MODE_PRIVATE)
    private val css by lazy { read("web/mobile.css") }
    private val js by lazy { read("web/mobile.js") }
    private val reset by lazy { read("web/reset-search.js") }
    private val origins = setOf("https://samanpl.ir", "https://*.samanpl.ir")
    private val registrations = WeakHashMap<WebView, ScriptHandler>()
    private fun read(path: String): String? = runCatching {
        app.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.onFailure { Log.e("WebViewOptimizer", "Asset unavailable", it) }.getOrNull()
    private fun allowed(url: String?): Boolean {
        val u = url?.let { Uri.parse(it) } ?: return false
        val host = u.host.orEmpty().lowercase(Locale.ROOT)
        return u.scheme.equals("https", true) && (host == "samanpl.ir" || host.endsWith(".samanpl.ir")) &&
            u.userInfo == null && u.port in listOf(-1, 443)
    }
    private fun source(hideSupport: Boolean): String? {
        val c = css ?: return null
        val j = js ?: return null
        return "(function(){if(self!==top)return;const host=location.hostname.toLowerCase();" +
            "if(location.protocol!=='https:'||(location.port&&location.port!=='443')||!(host==='samanpl.ir'||host.endsWith('.samanpl.ir')))return;" +
            "const boot=function(){if(!document.head||!document.documentElement)return false;(" + j + ")(" +
            JSONObject.quote(c) + ",host," + hideSupport + ");return true;};" +
            "if(!boot()){const ob=new MutationObserver(function(){if(boot())ob.disconnect();});" +
            "ob.observe(document,{childList:true,subtree:true});document.addEventListener('DOMContentLoaded',function(){ob.disconnect();boot();},{once:true});}})();"
    }
    fun install(view: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        if (registrations.containsKey(view)) return
        val script = source(preferences.getBoolean("hide_support",true)) ?: return
        runCatching {
            registrations[view] = WebViewCompat.addDocumentStartJavaScript(view,script,origins)
        }.onFailure { Log.w("WebViewOptimizer","Document-start unavailable; using fallback",it) }
    }
    fun remove(view: WebView) {
        runCatching { registrations.remove(view)?.remove() }
    }
    fun updateSetting(view: WebView) {
        remove(view)
        install(view)
        if (allowed(view.url)) {
            val hidden = preferences.getBoolean("hide_support",true)
            view.evaluateJavascript("window.__samanMobile&&window.__samanMobile.setHideSupport($hidden);",null)
        }
    }
    fun resetSearch(view: WebView) {
        val url = view.url ?: return
        if (!allowed(url)) return
        val script = reset ?: return
        val host = Uri.parse(url).host.orEmpty().lowercase(Locale.ROOT)
        runCatching { view.evaluateJavascript("($script)("+JSONObject.quote(host)+");", null) }
            .onFailure { Log.w("WebViewOptimizer", "Search reset failed", it) }
    }
    // One fallback injection per navigation, only for old WebView providers.
    fun onFinished(view: WebView, url: String?) {
        if (!allowed(url) || !allowed(view.url) || view.url != url) return
        if (registrations.containsKey(view)) return
        val script = source(preferences.getBoolean("hide_support",true)) ?: return
        runCatching { view.evaluateJavascript(script,null) }
            .onFailure { Log.w("WebViewOptimizer","Fallback injection failed",it) }
    }
    fun resume(view: WebView) {
        if (allowed(view.url)) view.evaluateJavascript("window.__samanMobile&&window.__samanMobile.refresh();",null)
    }
}
