package aiv.reem.mymishna

import android.content.Context
import android.webkit.JavascriptInterface

/**
 * גשר בין JavaScript ב-WebView לקוד Android Native.
 *
 * הפונקציות המוגדרות כ-@JavascriptInterface ניתנות לקריאה
 * מ-JS דרך האובייקט הגלובלי `window.AndroidBridge`.
 */
class AndroidBridge(private val context: Context) {

    /**
     * נקרא מ-JS כדי לתזמן התראה יומית בשעה הנתונה.
     * @param timeStr שעה בפורמט "HH:mm" (לדוגמה: "20:00")
     */
    @JavascriptInterface
    fun scheduleNotification(timeStr: String) {
        if (timeStr.isBlank()) return
        val parts = timeStr.split(":")
        if (parts.size != 2) return
        val hour = parts[0].trim().toIntOrNull() ?: return
        val minute = parts[1].trim().toIntOrNull() ?: return
        NotificationHelper.scheduleDaily(context, hour, minute)
    }

    /**
     * נקרא מ-JS כדי לבטל התראות מתוזמנות.
     */
    @JavascriptInterface
    fun cancelNotification() {
        NotificationHelper.cancelAlarm(context)
    }

    /**
     * נקרא מ-JS כדי לבדוק אם קיימת התראה פעילה.
     * @return true אם יש Alarm פעיל
     */
    @JavascriptInterface
    fun isNotificationScheduled(): Boolean {
        return NotificationHelper.isAlarmSet(context)
    }
}
