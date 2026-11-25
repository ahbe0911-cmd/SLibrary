package ir.library123057.app

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import org.json.JSONObject
import java.util.Locale

class WebViewOptimizer(context: Context) {
    private val app = context.applicationContext
    private val host = Uri.parse(BuildConfig.WEBSITE_URL).host.orEmpty().lowercase(Locale.ROOT)
    private val css by lazy { read("web/mobile.css") }
    private val js by lazy { read("web/mobile.js") }
    private fun read(path: String): String? = runCatching {
        app.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.onFailure { Log.e("WebViewOptimizer", "Asset unavailable", it) }.getOrNull()
    private fun allowed(url: String?): Boolean {
        val u = url?.let { Uri.parse(it) } ?: return false
        return u.scheme.equals("https", true) && u.host.equals(host, true) && u.userInfo == null && u.port in listOf(-1, 443)
    }
    fun apply(view: WebView, url: String?) {
        if (!allowed(url) || !allowed(view.url)) return
        val c = css ?: return
        val j = js ?: return
        runCatching {
            view.evaluateJavascript("($j)("+JSONObject.quote(c)+","+JSONObject.quote(host)+");") {
                Log.d("WebViewOptimizer", "Injection result: $it")
            }
        }.onFailure { Log.w("WebViewOptimizer", "Injection failed", it) }
    }
}
