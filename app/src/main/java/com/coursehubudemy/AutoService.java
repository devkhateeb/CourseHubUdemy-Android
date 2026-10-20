package com.coursehubudemy;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Foreground service that owns the user's scan/post timers and shows live progress in a notification. */
public class AutoService extends Service {
    public static final String ACTION_START = "com.coursehubudemy.START";
    public static final String ACTION_STOP = "com.coursehubudemy.STOP";
    public static final String ACTION_RUN_NOW = "com.coursehubudemy.RUN_NOW";
    public static final String ACTION_TICK = "com.coursehubudemy.TICK";
    private static volatile boolean running = false;

    private ScheduledExecutorService ex;
    private boolean foregroundStarted = false;
    private volatile long lastShown = 0;

    public static boolean isRunning() { return running; }
    public static void start(Context c) {
        ContextCompat.startForegroundService(c, new Intent(c, AutoService.class).setAction(ACTION_START));
    }
    public static void stop(Context c) { c.stopService(new Intent(c, AutoService.class)); }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        SharedPreferences sp = AutoEngine.prefs(this);
        if (ACTION_STOP.equals(action)) {
            sp.edit().putBoolean("auto", false).apply();
            shutdown();
            return START_NOT_STICKY;
        }
        if (!foregroundStarted) {
            Notifier.ensureChannels(this);
            try {
                android.app.Notification n = Notifier.ongoing(this, "CourseHub is starting", "Preparing background timers…", -1, true);
                if (Build.VERSION.SDK_INT >= 34) startForeground(Notifier.ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                else startForeground(Notifier.ONGOING_ID, n);
                foregroundStarted = true;
            } catch (Exception e) {   // Android refused a background start; the WorkManager watchdog will cover this
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        running = true;
        if (!sp.getBoolean("auto", false)) { shutdown(); return START_NOT_STICKY; }
        if (ACTION_RUN_NOW.equals(action)) sp.edit().putLong("nextScanAt", 0).putLong("nextPostAt", 0).apply();
        if (ex == null) {
            ex = Executors.newSingleThreadScheduledExecutor();
            ex.scheduleWithFixedDelay(this::safeTick, 0, 30, TimeUnit.SECONDS);
        } else {
            ex.execute(this::safeTick);
        }
        return START_STICKY;
    }

    private void safeTick() {
        try { tick(); } catch (Throwable ignored) { /* never let the schedule die */ }
    }

    private void tick() {
        SharedPreferences sp = AutoEngine.prefs(this);
        if (!sp.getBoolean("auto", false)) { shutdown(); return; }
        if (AutoEngine.anyDue(sp)) {
            PowerManager.WakeLock wl = null;
            try {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CourseHub:work");
                wl.acquire(10 * 60 * 1000L);
            } catch (Exception ignored) {}
            try {
                show("Working…", "Starting run", -1, true);
                AutoEngine.Cycle c = AutoEngine.runDue(this, (phase, text, pct) -> {
                    long now = System.currentTimeMillis();
                    if (pct < 100 && now - lastShown < 700) return;   // keep notification updates calm
                    lastShown = now;
                    show("scan".equals(phase) ? "Scanning courses" : "Posting to Telegram", text, pct, true);
                });
                if (c.ran()) Notifier.result(this, "CourseHub run finished", AutoEngine.describe(c));
            } catch (Throwable t) {
                Notifier.result(this, "CourseHub run failed", String.valueOf(t.getMessage()));
            } finally {
                if (wl != null && wl.isHeld()) wl.release();
            }
        }
        show("CourseHub · Auto mode active", AutoEngine.idleText(sp), 0, false);
        scheduleNudge(AutoEngine.nextWake(sp));
    }

    private void show(String title, String text, int pct, boolean busy) {
        AutoEngine.prefs(this).edit().putString("autoStatus", title + " · " + text.replace('\n', ' ')).apply();
        try {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(Notifier.ONGOING_ID, Notifier.ongoing(this, title, text, pct, busy));
        } catch (Exception ignored) {}
    }

    /** Wall-clock alarm so the next run still fires on time when the CPU sleeps / Doze delays the executor. */
    private void scheduleNudge(long atMs) {
        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(this, AutoService.class).setAction(ACTION_TICK);
            PendingIntent pi = PendingIntent.getService(this, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long at = Math.max(atMs, System.currentTimeMillis() + 1000);
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (Exception ignored) {}
    }

    private void cancelNudge() {
        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(this, AutoService.class).setAction(ACTION_TICK);
            am.cancel(PendingIntent.getService(this, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } catch (Exception ignored) {}
    }

    private void shutdown() {
        cancelNudge();
        AutoEngine.prefs(this).edit().putString("autoStatus", "Background mode off").apply();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(Service.STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        running = false;
        if (ex != null) ex.shutdownNow();
        cancelNudge();
        super.onDestroy();
    }
}
