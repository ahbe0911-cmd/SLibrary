package ir.library123057.app

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import org.json.JSONObject
import java.util.Locale

class WebViewOptimizer(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("library_tools", Context.MODE_PRIVATE)
    private val css by lazy { read("web/mobile.css") }
    private val js by lazy { read("web/mobile.js") }
    private val reset by lazy { read("web/reset-search.js") }
    private fun read(path: String): String? = runCatching {
        app.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.onFailure { Log.e("WebViewOptimizer", "Asset unavailable", it) }.getOrNull()
    private fun allowed(url: String?): Boolean {
        val u = url?.let { Uri.parse(it) } ?: return false
        return u.scheme.equals("https", true) && u.host.orEmpty().lowercase(Locale.ROOT).let { it == "samanpl.ir" || it.endsWith(".samanpl.ir") } && u.userInfo == null && u.port in listOf(-1, 443)
    }
    private fun pageUrlOrCurrent(url: String?, view: WebView): String = url ?: view.url.orEmpty()
    private fun Boolean?.orFalse() = this == true
    fun resetSearch(view: WebView) {
        val url = view.url ?: return
        if (!allowed(url)) return
        val script = reset ?: return
        val host = Uri.parse(url).host.orEmpty().lowercase(Locale.ROOT)
        runCatching { view.evaluateJavascript("($script)("+JSONObject.quote(host)+");", null) }
            .onFailure { Log.w("WebViewOptimizer", "Search reset failed", it) }
    }
    fun apply(view: WebView, url: String?) {
        if (!allowed(url) || !allowed(view.url)) return
        val host = Uri.parse(pageUrlOrCurrent(url, view)).host.orEmpty().lowercase(Locale.ROOT)
        if (!view.url?.let { Uri.parse(it).host.equals(host,true) }.orFalse()) return
        val c = css ?: return
        val j = js ?: return
        runCatching {
            view.evaluateJavascript("($j)("+JSONObject.quote(c)+","+JSONObject.quote(host)+","+preferences.getBoolean("hide_support", true)+");") {
                Log.d("WebViewOptimizer", "Injection result: $it")
            }
        }.onFailure { Log.w("WebViewOptimizer", "Injection failed", it) }
    }
}
