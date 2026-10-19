package com.novelreader.app.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import java.net.URLEncoder

/** Languages offered in the translate dialog (Google Translate codes). Persian is the suggested default. */
data class TranslateLanguage(val code: String, val label: String)

sealed interface TranslateOutcome {
    data object Opened : TranslateOutcome
    data object NoInternet : TranslateOutcome
    data object NoHandler : TranslateOutcome
    /** Opened, but only the first [limit] characters could be sent via the web link. */
    data class OpenedTruncated(val limit: Int) : TranslateOutcome
}

/**
 * "Translate" for selected text. There is no free private translation API available to the app, so this uses
 * the official supported routes only:
 *  1. the installed Google Translate app (system PROCESS_TEXT intent), or
 *  2. https://translate.google.com in the browser with the text URL-encoded in the query string.
 * The caller must have shown the user a confirmation that the text leaves the device.
 */
object TranslateLauncher {

    const val TRANSLATE_PACKAGE = "com.google.android.apps.translate"

    /** Browsers/servers reject very long URLs; the web route is capped. The app route is not. */
    const val WEB_TEXT_LIMIT = 1_200

    fun isTranslateAppInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TRANSLATE_PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Builds the browser URL. Text is URL-encoded (UTF-8) so Persian/English/any script survives intact. */
    fun webUrl(text: String, targetLang: String): String {
        val enc = URLEncoder.encode(text, "UTF-8")
        return "https://translate.google.com/?sl=auto&tl=${URLEncoder.encode(targetLang, "UTF-8")}&text=$enc&op=translate"
    }

    /**
     * @param preferApp true = try the Google Translate app first (its own target-language setting applies);
     *                  false = always use the browser route so [targetLang] is honoured.
     */
    fun translate(context: Context, text: String, targetLang: String, preferApp: Boolean): TranslateOutcome {
        if (!hasInternet(context)) return TranslateOutcome.NoInternet

        if (preferApp && isTranslateAppInstalled(context)) {
            val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
                type = "text/plain"
                setPackage(TRANSLATE_PACKAGE)
                putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                return TranslateOutcome.Opened
            } catch (_: ActivityNotFoundException) {
                // fall through to the browser route
            }
        }

        val truncated = text.length > WEB_TEXT_LIMIT
        val payload = if (truncated) text.take(WEB_TEXT_LIMIT) else text
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl(payload, targetLang)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(view)
            if (truncated) TranslateOutcome.OpenedTruncated(WEB_TEXT_LIMIT) else TranslateOutcome.Opened
        } catch (_: ActivityNotFoundException) {
            TranslateOutcome.NoHandler
        }
    }
}
