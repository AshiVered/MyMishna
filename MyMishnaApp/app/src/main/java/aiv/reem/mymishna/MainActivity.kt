package aiv.reem.mymishna

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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

            // ─── WebViewClient: מונע ניווט החוצה ─────────────────
            wv.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val url = request.url.toString()
                    // מאפשרים רק file:// — כל קישור חיצוני נחסם
                    return !url.startsWith("file://")
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
