# Keep WebView JavaScript interface methods
-keepclassmembers class aiv.reem.mymishna.AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep BroadcastReceiver
-keep class aiv.reem.mymishna.ReminderReceiver { *; }
-keep class aiv.reem.mymishna.BootReceiver { *; }
