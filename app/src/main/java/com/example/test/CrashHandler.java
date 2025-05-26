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
    
    // 修改重启间隔为1秒
    private static final long RESTART_COOLDOWN_MS = 1000; // 1秒冷却时间
    private static final int RESTART_DELAY_MS = 500; // 0.5秒重启延时
    private static final int MAX_CRASHES_PER_DAY = 100; // 增加最大崩溃次数

    private Context context;
    private Thread.UncaughtExceptionHandler defaultHandler;
    private static CrashHandler instance;

    public static synchronized CrashHandler getInstance(Context context) {
        if (instance == null) {
            instance = new CrashHandler(context);
        }
        return instance;
    }

    private CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        Log.e(TAG, "App crashed, handling uncaught exception...", ex);

        try {
            // 保存崩溃日志
            saveCrashLog(ex);
            
            // 直接重启，不检查崩溃次数
            restartAppImmediately();
            
        } catch (Exception e) {
            Log.e(TAG, "Error during crash handling", e);
        } finally {
            // 确保进程被终止
            Process.killProcess(Process.myPid());
            System.exit(1);
        }
    }

    private void restartAppImmediately() {
        Log.i(TAG, "Restarting app immediately...");
        
        try {
            // 使用 PendingIntent 启动 MainActivity
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
            );

            // 使用 AlarmManager 确保在系统休眠时也能重启
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        SystemClock.elapsedRealtime() + 100, // 减少延迟到100ms
                        pendingIntent
                    );
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        SystemClock.elapsedRealtime() + 100,
                        pendingIntent
                    );
                }
            }

            // 同时尝试直接启动
            try {
                context.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start activity directly", e);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to restart app", e);
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