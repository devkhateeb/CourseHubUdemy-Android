package com.coursehubudemy;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * Watchdog (runs every ~15 min). The real work is done by AutoService with the user's own timers.
 * If the service was killed, restart it; if Android refuses a background start, do any due work here instead.
 */
public class AutoWorker extends Worker {
    public AutoWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    @NonNull @Override public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences sp = AutoEngine.prefs(ctx);
        if (!sp.getBoolean("auto", false)) return Result.success();
        if (AutoService.isRunning()) return Result.success();
        try {
            AutoService.start(ctx);
            return Result.success();
        } catch (Exception blocked) {
            // fall through to running directly inside the worker
        }
        try {
            if (AutoEngine.anyDue(sp)) {
                AutoEngine.Cycle c = AutoEngine.runDue(ctx, null);
                if (c.ran()) Notifier.result(ctx, "CourseHub run finished", AutoEngine.describe(c));
            }
            return Result.success();
        } catch (Exception e) {
            sp.edit().putString("lastStatus", "Auto run failed: " + e.getMessage()).apply();
            return Result.retry();
        }
    }
}
