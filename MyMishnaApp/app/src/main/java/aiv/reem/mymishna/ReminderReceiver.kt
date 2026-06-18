package aiv.reem.mymishna

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * ReminderReceiver — מופעל ע"י AlarmManager בשעת התזכורת.
 * שולח את ההתראה דרך NotificationHelper.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.createChannel(context)
        NotificationHelper.sendNotification(context)
    }
}
