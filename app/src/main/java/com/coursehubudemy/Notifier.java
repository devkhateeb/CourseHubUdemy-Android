package com.coursehubudemy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

/** All notification building lives here so the service and the watchdog worker look the same. */
final class Notifier {
    private Notifier() {}
    static final String CH_STATUS = "coursehub_status";
    static final String CH_RESULT = "coursehub_result";
    static final int ONGOING_ID = 4101;
    static final int RESULT_ID = 4102;

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel st = new NotificationChannel(CH_STATUS, "Background status & progress", NotificationManager.IMPORTANCE_LOW);
        st.setDescription("Shows that CourseHub is running and the live progress of scanning and posting.");
        st.setShowBadge(false);
        NotificationChannel rs = new NotificationChannel(CH_RESULT, "Run summaries", NotificationManager.IMPORTANCE_LOW);
        rs.setDescription("A short summary after each automatic scan/post run.");
        nm.createNotificationChannel(st);
        nm.createNotificationChannel(rs);
    }

    private static PendingIntent open(Context c) {
        Intent i = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent svc(Context c, String action, int code) {
        Intent i = new Intent(c, AutoService.class).setAction(action);
        return PendingIntent.getService(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static NotificationCompat.Builder base(Context c, String channel, String title, String text) {
        return new NotificationCompat.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat_course)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(c))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true);
    }

    /** The persistent foreground-service notification. pct &lt; 0 with busy=true shows an indeterminate bar. */
    static Notification ongoing(Context c, String title, String text, int pct, boolean busy) {
        NotificationCompat.Builder b = base(c, CH_STATUS, title, text)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        if (busy) {
            if (pct >= 0) b.setProgress(100, Math.min(100, pct), false);
            else b.setProgress(0, 0, true);
        }
        b.addAction(0, "Run now", svc(c, AutoService.ACTION_RUN_NOW, 11));
        b.addAction(0, "Stop", svc(c, AutoService.ACTION_STOP, 12));
        return b.build();
    }

    /** Summary of a finished run; replaces the previous summary instead of stacking. */
    static void result(Context c, String title, String text) {
        try {
            ensureChannels(c);
            Notification n = base(c, CH_RESULT, title, text)
                .setAutoCancel(true).setShowWhen(true).setWhen(System.currentTimeMillis()).build();
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(RESULT_ID, n);
        } catch (Exception ignored) {}
    }
}
