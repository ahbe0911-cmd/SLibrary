package ir.library123057.app

/**
 * Central navigation configuration.
 * Supply the full HTTPS addresses of the authenticated library pages here.
 * Unverified URLs are intentionally empty; long-press a tab or membership
 * button to configure it locally without a new APK.
 * Existing locally saved bookmark links take precedence over these defaults.
 */
object AppConfig {
    // Main public library portal.
    const val HOME_URL = BuildConfig.WEBSITE_URL

    // وضعیت مدارک: exact staff document status URL.
    const val STATUS_URL = ""

    // امانت: exact staff loan URL.
    const val LOAN_URL = ""

    // بازگشت: exact staff return URL.
    const val RETURN_URL = ""

    // تمدید یا عضویت جدید: exact membership/renewal URL.
    const val MEMBERSHIP_URL = ""

    val staffDefaults: List<String>
        get() = listOf(STATUS_URL, LOAN_URL, RETURN_URL, MEMBERSHIP_URL)
}
