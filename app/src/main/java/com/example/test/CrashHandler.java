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
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashHandler";
    private static final String PREFS_NAME = "crash_prefs";
    private static final String KEY_LAST_CRASH_TIME = "last_crash_time";
    private static final String KEY_CRASH_COUNT = "crash_count";
    private static final String KEY_LAST_COUNT_RESET = "last_count_reset";
    private static final String CRASH_LOG_DIR = "crash_logs";
    
    // 修改重启间隔为30分钟
    private static final long RESTART_COOLDOWN_MS = 30 * 60 * 1000; // 30分钟冷却时间
    private static final int RESTART_DELAY_MS = 3000; // 5秒重启延时
    private static final int MAX_CRASHES_PER_DAY = 30; // 24小时内最大崩溃次数

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
            // 保存崩溃日志
            saveCrashLog(ex);
            
            if (shouldRestart()) {
                // 使用延迟重启
                restartAppWithDelay();
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
        
        // 检查是否需要重置崩溃计数
        long lastReset = prefs.getLong(KEY_LAST_COUNT_RESET, 0);
        if (now - lastReset > 24 * 60 * 60 * 1000) { // 24小时
            prefs.edit()
                .putInt(KEY_CRASH_COUNT, 0)
                .putLong(KEY_LAST_COUNT_RESET, now)
                .apply();
        }
        
        // 检查崩溃次数
        int crashCount = prefs.getInt(KEY_CRASH_COUNT, 0);
        if (crashCount >= MAX_CRASHES_PER_DAY) {
            Log.w(TAG, "Too many crashes in 24 hours");
            return false;
        }

        // 检查冷却时间
        if (now - lastCrash < RESTART_COOLDOWN_MS) {
            Log.w(TAG, "Too soon since last crash");
            return false;
        }

        // 更新崩溃时间和计数
        prefs.edit()
            .putLong(KEY_LAST_CRASH_TIME, now)
            .putInt(KEY_CRASH_COUNT, crashCount + 1)
            .apply();
            
        return true;
    }

    // 保留原有的立即重启方法
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

    // 新增延迟重启方法
    private void restartAppWithDelay() {
        Log.i(TAG, "Scheduling app restart in " + RESTART_DELAY_MS + "ms");
        
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
            Log.e(TAG, "Failed to schedule restart", e);
            // 如果延迟重启失败，尝试立即重启
            restartAppImmediately();
        }
    }

    private void saveCrashLog(Throwable ex) {
        try {
            // 创建崩溃日志目录
            File logDir = new File(context.getFilesDir(), CRASH_LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            // 生成日志文件名
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                .format(new Date());
            File logFile = new File(logDir, "crash_" + timestamp + ".txt");
            
            // 写入崩溃信息
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            
            String crashInfo = String.format(
                "Time: %s\n" +
                "Thread: %s\n" +
                "Exception: %s\n" +
                "Message: %s\n" +
                "Stack trace:\n%s",
                new Date(),
                Thread.currentThread().getName(),
                ex.getClass().getName(),
                ex.getMessage(),
                sw.toString()
            );
            
            // 保存到文件
            FileOutputStream fos = new FileOutputStream(logFile);
            fos.write(crashInfo.getBytes());
            fos.close();
            
            Log.i(TAG, "Crash log saved to: " + logFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error saving crash log", e);
        }
    }
}
