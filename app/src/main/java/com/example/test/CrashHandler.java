package com.example.test;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Process;
import android.util.Log;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashHandler";

    private static final String PREFS_NAME = "crash_prefs";
    private static final String KEY_LAST_CRASH_TIME = "last_crash_time";
    // 设置冷却时间，单位毫秒，比如 10 秒内不重启，避免死循环
    private static final long RESTART_COOLDOWN_MS = 10 * 1000;

    private Context context;
    private Thread.UncaughtExceptionHandler defaultHandler;

    public CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        Log.e(TAG, "App crashed, handling uncaught exception...", ex);

        if (shouldRestart()) {
            restartApp();
        } else {
            Log.e(TAG, "App crashed too frequently, will not restart to avoid loop.");
        }

        // 调用系统默认处理器（会杀掉进程）
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, ex);
        } else {
            // 如果没有默认处理器，则手动结束进程
            Process.killProcess(Process.myPid());
            System.exit(1);
        }
    }

    private boolean shouldRestart() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastCrash = prefs.getLong(KEY_LAST_CRASH_TIME, 0);
        long now = System.currentTimeMillis();

        if (now - lastCrash < RESTART_COOLDOWN_MS) {
            // 距离上次崩溃时间小于冷却时间，不重启
            return false;
        }

        // 记录这次崩溃时间
        prefs.edit().putLong(KEY_LAST_CRASH_TIME, now).apply();
        return true;
    }

    private void restartApp() {
        Log.i(TAG, "Restarting app...");

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        try {
            context.startActivity(intent);
            // 延迟杀进程，确保 Activity 能启动
            Thread.sleep(2000);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart app", e);
        }

        // 杀死当前进程
        Process.killProcess(Process.myPid());
        System.exit(1);
    }
}
