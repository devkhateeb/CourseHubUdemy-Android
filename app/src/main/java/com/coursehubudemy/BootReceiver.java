package com.coursehubudemy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the background service after a reboot or app update if automatic mode was on. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (!AutoEngine.prefs(c).getBoolean("auto", false)) return;
        try { AutoService.start(c); } catch (Exception ignored) { /* WorkManager watchdog will retry */ }
    }
}
