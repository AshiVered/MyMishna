package aiv.reem.mymishna

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.Calendar

object NotificationHelper {

    private const val CHANNEL_ID = "mishna_reminder_channel"
    private const val NOTIFICATION_ID = 1001
    private const val ALARM_REQUEST_CODE = 2001
    private const val PREF_NAME = "mishna_alarm_prefs"
    private const val PREF_HOUR = "alarm_hour"
    private const val PREF_MINUTE = "alarm_minute"
    private const val PREF_IS_SET = "alarm_is_set"

    // ─── יצירת Notification Channel (חובה מ-API 26) ──────────────────
    fun createChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "תזכורת לימוד משניות",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "תזכורת יומית לסיום לימוד המשניות היומי"
            enableVibration(true)
            enableLights(true)
        }
        mgr.createNotificationChannel(channel)
    }

    // ─── תיזמון Alarm יומי חוזר ───────────────────────────────────────
    fun scheduleDaily(context: Context, hour: Int, minute: Int) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // מחשב את ה-Calendar הבא לשעה הנתונה
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // אם השעה כבר עברה היום — מגדיר ליום הבא
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = buildAlarmIntent(context)

        // שומר את השעה ב-SharedPreferences כדי לשחזר אחרי ריסטארט
        saveAlarmPrefs(context, hour, minute)

        // setExactAndAllowWhileIdle — יפעיל גם במצב Doze
        alarmMgr.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            intent
        )
    }

    // ─── ביטול Alarm ──────────────────────────────────────────────────
    fun cancelAlarm(context: Context) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmMgr.cancel(buildAlarmIntent(context))
        clearAlarmPrefs(context)
    }

    // ─── בדיקה האם Alarm פעיל ────────────────────────────────────────
    fun isAlarmSet(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_IS_SET, false)
    }

    // ─── שחזור Alarm אחרי ריסטארט (נקרא מ-BootReceiver) ─────────────
    fun restoreAlarmIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_IS_SET, false)) return
        val hour = prefs.getInt(PREF_HOUR, -1)
        val minute = prefs.getInt(PREF_MINUTE, -1)
        if (hour < 0 || minute < 0) return
        scheduleDaily(context, hour, minute)
    }

    // ─── שליחת ההתראה עצמה (נקרא מ-ReminderReceiver) ─────────────────
    fun sendNotification(context: Context) {
        // פותח את האפליקציה בלחיצה על ההתראה
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("זמן ללמוד משניות! \uD83D\uDCDA")   // 📚
            .setContentText("הגיע הזמן ללימוד היומי שלך.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("הגיע הזמן ללימוד היומי שלך. לחץ כדי לפתוח את הלוח.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingOpen)
            .setAutoCancel(true)
            .build()

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, notif)

        // מתזמן מחדש ל-24 שעות הבאות (כי setExactAndAllowWhileIdle אינו חוזר)
        restoreAlarmIfNeeded(context)
    }

    // ─── עזרים פנימיים ────────────────────────────────────────────────
    private fun buildAlarmIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun saveAlarmPrefs(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().apply {
            putBoolean(PREF_IS_SET, true)
            putInt(PREF_HOUR, hour)
            putInt(PREF_MINUTE, minute)
            apply()
        }
    }

    private fun clearAlarmPrefs(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().apply {
            putBoolean(PREF_IS_SET, false)
            remove(PREF_HOUR)
            remove(PREF_MINUTE)
            apply()
        }
    }
}
