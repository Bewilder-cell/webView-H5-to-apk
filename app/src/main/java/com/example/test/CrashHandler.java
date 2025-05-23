package com.example.test;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Process;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.SystemClock;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashHandler";
    private static final String PREFS_NAME = "crash_prefs";
    private static final String KEY_LAST_CRASH_TIME = "last_crash_time";
    private static final long RESTART_COOLDOWN_MS = 5 * 1000; // 5秒冷却时间
    private static final int RESTART_DELAY_MS = 500; // 500毫秒重启延时

    private Context context;
    private Thread.UncaughtExceptionHandler defaultHandler;

    public CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        Log.e(TAG, "App crashed, handling uncaught exception...", ex);

        try {
            if (shouldRestart()) {
                restartAppImmediately();
            } else {
                Log.e(TAG, "App crashed too frequently, will not restart to avoid loop.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during crash handling", e);
        } finally {
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, ex);
            } else {
                Process.killProcess(Process.myPid());
            }
        }
    }

    private boolean shouldRestart() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCrash = prefs.getLong(KEY_LAST_CRASH_TIME, 0);
        long now = System.currentTimeMillis();

        if (now - lastCrash < RESTART_COOLDOWN_MS) {
            return false;
        }

        prefs.edit().putLong(KEY_LAST_CRASH_TIME, now).apply();
        return true;
    }

    private void restartAppImmediately() {
        Log.i(TAG, "Restarting app immediately...");
        
        // 方法1：直接启动（最快）
        try {
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart immediately", e);
        }

        // 方法2：使用 AlarmManager（备用方案）
        try {
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
            );

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    SystemClock.elapsedRealtime() + RESTART_DELAY_MS,
                    pendingIntent
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart with AlarmManager", e);
        }

        // 方法3：使用广播（最后的备用方案）
        try {
            Intent intent = new Intent("com.example.test.RESTART_APP");
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart with broadcast", e);
        }
    }
}
