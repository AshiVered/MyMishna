package aiv.reem.mymishna

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // ───── בקשת הרשאת התראות (Android 13+) ─────
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // אם המשתמש אישר — מנסים לשחזר Alarm קיים מה-SharedPreferences
            if (granted) {
                NotificationHelper.restoreAlarmIfNeeded(this)
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ─── WebView ───────────────────────────────────────────────
        webView = WebView(this).also { wv ->
            setContentView(wv)

            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true          // נדרש ל-localStorage
                allowFileAccess = true
                databaseEnabled = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
            }

            // ─── JS Bridge ────────────────────────────────────────
            wv.addJavascriptInterface(
                AndroidBridge(this),
                "AndroidBridge"
            )

            // ─── WebViewClient: מונע ניווט פנימית, פותח קישורים חיצוניים בדפדפן ───
            wv.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    if (url.startsWith("file://")) {
                        // ניווט פנימי בתוך האפליקציה — מותר, לא מיירטים
                        return false
                    }
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        // קישור חיצוני (ספריא, קהתי וכו') — מנסים דפדפן חיצוני,
                        // ואם אין כזה במכשיר, נופלים על תצוגה פנימית
                        openExternalOrFallback(this@MainActivity, url)
                        return true
                    }
                    // כל סכמה אחרת (mailto:, tel:, intent: וכו') — חסום לבטיחות
                    return true
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    // מזריקים את ה-JS Bridge hook אחרי שהדף נטען
                    injectBridgeHook(view)
                }
            }

            // WebChromeClient — נדרש כדי ש-console.log יעבוד בניפוי
            wv.webChromeClient = WebChromeClient()

            // טוען את ה-HTML מה-assets
            wv.loadUrl("file:///android_asset/mishna_app.html")
        }

        // ─── בקשת הרשאת POST_NOTIFICATIONS (API 33+) ────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                NotificationHelper.restoreAlarmIfNeeded(this)
            }
        } else {
            NotificationHelper.restoreAlarmIfNeeded(this)
        }

        // ─── יוצרים את ה-Notification Channel (חד-פעמי) ─────────
        NotificationHelper.createChannel(this)
    }

    /**
     * מנסה לפתוח קישור חיצוני (כמו קהתי) באפליקציה חיצונית — בדרך כלל דפדפן.
     * בודקים מראש (resolveActivity) אם יש בכלל אפליקציה שיודעת לטפל בקישורי
     * http/https, לפני שמנסים בפועל. אם אין — למשל מכשיר בלי דפדפן מותקן
     * כלל, או דפדפן שהושבת — נופלים בחזרה על תצוגה פנימית בתוך האפליקציה
     * עצמה (showInAppBrowserDialog), כך שהתוכן עדיין נגיש למשתמש בכל מקרה.
     */
    private fun openExternalOrFallback(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val canHandle = intent.resolveActivity(context.packageManager) != null

        if (canHandle) {
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                android.util.Log.e("MyMishna", "External app failed despite resolveActivity: $url", e)
                // נופלים לתצוגה הפנימית למטה במקום להיכשל בשקט
            }
        } else {
            android.util.Log.w("MyMishna", "No browser/app can handle external links — using in-app fallback: $url")
        }

        showInAppBrowserDialog(context, url)
    }

    /**
     * דיאלוג מסך-מלא עם WebView פנימי משלו, לתצוגת תוכן חיצוני (כגון קהתי)
     * כשאין דפדפן חיצוני זמין במכשיר. יש כפתור "✕ סגור" קבוע בפינה.
     * זה עובד תמיד, ללא תלות בקיומה של "אפליקציית דפדפן" נפרדת, כי מנוע
     * ה-WebView עצמו הוא חלק ממערכת ההפעלה ולא אפליקציה נפרדת שצריך להתקין.
     */
    private fun showInAppBrowserDialog(context: Context, url: String) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        val container = FrameLayout(context)

        val innerWebView = WebView(context)
        innerWebView.settings.javaScriptEnabled = true
        innerWebView.settings.domStorageEnabled = true
        innerWebView.settings.loadWithOverviewMode = true
        innerWebView.settings.useWideViewPort = true
        innerWebView.webViewClient = WebViewClient() // ניווט חופשי בתוך הדיאלוג הזה בלבד
        innerWebView.loadUrl(url)

        container.addView(
            innerWebView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val closeBtn = Button(context)
        closeBtn.text = "✕ סגור"
        closeBtn.setTextColor(Color.WHITE)
        closeBtn.setBackgroundColor(Color.parseColor("#2B4CB8"))
        closeBtn.setOnClickListener { dialog.dismiss() }

        val btnParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        btnParams.gravity = Gravity.TOP or Gravity.END
        btnParams.topMargin = 40
        btnParams.rightMargin = 24
        container.addView(closeBtn, btnParams)

        dialog.setContentView(container)
        dialog.setOnDismissListener {
            innerWebView.destroy()
        }
        dialog.show()
    }

    /**
     * מזריק hook קטן ל-JS שמסנכרן שמירת הגדרות תזכורת
     * עם ה-Android AlarmManager דרך AndroidBridge.
     * ה-HTML המקורי לא משתנה — ה-hook רץ רק בזמן ריצה.
     */
    private fun injectBridgeHook(view: WebView) {
        val js = """
            (function() {
                if (typeof AndroidBridge === 'undefined') return;
                
                // שומרים reference לפונקציה המקורית
                var _origSaveS = window.saveS;
                if (!_origSaveS || window._bridgeHooked) return;
                window._bridgeHooked = true;
                
                window.saveS = function() {
                    // קוראים לשמירה המקורית
                    _origSaveS.apply(this, arguments);
                    
                    // קוראים את ההגדרות מ-localStorage ומסנכרנים עם Android
                    try {
                        var raw = localStorage.getItem('mishna_v3');
                        if (!raw) return;
                        var state = JSON.parse(raw);
                        var reminder = state.reminder || {};
                        if (reminder.on && reminder.time) {
                            AndroidBridge.scheduleNotification(reminder.time);
                        } else {
                            AndroidBridge.cancelNotification();
                        }
                    } catch(e) {
                        console.log('AndroidBridge sync error: ' + e);
                    }
                };
                
                // סנכרון ראשוני — מיד אחרי הטעינה
                try {
                    var raw = localStorage.getItem('mishna_v3');
                    if (raw) {
                        var state = JSON.parse(raw);
                        var reminder = state.reminder || {};
                        if (reminder.on && reminder.time) {
                            AndroidBridge.scheduleNotification(reminder.time);
                        }
                    }
                } catch(e) {}
            })();
        """.trimIndent()

        view.evaluateJavascript(js, null)
    }

    // ─── Back Button: ניווט אחורה בתוך ה-WebView ────────────────
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
