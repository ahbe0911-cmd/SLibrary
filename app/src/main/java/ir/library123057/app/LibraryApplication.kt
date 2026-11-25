package ir.library123057.app

import android.app.Application
import android.os.Build
import android.webkit.WebView

class LibraryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Separate storage/cookies/cache from the authenticated staff WebViews.
        // Must run before any WebView is initialized in the membership process.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            getProcessName() == "$packageName:membership") {
            WebView.setDataDirectorySuffix("membership")
        }
    }
}
