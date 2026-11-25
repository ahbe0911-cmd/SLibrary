package ir.library123057.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import ir.library123057.app.databinding.ActivityMainBinding
import org.json.JSONObject
import java.util.Locale

class LibraryTools(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val trusted: (Uri) -> Boolean,
    private val open: (String) -> Unit,
    private val message: (String) -> Unit
) {
    private val prefs = activity.getSharedPreferences("library_tools", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var scanUrl: String? = null
    private var scanning = false
    private var cameraRunning = false
    private var torch = false
    private val timeout = Runnable { if (scanning) { cancelScan(); message("بارکد پیدا نشد. دوباره تلاش کنید.") } }
    private val slotIds = listOf(R.id.navStatus, R.id.navLoan, R.id.navReturn)
    private val labels = listOf("وضعیت مدارک","امانت","بازگشت","تمدید یا عضویت جدید")
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && scanUrl != null) startCamera()
        else { cancelScan(); message("مجوز دوربین داده نشد؛ ورود دستی در دسترس است.") }
    }

    private fun libraryAddress(uri: Uri): Boolean {
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        return uri.scheme.equals("https",true) && uri.userInfo == null && uri.port in listOf(-1,443) &&
            (host == "samanpl.ir" || host.endsWith(".samanpl.ir"))
    }
    fun target(i: Int): String? = (prefs.getString("bookmark_$i",null)?.let { normalize(it) }
        ?: normalize(AppConfig.staffDefaults[i]))
    fun isSavedOrigin(uri: Uri): Boolean = libraryAddress(uri) && (0..3).any { i ->
        target(i)?.let { Uri.parse(it).host.equals(uri.host,true) } == true
    }
    private fun normalize(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        val url = if (value.startsWith("/") && !value.startsWith("//")) AppConfig.HOME_URL.trimEnd('/')+value
            else if (!value.contains("://")) "https://"+value else value
        val uri = Uri.parse(url)
        return url.takeIf { libraryAddress(uri) }
    }
    fun setup() {
        // Existing bookmark preferences keep the same keys when moved to bottom navigation.
        slotIds.forEachIndexed { i, id ->
            binding.bottomNavigation.findViewById<View>(id)?.setOnLongClickListener { edit(i); true }
        }
        binding.membershipButton.setOnClickListener {
            val url = target(3)
            if (url == null) edit(3) else open(url)
        }
        binding.membershipButton.setOnLongClickListener { edit(3); true }
        binding.headerRefresh.setOnLongClickListener {
            val hidden = prefs.getBoolean("hide_support",true)
            AlertDialog.Builder(activity).setTitle("تنظیمات نمایش")
                .setItems(arrayOf(if (hidden) "نمایش پنجره خدمات سایت" else "مخفی کردن پنجره خدمات سایت","کپی نشانی صفحه")) { _, which ->
                    if (which == 0) {
                        prefs.edit().putBoolean("hide_support",!hidden).apply()
                        binding.webView.reload()
                    } else {
                        val url = binding.webView.url.orEmpty()
                        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Library URL",url))
                        message("نشانی صفحه کپی شد.")
                    }
                }.show()
            true
        }
        binding.scanManual.setOnClickListener { manual() }
        binding.scanCancel.setOnClickListener { cancelScan() }
        binding.scanTorch.setOnClickListener {
            if (cameraRunning) {
                torch = !torch
                if (torch) binding.barcodeCamera.setTorchOn() else binding.barcodeCamera.setTorchOff()
            }
        }
        binding.barcodeCamera.barcodeView.decoderFactory = DefaultDecoderFactory(listOf(
            BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93, BarcodeFormat.CODABAR,
            BarcodeFormat.ITF, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
            BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX
        ))
        binding.barcodeCamera.setStatusText("")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(binding.webView,"SLibraryScan",setOf("https://samanpl.ir","https://*.samanpl.ir")) { view, msg, origin, mainFrame, _ ->
                val current = view.url?.let { Uri.parse(it) }
                if (mainFrame && current != null && trusted(current) && trusted(origin) &&
                    current.host.equals(origin.host,true) && msg.data == "scan") requestScan()
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            val script = """
                (function(){
                  if(self!==top)return;
                  var value='width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no';
                  function fix(){document.querySelectorAll('meta[name="viewport"]').forEach(function(v){if(v.content!==value)v.content=value;});}
                  var ob=new MutationObserver(fix);ob.observe(document,{childList:true,subtree:true});
                  document.addEventListener('DOMContentLoaded',function(){ob.disconnect();if(!document.querySelector('meta[name="viewport"]')&&document.head){var m=document.createElement('meta');m.name='viewport';m.content=value;document.head.appendChild(m);}fix();},{once:true});
                })();
            """.trimIndent()
            WebViewCompat.addDocumentStartJavaScript(binding.webView,script,setOf("https://samanpl.ir","https://*.samanpl.ir"))
        }
    }
    fun edit(i: Int) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            hint = "https://123057.samanpl.ir/..."
            setText(target(i).orEmpty())
            setSelectAllOnFocus(true)
        }
        val wrap = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val p = (20*resources.displayMetrics.density).toInt()
            setPadding(p,p,p,0)
            addView(input)
        }
        val dialog = AlertDialog.Builder(activity).setTitle("ویرایش میان‌بر "+labels[i])
            .setMessage("لینک سامانه کتابخانه را وارد کنید یا از صفحه فعلی استفاده کنید.")
            .setView(wrap).setPositiveButton("ذخیره",null)
            .setNeutralButton("لینک صفحه فعلی",null).setNegativeButton("انصراف",null).create()
        dialog.setOnShowListener {
            dialog.window?.decorView?.layoutDirection = View.LAYOUT_DIRECTION_RTL
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { input.setText(binding.webView.url.orEmpty()) }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = input.text.toString().trim()
                val normalized = normalize(raw)
                if (raw.isNotEmpty() && normalized == null) {
                    input.error = "فقط لینک HTTPS سامانه samanpl.ir مجاز است."
                } else {
                    if (normalized == null) prefs.edit().remove("bookmark_$i").apply()
                    else prefs.edit().putString("bookmark_$i",normalized).apply()
                    dialog.dismiss(); message("میان‌بر ذخیره شد.")
                }
            }
        }
        dialog.show()
    }
    fun requestScan() {
        if (scanning || scanUrl != null || activity.isFinishing || activity.isDestroyed) return
        val url = binding.webView.url ?: return
        if (!trusted(Uri.parse(url))) return
        scanUrl = url
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            cancelScan(); message("این دستگاه دوربین ندارد."); return
        }
        if (ContextCompat.checkSelfPermission(activity,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }
    private fun startCamera() {
        if (scanUrl != binding.webView.url) { cancelScan(); return }
        scanning = true
        binding.scanPanel.visibility = View.VISIBLE
        binding.scanPanel.bringToFront()
        binding.webView.clearFocus()
        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(binding.webView.windowToken,0)
        binding.barcodeCamera.decodeSingle(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                val code = result.text
                val url = scanUrl
                if (!scanning || code.isNullOrBlank()) return
                cancelScan()
                if (url == null || url != binding.webView.url || !trusted(Uri.parse(url))) return
                binding.webView.evaluateJavascript("(function(){return !!(window.__samanBarcode && window.__samanBarcode.fill("+JSONObject.quote(code)+"));})()") {
                    if (it == "true") {
                        message("بارکد خوانده شد: "+code)
                        runCatching {
                            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC,65)
                            tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,110)
                            handler.postDelayed({tone.release()},180)
                        }
                    } else message("فیلد بارکد پیدا نشد؛ دوباره روی آن بزنید.")
                }
            }
        })
        resumeCamera()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout,45000)
    }
    fun pauseCamera() {
        if (cameraRunning) {
            runCatching { binding.barcodeCamera.setTorchOff(); binding.barcodeCamera.pause() }
            cameraRunning = false; torch = false
        }
    }
    fun resumeCamera() {
        if (!scanning || cameraRunning || ContextCompat.checkSelfPermission(activity,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) return
        try { binding.barcodeCamera.resume(); cameraRunning=true }
        catch(e: Exception) { cancelScan(); message("دوربین در دسترس نیست.") }
    }
    fun cancelScan(): Boolean {
        val active = scanning || scanUrl != null
        handler.removeCallbacks(timeout)
        pauseCamera()
        scanning=false;scanUrl=null
        binding.scanPanel.visibility=View.GONE
        return active
    }
    private fun manual() {
        val url=scanUrl
        cancelScan()
        if (url != null && url == binding.webView.url) {
            binding.webView.requestFocus()
            binding.webView.evaluateJavascript("window.__samanBarcode && window.__samanBarcode.manual()") {
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(binding.webView,InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }
}
