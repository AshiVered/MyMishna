package aiv.reem.mymishna

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BootReceiver — מופעל אחרי הפעלה מחדש של המכשיר
 * או אחרי עדכון של האפליקציה.
 * AlarmManager מאבד את כל ה-Alarms בריסטארט — זה משחזר אותם.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            NotificationHelper.createChannel(context)
            NotificationHelper.restoreAlarmIfNeeded(context)
        }
    }
}
