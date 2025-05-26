package com.example.test;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static CrashHandler instance;
    private Context context;
    private static final String TAG = "CrashHandler";
    private static final String CRASH_LOG_DIR = "crash_logs";
    private static final long RESTART_DELAY = 100; // 100ms

    public CrashHandler(Context context) {
        this.context = context.getApplicationContext();
    }

    public static CrashHandler getInstance(Context context) {
        if (instance == null) {
            instance = new CrashHandler(context);
        }
        return instance;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        Log.e(TAG, "Uncaught exception: " + ex.getMessage(), ex);
        
        // 保存崩溃日志
        saveCrashLog(ex);
        
        // 立即重启应用
        restartApp();
        
        // 结束当前进程
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }

    private void restartApp() {
        Log.d(TAG, "Restarting app...");
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, 
                    System.currentTimeMillis() + RESTART_DELAY, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, 
                    System.currentTimeMillis() + RESTART_DELAY, pendingIntent);
            }
        }
        
        // 发送广播作为备用重启方法
        context.sendBroadcast(new Intent("com.example.test.RESTART_APP"));
    }

    private void saveCrashLog(Throwable ex) {
        try {
            File logDir = new File(context.getExternalFilesDir(null), CRASH_LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
            File logFile = new File(logDir, "crash_" + timestamp + ".txt");
            
            FileWriter writer = new FileWriter(logFile);
            writer.write("Crash time: " + new Date().toString() + "\n");
            writer.write("Exception: " + ex.toString() + "\n");
            writer.write("Message: " + ex.getMessage() + "\n");
            writer.write("\nStack trace:\n");
            ex.printStackTrace(new java.io.PrintWriter(writer));
            writer.close();
            
            Log.d(TAG, "Crash log saved to: " + logFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to save crash log", e);
        }
    }
}